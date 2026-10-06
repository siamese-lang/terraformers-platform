package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.evaluation.CaseAQualityCalibrationReport.CaseResult;
import com.terraformers.modernization.evaluation.CaseAQualityCalibrationReport.TextCoverage;
import com.terraformers.modernization.evaluation.CaseAQualityCalibrationReport.RuntimeQualityComparison;
import com.terraformers.modernization.evaluation.EvaluationCase.InputClassification;
import com.terraformers.modernization.evaluation.EvaluationCase.ValidationExpectation;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;

/** Applies only deterministic frozen-dataset labels; it does not perform model-based evaluation. */
public final class CaseAQualityCalibrationScorer {
    private final RetrievalGroundingScorer groundingScorer = new RetrievalGroundingScorer();

    public CaseResult score(EvaluationCase definition, EvaluationTrace trace) {
        return score(definition, trace, null);
    }

    public CaseResult score(EvaluationCase definition, EvaluationTrace trace, QualityStatus runtimeQualityStatus) {
        if (!definition.caseId().equals(trace.caseId())) throw new IllegalArgumentException("caseId mismatch");
        boolean architecture = definition.expectedClassification() == InputClassification.ARCHITECTURE_DIAGRAM;
        boolean technical = architecture ? architectureTechnicalSuccess(trace) : negativeTechnicalSuccess(trace);
        RetrievalGroundingAssessment grounding = groundingScorer.score(definition, trace);
        GenerationEvidence generation = trace.generation().evidence();
        var factEvidence = trace.factExtraction().evidence();
        TextCoverage componentCoverage = textCoverage(
                definition.components(),
                factEvidence == null ? java.util.List.of() : factEvidence.components(),
                this::matchesComponent);
        TextCoverage relationshipCoverage = relationshipCoverage(
                definition,
                factEvidence == null ? java.util.List.of() : factEvidence.relationships());
        java.util.List<String> extractedResources = trace.factExtraction().evidence() == null
                ? java.util.List.of()
                : trace.factExtraction().evidence().resourceTypes();
        int requiredFactMatched = (int) definition.resourceTypes().required().stream()
                .filter(extractedResources::contains)
                .count();
        int forbiddenFactCount = (int) definition.resourceTypes().forbidden().stream()
                .filter(extractedResources::contains)
                .count();
        boolean factResourcesComplete = requiredFactMatched == definition.resourceTypes().required().size()
                && forbiddenFactCount == 0;
        boolean textFactsComplete = complete(componentCoverage) && complete(relationshipCoverage);
        boolean labeled = architecture
                ? architectureLabelSuccess(
                        definition, trace, generation, grounding, factResourcesComplete, textFactsComplete)
                : negativeLabelSuccess(definition, generation);
        return new CaseResult(trace.caseId(), definition.expectedClassification(),
                generation == null ? null : generation.observedClassification(), architecture, technical, labeled,
                technical && !labeled, componentCoverage, relationshipCoverage,
                requiredFactMatched, definition.resourceTypes().required().size(),
                forbiddenFactCount, grounding, runtimeQualityStatus, comparison(runtimeQualityStatus, labeled));
    }

    private boolean architectureTechnicalSuccess(EvaluationTrace trace) {
        return trace.factExtraction().status() == EvaluationStageStatus.PASS
                && trace.retrieval().status() == EvaluationStageStatus.PASS
                && trace.generation().status() == EvaluationStageStatus.PASS
                && trace.validation().status() == EvaluationStageStatus.PASS
                && trace.firstDivergence() == null;
    }

    private boolean negativeTechnicalSuccess(EvaluationTrace trace) {
        return trace.factExtraction().status() == EvaluationStageStatus.PASS
                && trace.generation().status() == EvaluationStageStatus.PASS
                && permittedOptionalStage(trace.retrieval().status())
                && permittedOptionalStage(trace.validation().status())
                && trace.firstDivergence() == null;
    }

