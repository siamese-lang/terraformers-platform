#!/usr/bin/env python3
"""One-shot Vertex/OpenSearch ingestion for the immutable v3 target corpus."""
from __future__ import annotations

import argparse
import json
import math
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Callable

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_CONTRACT = {
    "corpusVersion": "terraformers-reference-v3",
    "indexName": "terraformers-reference-v3",
    "embeddingModelId": "gemini-embedding-001",
    "vectorDimension": 1024,
    "vectorField": "embedding",
    "contentField": "content",
    "documentCount": 128,
}
CHECKSUM_META_KEY = "terraformers_corpus"


def fail(message: str) -> None:
    raise RuntimeError(message)


def validate_contract(corpus: Path) -> dict[str, object]:
    with tempfile.NamedTemporaryFile(suffix=".json") as summary:
        subprocess.run(
            [sys.executable, str(ROOT / "scripts/checks/rag-corpus-contract-verification.py"),
             "--corpus-dir", str(corpus), "--summary-json", summary.name],
            check=True,
        )
        summary.seek(0)
        return json.load(summary)


def load_corpus(corpus: Path) -> tuple[dict[str, object], dict[str, object], list[dict[str, object]], str]:
    contract = validate_contract(corpus)
    manifest = json.loads((corpus / "corpus-manifest.json").read_text(encoding="utf-8"))
    for name, expected in EXPECTED_CONTRACT.items():
        if manifest.get(name) != expected:
            fail(f"manifest {name} must be {expected!r}")
    schema = json.loads((corpus / "index-schema.json").read_text(encoding="utf-8"))
    documents = [json.loads(line) for line in
                 (corpus / "documents.jsonl").read_text(encoding="utf-8").splitlines() if line]
    return manifest, schema, documents, str(contract["sha256"])


def validate_embedding(response: object, dimension: int = 1024) -> list[float]:
    if not isinstance(response, dict) or not isinstance(response.get("predictions"), list):
        fail("Vertex embedding response has no predictions")
    predictions = response["predictions"]
    if len(predictions) != 1:
        fail("Vertex document embedding response must contain exactly one embedding")
    try:
        values = predictions[0]["embeddings"]["values"]
    except (KeyError, TypeError):
        fail("Vertex embedding response has no vector values")
    if not isinstance(values, list) or len(values) != dimension:
        fail("Vertex embedding vector dimension does not match 1024")
    if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value)
           for value in values):
        fail("Vertex embedding response contains a non-finite value")
    return [float(value) for value in values]


