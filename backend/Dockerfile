FROM maven:3.9.8-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

ARG BUILD_SOURCE_REVISION=unknown
ENV BUILD_SOURCE_REVISION=${BUILD_SOURCE_REVISION}

ARG TERRAFORM_VERSION=1.8.5
ARG TERRAFORM_SHA256=bb1ee3e8314da76658002e2e584f2d8854b6def50b7f124e27b957a42ddacfea
ARG AWS_PROVIDER_VERSION=5.100.0
ARG AWS_PROVIDER_SHA256=1589a2266af699cbd5d80737a0fe02e54ec9cf2ca54e7e00ac51c7359056f274
ENV TERRAFORM_VALIDATION_PLUGIN_DIR=/opt/terraform-plugins

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSLo /tmp/terraform.zip "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip" \
    && echo "${TERRAFORM_SHA256}  /tmp/terraform.zip" | sha256sum -c - \
    && unzip /tmp/terraform.zip -d /usr/local/bin \
    && rm -f /tmp/terraform.zip \
    && AWS_PROVIDER_PLUGIN_DIR="$TERRAFORM_VALIDATION_PLUGIN_DIR/registry.terraform.io/hashicorp/aws/${AWS_PROVIDER_VERSION}/linux_amd64" \
    && mkdir -p "$AWS_PROVIDER_PLUGIN_DIR" \
    && curl -fsSLo /tmp/aws-provider.zip "https://releases.hashicorp.com/terraform-provider-aws/${AWS_PROVIDER_VERSION}/terraform-provider-aws_${AWS_PROVIDER_VERSION}_linux_amd64.zip" \
    && echo "${AWS_PROVIDER_SHA256}  /tmp/aws-provider.zip" | sha256sum -c - \
    && unzip -q /tmp/aws-provider.zip -d "$AWS_PROVIDER_PLUGIN_DIR" \
    && rm -f /tmp/aws-provider.zip \
    && test "$(find "$AWS_PROVIDER_PLUGIN_DIR" -maxdepth 1 -type f -name 'terraform-provider-aws*' | wc -l)" -eq 1 \
    && find "$AWS_PROVIDER_PLUGIN_DIR" -maxdepth 1 -type f -name 'terraform-provider-aws*' -exec chmod 0755 {} + \
    && terraform version

RUN set -eux; \
    validation_dir="$(mktemp -d)"; \
    trap 'rm -rf "$validation_dir"' EXIT; \
    printf '%s\n' \
      'terraform {' \
      '  required_providers {' \
      '    aws = {' \
      '      source  = "hashicorp/aws"' \
      '      version = "= 5.100.0"' \
      '    }' \
      '  }' \
      '}' \
      'resource "aws_vpc" "valid" {' \
      '  cidr_block = "10.0.0.0/16"' \
      '}' > "$validation_dir/main.tf"; \
    env -i PATH=/usr/local/bin:/usr/bin:/bin HOME="$validation_dir" \
      TF_IN_AUTOMATION=1 TF_DATA_DIR="$validation_dir/.terraform-data" AWS_EC2_METADATA_DISABLED=true \
      terraform -chdir="$validation_dir" init -backend=false -input=false -get=false -no-color \
        -plugin-dir="$TERRAFORM_VALIDATION_PLUGIN_DIR"; \
    env -i PATH=/usr/local/bin:/usr/bin:/bin HOME="$validation_dir" \
      TF_IN_AUTOMATION=1 TF_DATA_DIR="$validation_dir/.terraform-data" AWS_EC2_METADATA_DISABLED=true \
      terraform -chdir="$validation_dir" validate -json > "$validation_dir/valid.json"; \
    grep -Eq '"valid"[[:space:]]*:[[:space:]]*true' "$validation_dir/valid.json"; \
    printf '%s\n' \
      'terraform {' \
      '  required_providers {' \
      '    aws = {' \
      '      source  = "hashicorp/aws"' \
      '      version = "= 5.100.0"' \
      '    }' \
      '  }' \
      '}' \
      'resource "aws_vpc" "invalid" {' \
      '  cidr_block = "10.0.0.0/16"' \
      '  definitely_not_a_real_argument = true' \
      '}' > "$validation_dir/main.tf"; \
    if env -i PATH=/usr/local/bin:/usr/bin:/bin HOME="$validation_dir" \
      TF_IN_AUTOMATION=1 TF_DATA_DIR="$validation_dir/.terraform-data" AWS_EC2_METADATA_DISABLED=true \
      terraform -chdir="$validation_dir" validate -json > "$validation_dir/invalid.json"; then \
        echo 'expected invalid provider schema to fail terraform validate' >&2; \
        exit 1; \
    fi; \
    grep -Eq '"valid"[[:space:]]*:[[:space:]]*false' "$validation_dir/invalid.json"

RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/target/*.jar /tmp/
RUN set -eux; \
    JAR_PATH="$(find /tmp -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | head -n 1)"; \
    test -n "$JAR_PATH"; \
    mv "$JAR_PATH" /app/app.jar; \
    rm -f /tmp/*.jar

USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