    private boolean permittedOptionalStage(EvaluationStageStatus status) {
        return status == EvaluationStageStatus.PASS || status == EvaluationStageStatus.NOT_RUN;
    }

    private boolean architectureLabelSuccess(EvaluationCase definition, EvaluationTrace trace,
            GenerationEvidence generation, RetrievalGroundingAssessment grounding,
            boolean factResourcesComplete, boolean textFactsComplete) {
        if (generation == null || generation.observedClassification() != definition.expectedClassification()) return false;
        boolean groundingComplete = completeWhenRequired(definition.retrieval().requiredProjectDecisionIds(),
                        grounding.projectDecisionCoverage())
                && completeWhenRequired(definition.retrieval().requiredResourceTypes(),
                        grounding.resourceTypeCoverage())
                && complete(grounding.factResourceOfficialEvidenceCoverage())
                && complete(grounding.generatedResourceOfficialEvidenceCoverage());
        boolean generatedComplete = grounding.requiredGeneratedResourceMatched()
                == grounding.requiredGeneratedResourceTotal()
                && grounding.forbiddenGeneratedResourceCount() == 0;
        return textFactsComplete
                && factResourcesComplete
                && groundingComplete
                && grounding.retrievalToGenerationHandoffComplete()
                && generatedComplete
                && validationMatches(definition.validation(), trace);
    }

    private TextCoverage textCoverage(
            EvaluationCase.TextExpectation expectation,
            java.util.List<String> observed,
            java.util.function.BiPredicate<String, String> matcher
    ) {
        java.util.List<String> safeObserved = observed == null ? java.util.List.of() : observed;
        java.util.List<String> missing = expectation.required().stream()
                .filter(expected -> safeObserved.stream().noneMatch(actual -> matcher.test(expected, actual)))
                .toList();
        java.util.List<String> forbidden = expectation.forbidden().stream()
                .filter(expected -> safeObserved.stream().anyMatch(actual -> matcher.test(expected, actual)))
                .toList();
        return new TextCoverage(
                expectation.required().size() - missing.size(),
                expectation.required().size(),
                forbidden.size(),
                missing,
                forbidden
        );
    }

    private TextCoverage relationshipCoverage(
            EvaluationCase definition,
            java.util.List<String> observed
    ) {
        return textCoverage(
                definition.relationships(),
                observed,
                (expected, actual) -> matchesRelationship(definition, expected, actual)
        );
    }

    private boolean matchesComponent(String expected, String actual) {
        String normalizedExpected = normalizeText(expected);
        String normalizedActual = normalizeText(actual);
        return !normalizedExpected.isBlank() && normalizedActual.contains(normalizedExpected);
    }

    private boolean matchesRelationship(EvaluationCase definition, String expected, String actual) {
        String normalizedActual = normalizeText(actual);
        String[] direction = expected.split("\\s*->\\s*", 2);
        if (direction.length != 2) {
            return normalizedActual.contains(normalizeText(expected));
        }

        String source = normalizeText(direction[0]);
        String right = normalizeText(direction[1]);
        if (source.isBlank()) return false;

        java.util.List<String> componentLabels = java.util.stream.Stream.of(
                        definition.components().required(),
                        definition.components().acceptable(),
                        definition.components().forbidden())
                .flatMap(java.util.Collection::stream)
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                .toList();

        String target = componentLabels.stream()
                .filter(right::contains)
                .findFirst()
                .orElse(right);
        int sourceIndex = entityIndex(source, normalizedActual);
        int targetIndex = entityIndex(target, normalizedActual);
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex >= targetIndex) {
            return false;
        }
        if (!hasForwardRelationSignal(normalizedActual, source, sourceIndex, targetIndex)) {
            return false;
        }

