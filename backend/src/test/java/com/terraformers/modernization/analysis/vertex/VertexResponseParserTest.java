package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import org.junit.jupiter.api.Test;

class VertexResponseParserTest {

    private final VertexResponseParser parser = new VertexResponseParser(new ObjectMapper());

    @Test
    void parsesArchitectureJsonIntoProviderNeutralGenerationResult() {
        String json = """
                {
                  "inputType":"ARCHITECTURE_DIAGRAM",
                  "classificationConfidence":0.93,
                  "classificationReason":"connected deployable components",
                  "summary":"VPC with an application tier",
                  "components":["VPC","Application"],
                  "relationships":["VPC contains Application"],
                  "warnings":[],
                  "terraformCode":"resource \\"aws_vpc\\" \\"main\\" { cidr_block = \\"10.0.0.0/16\\" }"
                }
                """;

        AnalysisGenerationResult result = parser.parse(
                "vertex:gemini-3.8-flash", json, "STOP", 120, false);

        assertThat(result.provider()).isEqualTo("vertex:gemini-3.8-flash");
        assertThat(result.inputClassification())
                .isEqualTo(AnalysisInputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(result.classificationConfidence()).isEqualTo(0.93);
        assertThat(result.components()).containsExactly("VPC", "Application");
        assertThat(result.terraformCode()).contains("aws_vpc");
        assertThat(result.outputTokens()).isEqualTo(120);
    }

    @Test
    void returnsNeutralRejectedSignalForNonArchitectureInput() {
        String json = """
                {
                  "inputType":"NON_ARCHITECTURE_IMAGE",
                  "classificationConfidence":0.99,
                  "classificationReason":"console dashboard",
                  "summary":"",
                  "components":[],
                  "relationships":[],
                  "warnings":[],
                  "terraformCode":""
                }
                """;

        assertThatThrownBy(() -> parser.parse(
                "vertex:gemini-3.8-flash", json, "STOP", 20, false))
                .isInstanceOf(AnalysisInputRejectedException.class)
                .satisfies(error -> {
                    AnalysisInputRejectedException rejected = (AnalysisInputRejectedException) error;
                    assertThat(rejected.classification())
                            .isEqualTo(AnalysisInputClassification.NON_ARCHITECTURE_IMAGE);
                    assertThat(rejected.classificationConfidence()).isEqualTo(0.99);
                });
    }

    @Test
    void rejectsRejectedClassificationThatStillContainsTerraform() {
        String json = """
                {
                  "inputType":"AMBIGUOUS",
                  "classificationConfidence":0.60,
                  "classificationReason":"cropped diagram",
                  "summary":"",
                  "components":[],
                  "relationships":[],
                  "warnings":[],
                  "terraformCode":"resource \\"aws_vpc\\" \\"invented\\" {}"
                }
                """;

        assertThatThrownBy(() -> parser.parse(
                "vertex:gemini-3.8-flash", json, "STOP", 30, false))
                .isInstanceOf(VertexResponseFormatException.class)
                .hasMessageContaining("must not include");
    }
}
