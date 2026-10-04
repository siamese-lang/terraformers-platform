package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GeneratedTerraformContractInspectorTest {

    private final AwsProviderSchemaCatalog catalog = new AwsProviderSchemaCatalog(new ObjectMapper(),
            Path.of("src/test/resources/terraform/aws-provider-schema-catalog-fixture.json"));
    private final GeneratedTerraformContractInspector inspector = new GeneratedTerraformContractInspector(catalog);

    @Test
    void acceptsCatalogResourceWithoutRequestLocalEnvelope() {
        inspector.inspect("resource \"aws_subnet\" \"x\" { vpc_id = \"vpc-example\" }");
    }

    @Test
    void rejectsModuleAndNonAwsResourceBlocks() {
        assertThatThrownBy(() -> inspector.inspect("module \"network\" { source = \"x\" }"))
                .isInstanceOfSatisfying(GeneratedTerraformContractViolation.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.reason())
                                .isEqualTo(GeneratedTerraformContractViolation.Reason.MODULE_BLOCK));

        assertThatThrownBy(() -> inspector.inspect("resource \"google_compute_network\" \"x\" { name = \"x\" }"))
                .isInstanceOfSatisfying(GeneratedTerraformContractViolation.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.reason())
                                .isEqualTo(GeneratedTerraformContractViolation.Reason
                                        .RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT));
    }

    @Test
    void extractsResourceTypesWithoutDuplicatingTheTerraformParser() {
        assertThat(inspector.resourceTypes("""
                resource "aws_vpc" "main" {}
                resource "aws_subnet" "private_a" {}
                resource "aws_subnet" "private_b" {}
                resource "aws_not_real" "candidate" {}
                """))
                .containsExactly("aws_vpc", "aws_subnet", "aws_not_real");
    }

    @Test
    void rejectsNonexistentAwsResource() {
        assertThatThrownBy(() -> inspector.inspect("resource \"aws_not_real\" \"x\" { name = \"x\" }"))
                .isInstanceOfSatisfying(GeneratedTerraformContractViolation.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.reason())
                                .isEqualTo(GeneratedTerraformContractViolation.Reason
                                        .RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT));
    }

    @Test
    void acceptsMultipleCatalogResources() {
        inspector.inspect("""
                resource "aws_vpc" "x" { cidr_block = "10.0.0.0/16" }
                resource "aws_subnet" "x" { vpc_id = aws_vpc.x.id }
                """);
    }
}
