package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeFixture.Snapshot;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeReport.Aggregate;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeReport.Hit;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeReport.Range;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeReport.SnapshotResult;
import com.terraformers.modernization.evaluation.CaseAAlternativeProbeReport.StrategyResult;
import com.terraformers.modernization.evaluation.RetrievalGroundingAssessment.Coverage;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchEndpoint;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import com.terraformers.modernization.reference.opensearch.OpenSearchTransport;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** Evaluation-only implementation of the six frozen A3 strategies. */
public final class CaseAAlternativeProbeRunner {
    public static final int CONTROL_K = 8;
    public static final int DIAGNOSTIC_K = 24;

    private final EmbeddingProvider embeddings;
    private final OpenSearchKnnQueryBuilder queryBuilder;
    private final OpenSearchTransport transport;
    private final OpenSearchResponseParser responseParser;
    private final AnalysisRuntimeProperties properties;
    private final RetrievalQueryTextBuilder textBuilder;
    private final Map<String, EvaluationCase> cases;

    public CaseAAlternativeProbeRunner(EmbeddingProvider embeddings, OpenSearchKnnQueryBuilder queryBuilder,
            OpenSearchTransport transport, OpenSearchResponseParser responseParser, AnalysisRuntimeProperties properties,
            RetrievalQueryTextBuilder textBuilder, List<EvaluationCase> cases) {
        this.embeddings = embeddings; this.queryBuilder = queryBuilder; this.transport = transport;
        this.responseParser = responseParser; this.properties = properties; this.textBuilder = textBuilder;
        this.cases = cases.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(EvaluationCase::caseId, c -> c));
    }

    public CaseAAlternativeProbeReport run(CaseAAlternativeProbeFixture fixture, String sourceCommit,
            String embeddingModel) {
        List<SnapshotResult> results = fixture.snapshots().stream().map(this::runSnapshot).toList();
        if (fixture.snapshots().stream().anyMatch(snapshot -> !cases.containsKey(snapshot.caseId()))) {
            throw new IllegalArgumentException("every snapshot must map to the canonical dataset");
        }
        return new CaseAAlternativeProbeReport("case-a-a3-retrieval-probe-v1", sourceCommit,
                fixture.datasetVersion(), properties.getCorpusVersion(), properties.getProviderVersion(), embeddingModel,
                properties.getExpectedVectorDimension(), results, aggregate(results));
    }

    private SnapshotResult runSnapshot(Snapshot snapshot) {
        EvaluationCase definition = requireCase(snapshot.caseId());
        String currentText = textBuilder.build(snapshot.facts());
        ReferenceQuery current = new ReferenceQuery(currentText, CONTROL_K);
        List<Float> currentVector = checkedEmbedding(currentText);
        Search control = search(currentVector, current.resourceTypes(), CONTROL_K);
        Search wide = search(currentVector, current.resourceTypes(), DIAGNOSTIC_K);
        Search unfiltered = search(currentVector, List.of(), DIAGNOSTIC_K);
        String relationshipText = relationshipFirstQuery(snapshot.facts());
        List<Float> relationshipVector = checkedEmbedding(relationshipText);
        Search relationship = search(relationshipVector, current.resourceTypes(), DIAGNOSTIC_K);

        List<StrategyResult> strategies = List.of(
                result("CONTROL_K8", 8, 8, "CURRENT_RESOURCE_TYPES", control, control.documents(), definition),
                result("WIDE_K24_DIAGNOSTIC", 24, 24, "CURRENT_RESOURCE_TYPES", wide, wide.documents(), definition),
                result("UNFILTERED_K24_DIAGNOSTIC", 24, 24, "NONE", unfiltered, unfiltered.documents(), definition),
                result("PRIORITY_RERANK_K24_TO8", 24, 8, "CURRENT_RESOURCE_TYPES", wide,
                        priorityRerank(wide.documents(), 8), definition),
                result("RESOURCE_COVERAGE_RERANK_K24_TO8", 24, 8, "CURRENT_RESOURCE_TYPES", wide,
                        resourceCoverageRerank(wide.documents(), current.resourceTypes(), 8), definition),
                result("RELATIONSHIP_FIRST_K24_TO8", 24, 8, "CURRENT_RESOURCE_TYPES", relationship,
                        relationship.documents().stream().limit(8).toList(), definition));
        return new SnapshotResult(snapshot.snapshotId(), snapshot.sourceWorkflowRunId(), snapshot.caseId(),
                snapshot.facts(), currentText, current.resourceTypes(), embeddingHash(currentVector), relationshipText,
                embeddingHash(relationshipVector), strategies);
    }

    Search search(List<Float> vector, List<String> resourceTypes, int k) {
        String body = queryBuilder.build(properties.getVectorFieldName(), properties.getContentFieldName(), vector, k,
                properties.getCorpusVersion(), properties.getProviderVersion(), resourceTypes);
        long start = System.nanoTime();
        String response = transport.post(OpenSearchEndpoint.searchUri(properties.getOpensearchEndpoint(),
                properties.getIndexName()), body);
        long latency = (System.nanoTime() - start) / 1_000_000;
        return new Search(responseParser.parse(response, properties.getContentFieldName()), latency);
    }

    private StrategyResult result(String id, int candidateK, int selectedK, String filterMode, Search search,
            List<ReferenceDocument> selected, EvaluationCase definition) {
        List<Hit> candidates = hits(search.documents());
        List<Hit> selections = hits(selected);
        Coverage decisions = coverage(definition.retrieval().requiredProjectDecisionIds(), selected,
                (required, document) -> required.equals(document.id()));
        Coverage resources = coverage(definition.retrieval().requiredResourceTypes(), selected,
                (required, document) -> document.resourceTypes().contains(required));
        return new StrategyResult(id, candidateK, selectedK, filterMode, search.latencyMs(), candidates.size(),
                selections.size(), candidates, selections, contentCharacters(search.documents()),
                contentCharacters(selected), decisions, resources,
                decisions.matched() == decisions.total() && resources.matched() == resources.total());
    }

    static List<ReferenceDocument> priorityRerank(List<ReferenceDocument> candidates, int limit) {
        Map<ReferenceDocument, Integer> ranks = originalRanks(candidates);
        return candidates.stream().sorted(Comparator.comparingInt(ReferenceDocument::priority).reversed()
                .thenComparingInt(ranks::get)).limit(limit).toList();
    }

    static List<ReferenceDocument> resourceCoverageRerank(List<ReferenceDocument> candidates,
            List<String> queryResourceTypes, int limit) {
        if (queryResourceTypes == null || queryResourceTypes.isEmpty()) return candidates.stream().limit(limit).toList();
        Set<String> uncovered = new LinkedHashSet<>(queryResourceTypes);
        List<ReferenceDocument> remaining = new ArrayList<>(candidates);
        List<ReferenceDocument> selected = new ArrayList<>();
        Map<ReferenceDocument, Integer> ranks = originalRanks(candidates);
        while (selected.size() < limit && !remaining.isEmpty()) {
            ReferenceDocument best = remaining.stream().filter(d -> coverageCount(d, uncovered) > 0)
                    .min(Comparator.<ReferenceDocument>comparingInt(d -> coverageCount(d, uncovered)).reversed()
                            .thenComparing(Comparator.comparingInt(ReferenceDocument::priority).reversed())
                            .thenComparingInt(ranks::get)).orElse(null);
            if (best == null) break;
            selected.add(best); remaining.remove(best); uncovered.removeAll(best.resourceTypes());
        }
        remaining.stream().sorted(Comparator.comparingInt(ranks::get)).limit(limit - selected.size()).forEach(selected::add);
        return List.copyOf(selected);
    }

    static String relationshipFirstQuery(ArchitectureRetrievalFacts facts) {
        return "Architecture relationships: %s. Terraform resource candidates: %s. Architecture summary: %s. Components: %s."
                .formatted(join(facts.relationships()), join(facts.resourceTypes()), facts.summary(), join(facts.components()));
    }

    static String embeddingHash(List<Float> vector) {
        try {
            ByteBuffer bytes = ByteBuffer.allocate(vector.size() * Float.BYTES);
            vector.forEach(value -> bytes.putInt(Float.floatToIntBits(value)));
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
        } catch (Exception exception) { throw new IllegalStateException("could not hash embedding", exception); }
    }

    private List<Float> checkedEmbedding(String text) {
        List<Float> vector = embeddings.embed(text);
        if (vector.size() != properties.getExpectedVectorDimension()) {
            throw new IllegalStateException("embedding dimension mismatch: " + vector.size());
        }
        return vector;
    }

    private Map<String, Aggregate> aggregate(List<SnapshotResult> snapshots) {
        Map<String, Aggregate> result = new LinkedHashMap<>();
        for (String strategy : snapshots.get(0).strategies().stream().map(StrategyResult::strategyId).toList()) {
            List<StrategyResult> values = snapshots.stream().map(s -> s.strategies().stream()
                    .filter(v -> v.strategyId().equals(strategy)).findFirst().orElseThrow()).toList();
            int vpcComplete = (int) java.util.stream.IntStream.range(0, snapshots.size())
                    .filter(i -> snapshots.get(i).caseId().equals("arch-vpc-three-tier") && values.get(i).complete()).count();
            int regressions = (int) java.util.stream.IntStream.range(0, snapshots.size())
                    .filter(i -> !snapshots.get(i).caseId().equals("arch-vpc-three-tier") && !values.get(i).complete()).count();
            result.put(strategy, new Aggregate(vpcComplete, 3, regressions, 3,
                    values.stream().mapToInt(v -> v.projectDecisionCoverage().matched()).sum(),
                    values.stream().mapToInt(v -> v.projectDecisionCoverage().total()).sum(),
                    values.stream().mapToInt(v -> v.resourceTypeCoverage().matched()).sum(),
                    values.stream().mapToInt(v -> v.resourceTypeCoverage().total()).sum(),
                    range(values.stream().mapToLong(StrategyResult::openSearchLatencyMs).toArray()),
                    range(values.stream().mapToLong(StrategyResult::candidateContentCharacters).toArray()),
                    range(values.stream().mapToLong(StrategyResult::selectedContentCharacters).toArray())));
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private EvaluationCase requireCase(String id) {
        EvaluationCase value = cases.get(id);
        if (value == null || value.expectedClassification() != EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM)
            throw new IllegalArgumentException("snapshot must map to canonical positive case: " + id);
        return value;
    }
    private static Range range(long[] input) { java.util.Arrays.sort(input); return new Range(input[0], input[input.length / 2], input[input.length - 1]); }
    private static int coverageCount(ReferenceDocument document, Set<String> uncovered) { return (int) document.resourceTypes().stream().filter(uncovered::contains).distinct().count(); }
    private static Map<ReferenceDocument, Integer> originalRanks(List<ReferenceDocument> values) { Map<ReferenceDocument, Integer> ranks = new java.util.IdentityHashMap<>(); for (int i=0;i<values.size();i++) ranks.put(values.get(i), i); return ranks; }
    private static String join(List<String> values) { return String.join(", ", values); }
    private static long contentCharacters(List<ReferenceDocument> values) { return values.stream().mapToLong(v -> v.content().length()).sum(); }
    private static List<Hit> hits(List<ReferenceDocument> documents) { return java.util.stream.IntStream.range(0, documents.size()).mapToObj(i -> { ReferenceDocument d=documents.get(i); return new Hit(i+1,d.id(),d.score(),d.authority(),d.priority(),d.resourceTypes(),d.riskTags(),d.content().length()); }).toList(); }
    private static Coverage coverage(List<String> required, List<ReferenceDocument> documents, BiPredicate<String, ReferenceDocument> predicate) { Map<String,Integer> ranks=new LinkedHashMap<>(); for(String item:required) java.util.stream.IntStream.range(0,documents.size()).filter(i->predicate.test(item,documents.get(i))).findFirst().ifPresent(i->ranks.put(item,i+1)); List<String> missing=required.stream().filter(v->!ranks.containsKey(v)).toList(); return new Coverage(ranks.size(),required.size(),required.isEmpty()?null:(double)ranks.size()/required.size(),java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ranks)),missing); }
    record Search(List<ReferenceDocument> documents, long latencyMs) { Search { documents=List.copyOf(documents); } }
}
