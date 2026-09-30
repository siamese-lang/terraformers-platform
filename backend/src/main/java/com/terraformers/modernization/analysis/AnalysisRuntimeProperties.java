package com.terraformers.modernization.analysis;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.opensearch.OpenSearchTransportType;

@ConfigurationProperties(prefix = "terraformers.analysis")
public class AnalysisRuntimeProperties {

    private AnalysisMode mode = AnalysisMode.INTEGRATED_JAVA;
    private RetrievalMode retrievalMode = RetrievalMode.DISABLED;
    private String provider = "stub";
    private String embeddingProvider = "disabled";
    private String progressPublisher = "logging";
    private String opensearchEndpoint;
    private String opensearchTransport = "http";
    private String indexName;
    private String vectorFieldName;
    private String contentFieldName;
    private String corpusVersion = "terraformers-reference-v1";
    private String providerVersion = "5.100.0";
    private Integer expectedVectorDimension;
    private int opensearchTopK = 3;
    private int opensearchMaxEvidence = 16;
    private String resultBucketName;
    private String resultKeyPrefix = "analysis-results";
    private boolean dispatchEnabled = true;
    private Duration dispatchPollInterval = Duration.ofSeconds(2);
    private int dispatchBatchSize = 4;
    private Duration leaseDuration = Duration.ofSeconds(60);
    private Duration leaseRenewInterval = Duration.ofSeconds(20);
    private int maxAttempts = 3;
    private Duration retryDelay = Duration.ofSeconds(10);

    @PostConstruct
    void validateDurableDispatch() {
        if (dispatchPollInterval == null || dispatchPollInterval.isZero() || dispatchPollInterval.isNegative()) {
            throw new IllegalStateException("terraformers.analysis.dispatch-poll-interval must be positive");
        }
        if (dispatchBatchSize <= 0) {
            throw new IllegalStateException("terraformers.analysis.dispatch-batch-size must be positive");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalStateException("terraformers.analysis.lease-duration must be positive");
        }
        if (leaseRenewInterval == null || leaseRenewInterval.isZero() || leaseRenewInterval.isNegative()) {
            throw new IllegalStateException("terraformers.analysis.lease-renew-interval must be positive");
        }
        if (leaseRenewInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalStateException("terraformers.analysis.lease-renew-interval must be shorter than lease-duration");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException("terraformers.analysis.max-attempts must be at least 1");
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalStateException("terraformers.analysis.retry-delay must be positive");
        }
    }

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

    public String getOpensearchTransport() {
        return opensearchTransport;
    }

    public void setOpensearchTransport(String opensearchTransport) {
        this.opensearchTransport = opensearchTransport;
    }

    public OpenSearchTransportType resolvedOpenSearchTransport() {
        return OpenSearchTransportType.from(opensearchTransport);
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

    public int getOpensearchMaxEvidence() {
        return opensearchMaxEvidence;
    }

    public void setOpensearchMaxEvidence(int opensearchMaxEvidence) {
        this.opensearchMaxEvidence = opensearchMaxEvidence;
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
    public boolean isDispatchEnabled() { return dispatchEnabled; }
    public void setDispatchEnabled(boolean dispatchEnabled) { this.dispatchEnabled = dispatchEnabled; }
    public Duration getDispatchPollInterval() { return dispatchPollInterval; }
    public void setDispatchPollInterval(Duration dispatchPollInterval) { this.dispatchPollInterval = dispatchPollInterval; }
    public int getDispatchBatchSize() { return dispatchBatchSize; }
    public void setDispatchBatchSize(int dispatchBatchSize) { this.dispatchBatchSize = dispatchBatchSize; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getLeaseRenewInterval() { return leaseRenewInterval; }
    public void setLeaseRenewInterval(Duration leaseRenewInterval) { this.leaseRenewInterval = leaseRenewInterval; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
}
