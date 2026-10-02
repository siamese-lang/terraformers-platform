package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VertexPromptBuilderTest {

    @Test
    void safetyRecoveryUsesOnlyFixedCategoryInstructionAndRetainsGroundingContent() {
        VertexPromptBuilder builder = new VertexPromptBuilder();
        ReferenceDocument reference = new ReferenceDocument("ref", "title", "schema guidance", 1.0);

        String prompt = builder.buildSensitiveCredentialRecovery(source(), List.of(reference));

        assertThat(prompt)
                .contains("previous Terraform draft was rejected because it contained a literal sensitive credential")
                .contains("Regenerate the complete response without literal credentials")
                .contains("variable references, generated-secret resources")
                .contains("Retrieved reference evidence:")
                .contains("schema guidance")
                .contains("inputType must be exactly")
                .contains("terraformCode must contain raw Terraform HCL");
    }

    @Test
    void promptContainsOnlyRequestSelectedSchemaSummariesAndForbidsModules() {
        VertexPromptBuilder builder = new VertexPromptBuilder();
        AwsProviderSchemaEvidence evidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)"));

        String prompt = builder.build(source(), List.of(), evidence, false);

        assertThat(prompt).contains("aws_vpc: cidr_block(optional)", "Module blocks are forbidden")
                .doesNotContain("aws_subnet", "resource or module blocks");
    }

    @Test
    void safetyRecoveryApiCannotAcceptRejectedTerraformOrCredentialValue() {
        Method method = Arrays.stream(VertexPromptBuilder.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("buildSensitiveCredentialRecovery"))
                .filter(candidate -> candidate.getParameterCount() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(method.getParameterTypes())
                .containsExactly(ObjectContent.class, List.class);
    }

    private ObjectContent source() {
        return new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3});
    }
}
