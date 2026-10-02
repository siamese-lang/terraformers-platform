package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedTerraformContractInspectorTest {

    private final AwsProviderSchemaCatalog catalog = new AwsProviderSchemaCatalog(new ObjectMapper(),
            Path.of("src/test/resources/terraform/aws-provider-schema-catalog-fixture.json"));
    private final GeneratedTerraformContractInspector inspector = new GeneratedTerraformContractInspector(catalog);

    @Test
    void rejectsResourceOutsideRequestEnvelope() {
        var evidence = catalog.resolve(List.of("aws_vpc"));
        assertThatThrownBy(() -> inspector.inspect("resource \"aws_subnet\" \"x\" {}", evidence))
                .isInstanceOf(GeneratedTerraformContractViolation.class)
                .hasMessageContaining("request schema envelope");
    }

    @Test
    void rejectsModuleAndNonAwsResourceBlocks() {
        var evidence = catalog.resolve(List.of("aws_vpc"));
        assertThatThrownBy(() -> inspector.inspect("module \"network\" { source = \"x\" }", evidence))
                .isInstanceOf(GeneratedTerraformContractViolation.class).hasMessageContaining("module");
        assertThatThrownBy(() -> inspector.inspect("resource \"google_compute_network\" \"x\" {}", evidence))
                .isInstanceOf(GeneratedTerraformContractViolation.class).hasMessageContaining("AWS provider");
    }

    @Test
    void acceptsEveryResourceCoveredByEvidence() {
        var evidence = catalog.resolve(List.of("aws_vpc", "aws_subnet"));
        inspector.inspect("resource \"aws_vpc\" \"x\" {}\nresource \"aws_subnet\" \"x\" {}", evidence);
    }
}
