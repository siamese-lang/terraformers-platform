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
    void detectsPendingCertificateConsumerDespiteSchemaValidityAndUnrelatedValidation() {
        String draft = pendingCertificateDraft();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft)).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace("pending", "renamed"))).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace("\"DNS\"", "\"EMAIL\""))).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + validationWait())).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace(
                "viewer_certificate {", "depends_on = [aws_acm_certificate_validation.other]\n viewer_certificate {")
                + validationWait().replace("pending.arn", "unrelated.arn").replace("\"issued\"", "\"other\""))).isTrue();
        String alias = "locals { requested = aws_acm_certificate.pending.arn }\n";
        assertThat(inspector.missingCloudFrontCertificateValidation(alias + draft.replace(
                "acm_certificate_arn = aws_acm_certificate.pending.arn", "acm_certificate_arn = local.requested"))).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace(
                "acm_certificate_arn = aws_acm_certificate.pending.arn",
                "acm_certificate_arn = \"${aws_acm_certificate.pending.arn}\""))).isTrue();
    }

    @Test
    void acceptsMatchingValidationDependencyAndIssuedExternalBoundaries() {
        String draft = pendingCertificateDraft();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace(
                "aws_acm_certificate.pending.arn", "aws_acm_certificate_validation.issued.certificate_arn")
                + validationWait())).isFalse();
        String ordered = draft.replace("viewer_certificate {",
                "depends_on = [aws_acm_certificate_validation.issued]\n viewer_certificate {") + validationWait();
        assertThat(inspector.missingCloudFrontCertificateValidation(ordered)).isFalse();
        String transitive = ordered.replace("depends_on = [aws_acm_certificate_validation.issued]",
                "depends_on = [aws_route53_record.validation_barrier]") + """
                resource "aws_route53_record" "validation_barrier" {
                  depends_on = [aws_acm_certificate_validation.issued]
                  name = var.domain
                }
                """;
        assertThat(inspector.missingCloudFrontCertificateValidation(transitive)).isFalse();
        String implicit = ordered.replace("depends_on = [aws_acm_certificate_validation.issued]",
                "comment = local.issued_certificate")
                + "locals { issued_certificate = aws_acm_certificate_validation.issued.certificate_arn }";
        assertThat(inspector.missingCloudFrontCertificateValidation(implicit)).isFalse();
        // External DNS can supply validation_record_fqdns; no Route 53 template is forced.
        for (String arn : List.of("var.issued_certificate_arn", "data.aws_acm_certificate.existing.arn")) {
            assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace(
                    "acm_certificate_arn = aws_acm_certificate.pending.arn", "acm_certificate_arn = " + arn))).isFalse();
        }
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + """
                import {
                  to = aws_acm_certificate.pending
                  id = var.existing_issued_certificate_arn
                }
                """)).isFalse();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft.replace(
                "validation_method = \"DNS\"", "certificate_body = var.imported_certificate_body"))).isFalse();
    }

    @Test
    void ignoresCommentAndStringLookalikesAndFindsEachPendingConsumer() {
        String draft = pendingCertificateDraft();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + """
                # depends_on = [aws_acm_certificate_validation.issued]
                locals {
                  example = "aws_acm_certificate_validation.issued"
                }
                """ + validationWait())).isTrue();
        assertThat(inspector.missingCloudFrontCertificateValidation("# " + draft.replace("\n", "\n# "))).isFalse();
        String valid = draft.replace("aws_acm_certificate.pending.arn", "var.issued_certificate_arn");
        assertThat(inspector.missingCloudFrontCertificateValidation(valid + draft.replace(
                "\"edge\"", "\"other_edge\"").replace("pending", "other_pending"))).isTrue();
    }

    @Test
    void acceptsOnlyTheConsumedImportedCertificateInstance() {
        for (String index : List.of("0", "\"existing\"")) {
            String draft = pendingCertificateDraft().replace("pending.arn", "pending[" + index + "].arn");
            String imported = "\nimport {\n  to = aws_acm_certificate.pending[" + index
                    + "]\n  id = var.existing_issued_certificate_arn\n}\n";
            assertThat(inspector.missingCloudFrontCertificateValidation(draft + imported)).isFalse();
            assertThat(inspector.missingCloudFrontCertificateValidation(draft)).isTrue();
            assertThat(inspector.missingCloudFrontCertificateValidation(draft + imported.replace(
                    "pending[" + index + "]", "pending[\"other\"]"))).isTrue();
            assertThat(inspector.missingCloudFrontCertificateValidation(draft + imported.replace(
                    "var.existing_issued_certificate_arn", "null"))).isTrue();
        }
        String indexed = pendingCertificateDraft().replace("pending.arn", "pending[0].arn");
        assertThat(inspector.missingCloudFrontCertificateValidation(indexed + """
                import {
                  to = aws_acm_certificate.pending
                  id = var.existing_issued_certificate_arn
                }
                """)).isTrue();
    }

    @Test
    void requiresIssuanceDependencyForTheConsumedCertificateInstance() {
        for (List<String> indices : List.of(List.of("0", "1"), List.of("\"other\"", "\"edge\""))) {
            String draft = pendingCertificateDraft().replace("pending.arn", "pending[" + indices.get(1) + "].arn")
                    .replace("viewer_certificate {", "depends_on = [aws_acm_certificate_validation.issued]\n viewer_certificate {");
            String mismatched = validationWait().replace("pending.arn", "pending[" + indices.get(0) + "].arn");
            String matching = validationWait().replace("pending.arn", "pending[" + indices.get(1) + "].arn");
            assertThat(inspector.missingCloudFrontCertificateValidation(draft + mismatched)).isTrue();
            assertThat(inspector.missingCloudFrontCertificateValidation(draft + matching)).isFalse();
            String transitive = draft.replace("depends_on = [aws_acm_certificate_validation.issued]",
                    "depends_on = [aws_route53_record.barrier]") + """
                    resource "aws_route53_record" "barrier" {
                      depends_on = [aws_acm_certificate_validation.issued]
                      name = var.domain
                    }
                    """;
            assertThat(inspector.missingCloudFrontCertificateValidation(transitive + mismatched)).isTrue();
            assertThat(inspector.missingCloudFrontCertificateValidation(transitive + matching)).isFalse();
        }
    }

    @Test
    void checksEveryPotentialConsumerInstanceWhilePreservingIndividualImports() {
        String draft = pendingCertificateDraft().replace("pending.arn",
                "var.use_imported ? aws_acm_certificate.pending[0].arn : aws_acm_certificate.pending[1].arn")
                .replace("viewer_certificate {", "depends_on = [aws_acm_certificate_validation.issued]\n viewer_certificate {");
        String firstWait = validationWait().replace("pending.arn", "pending[0].arn");
        String secondWait = validationWait().replace("pending.arn", "pending[1].arn");
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + firstWait)).isTrue();
        String imported = """
                import {
                  to = aws_acm_certificate.pending[0]
                  id = var.existing_issued_certificate_arn
                }
                """;
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + imported + secondWait)).isFalse();
        assertThat(inspector.missingCloudFrontCertificateValidation(draft + imported + firstWait)).isTrue();
    }

    private String pendingCertificateDraft() {
        return """
                resource "aws_acm_certificate" "pending" {
                  domain_name = var.domain
                  validation_method = "DNS"
                }
                resource "aws_cloudfront_distribution" "edge" {
                  viewer_certificate {
                    acm_certificate_arn = aws_acm_certificate.pending.arn
                    ssl_support_method = "sni-only"
                  }
                }
                """;
    }

    private String validationWait() {
        return """
                resource "aws_acm_certificate_validation" "issued" {
                  certificate_arn = aws_acm_certificate.pending.arn
                  validation_record_fqdns = var.externally_managed_validation_fqdns
                }
                """;
    }

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
