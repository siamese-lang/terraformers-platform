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
    void acceptsSingleLineResourceTerraformDraftAndStripsFence() {
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
    void acceptsEmptyResourceBodyAndDelegatesSchemaValidityToTerraformCli() {
        TerraformDraftValidation validation =
                validator.validate("resource \"aws_vpc\" \"example\" {}");

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void acceptsIncompleteResourceBodyShapeAndDelegatesSyntaxValidityToTerraformCli() {
        TerraformDraftValidation validation =
                validator.validate("resource \"aws_vpc\" \"example\" {");

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void acceptsIllustrativeCredentialLikeLiteralsBecauseDraftIsNotAutoApplied() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_db_instance" "database" {
                  engine   = "postgres"
                  username = "example-admin"
                  password = "ChangeMeSafely123!"
                  tags = {
                    api_key = "example-api-key"
                    token   = "example-token"
                  }
                }
                """);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void acceptsIllustrativeAccountIdentifiersAndAccountScopedArns() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_iam_role" "example" {
                  name = "example-role"
                  assume_role_policy = jsonencode({
                    Statement = [{
                      Principal = {
                        Federated = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
                      }
                    }]
                  })
                  tags = {
                    account_id = "123456789012"
                  }
                }
                """);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void acceptsPlaceholderTodoAndExampleWordingInsideUsableTerraform() {
        TerraformDraftValidation validation = validator.validate("""
                resource "aws_lb_listener" "https" {
                  # TODO: replace this example certificate ARN for your environment.
                  load_balancer_arn = aws_lb.example.arn
                  certificate_arn   = "arn:aws:acm:us-east-1:123456789012:certificate/placeholder-cert-id"
                  port              = 443
                  protocol          = "HTTPS"
                }
                """);

        assertThat(validation.valid()).isTrue();
    }

    @Test
    void stillRejectsBlankLanguageLabelAndProseOnlyOutput() {
        assertThat(validator.validate("").valid()).isFalse();
        assertThat(validator.validate("terraform").valid()).isFalse();
        assertThat(validator.validate("Here is the Terraform you requested.").valid()).isFalse();
    }
}
