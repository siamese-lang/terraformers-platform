#!/usr/bin/env python3
"""Build Terraformers reference corpus v4 from pinned AWS provider sources without a resource allowlist."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import tempfile
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / "corpus" / "terraformers-reference" / "v4"
DEFAULT_V3 = ROOT / "corpus" / "terraformers-reference" / "v3"
CORPUS_VERSION = "terraformers-reference-v4"
PROVIDER_VERSION = "5.100.0"
PROVIDER_SOURCE_VERSION = f"v{PROVIDER_VERSION}"
PROVIDER_ADDRESS = "registry.terraform.io/hashicorp/aws"
INDEX_NAME = "terraformers-reference-v4"
EMBEDDING_MODEL_ID = "gemini-embedding-001"
VECTOR_DIMENSION = 1024
VECTOR_FIELD = "embedding"
CONTENT_FIELD = "content"

AWS_RESOURCE = re.compile(r"aws_[a-z0-9_]+")
FRONT_MATTER = re.compile(r"\A---\s*\n.*?\n---\s*\n", re.DOTALL)
SUBCATEGORY = re.compile(r'(?m)^subcategory:\s*"([^"]+)"\s*$')
FENCE = re.compile(r"```(?:terraform|hcl)\s*\n.*?```", re.DOTALL | re.IGNORECASE)
MARKDOWN_LINK = re.compile(r"\[([^\]]+)\]\([^)]+\)")
ACCOUNT_ID = re.compile(r"(?<!\d)\d{12}(?!\d)")
ACCESS_KEY = re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")
ARN = re.compile(r"\barn:aws[a-z-]*:[^\s\"'`]+")
AWS_ENDPOINT = re.compile(r"https?://[^\s\"'`]*amazonaws\.com[^\s\"'`]*", re.IGNORECASE)
SECRET_ASSIGNMENT = re.compile(
    r'(?im)^(\s*(?:password|secret|token|access_key|secret_key)\s*=\s*)'
    r'(?:"[^"]*"|\'[^\']*\')'
)
PUBLIC_CIDR = re.compile(r'(?:"0\.0\.0\.0/0"|"::/0")')
PUBLIC_ACCESS = re.compile(
    r"(?im)^\s*(?:publicly_accessible|map_public_ip_on_launch)\s*=\s*true\b"
)
RECOVERY_RISK = re.compile(
    r"(?im)^\s*(?:skip_final_snapshot\s*=\s*true|deletion_protection\s*=\s*false)\b"
)
WILDCARD_IAM = re.compile(
    r'(?is)(?:actions?|resources?)\s*=\s*\[[^\]]*"\*"[^\]]*\]|'
    r'"(?:Action|Resource)"\s*:\s*"\*"'
)
PROVIDER_BLOCK = re.compile(r'(?m)^\s*provider\s+"aws"\s*\{')


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build corpus v4 from pinned terraform-provider-aws docs and schema."
    )
    parser.add_argument("--provider-source-dir", type=Path, required=True)
    parser.add_argument("--provider-schema-json", type=Path, required=True)
    parser.add_argument("--provider-source-commit", required=True)
    parser.add_argument("--project-source-commit", required=True)
    parser.add_argument("--v3-corpus-dir", type=Path, default=DEFAULT_V3)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--resource",
        action="append",
        help=(
            "Build the named AWS resource. Repeat as needed. "
            "The value is validated against the provider schema; there is no source-code allowlist."
        ),
    )
    parser.add_argument(
        "--all-documented-resources",
        action="store_true",
        help="Build every provider-schema resource with a matching official resource document.",
    )
    return parser.parse_args()


def require_commit(value: str, label: str) -> str:
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError(f"{label} must be a full lowercase 40-character commit SHA")
    return value


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize_text(value: str) -> str:
    value = MARKDOWN_LINK.sub(r"\1", value)
    value = re.sub(r"[ \t]+\n", "\n", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def risk_tags(value: str) -> list[str]:
    tags: list[str] = []
    checks = (
        ("plaintext-secret", SECRET_ASSIGNMENT),
        ("public-cidr", PUBLIC_CIDR),
        ("public-access", PUBLIC_ACCESS),
        ("recovery-risk", RECOVERY_RISK),
        ("wildcard-iam", WILDCARD_IAM),
        ("provider-block", PROVIDER_BLOCK),
    )
    for tag, pattern in checks:
        if pattern.search(value):
            tags.append(tag)
    return tags


def sanitize(value: str) -> str:
    value = SECRET_ASSIGNMENT.sub(r'\1"<sensitive-value>"', value)
    value = ACCESS_KEY.sub("<aws-access-key>", value)
    value = ARN.sub("<aws-arn>", value)
    value = AWS_ENDPOINT.sub("<aws-endpoint>", value)
    value = ACCOUNT_ID.sub("<aws-account-id>", value)
    return normalize_text(value)


def slug(value: str) -> str:
    result = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return result[:80] or "unknown"


def section(text: str, heading: str) -> str:
    pattern = re.compile(rf"(?ms)^## {re.escape(heading)}\s*$\n(.*?)(?=^## |\Z)")
    match = pattern.search(text)
    return match.group(1).strip() if match else ""


def resource_overview(text: str) -> str:
    body = FRONT_MATTER.sub("", text)
    match = re.search(r"(?ms)^# Resource:[^\n]*\n(.*?)(?=^## |\Z)", body)
    return normalize_text(match.group(1)) if match else ""


def example_chunks(text: str) -> list[tuple[str, str]]:
    example_section = section(FRONT_MATTER.sub("", text), "Example Usage")
    if not example_section:
        return []
    chunks: list[tuple[str, str]] = []
    cursor = 0
    for number, match in enumerate(FENCE.finditer(example_section), 1):
        prose = example_section[cursor:match.start()].strip()
        prose_lines = [line.strip() for line in prose.splitlines() if line.strip()]
        label = prose_lines[-1].rstrip(":") if prose_lines else f"Example {number}"
        immediate = "\n".join(prose_lines[-3:])
        chunks.append((label, f"{immediate}\n\n{match.group(0)}".strip()))
        cursor = match.end()
    return chunks


def schema_provider(schema_path: Path) -> dict[str, object]:
    payload = json.loads(schema_path.read_text(encoding="utf-8"))
    providers = payload.get("provider_schemas")
    if not isinstance(providers, dict) or PROVIDER_ADDRESS not in providers:
        raise ValueError(f"schema JSON does not contain {PROVIDER_ADDRESS}")
    provider = providers[PROVIDER_ADDRESS]
    if not isinstance(provider, dict):
        raise ValueError("AWS provider schema entry must be an object")
    resources = provider.get("resource_schemas")
    if not isinstance(resources, dict) or not resources:
        raise ValueError("AWS provider schema does not contain resource_schemas")
    return provider


def resource_schemas(provider_schema: dict[str, object]) -> dict[str, object]:
    value = provider_schema.get("resource_schemas")
    if not isinstance(value, dict):
        raise ValueError("AWS provider schema does not contain resource_schemas")
    return value


def provider_doc_path(provider_source_dir: Path, resource_type: str) -> Path:
    if not AWS_RESOURCE.fullmatch(resource_type):
        raise ValueError(f"invalid AWS resource type: {resource_type}")
    return provider_source_dir / "website" / "docs" / "r" / (
        resource_type.removeprefix("aws_") + ".html.markdown"
    )


def documented_resources(
    provider_source_dir: Path,
    provider_schema: dict[str, object],
) -> list[str]:
    schema_resources = set(resource_schemas(provider_schema))
    docs_dir = provider_source_dir / "website" / "docs" / "r"
    if not docs_dir.is_dir():
        raise FileNotFoundError(f"provider resource documentation directory is missing: {docs_dir}")
    documented = {
        "aws_" + path.name.removesuffix(".html.markdown")
        for path in docs_dir.glob("*.html.markdown")
        if path.is_file()
    }
    return sorted(schema_resources & documented)


def service_tags(raw: str | None, resource_type: str) -> list[str]:
    if raw:
        match = SUBCATEGORY.search(raw)
        if match:
            return [slug(match.group(1))]
    fallback = resource_type.removeprefix("aws_").split("_", 1)[0]
    return [fallback]


def format_type(value: object) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def summarize_block(block: dict[str, object], indent: int = 0) -> list[str]:
    prefix = "  " * indent
    lines: list[str] = []
    attributes = block.get("attributes", {})
    if isinstance(attributes, dict):
        for name in sorted(attributes):
            attribute = attributes[name]
            if not isinstance(attribute, dict):
                continue
            flags = [
                key
                for key in ("required", "optional", "computed", "sensitive")
                if attribute.get(key) is True
            ]
            lines.append(
                f"{prefix}- `{name}`: {', '.join(flags) or 'unspecified'}; "
                f"type={format_type(attribute.get('type'))}"
            )
    block_types = block.get("block_types", {})
    if isinstance(block_types, dict):
        for name in sorted(block_types):
            nested = block_types[name]
            if not isinstance(nested, dict):
                continue
            limits = []
            for key in ("min_items", "max_items"):
                if key in nested:
                    limits.append(f"{key}={nested[key]}")
            suffix = f"; {', '.join(limits)}" if limits else ""
            lines.append(
                f"{prefix}- nested block `{name}`: "
                f"nesting_mode={nested.get('nesting_mode', 'unknown')}{suffix}"
            )
            child = nested.get("block")
            if isinstance(child, dict):
                lines.extend(summarize_block(child, indent + 1))
    return lines


def make_provider_document(
    *,
    document_id: str,
    title: str,
    document_type: str,
    authority: str,
    priority: int,
    section_type: str,
    content: str,
    services: list[str],
    resource_type: str,
    source_path: str,
    source_commit: str,
    architecture_pattern: str,
) -> dict[str, object]:
    tags = risk_tags(content)
    safe_content = sanitize(content)
    return {
        "documentId": document_id,
        "title": title,
        "documentType": document_type,
        "authority": authority,
        "priority": priority,
        "sectionType": section_type,
        "riskTags": tags,
        "content": safe_content,
        "services": services,
        "resourceTypes": [resource_type],
        "architecturePattern": architecture_pattern,
        "securityConsiderations": tags,
        "sourceVersion": PROVIDER_SOURCE_VERSION,
        "providerVersion": PROVIDER_VERSION,
        "sourcePath": source_path,
        "sourceCommit": source_commit,
        "corpusVersion": CORPUS_VERSION,
    }


def provider_documents(
    provider_source_dir: Path,
    provider_schema: dict[str, object],
    provider_commit: str,
    selected_resources: Iterable[str],
) -> tuple[list[dict[str, object]], list[dict[str, object]], list[str]]:
    schemas = resource_schemas(provider_schema)
    documents: list[dict[str, object]] = []
    sources: list[dict[str, object]] = []
    missing_docs: list[str] = []

    for resource_type in sorted(set(selected_resources)):
        if not AWS_RESOURCE.fullmatch(resource_type):
            raise ValueError(f"invalid AWS resource type: {resource_type}")
        resource_schema = schemas.get(resource_type)
        if not isinstance(resource_schema, dict) or not isinstance(resource_schema.get("block"), dict):
            raise ValueError(
                f"provider schema does not contain managed resource {resource_type}"
            )

        doc_path = provider_doc_path(provider_source_dir, resource_type)
        raw: str | None = None
        services = service_tags(None, resource_type)
        source_path = str(doc_path.relative_to(provider_source_dir)).replace("\\", "/")
        if doc_path.is_file():
            raw = doc_path.read_text(encoding="utf-8")
            services = service_tags(raw, resource_type)
            overview = resource_overview(raw)
            if overview:
                documents.append(
                    make_provider_document(
                        document_id=f"tfaws-{PROVIDER_VERSION}-{resource_type}-overview",
                        title=f"{resource_type} - overview",
                        document_type="AWS_PROVIDER_DOC",
                        authority="PROVIDER_DOCUMENTATION",
                        priority=60,
                        section_type="overview",
                        content=overview,
                        services=services,
                        resource_type=resource_type,
                        source_path=source_path,
                        source_commit=provider_commit,
                        architecture_pattern="provider-resource-overview",
                    )
                )
                sources.append(
                    {
                        "sourceType": "AWS_PROVIDER_DOC",
                        "sourceVersion": PROVIDER_SOURCE_VERSION,
                        "sourcePath": source_path,
                        "sourceCommit": provider_commit,
                    }
                )

            examples = example_chunks(raw)
            if examples:
                sources.append(
                    {
                        "sourceType": "AWS_PROVIDER_EXAMPLE",
                        "sourceVersion": PROVIDER_SOURCE_VERSION,
                        "sourcePath": source_path,
                        "sourceCommit": provider_commit,
                    }
                )
            for number, (label, example) in enumerate(examples, 1):
                documents.append(
                    make_provider_document(
                        document_id=(
                            f"tfaws-{PROVIDER_VERSION}-{resource_type}-"
                            f"example-{number}-{slug(label)}"
                        ),
                        title=f"{resource_type} - {label}",
                        document_type="AWS_PROVIDER_EXAMPLE",
                        authority="PROVIDER_DOCUMENTATION",
                        priority=60,
                        section_type="example",
                        content=example,
                        services=services,
                        resource_type=resource_type,
                        source_path=source_path,
                        source_commit=provider_commit,
                        architecture_pattern="provider-resource-example",
                    )
                )
        else:
            missing_docs.append(resource_type)

        schema_path = f"terraform providers schema -json#resource_schemas.{resource_type}"
        schema_lines = summarize_block(resource_schema["block"])
        if not schema_lines:
            schema_lines = ["- no configurable arguments"]
        documents.append(
            make_provider_document(
                document_id=f"tfaws-{PROVIDER_VERSION}-{resource_type}-schema",
                title=f"{resource_type} - provider schema",
                document_type="AWS_PROVIDER_SCHEMA",
                authority="PROVIDER_SCHEMA",
                priority=70,
                section_type="schema",
                content=(
                    f"Provider {PROVIDER_VERSION} schema for `{resource_type}`.\n\n"
                    + "\n".join(schema_lines)
                ),
                services=services,
                resource_type=resource_type,
                source_path=schema_path,
                source_commit=provider_commit,
                architecture_pattern="provider-schema-contract",
            )
        )
        sources.append(
            {
                "sourceType": "AWS_PROVIDER_SCHEMA",
                "sourceVersion": PROVIDER_SOURCE_VERSION,
                "sourcePath": schema_path,
                "sourceCommit": provider_commit,
            }
        )
    return documents, sources, sorted(missing_docs)


def project_pattern_documents(
    v3_corpus_dir: Path,
    project_commit: str,
) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    documents: list[dict[str, object]] = []
    sources: list[dict[str, object]] = []
    for line in (v3_corpus_dir / "documents.jsonl").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        original = json.loads(line)
        if original.get("documentType") != "TERRAFORMERS_PATTERN":
            continue
        document = {
            **original,
            "sourceVersion": CORPUS_VERSION,
            "sourceCommit": project_commit,
            "corpusVersion": CORPUS_VERSION,
        }
        documents.append(document)
        sources.append(
            {
                "sourceType": "TERRAFORMERS_PATTERN",
                "sourceVersion": CORPUS_VERSION,
                "sourcePath": document["sourcePath"],
                "sourceCommit": project_commit,
            }
        )
    if not documents:
        raise ValueError("v3 corpus contains no Terraformers project decisions")
    return documents, sources


def v3_provider_resources(v3_corpus_dir: Path) -> set[str]:
    result: set[str] = set()
    for line in (v3_corpus_dir / "documents.jsonl").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        document = json.loads(line)
        if document.get("documentType") not in {
            "AWS_PROVIDER_DOC",
            "AWS_PROVIDER_EXAMPLE",
            "AWS_PROVIDER_SCHEMA",
        }:
            continue
        result.update(str(value) for value in document.get("resourceTypes", []))
    return result


def dedupe_sources(sources: Iterable[dict[str, object]]) -> list[dict[str, object]]:
    unique = {
        (
            str(source["sourceType"]),
            str(source["sourceVersion"]),
            str(source["sourcePath"]),
            str(source["sourceCommit"]),
        ): source
        for source in sources
    }
    return [unique[key] for key in sorted(unique)]


def index_schema() -> dict[str, object]:
    keyword_fields = [
        "documentId",
        "documentType",
        "authority",
        "sectionType",
        "riskTags",
        "sourceVersion",
        "sourcePath",
        "sourceCommit",
        "corpusVersion",
        "services",
        "resourceTypes",
        "architecturePattern",
        "securityConsiderations",
        "providerVersion",
    ]
    properties: dict[str, object] = {
        VECTOR_FIELD: {
            "type": "knn_vector",
            "dimension": VECTOR_DIMENSION,
            "method": {
                "name": "hnsw",
                "engine": "faiss",
                "space_type": "cosinesimil",
                "parameters": {},
            },
        },
        "title": {"type": "text"},
        CONTENT_FIELD: {"type": "text"},
        "priority": {"type": "integer"},
    }
    properties.update({field: {"type": "keyword"} for field in keyword_fields})
    return {
        "settings": {"index": {"knn": True}},
        "mappings": {"properties": properties},
    }


def build_coverage_report(
    *,
    provider_source_dir: Path,
    provider_schema: dict[str, object],
    provider_schema_json: Path,
    provider_commit: str,
    v3_corpus_dir: Path,
    selected_resources: list[str],
    missing_docs: list[str],
    provider_document_count: int,
    project_decision_count: int,
) -> dict[str, object]:
    schemas = set(resource_schemas(provider_schema))
    documented = set(documented_resources(provider_source_dir, provider_schema))
    historical = v3_provider_resources(v3_corpus_dir)
    selected = set(selected_resources)
    return {
        "corpusVersion": CORPUS_VERSION,
        "providerVersion": PROVIDER_VERSION,
        "providerSourceCommit": provider_commit,
        "providerSchemaSha256": sha256_file(provider_schema_json),
        "providerSchemaResourceCount": len(schemas),
        "officialDocumentationResourceCount": len(documented),
        "schemaResourcesWithoutOfficialDocumentationCount": len(schemas - documented),
        "v3ProviderResourceCount": len(historical),
        "selectedResourceCount": len(selected),
        "selectedOfficialDocumentationResourceCount": len(selected - set(missing_docs)),
        "selectedMissingOfficialDocumentationResourceTypes": sorted(missing_docs),
        "newResourceTypesComparedWithV3": sorted(selected - historical),
        "providerDocumentChunkCount": provider_document_count,
        "projectDecisionCount": project_decision_count,
    }


def write_corpus(
    output_dir: Path,
    documents: list[dict[str, object]],
    sources: list[dict[str, object]],
    coverage_report: dict[str, object],
) -> None:
    documents = sorted(documents, key=lambda item: str(item["documentId"]))
    ids = [str(document["documentId"]) for document in documents]
    if len(ids) != len(set(ids)):
        raise ValueError("generated corpus contains duplicate document IDs")

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix="terraformers-reference-v4-", dir=output_dir.parent))
    try:
        manifest = {
            "corpusVersion": CORPUS_VERSION,
            "awsProviderVersion": PROVIDER_VERSION,
            "embeddingModelId": EMBEDDING_MODEL_ID,
            "vectorDimension": VECTOR_DIMENSION,
            "indexName": INDEX_NAME,
            "vectorField": VECTOR_FIELD,
            "contentField": CONTENT_FIELD,
            "documentCount": len(documents),
            "chunkCount": len(documents),
            "checksumAlgorithm": "SHA-256",
            "coverageReportFile": "coverage-report.json",
        }
        (staging / "corpus-manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (staging / "source-manifest.json").write_text(
            json.dumps({"sources": dedupe_sources(sources)}, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (staging / "index-schema.json").write_text(
            json.dumps(index_schema(), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (staging / "coverage-report.json").write_text(
            json.dumps(coverage_report, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (staging / "documents.jsonl").write_text(
            "".join(
                json.dumps(document, sort_keys=True, ensure_ascii=False) + "\n"
                for document in documents
            ),
            encoding="utf-8",
        )
        if output_dir.exists():
            shutil.rmtree(output_dir)
        staging.replace(output_dir)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise


def select_resources(args: argparse.Namespace, provider_schema: dict[str, object]) -> list[str]:
    if args.resource and args.all_documented_resources:
        raise ValueError("--resource and --all-documented-resources are mutually exclusive")
    if args.all_documented_resources:
        return documented_resources(args.provider_source_dir.resolve(), provider_schema)
    if not args.resource:
        raise ValueError("provide at least one --resource or use --all-documented-resources")
    schemas = resource_schemas(provider_schema)
    selected = sorted(set(args.resource))
    for resource_type in selected:
        if not AWS_RESOURCE.fullmatch(resource_type):
            raise ValueError(f"invalid AWS resource type: {resource_type}")
        if resource_type not in schemas:
            raise ValueError(
                f"resource is absent from AWS {PROVIDER_VERSION} provider schema: {resource_type}"
            )
    return selected


def build(args: argparse.Namespace) -> dict[str, object]:
    provider_commit = require_commit(args.provider_source_commit, "provider-source-commit")
    project_commit = require_commit(args.project_source_commit, "project-source-commit")
    provider_source_dir = args.provider_source_dir.resolve()
    provider_schema_json = args.provider_schema_json.resolve()
    v3_corpus_dir = args.v3_corpus_dir.resolve()
    provider_schema = schema_provider(provider_schema_json)
    selected = select_resources(args, provider_schema)

    provider_docs, provider_sources, missing_docs = provider_documents(
        provider_source_dir,
        provider_schema,
        provider_commit,
        selected,
    )
    project_docs, project_sources = project_pattern_documents(v3_corpus_dir, project_commit)
    documents = provider_docs + project_docs
    sources = provider_sources + project_sources
    coverage = build_coverage_report(
        provider_source_dir=provider_source_dir,
        provider_schema=provider_schema,
        provider_schema_json=provider_schema_json,
        provider_commit=provider_commit,
        v3_corpus_dir=v3_corpus_dir,
        selected_resources=selected,
        missing_docs=missing_docs,
        provider_document_count=len(provider_docs),
        project_decision_count=len(project_docs),
    )
    write_corpus(args.output_dir.resolve(), documents, sources, coverage)
    return {
        "corpusVersion": CORPUS_VERSION,
        "selectedResourceCount": len(selected),
        "documentCount": len(documents),
        "missingOfficialDocumentationCount": len(missing_docs),
        "newResourceCountComparedWithV3": len(coverage["newResourceTypesComparedWithV3"]),
        "output": str(args.output_dir.resolve()),
    }


def main() -> None:
    summary = build(parse_args())
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
