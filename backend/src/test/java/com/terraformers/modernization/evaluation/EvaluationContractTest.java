package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.FactExtractionEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.FirstDivergence;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.InputIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import com.terraformers.modernization.evaluation.EvaluationTrace.RetrievalEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.StageTrace;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationCheck;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationContractTest {

    private static final String SCHEMA = "m3-evaluation-v1";
    private static final String DATASET = "terraformers-eval-v1";

    @Test
    void representsSuccessfulPipelineWithRetrievalProvenance() throws Exception {
        EvaluationTrace trace = trace(
                StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10, facts()),
                StageTrace.pass(EvaluationStage.RETRIEVAL, 20, retrieval()),
                StageTrace.pass(EvaluationStage.GENERATION, 30, generation("aws_vpc")),
                StageTrace.pass(EvaluationStage.VALIDATION, 2, validValidation()),
                null
        );

        JsonNode json = new ObjectMapper().valueToTree(trace);

        assertThat(json.path("retrieval").path("evidence").path("queryText").asText())
                .contains("VPC");
        assertThat(json.path("retrieval").path("evidence").path("hits").get(0).path("documentId").asText())
                .isEqualTo("tfaws-vpc-schema");
        assertThat(json.path("retrieval").path("evidence").path("hits").get(0).path("score").asDouble())
                .isEqualTo(0.91);
        assertThat(json.path("generation").path("evidence").path("suppliedReferenceIds").get(0).asText())
                .isEqualTo("tfaws-vpc-schema");
        assertThat(trace.firstDivergence()).isNull();
    }

    @Test
    void representsExtractionFailureAsFirstObservableDivergence() {
        EvaluationFailure failure = failure(
                EvaluationStage.FACT_EXTRACTION,
                EvaluationFailureCategory.FACT_EXTRACTION_MISSING_COMPONENT,
                "database component missing"
        );

        EvaluationTrace trace = trace(
                StageTrace.fail(EvaluationStage.FACT_EXTRACTION, 8, facts(), failure),
                StageTrace.notRun(EvaluationStage.RETRIEVAL),
                StageTrace.notRun(EvaluationStage.GENERATION),
                StageTrace.notRun(EvaluationStage.VALIDATION),
                divergence(failure)
        );

        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.FACT_EXTRACTION);
    }

    @Test
    void representsRetrievalFailureWithoutGenerationSpecificFields() {
        EvaluationFailure failure = failure(
                EvaluationStage.RETRIEVAL,
                EvaluationFailureCategory.RETRIEVAL_IRRELEVANT_EVIDENCE,
                "top hit does not cover expected VPC resource"
        );

        EvaluationTrace trace = trace(
                StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10, facts()),
                StageTrace.fail(EvaluationStage.RETRIEVAL, 25, retrieval(), failure),
                StageTrace.notRun(EvaluationStage.GENERATION),
                StageTrace.notRun(EvaluationStage.VALIDATION),
                divergence(failure)
        );

        assertThat(trace.retrieval().failures()).containsExactly(failure);
        assertThat(trace.firstDivergence().category())
                .isEqualTo(EvaluationFailureCategory.RETRIEVAL_IRRELEVANT_EVIDENCE);
    }

    @Test
    void representsUngroundedGenerationEvenWhenTerraformIsStructurallyValid() {
        EvaluationFailure failure = failure(
                EvaluationStage.GENERATION,
                EvaluationFailureCategory.GENERATION_UNGROUNDED_RESOURCE,
                "aws_db_instance was not supported by extracted facts or retrieved references"
        );

        EvaluationTrace trace = trace(
                StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10, facts()),
                StageTrace.pass(EvaluationStage.RETRIEVAL, 20, retrieval()),
                StageTrace.fail(EvaluationStage.GENERATION, 30, generation("aws_db_instance"), failure),
                StageTrace.pass(EvaluationStage.VALIDATION, 2, validValidation()),
                divergence(failure)
        );

        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.PASS);
    }

    @Test
    void representsTerraformValidationFailureAfterSuccessfulGeneration() {
        EvaluationFailure failure = failure(
                EvaluationStage.VALIDATION,
                EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION,
                "generated Terraform is not a structurally usable HCL draft"
        );
        ValidationEvidence invalid = new ValidationEvidence(
                new ValidationCheck("TerraformDraftValidator", false, failure.detail()),
                List.of()
        );

        EvaluationTrace trace = trace(
                StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10, facts()),
                StageTrace.pass(EvaluationStage.RETRIEVAL, 20, retrieval()),
                StageTrace.pass(EvaluationStage.GENERATION, 30, generation("aws_vpc")),
                StageTrace.fail(EvaluationStage.VALIDATION, 2, invalid, failure),
                divergence(failure)
        );

        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.VALIDATION);
        assertThat(trace.validation().evidence().applicationValidator().valid()).isFalse();
    }

    private EvaluationTrace trace(
            StageTrace<FactExtractionEvidence> extraction,
            StageTrace<RetrievalEvidence> retrieval,
            StageTrace<GenerationEvidence> generation,
            StageTrace<ValidationEvidence> validation,
            FirstDivergence divergence
    ) {
        return new EvaluationTrace(
                SCHEMA,
                DATASET,
                "run-001",
                "case-001",
                new InputIdentity("fixtures/case-001.png", "abc123", "image/png"),
                new ConfigurationIdentity(
                        "terraformers-reference-v2",
                        "5.100.0",
                        "bedrock",
                        "bedrock",
                        "REQUIRED",
                        5,
                        "generation-model",
                        "embedding-model",
                        "config-sha256"
                ),
                extraction,
                retrieval,
                generation,
                validation,
                divergence
        );
    }

    private FactExtractionEvidence facts() {
        return new FactExtractionEvidence(
                EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                "VPC with application tier",
                List.of("VPC", "application"),
                List.of("application runs inside VPC"),
                List.of("aws_vpc")
        );
    }

    private RetrievalEvidence retrieval() {
        return new RetrievalEvidence(
                "Architecture summary: VPC with application tier",
                List.of("aws_vpc"),
                5,
                List.of(new ReferenceHit(
                        1,
                        "tfaws-vpc-schema",
                        0.91,
                        "aws_vpc provider schema",
                        "PROVIDER_SCHEMA",
                        "AWS_PROVIDER_SCHEMA",
                        "terraform providers schema -json#resource_schemas.aws_vpc",
                        List.of("aws_vpc"),
                        "5.100.0",
                        "terraformers-reference-v2",
                        80,
                        List.of()
                ))
        );
    }

    private GenerationEvidence generation(String resourceType) {
        return new GenerationEvidence(
                List.of("tfaws-vpc-schema"),
                "Generated VPC draft",
                List.of("VPC"),
                List.of(),
                List.of(),
                "resource \"" + resourceType + "\" \"main\" { cidr_block = \"10.0.0.0/16\" }",
                List.of(resourceType),
                List.of(),
                "end_turn",
                null,
                false
        );
    }

    private ValidationEvidence validValidation() {
        return new ValidationEvidence(
                new ValidationCheck("TerraformDraftValidator", true, ""),
                List.of()
        );
    }

    private EvaluationFailure failure(
            EvaluationStage stage,
            EvaluationFailureCategory category,
            String detail
    ) {
        return new EvaluationFailure(stage, category, detail);
    }

    private FirstDivergence divergence(EvaluationFailure failure) {
        return new FirstDivergence(failure.stage(), failure.category());
    }
}
