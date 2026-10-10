package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedTerraformContractInspectorTest {

    private final AwsProviderSchemaCatalog catalog = new AwsProviderSchemaCatalog(new ObjectMapper(),
            Path.of("src/test/resources/terraform/aws-provider-schema-catalog-fixture.json"));
    private final GeneratedTerraformContractInspector inspector = new GeneratedTerraformContractInspector(catalog);

    @Test
    void detectsOmissionAcrossNamesInterpolationAndMultipleOrigins() throws Exception {
        String draft = originDraft();
        assertThat(inspector.missingCloudFrontS3Authorization(draft)).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(draft.replace("delivery_store", "another_name")))
                .isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(draft.replace(
                "aws_s3_bucket.delivery_store.bucket_regional_domain_name",
                "\"${aws_s3_bucket.delivery_store.bucket_regional_domain_name}\""))).isTrue();
        String authorized = draft + policy("aws_s3_bucket.delivery_store.id", "var.supplied_read_policy");
        String second = draft.replace("delivery_store", "uncovered").replace("origin_signer", "other_signer")
                .replace("\"edge\"", "\"other_edge\"");
        assertThat(inspector.missingCloudFrontS3Authorization(authorized + second)).isTrue();
    }

    @Test
    void acceptsSuppliedPolicyVariantsWithoutRequiringAParticularTemplate() throws Exception {
        for (String supplied : List.of("var.supplied_read_policy", "data.aws_iam_policy_document.origin_read.json",
                "templatefile(var.policy_path, { bucket_arn = aws_s3_bucket.delivery_store.arn })",
                "jsonencode({ Statement = [{ Effect = \"Allow\", Principal = { Service = \"cloudfront.amazonaws.com\" }, Action = \"s3:GetObject\", Resource = \"${aws_s3_bucket.delivery_store.arn}/*\" }] })",
                "<<-POLICY\n{\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"${aws_s3_bucket.delivery_store.arn}/*\"}]}\nPOLICY")) {
            assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                    + policy("aws_s3_bucket.delivery_store.id", supplied))).as(supplied).isFalse();
        }
    }

    @Test
    void acceptsBucketNameInputsAndTransparentLocalAliases() throws Exception {
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                + policy("var.bucket_name", "var.supplied_read_policy"))).isFalse();
        String locals = """
                locals {
                  origin_domain = aws_s3_bucket.delivery_store.bucket_regional_domain_name
                  bucket_id = aws_s3_bucket.delivery_store.id
                  bucket_alias = local.bucket_id
                }
                """;
        String draft = originDraft().replace("aws_s3_bucket.delivery_store.bucket_regional_domain_name",
                "local.origin_domain");
        assertThat(inspector.missingCloudFrontS3Authorization(locals + draft)).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(locals + draft
                + policy("local.bucket_alias", "var.supplied_read_policy"))).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization(locals + draft
                + policy("\"${local.bucket_alias}\"", "var.supplied_read_policy"))).isFalse();
    }

    @Test
    void acceptsAlternateDeclaredReadAuthorizationAndPreservesOtherOrigins() throws Exception {
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft() + """
                resource "aws_s3_bucket_acl" "public_origin" {
                  bucket = aws_s3_bucket.delivery_store.id
                  acl = "public-read"
                }
                """)).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft() + """
                resource "aws_s3_bucket_acl" "public_origin" {
                  bucket = aws_s3_bucket.delivery_store.id
                  access_control_policy {
                    grant {
                      grantee {
                        type = "Group"
                        uri = "http://acs.amazonaws.com/groups/global/AllUsers"
                      }
                      permission = "READ"
                    }
                  }
                }
                """)).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft().replace(
                "origin_access_control_id = aws_cloudfront_origin_access_control.origin_signer.id",
                "s3_origin_config { origin_access_identity = var.existing_oai_path }"))).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft().replace(
                "aws_s3_bucket.delivery_store.bucket_regional_domain_name", "var.existing_origin_domain"))).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft().replace(
                "aws_s3_bucket.delivery_store.bucket_regional_domain_name",
                "data.aws_s3_bucket.existing.bucket_regional_domain_name"))).isFalse();
        assertThat(inspector.missingCloudFrontS3Authorization("resource \"aws_vpc\" \"network\" {}"))
                .isFalse();
    }

    @Test
    void unrelatedPoliciesAndNonReadAclsCannotHideMissingAuthorization() throws Exception {
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                + policy("aws_s3_bucket.other.id", "var.supplied_read_policy"))).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                + policy("aws_s3_bucket.delivery_store.id", "\"\""))).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft() + """
                resource "aws_s3_bucket_acl" "private_origin" {
                  bucket = aws_s3_bucket.delivery_store.id
                  acl = "private"
                }
                """)).isTrue();
    }

    @Test
    void commentsLiteralStringsAndHeredocExamplesAreNotAuthorizationDeclarations() throws Exception {
        String grant = policy("aws_s3_bucket.delivery_store.id", "var.supplied_read_policy");
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft() + "/*\n" + grant + "*/"))
                .isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                + "locals { example = <<-EXAMPLE\n" + grant + "EXAMPLE\n}\n")).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization(originDraft()
                + "# resource \"aws_s3_bucket_policy\" \"fake\" {}\n")).isTrue();
        assertThat(inspector.missingCloudFrontS3Authorization("""
                locals {
                  explanation = "resource \\\"aws_cloudfront_distribution\\\" \\\"fake\\\" { origin { } }"
                }
                """)).isFalse();
    }

    private String originDraft() throws Exception {
        return Files.readString(Path.of("src/test/resources/terraform/pt3-origin-without-authorization.tf"));
    }

    private String policy(String bucket, String supplied) {
        return "\nresource \"aws_s3_bucket_policy\" \"read_grant\" {\n  bucket = " + bucket
                + "\n  policy = " + supplied + "\n}\n";
    }

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
