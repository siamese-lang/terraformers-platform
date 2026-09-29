package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.net.URI;
import java.util.ArrayList;
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
        Set<String> uncovered = new LinkedHashSet<>(resourceTypes);
        global.forEach(document -> uncovered.removeAll(document.resourceTypes()));
        if (uncovered.isEmpty()) {
            return global.stream().limit(limit).toList();
        }

        uncovered = new LinkedHashSet<>(resourceTypes);
        List<Candidate> remaining = new ArrayList<>(candidates);
        List<ReferenceDocument> selected = new ArrayList<>();
        while (selected.size() < limit) {
            Set<String> currentUncovered = uncovered;
            Candidate best = remaining.stream()
                    .filter(candidate -> coverage(candidate.document(), currentUncovered) > 0)
                    .min(Comparator
                            .<Candidate>comparingInt(candidate -> coverage(candidate.document(), currentUncovered))
                            .reversed()
                            .thenComparing(Comparator.comparingInt(
                                    (Candidate candidate) -> candidate.document().priority()).reversed())
                            .thenComparingInt(Candidate::discoveryOrder))
                    .orElse(null);
            if (best == null) {
                break;
            }
            selected.add(best.document());
            remaining.remove(best);
            uncovered.removeAll(best.document().resourceTypes());
        }

        Set<String> selectedIds = selected.stream()
                .map(ReferenceDocument::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (ReferenceDocument document : global) {
            if (selected.size() == limit) {
                break;
            }
            if (selectedIds.add(document.id())) {
                selected.add(document);
            }
        }
        remaining.stream()
                .sorted(Comparator.comparingInt(Candidate::discoveryOrder))
                .map(Candidate::document)
                .filter(document -> selectedIds.add(document.id()))
                .limit(limit - selected.size())
                .forEach(selected::add);
        return List.copyOf(selected);
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