        java.util.Set<String> stopWords = java.util.Set.of(
                "through", "via", "to", "the", "a", "an", "as", "by", "on", "in", "with");
        java.util.List<String> qualifierTokens = java.util.Arrays.stream(
                        right.replace(target, "").split("\\s+"))
                .filter(token -> !token.isBlank() && !stopWords.contains(token))
                .toList();
        return qualifierTokens.stream().allMatch(normalizedActual::contains);
    }

    private boolean hasForwardRelationSignal(
            String normalizedActual,
            String source,
            int sourceIndex,
            int targetIndex
    ) {
        int relationStart = Math.min(targetIndex, sourceIndex + source.length());
        String between = " " + normalizedActual.substring(relationStart, targetIndex).strip() + " ";
        java.util.List<String> relationTokens = java.util.Arrays.stream(between.strip().split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();

        java.util.Set<String> negations = java.util.Set.of("no", "not", "never", "cannot", "without");
        if (relationTokens.stream().anyMatch(negations::contains) || between.contains(" can t ")) {
            return false;
        }
        if (between.contains(" from ") || between.contains(" by ")) {
            return false;
        }

        java.util.List<String> forwardVerbStems = java.util.List.of(
                "send", "sent", "forward", "route", "connect", "quer", "write", "read",
                "host", "associate", "protect", "assume", "access", "invoke", "call",
                "direct", "deliver", "pass", "reach", "link", "attach", "use");
        return relationTokens.stream()
                .anyMatch(token -> forwardVerbStems.stream().anyMatch(token::startsWith));
    }

    private int entityIndex(String normalizedExpected, String normalizedActual) {
        int exact = normalizedActual.indexOf(normalizedExpected);
        if (exact >= 0) return exact;

        java.util.List<String> tokens = java.util.Arrays.stream(normalizedExpected.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
        if (tokens.size() < 3) return -1;

        java.util.List<Integer> positions = tokens.stream()
                .map(normalizedActual::indexOf)
                .filter(position -> position >= 0)
                .sorted()
                .toList();
        return positions.size() >= tokens.size() - 1 && positions.size() >= 2
                ? positions.get(0)
                : -1;
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return value.strip().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    private boolean complete(TextCoverage coverage) {
        return coverage.requiredMatched() == coverage.requiredTotal()
                && coverage.forbiddenMatched() == 0;
    }

    private boolean complete(RetrievalGroundingAssessment.Coverage coverage) {
        return coverage != null && coverage.matched() == coverage.total();
    }

    private boolean completeWhenRequired(java.util.List<String> required,
            RetrievalGroundingAssessment.Coverage coverage) {
        return required.isEmpty() || coverage != null && complete(coverage);
    }

    private boolean validationMatches(ValidationExpectation expected, EvaluationTrace trace) {
        Boolean valid = trace.validation().evidence() == null ? null
                : trace.validation().evidence().applicationValidator().valid();
        return switch (expected) {
            case PASS -> trace.validation().status() == EvaluationStageStatus.PASS && Boolean.TRUE.equals(valid);
            case FAIL -> trace.validation().status() == EvaluationStageStatus.FAIL && Boolean.FALSE.equals(valid);
            case NOT_APPLICABLE -> trace.validation().status() == EvaluationStageStatus.NOT_RUN;
        };
    }

    private boolean negativeLabelSuccess(EvaluationCase definition, GenerationEvidence generation) {
        return definition.validation() == ValidationExpectation.NOT_APPLICABLE
                && generation != null
                && generation.observedClassification() == definition.expectedClassification()
                && generation.terraformCode().isBlank()
                && generation.generatedResourceTypes().isEmpty();
    }

    private RuntimeQualityComparison comparison(QualityStatus status, boolean labeled) {
        if (status == null) return RuntimeQualityComparison.UNAVAILABLE;
        if (status == QualityStatus.UNKNOWN || status == QualityStatus.NOT_APPLICABLE) {
            return RuntimeQualityComparison.INDETERMINATE;
        }
        boolean runtimePositive = status == QualityStatus.EVIDENCE_BACKED;
        return runtimePositive == labeled ? RuntimeQualityComparison.MATCH : RuntimeQualityComparison.MISMATCH;
    }
}
