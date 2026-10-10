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
        assertThat(prompt).contains("image-observed intent only", "not evidence of an image connection",
                "declared external input", "Preserve every visible directed relationship",
                "without fabricated defaults", "dummy local artifact paths", "actionable descriptions",
                "us-east-1", "API's actual region", "Record implementation assumptions");
    }

    @Test
    void promptDoesNotInjectUnselectedProviderSchemaSummaries() {
        VertexPromptBuilder builder = new VertexPromptBuilder();
        AwsProviderSchemaEvidence evidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)"));

        String prompt = builder.build(source(), List.of(), evidence, false);

        assertThat(prompt).doesNotContain("aws_subnet");
    }

    @Test
    void repairPromptContainsPriorDraftFactsOfficialEvidenceAndExactSchemaConstraints() {
        var builder = new VertexPromptBuilder();
        var original = new com.terraformers.modernization.analysis.AnalysisGenerationResult(
                "vertex:test", com.terraformers.modernization.analysis.AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                1.0, "resource \"aws_instance\" \"app\" { instance_class = \"bad\" }", "API inside VPC",
                List.of("API", "VPC"), List.of("VPC -> API"), List.of(), "STOP", 10, false);
        var facts = new com.terraformers.modernization.reference.ArchitectureRetrievalFacts(
                "API architecture", List.of("API"), List.of("VPC -> API"), List.of("aws_instance"));
        var reference = new com.terraformers.modernization.reference.ReferenceDocument("instance-doc",
                "EC2 official", "EC2 documentation content", 1, "AWS_PROVIDER_DOC", List.of("aws_instance"),
                "instance.md", "5.100.0", "any-corpus", "PROVIDER_DOCUMENTATION", 1, List.of());
        var schema = new AwsProviderSchemaEvidence(Map.of("aws_instance",
                "ami: string (required), instance_type: string (optional), root_block_device block(optional)"));
        String prompt = builder.buildRepair(facts, original, List.of(reference), schema);
        assertThat(prompt).contains(original.terraformCode(), "API architecture", "VPC -> API",
                "PROVIDER_DOCUMENTATION", "AWS_PROVIDER_DOC", "EC2 documentation content", schema.promptText(),
                "Preserve necessary implementation", "validate them against the provider schema", "Module blocks are forbidden",
                "required provider arguments", "nested-block compatibility", "variable/reference placeholders",
                "not a new image analysis", "PROJECT_DECISION");
        assertThat(prompt).contains("complete HCL once", "no explanations or comments",
                "never required arguments", "wiring or authorization", "Do not use ellipses");
        assertThat(prompt).contains("Prior generated relationships (unverified)",
                "omission is not evidence of absence", "shared support", "IAM role",
                "resourceTypes=[aws_instance]", "documentation covers these managed resource types: [aws_instance]",
                "not an allowed-resource list", "Final resource types will be looked up separately",
                "without fabricated defaults", "CloudFront ACM certificates require us-east-1",
                "certificate request is not an issued certificate", "issuance wait",
                "explicit matching", "DNS records alone do not wait", "custom-domain aliases/mappings");
        assertThat(builder.repairResponseJsonSchema().get("required")).isEqualTo(List.of("terraformCode"));
    }

    private ObjectContent source() {
        return new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3});
    }
}
