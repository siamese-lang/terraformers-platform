package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchReferenceRetriever {

    private final EmbeddingProvider embeddingProvider;
    private final OpenSearchKnnQueryBuilder queryBuilder;
    private final OpenSearchResponseParser responseParser;
    private final OpenSearchTransport transport;
    private final AnalysisRuntimeProperties properties;

    public OpenSearchReferenceRetriever(
            EmbeddingProvider embeddingProvider,
            OpenSearchKnnQueryBuilder queryBuilder,
            OpenSearchResponseParser responseParser,
            OpenSearchTransport transport,
            AnalysisRuntimeProperties properties
    ) {
        this.embeddingProvider = embeddingProvider;
        this.queryBuilder = queryBuilder;
        this.responseParser = responseParser;
        this.transport = transport;
        this.properties = properties;
    }

    public List<ReferenceDocument> retrieve(ReferenceQuery query) {
        requireRuntimeConfig();

        List<Float> vector = embeddingProvider.embed(query.text());
        int topK = Math.min(query.limit(), properties.getOpensearchTopK());
        List<ReferenceDocument> global = search(vector, topK, query.resourceTypes());
        if (query.resourceTypes().isEmpty()) {
            return global;
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        addCandidates(candidates, global, true);
        for (String resourceType : query.resourceTypes()) {
            addCandidates(candidates, search(vector, topK, List.of(resourceType)), false);
        }
        return select(candidates.values().stream().toList(), global, query.resourceTypes(), topK);
    }

    private List<ReferenceDocument> search(List<Float> vector, int topK, List<String> resourceTypes) {
        String body = queryBuilder.build(
                properties.getVectorFieldName(),
                properties.getContentFieldName(),
                vector,
                topK,
                properties.getCorpusVersion(),
                properties.getProviderVersion(),
                resourceTypes
        );
        URI uri = OpenSearchEndpoint.searchUri(properties.getOpensearchEndpoint(), properties.getIndexName());
        String response = transport.post(uri, body);
        return responseParser.parse(response, properties.getContentFieldName());
    }

    private void addCandidates(Map<String, Candidate> candidates, List<ReferenceDocument> documents, boolean global) {
        for (ReferenceDocument document : documents) {
            Candidate existing = candidates.get(document.id());
            if (existing == null) {
                candidates.put(document.id(), new Candidate(document, global, candidates.size()));
            } else if (global && !existing.global()) {
                candidates.put(document.id(), new Candidate(document, true, existing.discoveryOrder()));
            }
        }
    }

    private List<ReferenceDocument> select(
            List<Candidate> candidates,
            List<ReferenceDocument> global,
            List<String> resourceTypes,
            int limit
    ) {
        List<ReferenceDocument> selected = new java.util.ArrayList<>(global.stream().limit(limit).toList());
        Set<String> missing = new LinkedHashSet<>(resourceTypes);
        for (ReferenceDocument document : selected) {
            missing.removeAll(document.resourceTypes());
        }
        if (missing.isEmpty()) {
            return global.stream().limit(limit).toList();
        }

        List<Candidate> targeted = candidates.stream().filter(candidate -> !candidate.global()).toList();
        Set<String> selectedIds = selected.stream()
                .map(ReferenceDocument::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        while (!missing.isEmpty()) {
            Set<String> currentMissing = missing;
            Candidate best = targeted.stream()
                    .filter(candidate -> !selectedIds.contains(candidate.document().id()))
                    .filter(candidate -> coverage(candidate.document(), currentMissing) > 0)
                    .min(Comparator
                            .<Candidate>comparingInt(candidate -> coverage(candidate.document(), currentMissing))
                            .reversed()
                            .thenComparing(Comparator.comparingInt(
                                    (Candidate candidate) -> candidate.document().priority()).reversed())
                            .thenComparingInt(Candidate::discoveryOrder))
                    .orElse(null);
            if (best == null) {
                break;
            }
            if (selected.size() < limit) {
                selected.add(best.document());
            } else {
                int replacement = replacementIndex(selected, best.document(), resourceTypes);
                if (replacement < 0) {
                    break;
                }
                selectedIds.remove(selected.get(replacement).id());
                selected.set(replacement, best.document());
            }
            selectedIds.add(best.document().id());
            missing.removeAll(best.document().resourceTypes());
        }
        return List.copyOf(selected);
    }

    private int replacementIndex(
            List<ReferenceDocument> selected,
            ReferenceDocument replacement,
            List<String> resourceTypes
    ) {
        Set<String> existingCoverage = coveredResourceTypes(selected, resourceTypes);
        for (int index = selected.size() - 1; index >= 0; index--) {
            List<ReferenceDocument> proposed = new java.util.ArrayList<>(selected);
            proposed.set(index, replacement);
            if (coveredResourceTypes(proposed, resourceTypes).containsAll(existingCoverage)) {
                return index;
            }
        }
        return -1;
    }

    private Set<String> coveredResourceTypes(List<ReferenceDocument> documents, List<String> resourceTypes) {
        Set<String> covered = new LinkedHashSet<>();
        Set<String> requested = new LinkedHashSet<>(resourceTypes);
        for (ReferenceDocument document : documents) {
            for (String resourceType : document.resourceTypes()) {
                if (requested.contains(resourceType)) {
                    covered.add(resourceType);
                }
            }
        }
        return covered;
    }

    private int coverage(ReferenceDocument document, Set<String> uncovered) {
        return (int) document.resourceTypes().stream().distinct().filter(uncovered::contains).count();
    }

    private record Candidate(ReferenceDocument document, boolean global, int discoveryOrder) {
    }

    private void requireRuntimeConfig() {
        if (isBlank(properties.getOpensearchEndpoint())) {
            throw new IllegalStateException("terraformers.analysis.opensearch-endpoint must be set when OpenSearch retriever is enabled");
        }
        if (isBlank(properties.getIndexName())) {
            throw new IllegalStateException("terraformers.analysis.index-name must be set when OpenSearch retriever is enabled");
        }
        if (isBlank(properties.getVectorFieldName())) {
            throw new IllegalStateException("terraformers.analysis.vector-field-name must be set when OpenSearch retriever is enabled");
        }
        if (isBlank(properties.getContentFieldName())) {
            throw new IllegalStateException("terraformers.analysis.content-field-name must be set when OpenSearch retriever is enabled");
        }
        if (isBlank(properties.getCorpusVersion())) {
            throw new IllegalStateException("terraformers.analysis.corpus-version must be set when OpenSearch retriever is enabled");
        }
        if (isBlank(properties.getProviderVersion())) {
            throw new IllegalStateException("terraformers.analysis.provider-version must be set when OpenSearch retriever is enabled");
        }
        if (properties.getOpensearchTopK() <= 0) {
            throw new IllegalStateException("terraformers.analysis.opensearch-top-k must be positive for active retrieval");
        }
        if (properties.getExpectedVectorDimension() != null && properties.getExpectedVectorDimension() <= 0) {
            throw new IllegalStateException("terraformers.analysis.expected-vector-dimension must be positive when set");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
