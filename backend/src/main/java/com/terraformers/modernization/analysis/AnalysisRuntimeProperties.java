package com.terraformers.modernization.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.terraformers.modernization.reference.RetrievalMode;

@ConfigurationProperties(prefix = "terraformers.analysis")
public class AnalysisRuntimeProperties {

    private AnalysisMode mode = AnalysisMode.INTEGRATED_JAVA;
    private RetrievalMode retrievalMode = RetrievalMode.DISABLED;
    private String provider = "stub";
    private String embeddingProvider = "disabled";
    private String progressPublisher = "logging";
    private String opensearchEndpoint;
    private String indexName;
    private String vectorFieldName;
    private String contentFieldName;
    private String corpusVersion = "terraformers-reference-v1";
    private String providerVersion = "5.100.0";
    private Integer expectedVectorDimension;
    private int opensearchTopK = 3;
    private String resultBucketName;
    private String resultKeyPrefix = "analysis-results";

    public AnalysisMode getMode() {
        return mode;
    }

    public void setMode(AnalysisMode mode) {
        this.mode = mode;
    }

    public RetrievalMode getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(RetrievalMode retrievalMode) { this.retrievalMode = retrievalMode; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }

    public AnalysisProviderType resolvedProvider() {
        return AnalysisProviderType.from(provider);
    }

    public EmbeddingProviderType resolvedEmbeddingProvider() {
        return EmbeddingProviderType.from(embeddingProvider);
    }

    public String getOpensearchEndpoint() {
        return opensearchEndpoint;
    }

    public void setOpensearchEndpoint(String opensearchEndpoint) {
        this.opensearchEndpoint = opensearchEndpoint;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public void setVectorFieldName(String vectorFieldName) {
        this.vectorFieldName = vectorFieldName;
    }

    public String getContentFieldName() {
        return contentFieldName;
    }

    public void setContentFieldName(String contentFieldName) {
        this.contentFieldName = contentFieldName;
    }

    public String getCorpusVersion() {
        return corpusVersion;
    }

    public void setCorpusVersion(String corpusVersion) {
        this.corpusVersion = corpusVersion;
    }

    public String getProviderVersion() {
        return providerVersion;
    }

    public void setProviderVersion(String providerVersion) {
        this.providerVersion = providerVersion;
    }

    public Integer getExpectedVectorDimension() { return expectedVectorDimension; }
    public void setExpectedVectorDimension(Integer expectedVectorDimension) { this.expectedVectorDimension = expectedVectorDimension; }

    public int getOpensearchTopK() {
        return opensearchTopK;
    }

    public void setOpensearchTopK(int opensearchTopK) {
        this.opensearchTopK = opensearchTopK;
    }

    public String getResultBucketName() {
        return resultBucketName;
    }

    public void setResultBucketName(String resultBucketName) {
        this.resultBucketName = resultBucketName;
    }

    public String getResultKeyPrefix() {
        return resultKeyPrefix;
    }

    public void setResultKeyPrefix(String resultKeyPrefix) {
        this.resultKeyPrefix = resultKeyPrefix;
    }

    public String getProgressPublisher() { return progressPublisher; }
    public void setProgressPublisher(String progressPublisher) { this.progressPublisher = progressPublisher; }
    public ProgressPublisherType resolvedProgressPublisher() { return ProgressPublisherType.from(progressPublisher); }
}
