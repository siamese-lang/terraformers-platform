#!/usr/bin/env python3
"""One-shot Vertex/OpenSearch ingestion for supported immutable Terraformers corpora."""
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
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from threading import Lock
from typing import Callable

ROOT = Path(__file__).resolve().parents[2]
SUPPORTED_CONTRACTS = {
    "terraformers-reference-v3": {
        "indexName": "terraformers-reference-v3",
        "embeddingModelId": "gemini-embedding-001",
        "vectorDimension": 1024,
        "vectorField": "embedding",
        "contentField": "content",
        "documentCount": 128,
    },
    "terraformers-reference-v4": {
        "indexName": "terraformers-reference-v4",
        "embeddingModelId": "gemini-embedding-2",
        "vectorDimension": 1536,
        "vectorField": "embedding",
        "contentField": "content",
        "documentCount": 5395,
    },
}
CHECKSUM_META_KEY = "terraformers_corpus"
VERTEX_RETRYABLE_STATUS = frozenset({429, 500, 502, 503, 504})
VERTEX_BACKOFF_SECONDS = (1, 2, 4, 8, 16, 32)
VERTEX_PACING_SECONDS = 0.5
INGESTION_WORKERS = 8
EMBEDDING_2_MODEL = "gemini-embedding-2"


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


def validate_supported_manifest(manifest: dict[str, object]) -> dict[str, object]:
    version = manifest.get("corpusVersion")
    if version not in SUPPORTED_CONTRACTS:
        fail(f"unsupported corpusVersion: {version!r}")
    expected = SUPPORTED_CONTRACTS[str(version)]
    for name, value in expected.items():
        if manifest.get(name) != value:
            fail(f"manifest {name} must be {value!r} for {version}")
    if manifest.get("chunkCount") != manifest.get("documentCount"):
        fail("manifest chunkCount must equal documentCount")
    return expected


def load_corpus(corpus: Path) -> tuple[dict[str, object], dict[str, object], list[dict[str, object]], str]:
    contract = validate_contract(corpus)
    manifest = json.loads((corpus / "corpus-manifest.json").read_text(encoding="utf-8"))
    validate_supported_manifest(manifest)
    if contract.get("corpusVersion") != manifest.get("corpusVersion"):
        fail("contract corpusVersion must match manifest")
    if contract.get("documentCount") != manifest.get("documentCount"):
        fail("contract documentCount must match manifest")
    schema = json.loads((corpus / "index-schema.json").read_text(encoding="utf-8"))
    documents = [json.loads(line) for line in
                 (corpus / "documents.jsonl").read_text(encoding="utf-8").splitlines() if line]
    if len(documents) != manifest["documentCount"]:
        fail("documents.jsonl count must equal manifest documentCount")
    return manifest, schema, documents, str(contract["sha256"])


def validate_embedding(response: object, dimension: int = 1024) -> list[float]:
    values: object | None = None
    if isinstance(response, dict):
        predictions = response.get("predictions")
        if isinstance(predictions, list):
            if len(predictions) != 1:
                fail("Vertex document embedding response must contain exactly one embedding")
            candidate = predictions[0]
            if isinstance(candidate, dict):
                embedded = candidate.get("embeddings")
                if isinstance(embedded, dict):
                    values = embedded.get("values")

        embeddings = response.get("embeddings")
        if values is None and isinstance(embeddings, list):
            if len(embeddings) != 1:
                fail("Vertex document embedding response must contain exactly one embedding")
            candidate = embeddings[0]
            if isinstance(candidate, dict):
                values = candidate.get("values")

        embedding = response.get("embedding")
        if values is None and isinstance(embedding, dict):
            values = embedding.get("values")

    if not isinstance(values, list):
        fail("Vertex embedding response has no vector values")
    if len(values) != dimension:
        fail(f"Vertex embedding vector dimension does not match {dimension}")
    if any(isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value)
           for value in values):
        fail("Vertex embedding response contains a non-finite value")
    return [float(value) for value in values]


def prepare_embedding_2_document(text: str, title: str | None) -> str:
    normalized_title = title.strip() if isinstance(title, str) and title.strip() else "none"
    return f"title: {normalized_title} | text: {text.strip()}"


class HttpRequestError(RuntimeError):
    def __init__(self, method: str, path: str, status_code: int, detail: str | None = None):
        self.status_code = status_code
        message = f"HTTP {method} {path} failed with {status_code}"
        if detail:
            message += f": {detail}"
        super().__init__(message)


