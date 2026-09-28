#!/usr/bin/env python3
"""Deterministically derive the immutable v3 identity from curated corpus v2."""
from __future__ import annotations

import argparse
import json
import shutil
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INPUT = ROOT / "corpus" / "terraformers-reference" / "v2"
DEFAULT_OUTPUT = ROOT / "corpus" / "terraformers-reference" / "v3"
SOURCE_VERSION = "terraformers-reference-v2"
TARGET_VERSION = "terraformers-reference-v3"
TARGET_INDEX = TARGET_VERSION
TARGET_EMBEDDING_MODEL = "gemini-embedding-001"
PROVIDER_TYPES = {"AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE", "AWS_PROVIDER_SCHEMA"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Derive terraformers-reference-v3 from curated v2.")
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def read_json(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path.name} must contain a JSON object")
    return value


def derive(input_dir: Path, output_dir: Path) -> dict[str, object]:
    input_dir = input_dir.resolve()
    output_dir = output_dir.resolve()
    if input_dir == output_dir:
        raise ValueError("input and output directories must differ; v2 is immutable")

    manifest = read_json(input_dir / "corpus-manifest.json")
    if manifest.get("corpusVersion") != SOURCE_VERSION:
        raise ValueError(f"input corpusVersion must be {SOURCE_VERSION}")
    documents = [
        json.loads(line)
        for line in (input_dir / "documents.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    source_manifest = read_json(input_dir / "source-manifest.json")
    schema = read_json(input_dir / "index-schema.json")

    target_manifest = {
        **manifest,
        "corpusVersion": TARGET_VERSION,
        "indexName": TARGET_INDEX,
        "embeddingModelId": TARGET_EMBEDDING_MODEL,
    }
    target_documents = []
    for document in documents:
        derived = {**document, "corpusVersion": TARGET_VERSION}
        if derived.get("documentType") not in PROVIDER_TYPES:
            derived["sourceVersion"] = TARGET_VERSION
        target_documents.append(derived)
    target_sources = []
    for source in source_manifest.get("sources", []):
        derived = dict(source)
        if derived.get("sourceType") not in PROVIDER_TYPES:
            derived["sourceVersion"] = TARGET_VERSION
        target_sources.append(derived)

    target_documents.sort(key=lambda item: str(item["documentId"]))
    target_sources.sort(
        key=lambda item: (
            str(item["sourceType"]),
            str(item["sourceVersion"]),
            str(item["sourcePath"]),
            str(item.get("sourceCommit", "")),
        )
    )
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix="terraformers-reference-v3-", dir=output_dir.parent))
    try:
        (staging / "corpus-manifest.json").write_text(
            json.dumps(target_manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        (staging / "documents.jsonl").write_text(
            "".join(json.dumps(item, sort_keys=True, ensure_ascii=False) + "\n" for item in target_documents),
            encoding="utf-8",
        )
        (staging / "index-schema.json").write_text(
            json.dumps(schema, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        (staging / "source-manifest.json").write_text(
            json.dumps({"sources": target_sources}, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        if output_dir.exists():
            shutil.rmtree(output_dir)
        staging.replace(output_dir)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return {"corpusVersion": TARGET_VERSION, "documentCount": len(target_documents)}


def main() -> None:
    args = parse_args()
    print(json.dumps(derive(args.input_dir, args.output_dir), sort_keys=True))


if __name__ == "__main__":
    main()
