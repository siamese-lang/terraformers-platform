package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VertexPromptBuilderTest {

    @Test
    void promptUsesRequestRelevantSchemaAsContextWithoutPolicyOnlyCredentialRules() {
        VertexPromptBuilder builder = new VertexPromptBuilder();
        AwsProviderSchemaEvidence evidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)"));

        String prompt = builder.build(source(), List.of(), evidence, false);

        assertThat(prompt)
                .contains("aws_vpc: cidr_block(optional)")
                .contains("Request-relevant AWS Provider 5.100.0 schema context")
                .contains("Module blocks are forbidden")
                .doesNotContain(
                        "Safety recovery mode",
                        "previous Terraform draft was rejected",
                        "without literal credentials",
                        "Do not include secrets, account IDs, access keys, static credentials");
    }

    @Test
    void promptDoesNotInjectUnselectedProviderSchemaSummaries() {
        VertexPromptBuilder builder = new VertexPromptBuilder();
        AwsProviderSchemaEvidence evidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)"));

        String prompt = builder.build(source(), List.of(), evidence, false);

        assertThat(prompt).doesNotContain("aws_subnet");
    }

    private ObjectContent source() {
        return new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3});
    }
}
