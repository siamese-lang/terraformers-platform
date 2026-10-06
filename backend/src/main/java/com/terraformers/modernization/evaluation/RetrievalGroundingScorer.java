package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import com.terraformers.modernization.evaluation.EvaluationTrace.RetrievalEvidence;
import com.terraformers.modernization.evaluation.RetrievalGroundingAssessment.Coverage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public final class RetrievalGroundingScorer {
    private static final Set<String> OFFICIAL_EVIDENCE_DOCUMENT_TYPES =
            Set.of("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE");
    public RetrievalGroundingAssessment score(EvaluationCase definition, EvaluationTrace trace) {
        if (!definition.caseId().equals(trace.caseId())) throw new IllegalArgumentException("caseId mismatch");
        List<String> decisions = definition.retrieval().requiredProjectDecisionIds();
        List<String> resources = definition.retrieval().requiredResourceTypes();
        boolean applicable = definition.expectedClassification() == EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM
                && (!decisions.isEmpty() || !resources.isEmpty());
        RetrievalEvidence retrieval = trace.retrieval().evidence();
        List<ReferenceHit> hits = retrieval == null ? List.of() : retrieval.hits();
        Coverage decisionCoverage = applicable ? coverage(decisions, hits,
                (required, hit) -> required.equals(hit.documentId())
                        && "TERRAFORMERS_PATTERN".equals(hit.documentType())) : null;
        Coverage resourceCoverage = applicable ? coverage(resources, hits,
                (required, hit) -> hit.resourceTypes().contains(required)) : null;
        List<ReferenceHit> officialHits = hits.stream()
                .filter(this::isOfficialEvidence)
                .toList();
        List<String> extracted = trace.factExtraction().evidence() == null
                ? List.of()
                : trace.factExtraction().evidence().resourceTypes();
        Coverage factOfficialEvidenceCoverage = applicable ? coverage(extracted, officialHits,
                (required, hit) -> hit.resourceTypes().contains(required)) : null;
        GenerationEvidence generation = trace.generation().evidence();
        List<String> generated = generation == null ? List.of() : generation.generatedResourceTypes();
        // Absent fields identify historical traces, whose initial evidence remains authoritative.
        List<ReferenceHit> finalHits = generation != null && generation.groundingClosure() != null
                ? generation.groundingClosure().finalSelectedReferences() : hits;
        List<ReferenceHit> finalOfficialHits = finalHits.stream().filter(this::isOfficialEvidence).toList();
        Coverage generatedOfficialEvidenceCoverage = applicable ? coverage(generated, finalOfficialHits,
                (required, hit) -> hit.resourceTypes().contains(required)) : null;
        List<String> retrievedIds = hits.stream().map(ReferenceHit::documentId).toList();
        List<String> suppliedIds = generation == null ? List.of() : generation.suppliedReferenceIds();
        boolean handoffComplete = retrievedIds.equals(suppliedIds);
        List<String> requiredGenerated = definition.generation().terraformResourceTypes().required();
        int generatedMatched = (int) requiredGenerated.stream().filter(generated::contains).count();
        int forbiddenGenerated = (int) definition.generation().terraformResourceTypes().forbidden().stream()
                .filter(generated::contains).count();
        Boolean validationPassed = trace.validation().evidence() == null ? null
                : trace.validation().evidence().applicationValidator().valid();
        boolean incomplete = applicable && (decisionCoverage.matched() < decisionCoverage.total()
                || resourceCoverage.matched() < resourceCoverage.total()
                || !handoffComplete);
        boolean generationSucceeded = trace.generation().status() == EvaluationStageStatus.PASS;
        boolean gap = incomplete && trace.retrieval().status() == EvaluationStageStatus.PASS && generationSucceeded;
        long sum = java.util.stream.Stream.of(trace.factExtraction().latencyMs(), trace.retrieval().latencyMs(),
                        trace.generation().latencyMs(), trace.validation().latencyMs())
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum();
        return new RetrievalGroundingAssessment(trace.caseId(), applicable, trace.retrieval().status(),
                retrieval == null ? null : retrieval.queryText(), retrieval == null ? List.of() : retrieval.resourceTypeFilters(),
                retrieval == null ? null : retrieval.requestedTopK(), hits, decisionCoverage, resourceCoverage,
                factOfficialEvidenceCoverage, generatedOfficialEvidenceCoverage,
                trace.generation().status(), handoffComplete, generatedMatched, requiredGenerated.size(), forbiddenGenerated,
                trace.validation().status(), validationPassed, trace.firstDivergence(), gap,
                gap && Boolean.TRUE.equals(validationPassed), trace.factExtraction().latencyMs(),
                trace.retrieval().latencyMs(), trace.generation().latencyMs(), sum);
    }

    private boolean isOfficialEvidence(ReferenceHit hit) {
        return "PROVIDER_DOCUMENTATION".equals(hit.authority())
                && OFFICIAL_EVIDENCE_DOCUMENT_TYPES.contains(hit.documentType());
    }

    private Coverage coverage(List<String> required, List<ReferenceHit> hits, BiPredicate<String, ReferenceHit> match) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (String item : required) {
            hits.stream().filter(hit -> match.test(item, hit)).findFirst().ifPresent(hit -> ranks.put(item, hit.rank()));
        }
        List<String> missing = required.stream().filter(item -> !ranks.containsKey(item)).toList();
        return new Coverage(ranks.size(), required.size(), required.isEmpty() ? null : (double) ranks.size() / required.size(),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ranks)), missing);
    }
}
