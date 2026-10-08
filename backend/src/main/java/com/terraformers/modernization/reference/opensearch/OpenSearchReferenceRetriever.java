package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchReferenceRetriever {

    private final EmbeddingProvider embeddingProvider;
    private final OpenSearchKnnQueryBuilder queryBuilder;
    private final OpenSearchResponseParser responseParser;
    private final OpenSearchTransport transport;
    private final AnalysisRuntimeProperties properties;
    private final ReferenceEvidenceSelector selector = new ReferenceEvidenceSelector();

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
        int baseTopK = Math.min(query.limit(), properties.getOpensearchTopK());
        int maxEvidence = Math.min(query.limit(), properties.getOpensearchMaxEvidence());
        if (query.resourceOnly()) {
            Map<String, ReferenceEvidenceSelector.Candidate> closure = new LinkedHashMap<>();
            for (String resourceType : query.resourceTypes()) {
                addCandidates(closure, search(vector, baseTopK, List.of(resourceType),
                        List.of("PROVIDER_DOCUMENTATION")), true, false);
            }
            return selector.select(closure.values(), List.of(), query.resourceTypes(), baseTopK, maxEvidence);
        }
        List<ReferenceDocument> global = search(vector, baseTopK, query.resourceTypes());
        if (query.resourceTypes().isEmpty()) {
            return global;
        }

        Map<String, ReferenceEvidenceSelector.Candidate> candidates = new LinkedHashMap<>();
        addCandidates(candidates, global, true, true);
        for (String resourceType : query.resourceTypes().stream().distinct().toList()) {
            addCandidates(candidates, search(vector, baseTopK, List.of(resourceType), List.of()), true, false);
        }
        addCandidates(
                candidates,
                search(vector, baseTopK, query.resourceTypes(), List.of("PROJECT_DECISION")),
                false,
                false
        );
        for (String resourceType : query.resourceTypes()) {
            boolean officiallyCovered = candidates.values().stream()
                    .map(ReferenceEvidenceSelector.Candidate::document)
                    .anyMatch(document -> document.isOfficialProviderDocumentation()
                            && document.resourceTypes().contains(resourceType));
            if (!officiallyCovered) {
                addCandidates(candidates, search(vector, baseTopK, List.of(resourceType),
                        List.of("PROVIDER_DOCUMENTATION")), true, false);
            }
        }
        return selector.select(candidates.values(), global, query.resourceTypes(), baseTopK, maxEvidence);
    }

    private List<ReferenceDocument> search(List<Float> vector, int topK, List<String> resourceTypes) {
        return search(vector, topK, resourceTypes, List.of());
    }

    public List<ReferenceDocument> retrieveOfficialDocumentation(String resourceType) {
        requireRuntimeConfig();
        String body = queryBuilder.buildOfficialDocumentation(properties.getContentFieldName(),
                properties.getCorpusVersion(), properties.getProviderVersion(), resourceType);
        URI uri = OpenSearchEndpoint.searchUri(properties.getOpensearchEndpoint(), properties.getIndexName());
        return responseParser.parse(transport.post(uri, body), properties.getContentFieldName()).stream()
                .filter(ReferenceDocument::isOfficialProviderDocumentation)
                .filter(document -> document.resourceTypes().contains(resourceType))
                .filter(document -> properties.getCorpusVersion().equals(document.corpusVersion())
                        && properties.getProviderVersion().equals(document.providerVersion()))
                .filter(document -> document.content() != null && !document.content().isBlank()
                        && document.sourcePath() != null && !document.sourcePath().isBlank())
                .limit(1).toList();
    }

    private List<ReferenceDocument> search(
            List<Float> vector, int topK, List<String> resourceTypes, List<String> authorities) {
        String body = queryBuilder.build(
                properties.getVectorFieldName(),
                properties.getContentFieldName(),
                vector,
                topK,
                properties.getCorpusVersion(),
                properties.getProviderVersion(),
                resourceTypes,
                authorities,
                authorities.contains("PROVIDER_DOCUMENTATION")
                        ? List.of("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE") : List.of()
        );
        URI uri = OpenSearchEndpoint.searchUri(properties.getOpensearchEndpoint(), properties.getIndexName());
        String response = transport.post(uri, body);
        return responseParser.parse(response, properties.getContentFieldName());
    }

    private void addCandidates(
            Map<String, ReferenceEvidenceSelector.Candidate> candidates,
            List<ReferenceDocument> documents,
            boolean normalSemantic,
            boolean globalSemantic
    ) {
        for (ReferenceDocument document : documents) {
            ReferenceEvidenceSelector.Candidate existing = candidates.get(document.id());
            if (existing == null) {
                candidates.put(document.id(), new ReferenceEvidenceSelector.Candidate(
                        document, normalSemantic, globalSemantic, candidates.size()));
            } else if ((normalSemantic && !existing.normalSemantic())
                    || (globalSemantic && !existing.globalSemantic())) {
                candidates.put(document.id(), new ReferenceEvidenceSelector.Candidate(
                        existing.document(),
                        existing.normalSemantic() || normalSemantic,
                        existing.globalSemantic() || globalSemantic,
                        existing.discoveryOrder()));
            }
        }
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
        if (properties.getOpensearchMaxEvidence() <= 0) {
            throw new IllegalStateException(
                    "terraformers.analysis.opensearch-max-evidence must be positive for active retrieval");
        }
        if (properties.getOpensearchMaxEvidence() < properties.getOpensearchTopK()) {
            throw new IllegalStateException(
                    "terraformers.analysis.opensearch-max-evidence must be greater than or equal to opensearch-top-k");
        }
        if (properties.getExpectedVectorDimension() != null && properties.getExpectedVectorDimension() <= 0) {
            throw new IllegalStateException("terraformers.analysis.expected-vector-dimension must be positive when set");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
