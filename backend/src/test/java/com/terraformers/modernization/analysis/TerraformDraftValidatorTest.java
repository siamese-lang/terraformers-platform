package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TerraformDraftValidatorTest {
    private final TerraformDraftValidator validator = new TerraformDraftValidator();

    @Test
    void rejectsProviderOnlyTerraform() {
        TerraformDraftValidation validation = validator.validate("""
                terraform { required_version = ">= 1.6" }
                provider "aws" { region = var.aws_region }
                """);
        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("resource or module");
    }

    @Test
    void acceptsSingleLineResourceTerraformDraft() {
        TerraformDraftValidation validation = validator.validate("""
                ```hcl
                provider "aws" { region = var.aws_region }
                resource "aws_s3_bucket" "accepted" { bucket_prefix = "accepted-" }
                ```
                """);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.sanitizedContent()).doesNotContain("```");
    }

    @Test
    void acceptsMultilineResourceTerraformDraft() {
        TerraformDraftValidation validation = validator.validate("""
                provider "aws" { region = var.aws_region }

                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }

                resource "aws_subnet" "public" {
                  vpc_id     = aws_vpc.main.id
                  cidr_block = "10.0.1.0/24"
                }
                """);
        assertThat(validation.valid()).isTrue();
    }

    @Test
    void rejectsHardCodedPasswordObservedInGeminiComparisonRun() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "dbadmin"
                  password = "ChangeMeSafely123!"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo(
                "generated Terraform contains a hard-coded sensitive credential");
        assertThat(validation.reason()).doesNotContain("ChangeMeSafely123!");
        assertThat(validator.isHardCodedSensitiveCredentialFailure(validation)).isTrue();
    }

    @Test
    void rejectsOtherHardCodedSensitiveCredentialLiterals() {
        assertSensitiveLiteralRejected("access_key", "AKIAEXAMPLE000000000");
        assertSensitiveLiteralRejected("secret_access_key", "example-secret-value");
        assertSensitiveLiteralRejected("client_secret", "example-client-secret");
        assertSensitiveLiteralRejected("secret", "example-secret");
        assertSensitiveLiteralRejected("secret_string", "example-secret-string");
        assertSensitiveLiteralRejected("api_key", "example-api-key");
        assertSensitiveLiteralRejected("auth_token", "example-auth-token");
        assertSensitiveLiteralRejected("access_token", "example-access-token");
        assertSensitiveLiteralRejected("token", "example-token");
        assertSensitiveLiteralRejected("private_key", "example-private-key");
    }

    @Test
    void acceptsReferencedOrManagedSensitiveValues() {
        TerraformDraftValidation variable = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "dbadmin"
                  password = var.db_password
                }
                """);
        TerraformDraftValidation generated = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "dbadmin"
                  password = random_password.database.result
                }
                """);
        TerraformDraftValidation managed = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine                      = "postgres"
                  username                    = "dbadmin"
                  manage_master_user_password = true
                }
                """);
        TerraformDraftValidation interpolation = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "dbadmin"
                  password = "${var.db_password}"
                }
                """);

        assertThat(variable.valid()).isTrue();
        assertThat(generated.valid()).isTrue();
        assertThat(managed.valid()).isTrue();
        assertThat(interpolation.valid()).isTrue();
    }

    @Test
    void rejectsMixedLiteralAndInterpolationCredential() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "dbadmin"
                  password = "HardCodedSecret-${var.suffix}"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("hard-coded sensitive credential");
    }

    @Test
    void rejectsSensitiveLiteralInsideSingleLineResource() {
        TerraformDraftValidation validation = validator.validate(
                "resource \"aws_db_instance\" \"database\" { engine = \"postgres\" password = \"HardCodedSecret!\" }");

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("hard-coded sensitive credential");
    }

    @Test
    void rejectsAccountScopedArnObservedInGeminiComparisonRun() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_iam_role" "gha" {
                  name = "gha-role"
                  assume_role_policy = jsonencode({
                    Statement = [{
                      Principal = {
                        Federated = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
                      }
                    }]
                  })
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo(
                "generated Terraform contains an account-specific AWS identifier");
        assertThat(validation.reason()).doesNotContain("123456789012");
    }

    @Test
    void rejectsLiteralAwsAccountIdAttribute() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_s3_bucket" "example" {
                  bucket_prefix = "example-"
                  tags = {
                    account_id = "123456789012"
                  }
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("account-specific AWS identifier");
    }

    @Test
    void rejectsLiteralAwsAccountIdInsideSingleLineMap() {
        TerraformDraftValidation validation = validator.validate(
                "resource \"aws_s3_bucket\" \"example\" { bucket_prefix = \"example-\" tags = { account_id = \"123456789012\" } }");

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("account-specific AWS identifier");
    }

    @Test
    void acceptsAwsManagedPolicyArnAndGeneratedResourceArn() {
        TerraformDraftValidation managedPolicy = validator.validate("""
                resource "aws_iam_role_policy_attachment" "readonly" {
                  role       = aws_iam_role.example.name
                  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
                }
                """);
        TerraformDraftValidation generatedArn = validator.validate("""
                resource "aws_lambda_permission" "invoke" {
                  function_name = aws_lambda_function.example.function_name
                  principal     = "events.amazonaws.com"
                  source_arn    = aws_cloudwatch_event_rule.example.arn
                }
                """);

        assertThat(managedPolicy.valid()).isTrue();
        assertThat(generatedArn.valid()).isTrue();
    }

    @Test
    void preservesPlaceholderFailurePrecedence() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_lb_listener" "https" {
                  load_balancer_arn = aws_lb.app.arn
                  certificate_arn   = "arn:aws:acm:us-east-1:123456789012:certificate/placeholder-cert-id"
                  port              = 443
                  protocol          = "HTTPS"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo(
                "generated Terraform appears to be placeholder/example output");
        assertThat(validator.isHardCodedSensitiveCredentialFailure(validation)).isFalse();
    }

    @Test
    void doesNotClassifyValidDraftOrNullAsSensitiveCredentialFailure() {
        TerraformDraftValidation valid = validator.validate("""
                resource "aws_db_instance" "database" {
                  password = var.db_password
                }
                """);

        assertThat(validator.isHardCodedSensitiveCredentialFailure(valid)).isFalse();
        assertThat(validator.isHardCodedSensitiveCredentialFailure(null)).isFalse();
    }

    private void assertSensitiveLiteralRejected(String attribute, String value) {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_instance" "example" {
                  ami = "ami-12345678"
                  %s = "%s"
                }
                """.formatted(attribute, value));

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).contains("hard-coded sensitive credential");
    }
}
