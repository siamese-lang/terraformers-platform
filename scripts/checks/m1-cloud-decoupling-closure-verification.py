#!/usr/bin/env python3
"""Deterministically verify the M1 cloud-decoupling source boundaries."""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "artifacts/m1-cloud-decoupling-closure"
JAVA = ROOT / "backend/src/main/java/com/terraformers/modernization"


@dataclass
class Boundary:
    name: str
    passed: bool
    checks: list[str]
    failures: list[str]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def evaluate(name: str, assertions: list[tuple[bool, str]]) -> Boundary:
    failures = [message for condition, message in assertions if not condition]
    return Boundary(name, not failures, [message for _, message in assertions], failures)


def lacks(text: str, terms: list[str]) -> bool:
    return all(term not in text for term in terms)


def main() -> int:
    identity_service = read("backend/src/main/java/com/terraformers/modernization/identity/AuthenticatedUserService.java")
    user_entity = read("backend/src/main/java/com/terraformers/modernization/identity/UserEntity.java")
    user_repository = read("backend/src/main/java/com/terraformers/modernization/identity/UserRepository.java")
    jwt_config = read("backend/src/main/java/com/terraformers/modernization/security/JwtResourceServerSecurityConfig.java")
    cognito_validator = read("backend/src/main/java/com/terraformers/modernization/security/CognitoAccessTokenValidator.java")
    cognito_mapper = read("backend/src/main/java/com/terraformers/modernization/identity/CognitoJwtExternalIdentityMapper.java")

    boundaries = [
        evaluate("backend_identity_boundary", [
            ("JwtExternalIdentityMapper" in identity_service, "AuthenticatedUserService uses JwtExternalIdentityMapper"),
            (lacks(identity_service.lower(), ["cognito"]), "AuthenticatedUserService contains no Cognito-specific interpretation"),
            ("findByExternalIdentityProviderAndExternalIdentitySubject" in identity_service + user_repository,
             "user lookup uses provider plus subject"),
            (all(term in user_entity for term in ("externalIdentityProvider", "externalIdentitySubject")),
             "user persistence contains provider-neutral external identity fields"),
        ]),
        evaluate("backend_jwt_boundary", [
            ("JwtProviderTokenValidator" in jwt_config, "generic resource-server wiring uses JwtProviderTokenValidator"),
            (lacks(jwt_config.lower(), ["token_use", "client_id", "cognito"]),
             "generic resource-server wiring contains no Cognito claim interpretation"),
            (all(term in cognito_validator for term in ("token_use", "client_id")),
             "Cognito token claims remain in CognitoAccessTokenValidator"),
            ('PROVIDER = "cognito"' in cognito_mapper, "Cognito provider key remains in its identity adapter"),
        ]),
    ]

    amplify_imports = []
    for path in sorted((ROOT / "frontend/src").rglob("*.js")):
        if path.name.endswith(".test.js"):
            continue
        if "aws-amplify/auth" in path.read_text(encoding="utf-8"):
            amplify_imports.append(path.relative_to(ROOT).as_posix())
    allowed_amplify = {"frontend/src/auth/providers/cognitoAmplifyAuthClient.js"}
    frontend_contract = "\n".join(read(path) for path in [
        "frontend/src/auth/AuthSessionContext.js", "frontend/src/utils/api.js",
        "frontend/src/components/EntryPage.js", "frontend/src/components/ConfirmSignUpPage.js",
    ])
    boundaries.append(evaluate("frontend_auth_boundary", [
        (set(amplify_imports) == allowed_amplify,
         f"aws-amplify/auth imports are adapter-only (found: {amplify_imports})"),
        ("authClient" in frontend_contract, "application/session/UI paths use the neutral auth client"),
        (all((ROOT / path).is_file() for path in [
            "frontend/src/auth/AuthSessionContext.test.js", "frontend/src/utils/api.test.js",
            "frontend/src/components/EntryPage.test.js", "frontend/src/components/ConfirmSignUpPage.test.js",
        ]), "frontend auth/session and protected-request regression tests exist"),
    ]))

    storage_paths = [
        "backend/src/main/java/com/terraformers/modernization/storage/SourceObjectReaderService.java",
        "backend/src/main/java/com/terraformers/modernization/storage/UploadObjectStorageService.java",
        "backend/src/main/java/com/terraformers/modernization/projectcore/ProjectArtifactService.java",
        "backend/src/main/java/com/terraformers/modernization/storage/ObjectReader.java",
        "backend/src/main/java/com/terraformers/modernization/storage/ObjectWriter.java",
        "backend/src/main/java/com/terraformers/modernization/storage/ObjectReference.java",
        "backend/src/main/java/com/terraformers/modernization/storage/ObjectWriteResult.java",
    ]
    storage_core = "\n".join(read(path) for path in storage_paths)
    artifact_service = read(storage_paths[2])
    stub_writer = read("backend/src/main/java/com/terraformers/modernization/storage/StubObjectWriter.java")
    boundaries.append(evaluate("object_storage_boundary", [
        ("software.amazon" not in storage_core, "storage ports/application services contain no AWS SDK import"),
        ("writeResult.persisted()" in artifact_service and "writeResult.provider()" in artifact_service,
         "artifact persistence records explicit provider/persisted semantics"),
        (not re.search(r"eTag\(\)\s*[!=]=?\s*null", artifact_service),
         "ProjectArtifactService does not infer persistence/provider from eTag presence"),
        (all(term in stub_writer for term in ('"metadata-only"', "false", "null")),
         "StubObjectWriter preserves metadata-only/non-persisted/null-eTag semantics"),
    ]))

    retriever = read("backend/src/main/java/com/terraformers/modernization/reference/opensearch/OpenSearchReferenceRetriever.java")
    boundaries.append(evaluate("opensearch_transport_boundary", [
        ("OpenSearchTransport" in retriever, "OpenSearchReferenceRetriever depends on OpenSearchTransport"),
        (lacks(retriever, ["SignedOpenSearchHttpClient", "software.amazon", "AwsCredentials", "Region", "signing-service-name"]),
         "retriever contains no AWS signing implementation knowledge"),
        ((ROOT / "backend/src/main/java/com/terraformers/modernization/reference/opensearch/SignedOpenSearchHttpClient.java").is_file(),
         "AWS-signed OpenSearch compatibility adapter remains present"),
    ]))

    analysis_properties = read("backend/src/main/java/com/terraformers/modernization/analysis/AnalysisRuntimeProperties.java")
    generic_retrieval = "\n".join(path.read_text(encoding="utf-8") for path in sorted((JAVA / "reference").glob("*.java"))
                                    if not path.name.startswith("Bedrock"))
    forbidden_properties = ["bedrockModelId", "bedrockEmbeddingModelId", "bedrockProviderEnabled",
                            "opensearchServiceName", "sqsPublisherEnabled", "progressQueueUrl", "resultQueueUrl"]
    boundaries.append(evaluate("model_provider_boundary", [
        ((JAVA / "analysis/AnalysisProvider.java").is_file(), "AnalysisProvider port exists"),
        ((JAVA / "reference/EmbeddingProvider.java").is_file(), "EmbeddingProvider port exists"),
        (lacks(analysis_properties, forbidden_properties), "generic AnalysisRuntimeProperties excludes provider-specific values"),
        ("BEDROCK_" not in generic_retrieval and "bedrockModelId" not in generic_retrieval,
         "generic retrieval code does not know Bedrock model configuration"),
    ]))

    prod = read("backend/src/main/resources/application-prod.yml")
    legacy_keys = ["COGNITO_REGION", "COGNITO_USER_POOL_ID", "COGNITO_JWKS_URL", "S3_BUCKET_NAME",
                   "S3_READER_ENABLED", "S3_WRITER_ENABLED", "BEDROCK_PROVIDER_ENABLED",
                   "BEDROCK_EMBEDDING_ENABLED", "ANALYSIS_SQS_PUBLISHER_ENABLED",
                   "OPENSEARCH_RETRIEVER_ENABLED", "OPENSEARCH_SERVICE_NAME"]
    selectors = ["JWT_PROVIDER", "JWT_ISSUER_URI", "JWT_JWK_SET_URI", "OBJECT_READER_PROVIDER",
                 "OBJECT_WRITER_PROVIDER", "ANALYSIS_PROVIDER", "EMBEDDING_PROVIDER", "RETRIEVAL_MODE",
                 "PROGRESS_PUBLISHER", "UPLOAD_SOURCE_BUCKET"]
    boundaries.append(evaluate("runtime_configuration_boundary", [
        (lacks(prod, legacy_keys), "canonical production profile excludes legacy provider-specific selection keys"),
        (all(selector in prod for selector in selectors), "canonical production profile retains neutral selectors"),
        ((ROOT / "backend/src/main/resources/application-aws-compat.yml").is_file(),
         "explicit AWS compatibility profile remains present"),
    ]))

    aws_import_files = {
        path.relative_to(ROOT).as_posix()
        for path in (ROOT / "backend/src/main/java").rglob("*.java")
        if re.search(r"^import software\.amazon", path.read_text(encoding="utf-8"), re.MULTILINE)
    }
    allowed_aws_import_files = {
        "backend/src/main/java/com/terraformers/modernization/analysis/SqsProgressPublisher.java",
        "backend/src/main/java/com/terraformers/modernization/analysis/bedrock/BedrockAnalysisProvider.java",
        "backend/src/main/java/com/terraformers/modernization/analysis/bedrock/BedrockRuntimeConfiguration.java",
        "backend/src/main/java/com/terraformers/modernization/config/CloudWatchMetricsConfiguration.java",
        "backend/src/main/java/com/terraformers/modernization/reference/BedrockArchitectureFactsExtractor.java",
        "backend/src/main/java/com/terraformers/modernization/reference/BedrockEmbeddingProvider.java",
        "backend/src/main/java/com/terraformers/modernization/reference/opensearch/SignedOpenSearchHttpClient.java",
        "backend/src/main/java/com/terraformers/modernization/storage/AwsS3ObjectReader.java",
        "backend/src/main/java/com/terraformers/modernization/storage/AwsS3ObjectWriter.java",
    }
    unexpected_aws = sorted(aws_import_files - allowed_aws_import_files)
    missing_adapters = sorted(allowed_aws_import_files - aws_import_files)
    aws_allowlist_passed = not unexpected_aws and not missing_adapters

    ingestion = read("scripts/rag/ingest-corpus.py")
    cloudwatch_preserved = (JAVA / "config/CloudWatchMetricsConfiguration.java").is_file()
    aws_compat = read("backend/src/main/resources/application-aws-compat.yml")
    historical_aws_preserved = aws_allowlist_passed and all(
        term in aws_compat for term in ("cognito", "s3", "bedrock", "sqs", "signing-service-name")
    )
    gcp_markers = ["com.google.cloud", "GcsObject", "VertexAi", "Gemini"]
    production_text = "\n".join(path.read_text(encoding="utf-8", errors="ignore")
                                  for path in (ROOT / "backend/src/main").rglob("*.*") if path.is_file())
    gcp_selected = any(marker in production_text for marker in gcp_markers)

    overall = all(boundary.passed for boundary in boundaries) and aws_allowlist_passed
    summary = {
        "m1_cloud_decoupling_boundary": "passed" if overall else "failed",
        **{boundary.name: "passed" if boundary.passed else "failed" for boundary in boundaries},
        "aws_sdk_allowlist": {
            "result": "passed" if aws_allowlist_passed else "failed",
            "allowed_files": sorted(allowed_aws_import_files),
            "observed_files": sorted(aws_import_files),
            "unexpected_files": unexpected_aws,
            "missing_expected_adapter_imports": missing_adapters,
        },
        "historical_aws_adapters_preserved": historical_aws_preserved,
        "gcp_provider_selected": gcp_selected,
        "residual_batch_ingestion_aws_bound": all(term in ingestion for term in ("boto3", "AWS4Auth", "bedrock")),
        "residual_cloudwatch_historical": cloudwatch_preserved,
        "boundaries": [asdict(boundary) for boundary in boundaries],
    }

    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "boundary-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    markdown = ["# M1 cloud-decoupling boundary summary", "", f"Overall: **{summary['m1_cloud_decoupling_boundary'].upper()}**", "",
                "| Boundary | Result |", "|---|---|"]
    markdown.extend(f"| `{boundary.name}` | {('PASS' if boundary.passed else 'FAIL')} |" for boundary in boundaries)
    markdown.extend(["", "## AWS SDK import inventory", ""] + [f"- `{path}`" for path in sorted(aws_import_files)])
    for boundary in boundaries:
        if boundary.failures:
            markdown.extend(["", f"## {boundary.name} failures", ""] + [f"- {failure}" for failure in boundary.failures])
    (OUTPUT / "boundary-summary.md").write_text("\n".join(markdown) + "\n", encoding="utf-8")
    lines = [f"m1_cloud_decoupling_boundary={summary['m1_cloud_decoupling_boundary']}"]
    lines.extend(f"{boundary.name}={'passed' if boundary.passed else 'failed'}" for boundary in boundaries)
    lines.extend([
        f"historical_aws_adapters_preserved={str(historical_aws_preserved).lower()}",
        f"gcp_provider_selected={str(gcp_selected).lower()}",
        f"residual_batch_ingestion_aws_bound={str(summary['residual_batch_ingestion_aws_bound']).lower()}",
        f"residual_cloudwatch_historical={str(cloudwatch_preserved).lower()}",
    ])
    (OUTPUT / "verification-summary.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    if unexpected_aws:
        print("Unexpected AWS SDK imports: " + ", ".join(unexpected_aws), file=sys.stderr)
    if missing_adapters:
        print("Expected AWS adapter imports missing: " + ", ".join(missing_adapters), file=sys.stderr)
    return 0 if overall else 1


if __name__ == "__main__":
    raise SystemExit(main())
