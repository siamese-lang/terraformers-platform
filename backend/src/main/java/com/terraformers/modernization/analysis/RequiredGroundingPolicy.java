package com.terraformers.modernization.analysis;

import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.RetrievalMode;
import java.util.List;

/** Enforces required evidence after the generated input classification is known. */
public final class RequiredGroundingPolicy {

    private RequiredGroundingPolicy() {
    }

    public static boolean isMissing(
            RetrievalMode retrievalMode,
            AnalysisInputClassification classification,
            List<ReferenceDocument> references
    ) {
        return retrievalMode == RetrievalMode.REQUIRED
                && classification == AnalysisInputClassification.ARCHITECTURE_DIAGRAM
                && references.isEmpty();
    }

    public static void requireForArchitecture(
            RetrievalMode retrievalMode,
            AnalysisGenerationResult generated,
            List<ReferenceDocument> references
    ) {
        if (isMissing(retrievalMode, generated.inputClassification(), references)) {
            throw new IllegalStateException("required grounding is missing for architecture analysis");
        }
    }
}
