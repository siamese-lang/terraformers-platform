package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.KnowledgeStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.ProjectDecisionStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.Reason;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.RuntimeQualityBoundary;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment.TechnicalStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessor.ProjectDecisionApplicability;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.ReferenceDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvidenceQualityAssessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AwsProviderSchemaCatalog catalog = new AwsProviderSchemaCatalog(
            objectMapper,
            Path.of("src/test/resources/terraform/aws-provider-schema-catalog-fixture.json"));
    private final GeneratedTerraformContractInspector inspector =
            new GeneratedTerraformContractInspector(catalog);
    private final EvidenceQualityAssessor assessor = new EvidenceQualityAssessor(catalog, inspector);

    @Test
    void treatsMissingOfficialKnowledgeAsKnowledgeGapNotQualityFailure() {
        EvidenceQualityAssessment assessment = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_lambda_function"),
                Set.of(),
                List.of(),
                "",
                ProjectDecisionApplicability.NOT_APPLICABLE,
                List.of()
        ));

        assertThat(assessment.knowledgeStatus()).isEqualTo(KnowledgeStatus.INCOMPLETE);
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.UNKNOWN);
        assertThat(assessment.missingOfficialKnowledgeResourceTypes())
                .containsExactly("aws_lambda_function");
        assertThat(assessment.reasons())
                .containsExactly(Reason.OFFICIAL_KNOWLEDGE_NOT_AVAILABLE);
    }

    @Test
    void distinguishesRetrievalMissFromCorpusKnowledgeGap() {
        EvidenceQualityAssessment assessment = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc"),
                Set.of("aws_vpc"),
                List.of(),
                "",
                ProjectDecisionApplicability.NOT_APPLICABLE,
                List.of()
        ));

        assertThat(assessment.knowledgeStatus()).isEqualTo(KnowledgeStatus.COMPLETE);
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(assessment.missingOfficialKnowledgeResourceTypes()).isEmpty();
        assertThat(assessment.missingSelectedEvidenceResourceTypes()).containsExactly("aws_vpc");
        assertThat(assessment.reasons()).containsExactly(Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
    }

    @Test
    void checksGeneratedResourcesAgainstProviderSchemaAndSelectedOfficialEvidence() {
        EvidenceQualityAssessment assessment = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc"),
                Set.of("aws_vpc", "aws_subnet"),
                List.of(providerDocument("vpc-doc", "aws_vpc")),
                """
                resource "aws_vpc" "main" {}
                resource "aws_subnet" "private" {}
                resource "aws_not_real" "invalid" {}
                """,
                ProjectDecisionApplicability.NOT_APPLICABLE,
                List.of()
        ));

        assertThat(assessment.generatedResourceTypes())
                .containsExactly("aws_not_real", "aws_subnet", "aws_vpc");
        assertThat(assessment.generatedResourcesAbsentFromProviderSchema())
                .containsExactly("aws_not_real");
        assertThat(assessment.generatedResourcesWithoutSelectedEvidence())
                .containsExactly("aws_subnet");
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(assessment.reasons()).containsExactly(
                Reason.RESOURCE_UNKNOWN_TO_PROVIDER,
                Reason.GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE
        );
    }

    @Test
    void keepsUnknownProjectDecisionApplicabilitySeparateFromKnownMissingDecision() {
        List<ReferenceDocument> providerEvidence = List.of(providerDocument("vpc-doc", "aws_vpc"));

        EvidenceQualityAssessment unknown = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc"),
                Set.of("aws_vpc"),
                providerEvidence,
                "",
                ProjectDecisionApplicability.UNKNOWN,
                List.of()
        ));
        assertThat(unknown.projectDecisionStatus()).isEqualTo(ProjectDecisionStatus.UNKNOWN);
        assertThat(unknown.qualityStatus()).isEqualTo(QualityStatus.UNKNOWN);
        assertThat(unknown.reasons()).doesNotContain(Reason.REQUIRED_PROJECT_DECISION_NOT_RETRIEVED);

        EvidenceQualityAssessment missing = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc"),
                Set.of("aws_vpc"),
                List.of(providerDocument("decision-1", "aws_vpc")),
                "",
                ProjectDecisionApplicability.APPLICABLE,
                List.of("decision-1")
        ));
        assertThat(missing.projectDecisionStatus()).isEqualTo(ProjectDecisionStatus.INCOMPLETE);
        assertThat(missing.missingProjectDecisionIds()).containsExactly("decision-1");
        assertThat(missing.qualityStatus()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(missing.reasons()).contains(Reason.REQUIRED_PROJECT_DECISION_NOT_RETRIEVED);

        EvidenceQualityAssessment complete = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc"),
                Set.of("aws_vpc"),
                List.of(providerDocument("vpc-doc", "aws_vpc"), projectDecision("decision-1")),
                "",
                ProjectDecisionApplicability.APPLICABLE,
                List.of("decision-1")
        ));
        assertThat(complete.projectDecisionStatus()).isEqualTo(ProjectDecisionStatus.COMPLETE);
        assertThat(complete.qualityStatus()).isEqualTo(QualityStatus.EVIDENCE_BACKED);
    }

    @Test
    void returnsNotApplicableForNonArchitectureInput() {
        EvidenceQualityAssessor.Input input = new EvidenceQualityAssessor.Input(
                TechnicalStatus.PASS,
                AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                List.of(),
                Set.of(),
                List.of(),
                "",
                ProjectDecisionApplicability.UNKNOWN,
                List.of()
        );

        EvidenceQualityAssessment assessment = assessor.assess(input);

        assertThat(assessment.knowledgeStatus()).isEqualTo(KnowledgeStatus.NOT_APPLICABLE);
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.NOT_APPLICABLE);
        assertThat(assessment.projectDecisionStatus()).isEqualTo(ProjectDecisionStatus.NOT_APPLICABLE);
        assertThat(assessment.runtimeQualityBoundary())
                .isEqualTo(RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS);
    }

    @Test
    void deterministicDegradationWinsOverUnknownKnowledge() {
        EvidenceQualityAssessment assessment = assessor.assess(input(
                TechnicalStatus.PASS,
                List.of("aws_vpc", "aws_lambda_function"),
                Set.of("aws_vpc"),
                List.of(),
                "",
                ProjectDecisionApplicability.NOT_APPLICABLE,
                List.of()
        ));

        assertThat(assessment.knowledgeStatus()).isEqualTo(KnowledgeStatus.INCOMPLETE);
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.DEGRADED);
        assertThat(assessment.reasons()).containsExactly(
                Reason.OFFICIAL_KNOWLEDGE_NOT_AVAILABLE,
                Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED
        );
    }

    @Test
    void contractIsVersionedJsonRoundTrippableAndTechnicalStatusIsIndependent() throws Exception {
        EvidenceQualityAssessment assessment = assessor.assess(input(
                TechnicalStatus.FAIL,
                List.of("aws_vpc"),
                Set.of("aws_vpc"),
                List.of(providerDocument("vpc-doc", "aws_vpc")),
                "resource \"aws_vpc\" \"main\" {}",
                ProjectDecisionApplicability.NOT_APPLICABLE,
                List.of()
        ));

        assertThat(assessment.contractVersion()).isEqualTo("evidence-quality-v1");
        assertThat(assessment.technicalStatus()).isEqualTo(TechnicalStatus.FAIL);
        assertThat(assessment.knowledgeStatus()).isEqualTo(KnowledgeStatus.COMPLETE);
        assertThat(assessment.qualityStatus()).isEqualTo(QualityStatus.EVIDENCE_BACKED);

        String json = objectMapper.writeValueAsString(assessment);
        EvidenceQualityAssessment restored =
                objectMapper.readValue(json, EvidenceQualityAssessment.class);

        assertThat(restored).isEqualTo(assessment);
        assertThat(json).contains(
                "\"contractVersion\":\"evidence-quality-v1\"",
                "\"runtimeQualityBoundary\":\"CONDITIONAL_ON_EXTRACTED_FACTS\""
        );
    }

    private EvidenceQualityAssessor.Input input(
            TechnicalStatus technicalStatus,
            List<String> extractedResources,
            Set<String> officialKnowledge,
            List<ReferenceDocument> selectedReferences,
            String generatedTerraform,
            ProjectDecisionApplicability projectDecisionApplicability,
            List<String> requiredProjectDecisionIds
    ) {
        return new EvidenceQualityAssessor.Input(
                technicalStatus,
                AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                extractedResources,
                officialKnowledge,
                selectedReferences,
                generatedTerraform,
                projectDecisionApplicability,
                requiredProjectDecisionIds
        );
    }

    private ReferenceDocument providerDocument(String id, String resourceType) {
        return new ReferenceDocument(
                id,
                id,
                "official provider evidence",
                1.0,
                "AWS_PROVIDER_DOC",
                List.of(resourceType),
                "website/docs/r/example.html.markdown",
                "5.100.0",
                "terraformers-reference-v4",
                "PROVIDER_DOCUMENTATION",
                60,
                List.of()
        );
    }

    private ReferenceDocument projectDecision(String id) {
        return new ReferenceDocument(
                id,
                id,
                "curated project decision",
                1.0,
                "TERRAFORMERS_PATTERN",
                List.of("aws_vpc"),
                "docs/reference-retrieval.md",
                "",
                "terraformers-reference-v4",
                "PROJECT_DECISION",
                100,
                List.of()
        );
    }
}
