package com.terraformers.modernization.evaluation.adaptive;

import java.util.List;

public record AdaptiveRetrievalProbeReport(
        String schemaVersion,
        String sourceCommit,
        String scenarioId,
        String corpusVersion,
        String providerVersion,
        String embeddingModel,
        int embeddingDimension,
        int baseTopK,
        int controlMaxEvidence,
        int adaptiveMaxEvidence,
        int minimumCorpusDocumentCover,
        List<String> queryResourceTypes,
        int embeddingDelegateCalls,
        ArmEvidence control,
        ArmEvidence adaptive
) {
    public AdaptiveRetrievalProbeReport {
        queryResourceTypes = List.copyOf(queryResourceTypes);
    }

    public record ArmEvidence(
            long latencyMs,
            int evidenceCount,
            List<String> selectedDocumentIds,
            List<String> coveredResourceTypes,
            List<String> missingResourceTypes,
            int coverageMatched,
            int coverageTotal,
            List<DocumentEvidence> documents
    ) {
        public ArmEvidence {
            selectedDocumentIds = List.copyOf(selectedDocumentIds);
            coveredResourceTypes = List.copyOf(coveredResourceTypes);
            missingResourceTypes = List.copyOf(missingResourceTypes);
            documents = List.copyOf(documents);
        }
    }

    public record DocumentEvidence(
            String id,
            String authority,
            double score,
            List<String> resourceTypes
    ) {
        public DocumentEvidence {
            resourceTypes = List.copyOf(resourceTypes);
        }
    }
}
