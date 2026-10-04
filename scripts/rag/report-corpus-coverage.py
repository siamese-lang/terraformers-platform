#!/usr/bin/env python3
"""Report provider-schema, official-documentation, and committed-corpus resource coverage."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

PROVIDER_ADDRESS = "registry.terraform.io/hashicorp/aws"
PROVIDER_VERSION = "5.100.0"
PROVIDER_TYPES = {"AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE", "AWS_PROVIDER_SCHEMA"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Measure Terraformers RAG knowledge coverage.")
    parser.add_argument("--corpus-dir", type=Path, required=True)
    parser.add_argument("--provider-schema-json", type=Path, required=True)
    parser.add_argument("--provider-source-dir", type=Path)
    parser.add_argument("--provider-source-commit")
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def schema_resources(path: Path) -> set[str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    resources = (
        payload.get("provider_schemas", {})
        .get(PROVIDER_ADDRESS, {})
        .get("resource_schemas")
    )
    if not isinstance(resources, dict) or not resources:
        raise ValueError(f"schema JSON does not contain {PROVIDER_ADDRESS} resource_schemas")
    return set(resources)


def corpus_resources(corpus_dir: Path) -> tuple[set[str], set[str], int]:
    provider_resources: set[str] = set()
    schema_document_resources: set[str] = set()
    project_decisions = 0
    for line in (corpus_dir / "documents.jsonl").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        document = json.loads(line)
        document_type = document.get("documentType")
        if document_type == "TERRAFORMERS_PATTERN":
            project_decisions += 1
            continue
        if document_type not in PROVIDER_TYPES:
            continue
        values = {str(value) for value in document.get("resourceTypes", [])}
        provider_resources.update(values)
        if document_type == "AWS_PROVIDER_SCHEMA":
            schema_document_resources.update(values)
    return provider_resources, schema_document_resources, project_decisions


def official_document_resources(provider_source_dir: Path) -> set[str]:
    docs_dir = provider_source_dir / "website" / "docs" / "r"
    if not docs_dir.is_dir():
        raise FileNotFoundError(f"provider resource documentation directory is missing: {docs_dir}")
    return {
        "aws_" + path.name.removesuffix(".html.markdown")
        for path in docs_dir.glob("*.html.markdown")
        if path.is_file()
    }


def report(args: argparse.Namespace) -> dict[str, object]:
    corpus_dir = args.corpus_dir.resolve()
    schema_path = args.provider_schema_json.resolve()
    schema = schema_resources(schema_path)
    corpus, corpus_schema_docs, project_decisions = corpus_resources(corpus_dir)
    result: dict[str, object] = {
        "corpusVersion": json.loads(
            (corpus_dir / "corpus-manifest.json").read_text(encoding="utf-8")
        )["corpusVersion"],
        "providerVersion": PROVIDER_VERSION,
        "providerSchemaSha256": sha256_file(schema_path),
        "providerSchemaResourceCount": len(schema),
        "corpusProviderResourceCount": len(corpus),
        "corpusProviderSchemaDocumentResourceCount": len(corpus_schema_docs),
        "corpusCoverageOfProviderSchema": round(len(corpus & schema) / len(schema), 6),
        "corpusResourcesAbsentFromProviderSchema": sorted(corpus - schema),
        "providerSchemaResourcesAbsentFromCorpusCount": len(schema - corpus),
        "projectDecisionCount": project_decisions,
    }
    if args.provider_source_dir:
        official = official_document_resources(args.provider_source_dir.resolve())
        documented_schema = official & schema
        result.update(
            {
                "providerSourceCommit": args.provider_source_commit,
                "officialResourceDocumentCount": len(official),
                "officialDocumentedProviderSchemaResourceCount": len(documented_schema),
                "providerSchemaResourcesWithoutOfficialDocumentCount": len(schema - official),
                "corpusCoverageOfOfficialDocumentedResources": round(
                    len(corpus & documented_schema) / len(documented_schema), 6
                )
                if documented_schema
                else 0.0,
            }
        )
    return result


def main() -> None:
    args = parse_args()
    result = report(args)
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
