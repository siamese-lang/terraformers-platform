package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AwsProviderSchemaCatalogTest {

    private final AwsProviderSchemaCatalog catalog = new AwsProviderSchemaCatalog(new ObjectMapper(),
            Path.of("src/test/resources/terraform/aws-provider-schema-catalog-fixture.json"));

    @Test
    void resolvesFactSelectedCatalogResourceWithoutCuratedDocument() {
        AwsProviderSchemaEvidence evidence = catalog.resolve(List.of("aws_lambda_function"));
        assertThat(evidence.resourceTypes()).containsExactly("aws_lambda_function");
        assertThat(evidence.promptText()).contains(
                "function_name: type=\"string\" (required)",
                "handler: type=\"string\" (optional)");
    }

    @Test
    void rejectsUnknownAndNonAwsCandidatesBeforeEvidenceCreation() {
        assertThatThrownBy(() -> catalog.resolve(List.of("aws_not_real")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absent");
        assertThatThrownBy(() -> catalog.resolve(List.of("google_compute_network")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an AWS");
    }

    @Test
    void evidenceIsDeterministicAndRequestBounded() {
        AwsProviderSchemaEvidence evidence = catalog.resolve(List.of("aws_subnet", "aws_vpc", "aws_vpc"));
        assertThat(evidence.resourceTypes()).containsExactly("aws_subnet", "aws_vpc");
        assertThat(evidence.promptText())
                .contains("vpc_id: type=\"string\" (required)")
                .contains("cidr_block: type=\"string\" (optional)")
                .contains("timeouts block(nesting=single, optional, arguments=[")
                .contains("create: type=\"string\" (optional)")
                .doesNotContain(
                        "arguments=[id: type=",
                        ", id: type=",
                        "arn: type=",
                        "aws_lambda_function");
    }
}