class JsonHttpClient:
    def __init__(self, endpoint: str, headers: Callable[[], dict[str, str]] = lambda: {}):
        self.endpoint = endpoint.rstrip("/")
        self.headers = headers

    def request(self, method: str, path: str, body: object | None = None,
                accepted: tuple[int, ...] = (200,)) -> tuple[int, object]:
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json", **self.headers()}
        request = urllib.request.Request(self.endpoint + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = response.read()
                return response.status, json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exc:
            if exc.code in accepted:
                payload = exc.read()
                return exc.code, json.loads(payload) if payload else {}
            raise RuntimeError(f"HTTP {method} {path} failed with {exc.code}") from exc


class VertexDocumentEmbedder:
    def __init__(self, project: str, location: str, model: str, token: Callable[[], str],
                 transport: JsonHttpClient | None = None):
        host = "aiplatform.googleapis.com" if location == "global" else f"{location}-aiplatform.googleapis.com"
        self.path = (f"/v1/projects/{urllib.parse.quote(project, safe='')}/locations/{location}"
                     f"/publishers/google/models/{model}:predict")
        self.transport = transport or JsonHttpClient(f"https://{host}", lambda: {"Authorization": f"Bearer {token()}"})

    def embed(self, text: str) -> list[float]:
        body = {
            "instances": [{"content": text, "task_type": "RETRIEVAL_DOCUMENT"}],
            "parameters": {"outputDimensionality": 1024},
        }
        _, response = self.transport.request("POST", self.path, body)
        return validate_embedding(response)


def properties(schema: dict[str, object]) -> dict[str, object]:
    result = schema.get("mappings", {}).get("properties", {})
    if not isinstance(result, dict):
        fail("committed index schema properties are missing")
    return result


def ensure_mapping(actual: dict[str, object], schema: dict[str, object]) -> None:
    expected = properties(schema)
    current = actual.get("mappings", {}).get("properties", {})
    for name, wanted in expected.items():
        found = current.get(name)
        if not isinstance(found, dict) or found.get("type") != wanted.get("type"):
            fail(f"existing index mapping differs for {name}")
        if wanted.get("type") == "knn_vector" and found.get("dimension") != wanted.get("dimension"):
            fail(f"existing index mapping differs for {name}")


def stored_checksum(mapping: dict[str, object]) -> str | None:
    metadata = mapping.get("mappings", {}).get("_meta", {}).get(CHECKSUM_META_KEY, {})
    return metadata.get("checksum") if isinstance(metadata, dict) else None


def ingest(client: JsonHttpClient, embedder: VertexDocumentEmbedder, manifest: dict[str, object],
           schema: dict[str, object], documents: list[dict[str, object]], checksum: str) -> dict[str, object]:
    started = time.monotonic()
    index = str(manifest["indexName"])
    status, _ = client.request("HEAD", f"/{index}", accepted=(200, 404))
    if status == 404:
        client.request("PUT", f"/{index}", schema)
        mapping: dict[str, object] = {"mappings": schema["mappings"]}
    else:
        _, response = client.request("GET", f"/{index}/_mapping")
        mapping = response[index]
        ensure_mapping(mapping, schema)
        previous = stored_checksum(mapping)
        if previous is None:
            fail("existing v3 index has no corpus checksum metadata")
        if previous != checksum:
            fail("corpus version checksum changed; bump corpus version")

    metadata = {CHECKSUM_META_KEY: {"corpus_version": manifest["corpusVersion"], "checksum": checksum}}
    client.request("PUT", f"/{index}/_mapping", {"_meta": metadata})
    indexed = 0
    representative: list[float] | None = None
    for document in documents:
        document_id = str(document["documentId"])
        status, _ = client.request("HEAD", f"/{index}/_doc/{urllib.parse.quote(document_id, safe='')}",
                                   accepted=(200, 404))
        if status == 404:
            vector = embedder.embed(str(document[manifest["contentField"]]))
            representative = representative or vector
            client.request("PUT", f"/{index}/_doc/{urllib.parse.quote(document_id, safe='')}",
                           {**document, str(manifest["vectorField"]): vector}, accepted=(200, 201))
            indexed += 1
    client.request("POST", f"/{index}/_refresh", {})
    _, count_response = client.request("POST", f"/{index}/_count", {
        "query": {"term": {"corpusVersion": manifest["corpusVersion"]}}
    })
    count = int(count_response["count"])
    if count != EXPECTED_CONTRACT["documentCount"]:
        fail("final corpus-version document count must be exactly 128")
    if representative is None:
        representative = embedder.embed(str(documents[0][manifest["contentField"]]))
    _, search_response = client.request("POST", f"/{index}/_search", {
        "size": 3,
        "query": {"knn": {manifest["vectorField"]: {"vector": representative, "k": 3,
            "filter": {"term": {"corpusVersion": manifest["corpusVersion"]}}}}},
    })
    hits = search_response.get("hits", {}).get("hits", [])
    if not hits:
        fail("representative 1024-dimensional k-NN query returned no hits")
    hit_ids = [str(hit.get("_source", {}).get("documentId", hit.get("_id", ""))) for hit in hits]
    return {
        "corpus_version": manifest["corpusVersion"], "checksum": checksum,
        "document_count": count, "index_name": index,
        "embedding_model_id": manifest["embeddingModelId"],
        "vector_dimension": manifest["vectorDimension"], "representative_hit_ids": hit_ids,
        "outcome": "ingested" if indexed else "already-ingested",
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Ingest the v3 corpus with Vertex document embeddings.")
    parser.add_argument("--corpus-dir", type=Path, default=ROOT / "corpus/terraformers-reference/v3")
    parser.add_argument("--opensearch-endpoint", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--location", default="global")
    parser.add_argument("--receipt", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    manifest, schema, documents, checksum = load_corpus(args.corpus_dir)
    import google.auth
    from google.auth.transport.requests import Request
    credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])

    def token() -> str:
        if not credentials.valid:
            credentials.refresh(Request())
        return str(credentials.token)

    client = JsonHttpClient(args.opensearch_endpoint)
    embedder = VertexDocumentEmbedder(args.project, args.location, str(manifest["embeddingModelId"]), token)
    receipt = ingest(client, embedder, manifest, schema, documents, checksum)
    rendered = json.dumps(receipt, sort_keys=True)
    if args.receipt:
        args.receipt.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(json.dumps({"outcome": "failed", "error_class": exc.__class__.__name__}, sort_keys=True))
        print(str(exc), file=sys.stderr)
        sys.exit(1)
