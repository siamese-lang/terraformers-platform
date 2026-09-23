package com.terraformers.modernization.analysis;

public class AnalysisInputRejectedException extends RuntimeException {

    private final AnalysisInputClassification classification;
    private final Double classificationConfidence;
    private final boolean retryOccurred;

    public AnalysisInputRejectedException(
            AnalysisInputClassification classification,
            Double classificationConfidence,
            boolean retryOccurred,
            Throwable cause
    ) {
        super("analysis input was rejected: " + classification, cause);
        this.classification = java.util.Objects.requireNonNull(classification, "classification");
        this.classificationConfidence = classificationConfidence;
        this.retryOccurred = retryOccurred;
    }

    public AnalysisInputClassification classification() {
        return classification;
    }

    public Double classificationConfidence() {
        return classificationConfidence;
    }

    public boolean retryOccurred() {
        return retryOccurred;
    }
}