def sanitized_google_error(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None
    error = payload.get("error")
    if not isinstance(error, dict):
        return None
    parts: list[str] = []
    status = error.get("status")
    message = error.get("message")
    if isinstance(status, str) and status:
        parts.append(status)
    if isinstance(message, str) and message:
        parts.append(message[:500])
    for detail in error.get("details", []):
        if not isinstance(detail, dict):
            continue
        reason = detail.get("reason")
        if isinstance(reason, str) and reason:
            parts.append(f"reason={reason}")
        metadata = detail.get("metadata")
        if isinstance(metadata, dict):
            quota_metric = metadata.get("quota_metric")
            if isinstance(quota_metric, str) and quota_metric:
                parts.append(f"quota_metric={quota_metric}")
    return " | ".join(dict.fromkeys(parts)) or None


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
            payload = exc.read()
            parsed: object = {}
            if payload:
                try:
                    parsed = json.loads(payload)
                except json.JSONDecodeError:
                    parsed = {}
            if exc.code in accepted:
                return exc.code, parsed
            raise HttpRequestError(
                method, path, exc.code, sanitized_google_error(parsed)
            ) from exc


class VertexDocumentEmbedder:
    def __init__(self, project: str, location: str, model: str, token: Callable[[], str],
                 transport: JsonHttpClient | None = None,
                 sleeper: Callable[[float], None] = time.sleep,
                 pacing_seconds: float = VERTEX_PACING_SECONDS,
                 dimension: int = 1024):
        self.model = model
        if model == EMBEDDING_2_MODEL:
            host = f"aiplatform.{location}.rep.googleapis.com"
            method = "embedContent"
        else:
            host = "aiplatform.googleapis.com" if location == "global" else f"{location}-aiplatform.googleapis.com"
            method = "predict"
        self.path = (f"/v1/projects/{urllib.parse.quote(project, safe='')}/locations/{location}"
                     f"/publishers/google/models/{model}:{method}")
        self.transport = transport or JsonHttpClient(f"https://{host}", lambda: {"Authorization": f"Bearer {token()}"})
        self.sleeper = sleeper
        self.pacing_seconds = pacing_seconds
        self.dimension = dimension

    def embed(self, text: str, title: str | None = None) -> list[float]:
        if self.model == EMBEDDING_2_MODEL:
            body = {
                "content": {"parts": [{"text": prepare_embedding_2_document(text, title)}]},
                "embedContentConfig": {"outputDimensionality": self.dimension},
            }
        else:
            body = {
                "instances": [{"content": text, "task_type": "RETRIEVAL_DOCUMENT"}],
                "parameters": {"outputDimensionality": self.dimension},
            }
        for attempt in range(len(VERTEX_BACKOFF_SECONDS) + 1):
            try:
                _, response = self.transport.request("POST", self.path, body)
                vector = validate_embedding(response, self.dimension)
                if self.pacing_seconds > 0:
                    self.sleeper(self.pacing_seconds)
                return vector
            except HttpRequestError as exc:
                if exc.status_code not in VERTEX_RETRYABLE_STATUS or attempt >= len(VERTEX_BACKOFF_SECONDS):
                    raise
                self.sleeper(float(VERTEX_BACKOFF_SECONDS[attempt]))
        raise AssertionError("unreachable")


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
        if not isinstance(wanted, dict) or not isinstance(found, dict):
            fail(f"existing index mapping differs for {name}")
        if found.get("type") != wanted.get("type"):
            fail(f"existing index mapping differs for {name}")
        if wanted.get("type") != "knn_vector":
            continue
        if found.get("dimension") != wanted.get("dimension"):
            fail(f"existing index mapping differs for {name}")

        wanted_method = wanted.get("method")
        found_method = found.get("method")
        if not isinstance(wanted_method, dict) or not isinstance(found_method, dict):
            fail(f"existing index mapping differs for {name}")
        for key in ("name", "engine", "space_type"):
            if found_method.get(key) != wanted_method.get(key):
                fail(f"existing index mapping differs for {name}")

        wanted_parameters = wanted_method.get("parameters", {})
        found_parameters = found_method.get("parameters", {})
        if not isinstance(wanted_parameters, dict) or not isinstance(found_parameters, dict):
            fail(f"existing index mapping differs for {name}")
        for key, value in wanted_parameters.items():
            if found_parameters.get(key) != value:
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
            fail("existing index has no corpus checksum metadata")
        if previous != checksum:
            fail("corpus version checksum changed; bump corpus version")

    metadata = {CHECKSUM_META_KEY: {"corpus_version": manifest["corpusVersion"], "checksum": checksum}}
    client.request("PUT", f"/{index}/_mapping", {"_meta": metadata})
    indexed = 0
    representative: list[float] | None = None

    def ingest_document(document: dict[str, object]) -> list[float] | None:
        document_id = str(document["documentId"])
        encoded_id = urllib.parse.quote(document_id, safe="")
        status, _ = client.request("HEAD", f"/{index}/_doc/{encoded_id}",
                                   accepted=(200, 404))
        if status == 200:
            return None
        vector = embedder.embed(
            str(document[manifest["contentField"]]),
            str(document.get("title") or ""),
        )
        client.request("PUT", f"/{index}/_doc/{encoded_id}",
                       {**document, str(manifest["vectorField"]): vector}, accepted=(200, 201))
        return vector

    with ThreadPoolExecutor(max_workers=INGESTION_WORKERS) as executor:
        futures = [executor.submit(ingest_document, document) for document in documents]
        try:
            for completed, future in enumerate(as_completed(futures), 1):
                vector = future.result()
                if vector is not None:
                    representative = representative or vector
                    indexed += 1
                if completed % 100 == 0 or completed == len(futures):
                    print(json.dumps({
                        "checked": completed,
                        "indexed_this_run": indexed,
                        "total": len(futures),
                    }, sort_keys=True), flush=True)
        except Exception:
            for future in futures:
                future.cancel()
            raise
    client.request("POST", f"/{index}/_refresh", {})
    _, count_response = client.request("POST", f"/{index}/_count", {
        "query": {"term": {"corpusVersion": manifest["corpusVersion"]}}
    })
    count = int(count_response["count"])
    expected_count = int(manifest["documentCount"])
    if count != expected_count:
        fail(f"final corpus-version document count must be exactly {expected_count}")
    if representative is None:
        representative = embedder.embed(
            str(documents[0][manifest["contentField"]]),
            str(documents[0].get("title") or ""),
        )
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
    parser = argparse.ArgumentParser(description="Ingest a supported Terraformers corpus with Vertex document embeddings.")
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
    token_lock = Lock()

    def token() -> str:
        with token_lock:
            if not credentials.valid:
                credentials.refresh(Request())
            return str(credentials.token)

    client = JsonHttpClient(args.opensearch_endpoint)
    embedder = VertexDocumentEmbedder(
        args.project,
        args.location,
        str(manifest["embeddingModelId"]),
        token,
        dimension=int(manifest["vectorDimension"]),
    )
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
