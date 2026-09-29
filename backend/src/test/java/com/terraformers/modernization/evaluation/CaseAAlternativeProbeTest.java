package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaseAAlternativeProbeTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void fixtureFreezesSixSnapshotsAndCanonicalPositiveMappings() {
        CaseAAlternativeProbeFixture fixture = CaseAAlternativeProbeFixture.load(mapper,
                Path.of("../evaluation/case-a-a3-fixed-facts-v1.json"));
        assertThat(fixture.snapshots()).hasSize(6);
        assertThat(fixture.snapshots().stream().filter(s -> s.caseId().equals("arch-vpc-three-tier"))
                .map(CaseAAlternativeProbeFixture.Snapshot::sourceWorkflowRunId))
                .containsExactly(36574906125L, 36576165161L, 36577747809L);
        assertThat(fixture.snapshots().stream().skip(3).map(CaseAAlternativeProbeFixture.Snapshot::caseId))
                .containsExactly("arch-cloudfront-private-alb", "arch-private-aoss", "arch-s3-metadata-split");
    }

    @Test
    void controlQueryAndFiltersAreCurrentSemanticsAndSharedEmbeddingIsReused() throws Exception {
        ArchitectureRetrievalFacts facts = new ArchitectureRetrievalFacts("summary", List.of("component"),
                List.of("relationship"), List.of("aws_vpc"));
        AtomicInteger embeddingCalls = new AtomicInteger();
        List<String> requests = new ArrayList<>();
        CaseAAlternativeProbeRunner runner = runner(text -> {
            embeddingCalls.incrementAndGet(); return java.util.Collections.nCopies(1024, 0.25f);
        }, (uri, body) -> { requests.add(body); return "{\"hits\":{\"hits\":[]}}"; });
        EvaluationCase definition = positiveCase("case", List.of(), List.of());
        CaseAAlternativeProbeFixture fixture = fixture("case", facts);
        runner = runnerWithCases(runner, List.of(definition));
        CaseAAlternativeProbeReport.SnapshotResult result = runner.run(fixture, "a".repeat(40), "model").snapshots().get(0);

        ReferenceQuery productionQuery = new ReferenceQuery(new RetrievalQueryTextBuilder().build(facts), 8);
        assertThat(result.currentQuery()).isEqualTo(productionQuery.text());
        assertThat(result.currentQueryResourceFilters()).isEqualTo(productionQuery.resourceTypes());
        assertThat(embeddingCalls).hasValue(12); // exactly twice for each of six snapshots
        JsonNode control = mapper.readTree(requests.get(0));
        JsonNode wide = mapper.readTree(requests.get(1));
        JsonNode unfiltered = mapper.readTree(requests.get(2));
        assertThat(control.at("/query/knn/embedding/vector")).isEqualTo(wide.at("/query/knn/embedding/vector"));
        assertThat(wide.at("/query/knn/embedding/vector")).isEqualTo(unfiltered.at("/query/knn/embedding/vector"));
        assertThat(control.at("/size").asInt()).isEqualTo(8);
        assertThat(control.at("/query/knn/embedding/filter").toString())
                .contains("\"resourceTypes\"").contains("aws_vpc");
        assertThat(unfiltered.at("/query/knn/embedding/filter").toString())
                .doesNotContain("\"resourceTypes\"");
    }

    @Test
    void rerankPoliciesAreGenericAndDeterministic() {
        ReferenceDocument first = doc("first", 5, List.of("aws_vpc"));
        ReferenceDocument priority = doc("priority", 10, List.of("aws_lb"));
        ReferenceDocument both = doc("both", 10, List.of("aws_vpc", "aws_lb"));
        List<ReferenceDocument> candidates = List.of(first, priority, both);
        assertThat(CaseAAlternativeProbeRunner.priorityRerank(candidates, 3))
                .extracting(ReferenceDocument::id).containsExactly("priority", "both", "first");
        assertThat(CaseAAlternativeProbeRunner.resourceCoverageRerank(candidates,
                List.of("aws_vpc", "aws_lb"), 3)).extracting(ReferenceDocument::id)
                .containsExactly("both", "first", "priority");
        assertThat(CaseAAlternativeProbeRunner.resourceCoverageRerank(candidates, List.of(), 2))
                .extracting(ReferenceDocument::id).containsExactly("first", "priority");

        ReferenceDocument low = doc("low", 1, List.of("aws_db_instance"));
        ReferenceDocument high = doc("high", 2, List.of("aws_db_instance"));
        assertThat(CaseAAlternativeProbeRunner.resourceCoverageRerank(List.of(low, high),
                List.of("aws_db_instance"), 1)).extracting(ReferenceDocument::id).containsExactly("high");
        ReferenceDocument expectationOnly = doc("expectation-only", 100, List.of("aws_db_instance"));
        ReferenceDocument queryType = doc("query-type", 1, List.of("aws_vpc"));
        assertThat(CaseAAlternativeProbeRunner.resourceCoverageRerank(List.of(expectationOnly, queryType),
                List.of("aws_vpc"), 1)).extracting(ReferenceDocument::id).containsExactly("query-type");
    }

    @Test
    void relationshipQueryUsesFactsAndExactScoringDoesNotFuzzyMatch() {
        ArchitectureRetrievalFacts facts = new ArchitectureRetrievalFacts("summary", List.of("component"),
                List.of("relationship"), List.of("aws_db_instance"));
        String query = CaseAAlternativeProbeRunner.relationshipFirstQuery(facts);
        assertThat(query).isEqualTo("Architecture relationships: relationship. Terraform resource candidates: "
                + "aws_db_instance. Architecture summary: summary. Components: component.");
        assertThat(query).doesNotContain("tfref-");

        EvaluationCase definition = positiveCase("case", List.of(), List.of("aws_db_instance", "aws_security_group"));
        String response = response(doc("wrong", 1,
                List.of("aws_db_subnet_group", "aws_vpc_security_group_ingress_rule")));
        CaseAAlternativeProbeRunner runner = runnerWithCases(runner(text -> java.util.Collections.nCopies(1024, 1f),
                (uri, body) -> response), List.of(definition));
        CaseAAlternativeProbeFixture fixture = fixture("case", facts);
        assertThat(runner.run(fixture, "a".repeat(40), "model").snapshots().get(0).strategies().get(0)
                .resourceTypeCoverage().matched()).isZero();
    }

    @Test
    void reportContractContainsNoTailPercentiles() {
        String fields = java.util.stream.Stream.of(CaseAAlternativeProbeReport.class,
                        CaseAAlternativeProbeReport.SnapshotResult.class,
                        CaseAAlternativeProbeReport.StrategyResult.class,
                        CaseAAlternativeProbeReport.Aggregate.class, CaseAAlternativeProbeReport.Range.class)
                .flatMap(type -> java.util.Arrays.stream(type.getRecordComponents()))
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.joining(","));
        assertThat(fields)
                .doesNotContainIgnoringCase("p95").doesNotContainIgnoringCase("p99");
    }

    private CaseAAlternativeProbeFixture fixture(String caseId, ArchitectureRetrievalFacts facts) {
        return new CaseAAlternativeProbeFixture("case-a-a3-fixed-facts-v1", "terraformers-eval-v1",
                java.util.stream.IntStream.range(0, 6).mapToObj(i ->
                        new CaseAAlternativeProbeFixture.Snapshot("snapshot-" + i, i + 1, caseId, facts)).toList());
    }
    private CaseAAlternativeProbeRunner runner(com.terraformers.modernization.reference.EmbeddingProvider embedding,
            com.terraformers.modernization.reference.opensearch.OpenSearchTransport transport) {
        return runnerWithCases(new CaseAAlternativeProbeRunner(embedding, new OpenSearchKnnQueryBuilder(mapper), transport,
                new OpenSearchResponseParser(mapper), properties(), new RetrievalQueryTextBuilder(), List.of()), List.of());
    }

    private CaseAAlternativeProbeRunner runnerWithCases(CaseAAlternativeProbeRunner original, List<EvaluationCase> cases) {
        try {
            var e = CaseAAlternativeProbeRunner.class.getDeclaredField("embeddings"); e.setAccessible(true);
            var t = CaseAAlternativeProbeRunner.class.getDeclaredField("transport"); t.setAccessible(true);
            return new CaseAAlternativeProbeRunner((com.terraformers.modernization.reference.EmbeddingProvider)e.get(original),
                    new OpenSearchKnnQueryBuilder(mapper),
                    (com.terraformers.modernization.reference.opensearch.OpenSearchTransport)t.get(original),
                    new OpenSearchResponseParser(mapper), properties(), new RetrievalQueryTextBuilder(), cases);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private AnalysisRuntimeProperties properties() { AnalysisRuntimeProperties p=new AnalysisRuntimeProperties(); p.setOpensearchEndpoint("http://example"); p.setIndexName("index"); p.setVectorFieldName("embedding"); p.setContentFieldName("content"); p.setCorpusVersion("terraformers-reference-v3"); p.setProviderVersion("5.100.0"); p.setExpectedVectorDimension(1024); p.setOpensearchTopK(8); return p; }
    private ReferenceDocument doc(String id,int priority,List<String> resources) { return new ReferenceDocument(id,id,"content",1,"",resources,"","5.100.0","terraformers-reference-v3","AUTH",priority,List.of()); }
    private String response(ReferenceDocument d) { return "{\"hits\":{\"hits\":[{\"_score\":1,\"_source\":{\"documentId\":\""+d.id()+"\",\"content\":\"content\",\"resourceTypes\":"+write(d.resourceTypes())+"}}]}}"; }
    private String write(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new AssertionError(e);} }
    private EvaluationCase positiveCase(String id,List<String> decisions,List<String> resources) { return new EvaluationCase("v","d",id,new EvaluationCase.InputFixture("x","y","z"),EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,null,null,null,new EvaluationCase.RetrievalExpectation(List.of(),List.of(),resources,decisions,List.of()),null,EvaluationCase.ValidationExpectation.PASS,List.of()); }
}
