#!/usr/bin/env python3
"""Offline validation for versioned, project-owned Terraform reference corpora."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CORPUS = ROOT / "corpus" / "terraformers-reference" / "v1"
REQUIRED_MANIFEST = {
    "corpusVersion",
    "awsProviderVersion",
    "embeddingModelId",
    "vectorDimension",
    "indexName",
    "vectorField",
    "contentField",
    "documentCount",
    "chunkCount",
    "checksumAlgorithm",
}
BASE_REQUIRED_DOCUMENT = {
    "documentId",
    "title",
    "documentType",
    "content",
    "services",
    "resourceTypes",
    "architecturePattern",
    "securityConsiderations",
    "sourceVersion",
    "providerVersion",
    "sourcePath",
    "corpusVersion",
}
V2_REQUIRED_DOCUMENT = {"authority", "priority", "sectionType", "riskTags", "sourceCommit"}
TYPES = {
    "AWS_PROVIDER_DOC",
    "AWS_PROVIDER_EXAMPLE",
    "AWS_PROVIDER_SCHEMA",
    "MODULE_EXAMPLE",
    "TERRAFORMERS_PATTERN",
}
PROVIDER_TYPES = {"AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE", "AWS_PROVIDER_SCHEMA"}
HISTORICAL_RUNTIME = {
    "awsProviderVersion": "5.100.0",
    "embeddingModelId": "amazon.titan-embed-text-v2:0",
    "vectorDimension": 1024,
    "indexName": "terraformers-reference-v1",
    "vectorField": "embedding",
    "contentField": "content",
}
V3_RUNTIME = {
    "awsProviderVersion": "5.100.0",
    "embeddingModelId": "gemini-embedding-001",
    "vectorDimension": 1024,
    "indexName": "terraformers-reference-v3",
    "vectorField": "embedding",
    "contentField": "content",
}
V4_RUNTIME = {
    "awsProviderVersion": "5.100.0",
    "embeddingModelId": "gemini-embedding-001",
    "vectorDimension": 1024,
    "indexName": "terraformers-reference-v4",
    "vectorField": "embedding",
    "contentField": "content",
}
EXPECTED_RUNTIME_BY_MAJOR = {
    1: HISTORICAL_RUNTIME,
    2: HISTORICAL_RUNTIME,
    3: V3_RUNTIME,
    4: V4_RUNTIME,
}
CORPUS_VERSION_PATTERN = re.compile(r"^terraformers-reference-v([1-9][0-9]*)$")
FORBIDDEN = [
    (re.compile(r"(?<!\d)\d{12}(?!\d)"), "account-like identifier"),
    (re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"), "access key"),
    (re.compile(r"\barn:aws[a-z-]*:"), "live ARN"),
    (re.compile(r"https?://[^\s\"']*amazonaws\.com", re.IGNORECASE), "live AWS endpoint"),
    (re.compile(r"(?i)\.tfstate\b"), "tfstate reference"),
]
SECRET_LITERAL = re.compile(
    r'(?i)\b(?:password|secret|token|access_key|secret_key)\b\s*[:=]\s*'
    r'(?:("(?!<[^>]+>"|REDACTED"|example"|change-me")[^"]+")|'
    r"('(?!<[^>]+>'|REDACTED'|example'|change-me')[^']+'))"
)
BASE_KEYWORD_FIELDS = {
    "documentId",
    "documentType",
    "sourceVersion",
    "sourcePath",
    "corpusVersion",
    "services",
    "resourceTypes",
    "architecturePattern",
    "securityConsiderations",
    "providerVersion",
}
V2_KEYWORD_FIELDS = {"authority", "sectionType", "riskTags", "sourceCommit"}


def fail(message: str) -> None:
    print(f"RAG corpus contract failure: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        try:
            label = path.relative_to(ROOT)
        except ValueError:
            label = path
        fail(f"cannot parse {label}: {exc}")


def require_nonblank_string(value: object, label: str) -> None:
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} must be a nonblank string")


def require_nonblank_list(value: object, label: str, nonempty: bool = True) -> None:
    if (
        not isinstance(value, list)
        or (nonempty and not value)
        or any(not isinstance(item, str) or not item.strip() for item in value)
    ):
        qualifier = "a non-empty " if nonempty else "a "
        fail(f"{label} must be {qualifier}list of nonblank strings")


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def reject_forbidden(value: object, label: str) -> None:
    text = canonical(value)
    for regex, kind in FORBIDDEN:
        if regex.search(text):
            fail(f"{kind} is not permitted in {label}")
    if SECRET_LITERAL.search(text):
        fail(f"literal secret-like value is not permitted in {label}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate a versioned Terraform reference corpus.")
    parser.add_argument("--corpus-dir", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--summary-json", type=Path)
    return parser.parse_args()


def corpus_major(corpus_version: str) -> int:
    match = CORPUS_VERSION_PATTERN.fullmatch(corpus_version)
    if not match:
        fail("manifest corpusVersion must match terraformers-reference-vN")
    return int(match.group(1))


def main() -> None:
    args = parse_args()
    corpus = args.corpus_dir.resolve()
    manifest = load_json(corpus / "corpus-manifest.json")
    if not isinstance(manifest, dict):
        fail("corpus manifest must be a JSON object")
    missing = sorted(REQUIRED_MANIFEST - set(manifest))
    if missing:
        fail(f"corpus manifest is missing required fields: {', '.join(missing)}")

    version = str(manifest["corpusVersion"])
    major = corpus_major(version)
    if corpus.name != f"v{major}":
        fail("corpus directory name must match manifest corpusVersion")
    expected_runtime = EXPECTED_RUNTIME_BY_MAJOR.get(major)
    if expected_runtime is None:
        fail(f"corpus version v{major} has no registered runtime contract")
    for key, value in expected_runtime.items():
        if manifest.get(key) != value:
            fail(f"manifest {key} must be {value!r}")
    if manifest["checksumAlgorithm"] != "SHA-256":
        fail("checksumAlgorithm must be SHA-256")
    if major >= 4 and manifest.get("coverageReportFile") != "coverage-report.json":
        fail("v4+ corpus manifest must declare coverage-report.json")

    provider_source_version = f"v{manifest['awsProviderVersion']}"
    source_manifest = load_json(corpus / "source-manifest.json")
    if not isinstance(source_manifest, dict):
        fail("source manifest must be a JSON object")
    sources = source_manifest.get("sources")
    if not isinstance(sources, list) or not sources:
        fail("source manifest must contain sources")

    source_keys: set[tuple[str, str, str, str]] = set()
    for source in sources:
        if not isinstance(source, dict):
            fail("source manifest entries must be objects")
        source_type = source.get("sourceType")
        source_version = source.get("sourceVersion")
        source_path = source.get("sourcePath")
        source_commit = source.get("sourceCommit", "")
        if source_type not in TYPES:
            fail("source manifest contains an unsupported source type")
        require_nonblank_string(source_version, "source manifest sourceVersion")
        require_nonblank_string(source_path, "source manifest sourcePath")
        if major >= 2:
            require_nonblank_string(source_commit, "source manifest sourceCommit")
        if source_type in PROVIDER_TYPES and source_version != provider_source_version:
            fail(f"AWS provider sources must use tag {provider_source_version}")
        if source_type not in PROVIDER_TYPES and source_version != version:
            fail("project-owned sourceVersion must match corpusVersion")
        key = (str(source_type), str(source_version), str(source_path), str(source_commit))
        if key in source_keys:
            fail(f"duplicate source tuple: {key}")
        source_keys.add(key)
        reject_forbidden(source, "source manifest")

    documents: list[dict[str, object]] = []
    try:
        lines = (corpus / "documents.jsonl").read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail(f"cannot read documents.jsonl: {exc.__class__.__name__}")
    for line_no, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            document = json.loads(line)
        except json.JSONDecodeError:
            fail(f"documents.jsonl line {line_no} is not valid JSON")
        if not isinstance(document, dict):
            fail(f"documents.jsonl line {line_no} must be a JSON object")
        documents.append(document)

    required_document = BASE_REQUIRED_DOCUMENT | (V2_REQUIRED_DOCUMENT if major >= 2 else set())
    ids: set[str] = set()
    for doc in documents:
        missing = sorted(required_document - set(doc))
        if missing:
            fail(f"document is missing required fields: {', '.join(missing)}")
        require_nonblank_string(doc["documentId"], "documentId")
        require_nonblank_string(doc["title"], "title")
        require_nonblank_string(doc["content"], "content")
        require_nonblank_string(doc["architecturePattern"], "architecturePattern")
        require_nonblank_string(doc["sourcePath"], "sourcePath")
        require_nonblank_list(doc["services"], "services")
        require_nonblank_list(doc["resourceTypes"], "resourceTypes")
        require_nonblank_list(doc["securityConsiderations"], "securityConsiderations", nonempty=False)
        if doc["documentType"] not in TYPES:
            fail(f"unsupported document type for {doc['documentId']}")
        require_nonblank_string(doc["sourceVersion"], "sourceVersion")
        require_nonblank_string(doc["providerVersion"], "providerVersion")
        require_nonblank_string(doc["corpusVersion"], "corpusVersion")
        if doc["providerVersion"] != manifest["awsProviderVersion"]:
            fail(f"document {doc['documentId']} providerVersion must match manifest awsProviderVersion")
        if doc["corpusVersion"] != version:
            fail(f"wrong corpus version for {doc['documentId']}")
        if doc["documentType"] in PROVIDER_TYPES:
            if doc["sourceVersion"] != provider_source_version:
                fail(f"provider document {doc['documentId']} must use sourceVersion {provider_source_version}")
        elif doc["sourceVersion"] != version:
            fail(f"project-owned document {doc['documentId']} sourceVersion must match corpusVersion")

        source_commit = str(doc.get("sourceCommit", ""))
        if major >= 2:
            require_nonblank_string(doc["authority"], "authority")
            require_nonblank_string(doc["sectionType"], "sectionType")
            require_nonblank_list(doc["riskTags"], "riskTags", nonempty=False)
            require_nonblank_string(source_commit, "sourceCommit")
            priority = doc["priority"]
            if not isinstance(priority, int) or isinstance(priority, bool) or not 0 <= priority <= 100:
                fail(f"document {doc['documentId']} priority must be an integer from 0 to 100")

        document_id = str(doc["documentId"])
        if document_id in ids:
            fail(f"duplicate document ID {document_id}")
        source_key = (
            str(doc["documentType"]),
            str(doc["sourceVersion"]),
            str(doc["sourcePath"]),
            source_commit,
        )
        if source_key not in source_keys:
            fail(f"document {document_id} does not have a matching source manifest tuple")
        ids.add(document_id)
        reject_forbidden(doc, "corpus documents")

    if manifest["documentCount"] != len(documents) or manifest["chunkCount"] != len(documents):
        fail("manifest documentCount and chunkCount must equal JSONL chunk count")

    coverage_report = None
    if major >= 4:
        coverage_report = load_json(corpus / "coverage-report.json")
        if not isinstance(coverage_report, dict):
            fail("coverage report must be a JSON object")
        required_coverage = {
            "corpusVersion",
            "providerVersion",
            "providerSourceCommit",
            "providerSchemaSha256",
            "providerSchemaResourceCount",
            "officialDocumentationResourceCount",
            "schemaResourcesWithoutOfficialDocumentationCount",
            "v3ProviderResourceCount",
            "selectedResourceCount",
            "selectedOfficialDocumentationResourceCount",
            "selectedOfficialEvidenceResourceCount",
            "selectedMissingOfficialDocumentationResourceTypes",
            "selectedOfficialEvidenceExtractionGapResourceTypes",
            "newResourceTypesComparedWithV3",
            "providerDocumentChunkCount",
            "projectDecisionCount",
        }
        missing_coverage = sorted(required_coverage - set(coverage_report))
        if missing_coverage:
            fail("coverage report is missing required fields: " + ", ".join(missing_coverage))
        if coverage_report["corpusVersion"] != version:
            fail("coverage report corpusVersion must match manifest")
        if coverage_report["providerVersion"] != manifest["awsProviderVersion"]:
            fail("coverage report providerVersion must match manifest")
        require_nonblank_string(
            coverage_report["providerSourceCommit"], "coverage report providerSourceCommit"
        )
        if not re.fullmatch(r"[0-9a-f]{64}", str(coverage_report["providerSchemaSha256"])):
            fail("coverage report providerSchemaSha256 must be a lowercase SHA-256")
        for field in (
            "providerSchemaResourceCount",
            "officialDocumentationResourceCount",
            "schemaResourcesWithoutOfficialDocumentationCount",
            "v3ProviderResourceCount",
            "selectedResourceCount",
            "selectedOfficialDocumentationResourceCount",
            "selectedOfficialEvidenceResourceCount",
            "providerDocumentChunkCount",
            "projectDecisionCount",
        ):
            value = coverage_report[field]
            if not isinstance(value, int) or isinstance(value, bool) or value < 0:
                fail(f"coverage report {field} must be a non-negative integer")
        require_nonblank_list(
            coverage_report["selectedMissingOfficialDocumentationResourceTypes"],
            "selectedMissingOfficialDocumentationResourceTypes",
            nonempty=False,
        )
        require_nonblank_list(
            coverage_report["selectedOfficialEvidenceExtractionGapResourceTypes"],
            "selectedOfficialEvidenceExtractionGapResourceTypes",
            nonempty=False,
        )
        require_nonblank_list(
            coverage_report["newResourceTypesComparedWithV3"],
            "newResourceTypesComparedWithV3",
            nonempty=False,
        )
        provider_documents = [
            document for document in documents if document["documentType"] in PROVIDER_TYPES
        ]
        provider_resource_types = {
            str(resource_type)
            for document in provider_documents
            for resource_type in document["resourceTypes"]
        }
        official_evidence_resource_types = {
            str(resource_type)
            for document in provider_documents
            if document["documentType"] in {"AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE"}
            for resource_type in document["resourceTypes"]
        }
        project_decision_count = sum(
            document["documentType"] == "TERRAFORMERS_PATTERN" for document in documents
        )
        missing_official = set(
            str(value)
            for value in coverage_report["selectedMissingOfficialDocumentationResourceTypes"]
        )
        extraction_gaps = set(
            str(value)
            for value in coverage_report[
                "selectedOfficialEvidenceExtractionGapResourceTypes"
            ]
        )
        if coverage_report["selectedResourceCount"] != len(provider_resource_types):
            fail("coverage report selectedResourceCount does not match corpus provider resources")
        if coverage_report["providerDocumentChunkCount"] != len(provider_documents):
            fail("coverage report providerDocumentChunkCount does not match corpus")
        if coverage_report["projectDecisionCount"] != project_decision_count:
            fail("coverage report projectDecisionCount does not match corpus")
        if not missing_official <= provider_resource_types:
            fail("coverage report missing official resources must be selected corpus resources")
        if not extraction_gaps <= provider_resource_types:
            fail("coverage report extraction-gap resources must be selected corpus resources")
        if missing_official & extraction_gaps:
            fail("coverage report source-missing and extraction-gap resources must be disjoint")
        if official_evidence_resource_types & (missing_official | extraction_gaps):
            fail("coverage report official-evidence resources overlap a knowledge gap")
        if (
            official_evidence_resource_types | missing_official | extraction_gaps
            != provider_resource_types
        ):
            fail("coverage report official evidence and gap classes do not cover selected resources")
        if (
            coverage_report["selectedOfficialEvidenceResourceCount"]
            != len(official_evidence_resource_types)
        ):
            fail("coverage report selectedOfficialEvidenceResourceCount does not match corpus")
        if (
            coverage_report["selectedOfficialDocumentationResourceCount"]
            != len(official_evidence_resource_types | extraction_gaps)
        ):
            fail("coverage report selected official source count does not reconcile")
        if (
            coverage_report["selectedOfficialDocumentationResourceCount"] + len(missing_official)
            != coverage_report["selectedResourceCount"]
        ):
            fail("coverage report selected official/missing counts do not reconcile")
        if coverage_report["providerSchemaResourceCount"] < coverage_report["selectedResourceCount"]:
            fail("coverage report provider schema universe is smaller than selected resources")
        if (
            coverage_report["officialDocumentationResourceCount"]
            > coverage_report["providerSchemaResourceCount"]
        ):
            fail("coverage report official documented resources exceed provider schema universe")
        provider_commits = {
            str(document["sourceCommit"])
            for document in provider_documents
            if document["documentType"] in PROVIDER_TYPES
        }
        if provider_commits != {coverage_report["providerSourceCommit"]}:
            fail("coverage report providerSourceCommit does not match provider documents")

    schema = load_json(corpus / "index-schema.json")
    if not isinstance(schema, dict):
        fail("index schema must be a JSON object")
    props = schema.get("mappings", {}).get("properties", {})
    vector = props.get(manifest["vectorField"], {})
    if (
        schema.get("settings", {}).get("index", {}).get("knn") is not True
        or vector.get("type") != "knn_vector"
        or vector.get("dimension") != manifest["vectorDimension"]
    ):
        fail("index vector settings do not match manifest")
    if (
        vector.get("method", {}).get("name") != "hnsw"
        or vector.get("method", {}).get("engine") != "faiss"
        or vector.get("method", {}).get("space_type") != "cosinesimil"
    ):
        fail("index must use Faiss HNSW cosine similarity")
    if props.get(manifest["contentField"], {}).get("type") != "text" or props.get("title", {}).get("type") != "text":
        fail("title and content must be text fields")

    keyword_fields = BASE_KEYWORD_FIELDS | (V2_KEYWORD_FIELDS if major >= 2 else set())
    invalid_keyword_fields = sorted(
        field for field in keyword_fields if props.get(field, {}).get("type") != "keyword"
    )
    if invalid_keyword_fields:
        fail(f"index metadata fields must be keyword: {', '.join(invalid_keyword_fields)}")
    if major >= 2 and props.get("priority", {}).get("type") != "integer":
        fail("index priority field must be integer")

    for source in sources:
        if source["sourceType"] not in PROVIDER_TYPES and not (ROOT / source["sourcePath"]).is_file():
            fail(f"project-owned source path does not exist: {source['sourcePath']}")

    sorted_sources = sorted(
        sources,
        key=lambda item: (
            item["sourceType"],
            item["sourceVersion"],
            item["sourcePath"],
            item.get("sourceCommit", ""),
        ),
    )
    sorted_documents = sorted(documents, key=lambda item: item["documentId"])
    checksum_input = {
        "corpusManifest": manifest,
        "sourceManifest": {"sources": sorted_sources},
        "indexSchema": schema,
        "documents": sorted_documents,
    }
    if coverage_report is not None:
        checksum_input["coverageReport"] = coverage_report
    digest = hashlib.sha256(canonical(checksum_input).encode("utf-8")).hexdigest()
    summary = {
        "corpusVersion": version,
        "documentCount": len(documents),
        "chunkCount": len(documents),
        "sourceCount": len(sources),
        "sha256": digest,
    }
    if args.summary_json:
        args.summary_json.parent.mkdir(parents=True, exist_ok=True)
        args.summary_json.write_text(json.dumps(summary, sort_keys=True) + "\n", encoding="utf-8")
    print(
        "RAG corpus contract passed: "
        f"corpusVersion={summary['corpusVersion']} "
        f"documents={summary['documentCount']} "
        f"chunks={summary['chunkCount']} "
        f"sources={summary['sourceCount']} "
        f"sha256={summary['sha256']}"
    )


if __name__ == "__main__":
    main()
