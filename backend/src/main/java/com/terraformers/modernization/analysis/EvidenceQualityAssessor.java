package com.terraformers.modernization.analysis;

import com.terraformers.modernization.analysis.EvidenceQualityAssessment.KnowledgeStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.ProjectDecisionStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.Reason;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.RuntimeQualityBoundary;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.TechnicalStatus;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic A7-1 evidence-quality-v1 decision engine. No model call is involved. */
public final class EvidenceQualityAssessor {

    private static final Set<String> OFFICIAL_EVIDENCE_DOCUMENT_TYPES =
            Set.of("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE");

    private final AwsProviderSchemaCatalog schemaCatalog;
    private final GeneratedTerraformContractInspector terraformInspector;

    public EvidenceQualityAssessor(
            AwsProviderSchemaCatalog schemaCatalog,
            GeneratedTerraformContractInspector terraformInspector
    ) {
        this.schemaCatalog = Objects.requireNonNull(schemaCatalog, "schemaCatalog");
        this.terraformInspector = Objects.requireNonNull(terraformInspector, "terraformInspector");
    }

    public EvidenceQualityAssessment assess(Input input) {
        Objects.requireNonNull(input, "input");

        if (input.inputClassification() != AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
            return new EvidenceQualityAssessment(
                    EvidenceQualityAssessment.CONTRACT_VERSION,
                    input.technicalStatus(),
                    KnowledgeStatus.NOT_APPLICABLE,
                    QualityStatus.NOT_APPLICABLE,
                    ProjectDecisionStatus.NOT_APPLICABLE,
                    RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                    List.of(),
                    input.extractedResourceTypes(),
                    List.of(),
                    List.of(),
                    terraformInspector.resourceTypes(input.generatedTerraform()),
                    List.of(),
                    List.of(),
                    input.requiredProjectDecisionIds(),
                    List.of()
            );
        }

        Set<String> extracted = normalized(input.extractedResourceTypes());
        Set<String> officialKnowledge = normalized(input.officialKnowledgeAvailableResourceTypes());
        Set<String> selectedOfficialEvidence = selectedOfficialEvidenceResourceTypes(input.selectedReferences());
        Set<String> generated = new TreeSet<>(terraformInspector.resourceTypes(input.generatedTerraform()));

        Set<String> extractedUnknownToProvider = filter(extracted, value -> !schemaCatalog.contains(value));
        Set<String> extractedKnownToProvider = difference(extracted, extractedUnknownToProvider);
        Set<String> missingOfficialKnowledge = difference(extractedKnownToProvider, officialKnowledge);
        Set<String> documentedExtracted = intersection(extractedKnownToProvider, officialKnowledge);
        Set<String> missingSelectedEvidence = difference(documentedExtracted, selectedOfficialEvidence);

        Set<String> generatedAbsentFromProvider = filter(generated, value -> !schemaCatalog.contains(value));
        Set<String> generatedKnownToProvider = difference(generated, generatedAbsentFromProvider);
        Set<String> generatedWithoutSelectedEvidence =
                difference(generatedKnownToProvider, selectedOfficialEvidence);

        ProjectDecisionOutcome projectDecisions = projectDecisionOutcome(input);

        EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        if (!extractedUnknownToProvider.isEmpty() || !generatedAbsentFromProvider.isEmpty()) {
            reasons.add(Reason.RESOURCE_UNKNOWN_TO_PROVIDER);
        }
        if (!missingOfficialKnowledge.isEmpty()) {
            reasons.add(Reason.OFFICIAL_KNOWLEDGE_NOT_AVAILABLE);
        }
        if (!missingSelectedEvidence.isEmpty()) {
            reasons.add(Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
        }
        if (!generatedWithoutSelectedEvidence.isEmpty()) {
            reasons.add(Reason.GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE);
        }
        if (!projectDecisions.missing().isEmpty()) {
            reasons.add(Reason.REQUIRED_PROJECT_DECISION_NOT_RETRIEVED);
        }

        KnowledgeStatus knowledgeStatus = knowledgeStatus(
                extracted, extractedUnknownToProvider, missingOfficialKnowledge);

        boolean deterministicDegradation = !missingSelectedEvidence.isEmpty()
                || !generatedAbsentFromProvider.isEmpty()
                || !generatedWithoutSelectedEvidence.isEmpty()
                || projectDecisions.status() == ProjectDecisionStatus.INCOMPLETE;

        QualityStatus qualityStatus;
        if (deterministicDegradation) {
            qualityStatus = QualityStatus.DEGRADED;
        } else if (knowledgeStatus != KnowledgeStatus.COMPLETE
                || projectDecisions.status() == ProjectDecisionStatus.UNKNOWN) {
            qualityStatus = QualityStatus.UNKNOWN;
        } else {
            qualityStatus = QualityStatus.EVIDENCE_BACKED;
        }

        return new EvidenceQualityAssessment(
                EvidenceQualityAssessment.CONTRACT_VERSION,
                input.technicalStatus(),
                knowledgeStatus,
                qualityStatus,
                projectDecisions.status(),
                RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                reasons,
                extracted,
                missingOfficialKnowledge,
                missingSelectedEvidence,
                generated,
                generatedAbsentFromProvider,
                generatedWithoutSelectedEvidence,
                input.requiredProjectDecisionIds(),
                projectDecisions.missing()
        );
    }

    private KnowledgeStatus knowledgeStatus(
            Set<String> extracted,
            Set<String> unknownToProvider,
            Set<String> missingOfficialKnowledge
    ) {
        if (extracted.isEmpty() || !unknownToProvider.isEmpty()) {
            return KnowledgeStatus.UNKNOWN;
        }
        if (!missingOfficialKnowledge.isEmpty()) {
            return KnowledgeStatus.INCOMPLETE;
        }
        return KnowledgeStatus.COMPLETE;
    }

    private ProjectDecisionOutcome projectDecisionOutcome(Input input) {
        return switch (input.projectDecisionApplicability()) {
            case UNKNOWN -> new ProjectDecisionOutcome(ProjectDecisionStatus.UNKNOWN, Set.of());
            case NOT_APPLICABLE -> new ProjectDecisionOutcome(ProjectDecisionStatus.NOT_APPLICABLE, Set.of());
            case APPLICABLE -> {
                Set<String> required = normalized(input.requiredProjectDecisionIds());
                Set<String> selectedIds = new TreeSet<>();
                for (ReferenceDocument reference : safeReferences(input.selectedReferences())) {
                    if (reference.id() != null && !reference.id().isBlank()) {
                        selectedIds.add(reference.id().strip());
                    }
                }
                Set<String> missing = difference(required, selectedIds);
                yield new ProjectDecisionOutcome(
                        missing.isEmpty() ? ProjectDecisionStatus.COMPLETE : ProjectDecisionStatus.INCOMPLETE,
                        missing
                );
            }
        };
    }

    private Set<String> selectedOfficialEvidenceResourceTypes(List<ReferenceDocument> references) {
        Set<String> result = new TreeSet<>();
        for (ReferenceDocument reference : safeReferences(references)) {
            if (!"PROVIDER_DOCUMENTATION".equals(reference.authority())
                    || !OFFICIAL_EVIDENCE_DOCUMENT_TYPES.contains(reference.documentType())) {
                continue;
            }
            result.addAll(normalized(reference.resourceTypes()));
        }
        return result;
    }

    private List<ReferenceDocument> safeReferences(List<ReferenceDocument> references) {
        return references == null ? List.of() : List.copyOf(references);
    }

    private Set<String> normalized(Collection<String> values) {
        TreeSet<String> result = new TreeSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.strip());
            }
        }
        return result;
    }

    private Set<String> filter(Set<String> values, java.util.function.Predicate<String> predicate) {
        Set<String> result = new TreeSet<>();
        for (String value : values) {
            if (predicate.test(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.retainAll(right);
        return result;
    }

    public record Input(
            TechnicalStatus technicalStatus,
            AnalysisInputClassification inputClassification,
            List<String> extractedResourceTypes,
            Set<String> officialKnowledgeAvailableResourceTypes,
            List<ReferenceDocument> selectedReferences,
            String generatedTerraform,
            ProjectDecisionApplicability projectDecisionApplicability,
            List<String> requiredProjectDecisionIds
    ) {
        public Input {
            technicalStatus = Objects.requireNonNull(technicalStatus, "technicalStatus");
            inputClassification = Objects.requireNonNull(inputClassification, "inputClassification");
            extractedResourceTypes = immutable(extractedResourceTypes);
            officialKnowledgeAvailableResourceTypes =
                    Set.copyOf(normalizedStatic(officialKnowledgeAvailableResourceTypes));
            selectedReferences = selectedReferences == null ? List.of() : List.copyOf(selectedReferences);
            generatedTerraform = generatedTerraform == null ? "" : generatedTerraform;
            projectDecisionApplicability =
                    Objects.requireNonNull(projectDecisionApplicability, "projectDecisionApplicability");
            requiredProjectDecisionIds = immutable(requiredProjectDecisionIds);
        }

        private static List<String> immutable(Collection<String> values) {
            return List.copyOf(normalizedStatic(values));
        }

        private static Set<String> normalizedStatic(Collection<String> values) {
            TreeSet<String> result = new TreeSet<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        result.add(value.strip());
                    }
                }
            }
            return result;
        }
    }

    public enum ProjectDecisionApplicability {
        UNKNOWN,
        NOT_APPLICABLE,
        APPLICABLE
    }

    private record ProjectDecisionOutcome(ProjectDecisionStatus status, Set<String> missing) {
        private ProjectDecisionOutcome {
            status = Objects.requireNonNull(status, "status");
            missing = Set.copyOf(new LinkedHashSet<>(missing));
        }
    }
}
