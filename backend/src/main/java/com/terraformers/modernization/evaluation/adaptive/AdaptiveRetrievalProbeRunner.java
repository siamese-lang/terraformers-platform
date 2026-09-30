package com.terraformers.modernization.evaluation.adaptive;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchReferenceRetriever;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import com.terraformers.modernization.reference.opensearch.OpenSearchTransport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdaptiveRetrievalProbeRunner {
    public static final int BASE_TOP_K = 8;
    public static final int CONTROL_MAX_EVIDENCE = 8;
    public static final int ADAPTIVE_MAX_EVIDENCE = 16;

    private final EmbeddingProvider embeddingDelegate;
    private final OpenSearchKnnQueryBuilder queryBuilder;
    private final OpenSearchResponseParser responseParser;
    private final OpenSearchTransport transport;
    private final AnalysisRuntimeProperties commonProperties;
    private final RetrievalQueryTextBuilder queryTextBuilder;

    public AdaptiveRetrievalProbeRunner(
            EmbeddingProvider embeddingDelegate,
            OpenSearchKnnQueryBuilder queryBuilder,
            OpenSearchResponseParser responseParser,
            OpenSearchTransport transport,
            AnalysisRuntimeProperties commonProperties,
            RetrievalQueryTextBuilder queryTextBuilder
    ) {
        this.embeddingDelegate = embeddingDelegate;
        this.queryBuilder = queryBuilder;
        this.responseParser = responseParser;
        this.transport = transport;
        this.commonProperties = commonProperties;
        this.queryTextBuilder = queryTextBuilder;
    }

    public AdaptiveRetrievalProbeReport run(
            AdaptiveRetrievalProbeFixture fixture,
            String sourceCommit,
            String embeddingModel,
            int embeddingDimension
    ) {
        requireSha(sourceCommit);
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("embeddingModel must not be blank");
        }
        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive");
        }
        requireFrozenIdentity(fixture, embeddingDimension);

        String queryText = queryTextBuilder.build(fixture.facts());
        ReferenceQuery query = new ReferenceQuery(queryText, fixture.facts().resourceTypes(), ADAPTIVE_MAX_EVIDENCE);
        MemoizingEmbeddingProvider sharedEmbedding = new MemoizingEmbeddingProvider(embeddingDelegate);

        AdaptiveRetrievalProbeReport.ArmEvidence control = runArm(
                query,
                sharedEmbedding,
                properties(CONTROL_MAX_EVIDENCE)
        );
        AdaptiveRetrievalProbeReport.ArmEvidence adaptive = runArm(
                query,
                sharedEmbedding,
                properties(ADAPTIVE_MAX_EVIDENCE)
        );

        return new AdaptiveRetrievalProbeReport(
                "adaptive-retrieval-live-probe-v1",
                sourceCommit,
                fixture.scenarioId(),
                fixture.corpusVersion(),
                fixture.providerVersion(),
                embeddingModel.strip(),
                embeddingDimension,
                BASE_TOP_K,
                CONTROL_MAX_EVIDENCE,
                ADAPTIVE_MAX_EVIDENCE,
                fixture.minimumCorpusDocumentCover(),
                query.resourceTypes(),
                sharedEmbedding.delegateCalls(),
                control,
                adaptive
        );
    }

    private AdaptiveRetrievalProbeReport.ArmEvidence runArm(
            ReferenceQuery query,
            MemoizingEmbeddingProvider embedding,
            AnalysisRuntimeProperties properties
    ) {
        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(
                embedding, queryBuilder, responseParser, transport, properties);
        long started = System.nanoTime();
        List<ReferenceDocument> documents = retriever.retrieve(query);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        Set<String> coveredSet = new LinkedHashSet<>();
        for (ReferenceDocument document : documents) {
            for (String resourceType : document.resourceTypes()) {
                if (query.resourceTypes().contains(resourceType)) {
                    coveredSet.add(resourceType);
                }
            }
        }

        List<String> covered = query.resourceTypes().stream().filter(coveredSet::contains).toList();
        List<String> missing = query.resourceTypes().stream().filter(resource -> !coveredSet.contains(resource)).toList();
        List<String> ids = documents.stream().map(ReferenceDocument::id).toList();
        List<AdaptiveRetrievalProbeReport.DocumentEvidence> evidence = documents.stream()
                .map(document -> new AdaptiveRetrievalProbeReport.DocumentEvidence(
                        document.id(), document.authority(), document.score(), document.resourceTypes()))
                .toList();

        return new AdaptiveRetrievalProbeReport.ArmEvidence(
                latencyMs,
                documents.size(),
                ids,
                covered,
                missing,
                covered.size(),
                query.resourceTypes().size(),
                evidence
        );
    }

    private AnalysisRuntimeProperties properties(int maxEvidence) {
        AnalysisRuntimeProperties result = new AnalysisRuntimeProperties();
        result.setOpensearchEndpoint(commonProperties.getOpensearchEndpoint());
        result.setIndexName(commonProperties.getIndexName());
        result.setVectorFieldName(commonProperties.getVectorFieldName());
        result.setContentFieldName(commonProperties.getContentFieldName());
        result.setCorpusVersion(commonProperties.getCorpusVersion());
        result.setProviderVersion(commonProperties.getProviderVersion());
        result.setExpectedVectorDimension(commonProperties.getExpectedVectorDimension());
        result.setOpensearchTopK(BASE_TOP_K);
        result.setOpensearchMaxEvidence(maxEvidence);
        return result;
    }

    private void requireFrozenIdentity(AdaptiveRetrievalProbeFixture fixture, int embeddingDimension) {
        if (!fixture.corpusVersion().equals(commonProperties.getCorpusVersion())) {
            throw new IllegalArgumentException("fixture corpusVersion does not match runtime corpus");
        }
        if (!fixture.providerVersion().equals(commonProperties.getProviderVersion())) {
            throw new IllegalArgumentException("fixture providerVersion does not match runtime provider");
        }
        if (commonProperties.getExpectedVectorDimension() == null
                || commonProperties.getExpectedVectorDimension() != embeddingDimension) {
            throw new IllegalArgumentException("runtime embedding dimension does not match frozen identity");
        }
    }

    private static void requireSha(String sourceCommit) {
        if (sourceCommit == null || !sourceCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("sourceCommit must be a full lowercase commit SHA");
        }
    }

    static final class MemoizingEmbeddingProvider implements EmbeddingProvider {
        private final EmbeddingProvider delegate;
        private String cachedText;
        private List<Float> cachedVector;
        private int delegateCalls;

        MemoizingEmbeddingProvider(EmbeddingProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Float> embed(String text) {
            if (cachedText == null) {
                cachedText = text;
                cachedVector = List.copyOf(delegate.embed(text));
                delegateCalls++;
                return cachedVector;
            }
            if (!cachedText.equals(text)) {
                throw new IllegalStateException("adaptive retrieval probe attempted a second distinct embedding query");
            }
            return cachedVector;
        }

        int delegateCalls() {
            return delegateCalls;
        }
    }
}
