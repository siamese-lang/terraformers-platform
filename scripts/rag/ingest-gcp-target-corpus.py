#!/usr/bin/env python3
"""One-shot Vertex/OpenSearch ingestion for supported immutable Terraformers corpora."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
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
PT8A_CLEAN_MODE = "pt8a-clean-v4"
PT8A_PROJECT_SOURCE = "1ae69d589ac3965733818819792d98c5638e0ae5"
PT8A_PROVIDER_SOURCE = "f7a3b98da589ab1d52756b0dcee0dbf2de83d635"
PT8A_CHECKSUM = "da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a"
PT8A_CANDIDATE = "3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757"
PT8A_PROCEDURE = "docs/evaluation/product-trust-pt-8a-official-acceptance-procedure.md"
PT8A_COVERAGE = {"providerSchemaResourceCount": 1526, "officialDocumentationResourceCount": 1514,
                 "selectedResourceCount": 1514, "selectedOfficialEvidenceResourceCount": 1514,
                 "selectedOfficialEvidenceExtractionGapResourceTypes": [],
                 "providerDocumentChunkCount": 5387, "projectDecisionCount": 8}


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
            host = (
                "aiplatform.googleapis.com"
                if location == "global"
                else f"aiplatform.{location}.rep.googleapis.com"
            )
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


def content_identity(documents: list[dict[str, object]], vector_field: str = "embedding") -> str:
    """Identity of every persisted non-vector field, not merely the declared mapping checksum."""
    sources = [{key: value for key, value in doc.items() if key != vector_field} for doc in documents]
    ids = [doc.get("documentId") for doc in sources]
    if any(not isinstance(value, str) or not value for value in ids) or len(set(ids)) != len(ids):
        fail("documentId must be unique and nonempty")
    canonical = json.dumps(sorted(sources, key=lambda doc: doc["documentId"]),
                           sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False)
    return hashlib.sha256(canonical.encode()).hexdigest()


def index_uuid(client: JsonHttpClient, index: str) -> str | None:
    _, settings = client.request("GET", f"/{index}/_settings")
    return settings.get(index, {}).get("settings", {}).get("index", {}).get("uuid")


def verify_exact_v4(client: JsonHttpClient, manifest: dict[str, object], schema: dict[str, object],
                    documents: list[dict[str, object]], checksum: str,
                    provenance: dict[str, object] | None = None) -> dict[str, object]:
    """Read-only snapshot: full ID universe/source equality plus independently bound model lineage.

    `provenance` must come from the authenticated GitHub artifact binding at the caller. This
    function never instantiates an embedder, writes mapping metadata, or returns document bodies.
    """
    validate_supported_manifest(manifest)
    if manifest["corpusVersion"] != "terraformers-reference-v4" or len(documents) != 5395:
        fail("exact-v4 verification requires the complete rebuilt 5395-document corpus")
    index, vector = str(manifest["indexName"]), str(manifest["vectorField"])
    expected_hash = content_identity(documents, vector)
    summary: dict[str, object] = {"indexName": index, "expectedCount": len(documents),
        "expectedContentIdentity": expected_hash, "expectedChecksum": checksum,
        "snapshotOnly": True, "embeddingRequests": 0}

    def result(classification: str, reason: str, **details: object) -> dict[str, object]:
        return {**summary, **details, "classification": classification, "reason": reason}

    status, _ = client.request("HEAD", f"/{index}", accepted=(200, 404))
    if status == 404:
        return result("MISSING_INDEX", "index_absent")
    _, response = client.request("GET", f"/{index}/_mapping")
    if set(response) != {index}:
        return result("STALE_OR_MIXED_MODEL_SPACE", "mapping_index_identity_mismatch")
    mapping = response[index]
    actual_vector = properties(mapping).get(vector, {})
    if actual_vector.get("type") != "knn_vector" or actual_vector.get("dimension") != 1536:
        return result("WRONG_MODEL_OR_DIMENSION", "vector_contract_mismatch")
    try:
        ensure_mapping(mapping, schema)
    except RuntimeError:
        return result("STALE_OR_MIXED_MODEL_SPACE", "mapping_fields_or_method_mismatch")
    if set(properties(mapping)) != set(properties(schema)):
        return result("STALE_OR_MIXED_MODEL_SPACE", "mapping_field_universe_mismatch")
    # OpenSearch may omit an empty method parameters object; normalize only that equivalent
    # representation. Extra analyzers/subfields/vector parameters must not silently pass.
    def exact_properties(value):
        normalized = json.loads(json.dumps(properties(value)))
        normalized[vector].setdefault("method", {}).setdefault("parameters", {})
        return normalized
    if exact_properties(mapping) != exact_properties(schema):
        return result("STALE_OR_MIXED_MODEL_SPACE", "exact_mapping_properties_mismatch")
    _, settings = client.request("GET", f"/{index}/_settings")
    if settings.get(index, {}).get("settings", {}).get("index", {}).get("knn") not in (True, "true"):
        return result("STALE_OR_MIXED_MODEL_SPACE", "knn_index_setting_missing_or_disabled")
    meta = mapping.get("mappings", {}).get("_meta", {}).get(CHECKSUM_META_KEY, {})
    if meta.get("checksum") != checksum or meta.get("corpus_version") != manifest["corpusVersion"]:
        return result("STALE_OR_MIXED_MODEL_SPACE", "missing_or_wrong_corpus_metadata")
    uuid_before = index_uuid(client, index)
    summary["indexUuid"] = uuid_before
    _, count = client.request("POST", f"/{index}/_count", {"query": {"match_all": {}}})
    summary["totalLiveCount"] = count.get("count")
    if count.get("count") != 5395:
        return result("PARTIAL_INDEX", "total_document_count_mismatch")
    live: dict[str, dict[str, object]] = {}
    scroll_id = None
    try:
        _, page = client.request("POST", f"/{index}/_search?scroll=1m", {
            "size": 500, "sort": ["_doc"], "track_total_hits": True,
            "query": {"match_all": {}}, "_source": {"excludes": [vector]}})
        scroll_id = page.get("_scroll_id")
        if page.get("hits", {}).get("total") != {"value": count.get("count"), "relation": "eq"}:
            return result("PARTIAL_INDEX", "snapshot_total_not_exact")
        while True:
            scroll_id = page.get("_scroll_id", scroll_id)
            hits = page.get("hits", {}).get("hits")
            if page.get("timed_out") or page.get("_shards", {}).get("failed", 0) or not isinstance(hits, list):
                return result("PARTIAL_INDEX", "incomplete_search_snapshot")
            if not hits:
                break
            for hit in hits:
                source = hit.get("_source")
                doc_id = hit.get("_id")
                if (not isinstance(doc_id, str) or not doc_id or not isinstance(source, dict) or source.get("documentId") != doc_id
                        or doc_id in live or vector in source):
                    return result("STALE_OR_MIXED_MODEL_SPACE", "duplicate_id_or_source_identity_mismatch")
                live[doc_id] = source
            # Bound malformed/non-terminating snapshots; no unbounded corpus-body collection.
            if len(live) > max(5395, int(count.get("count", 0))) or not scroll_id:
                return result("PARTIAL_INDEX", "invalid_scroll_snapshot")
            _, page = client.request("POST", "/_search/scroll", {"scroll": "1m", "scroll_id": scroll_id})
    finally:
        if scroll_id:
            client.request("DELETE", "/_search/scroll", {"scroll_id": [scroll_id]}, accepted=(200, 404))
    expected = {str(doc["documentId"]): doc for doc in documents}
    missing, unexpected = sorted(expected.keys() - live.keys()), sorted(live.keys() - expected.keys())
    summary.update(liveContentIdentity=content_identity(list(live.values()), vector),
                   missingCount=len(missing), unexpectedCount=len(unexpected),
                   missingIds=missing[:20], unexpectedIds=unexpected[:20])
    if missing or unexpected or len(live) != 5395 or count.get("count") != 5395:
        return result("PARTIAL_INDEX", "document_universe_mismatch")
    changed = [key for key in sorted(expected) if expected[key] != live[key]]
    if changed or summary["liveContentIdentity"] != expected_hash:
        return result("STALE_OR_MIXED_MODEL_SPACE", "non_vector_source_mismatch",
                      changedCount=len(changed), changedIds=changed[:20])
    _, count_after = client.request("POST", f"/{index}/_count", {"query": {"match_all": {}}})
    if index_uuid(client, index) != uuid_before or count_after.get("count") != 5395:
        return result("STALE_OR_MIXED_MODEL_SPACE", "index_changed_during_snapshot")
    if not provenance or not provenance.get("githubArtifactBindingVerified"):
        return result("MODEL_PROVENANCE_UNPROVEN", "no_authenticated_completed_ingestion_receipt")
    receipt = provenance.get("receipt", {})
    if receipt.get("embedding_model_id") != "gemini-embedding-2" or receipt.get("vector_dimension") != 1536:
        return result("WRONG_MODEL_OR_DIMENSION", "receipt_model_or_dimension_mismatch")
    required = {"corpus_version": manifest["corpusVersion"], "index_name": index,
        "checksum": checksum, "document_count": 5395, "index_uuid": uuid_before,
        "non_vector_content_identity": expected_hash,
        "skipped_existing": 0, "outcome": "ingested"}
    if receipt.get("ingestion_mode") == PT8A_CLEAN_MODE:
        required.update(embedded_this_run=5395, index_uuid_before=uuid_before, index_uuid_after=uuid_before,
                        pre_content_identity=expected_hash, post_content_identity=expected_hash,
                        provider_source=PT8A_PROVIDER_SOURCE, project_decision_source=PT8A_PROJECT_SOURCE,
                        candidate_identity=PT8A_CANDIDATE,
                        procedure_sha256=hashlib.sha256((ROOT / PT8A_PROCEDURE).read_bytes()).hexdigest(),
                        reviewed_source_sha=provenance.get("sourceSha"))
        if (checksum != PT8A_CHECKSUM or not re.fullmatch(r"[0-9a-f]{40}", str(provenance.get("sourceSha", "")))
                or type(receipt.get("live_approval_comment_id")) is not int or receipt["live_approval_comment_id"] <= 0):
            return result("MODEL_PROVENANCE_UNPROVEN", "clean_receipt_authority_or_corpus_binding_mismatch")
    elif receipt.get("ingestion_mode") is None:
        required["indexed_this_run"] = 5395  # Preserve ordinary all-fresh v4 receipt semantics.
    else:
        return result("MODEL_PROVENANCE_UNPROVEN", "unsupported_receipt_mode")
    if not uuid_before or any(receipt.get(key) != value for key, value in required.items()):
        return result("MODEL_PROVENANCE_UNPROVEN", "clean_ingestion_or_retained_uuid_binding_unproven")
    return result("EXACT_REUSABLE_COMPLETED_V4", "exact_snapshot_and_completed_clean_model_lineage",
                  provenanceRunId=provenance.get("runId"), provenanceArtifactId=provenance.get("artifactId"))


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
    v4_uuid_before = index_uuid(client, index) if manifest["corpusVersion"] == "terraformers-reference-v4" else None
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
    receipt = {
        "corpus_version": manifest["corpusVersion"], "checksum": checksum,
        "document_count": count, "index_name": index,
        "embedding_model_id": manifest["embeddingModelId"],
        "vector_dimension": manifest["vectorDimension"], "representative_hit_ids": hit_ids,
        "outcome": "ingested" if indexed else "already-ingested",
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    if manifest["corpusVersion"] == "terraformers-reference-v4":
        # A skipped ID cannot acquire model lineage by relabeling mapping metadata.
        uuid_after = index_uuid(client, index)
        if not v4_uuid_before or uuid_after != v4_uuid_before:
            fail("v4 index UUID changed during ingestion; no completed lineage receipt")
        receipt.update(index_uuid=uuid_after, indexed_this_run=indexed,
                       skipped_existing=len(documents) - indexed,
                       non_vector_content_identity=content_identity(documents, str(manifest["vectorField"])))
    return receipt


def authorize_pt8a_clean_v4(source: str | None, comment_id: int | None,
                           reader: JsonHttpClient | None = None,
                           environment: dict[str, str] | None = None) -> dict[str, object]:
    """Read the repository USER's exact live gate before any embedding; no credentials in evidence.

    Runner uses its existing GitHub token. The isolated pod rechecks the same public GitHub
    authority over verified HTTPS without copying a GitHub token into the pod.
    """
    env = os.environ if environment is None else environment
    if (not re.fullmatch(r"[0-9a-f]{40}", str(source or "")) or type(comment_id) is not int or comment_id <= 0
            or env.get("GITHUB_SHA") != source or env.get("GITHUB_REF") != "refs/heads/main"
            or env.get("GITHUB_REPOSITORY") != "siamese-lang/terraformers-platform"
            or env.get("GITHUB_RUN_ATTEMPT") != "1"):
        fail("PT8A clean mode requires exact main/source/attempt-1 and explicit live approval")
    procedure_sha = hashlib.sha256((ROOT / PT8A_PROCEDURE).read_bytes()).hexdigest()
    reader = reader or JsonHttpClient("https://api.github.com/repos/siamese-lang/terraformers-platform",
        lambda: {"Authorization": "Bearer " + env["GH_TOKEN"]} if env.get("GH_TOKEN") else {})
    _, comment = reader.request("GET", f"/issues/comments/{comment_id}")
    body = comment.get("body", "")
    if comment.get("user", {}).get("login") != "siamese-lang" or not body.startswith("[HUMAN_GATE_APPROVAL:v1]\n"):
        fail("PT8A clean live authority must be the repository USER's structured approval")
    fields = dict(re.findall(r"^([a-z_][a-z0-9_]*):\s*([^\n]+)$", body, re.M))
    expected = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
                "reviewed_source_sha": source, "candidate_identity": PT8A_CANDIDATE,
                "procedure_sha256": procedure_sha, "purpose": "PT8A_CLEAN_V4_REEMBED_ALL_5395",
                "corpus_checksum": PT8A_CHECKSUM, "provider_source": PT8A_PROVIDER_SOURCE,
                "project_decision_source": PT8A_PROJECT_SOURCE,
                "embedding_model": EMBEDDING_2_MODEL, "vector_dimension": "1536"}
    if any(fields.get(key) != value for key, value in expected.items()):
        fail("PT8A clean live approval does not bind exact purpose/source/candidate/procedure/corpus/model")
    _, main = reader.request("GET", "/git/ref/heads/main")
    if main.get("object", {}).get("sha") != source:
        fail("HUMAN_REQUIRED: MAIN_DRIFT before PT8A clean embedding")
    return {"live_approval_comment_id": comment_id, "reviewed_source_sha": source,
            "candidate_identity": PT8A_CANDIDATE, "procedure_sha256": procedure_sha}


def validate_pt8a_clean_corpus(manifest: dict[str, object], documents: list[dict[str, object]],
                              checksum: str, coverage: dict[str, object]) -> None:
    validate_supported_manifest(manifest)
    if manifest["corpusVersion"] != "terraformers-reference-v4" or checksum != PT8A_CHECKSUM or len(documents) != 5395:
        fail("PT8A clean mode requires the exact frozen v4 corpus checksum and 5395 documents; v3 is prohibited")
    if any(coverage.get(key) != value for key, value in PT8A_COVERAGE.items()):
        fail("PT8A clean corpus must preserve 1526/1514/1514/0/5387/8/5395 coverage")
    project = [d for d in documents if d.get("documentType") == "TERRAFORMERS_PATTERN"]
    provider = [d for d in documents if d.get("documentType") in ("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE", "AWS_PROVIDER_SCHEMA")]
    if (len(project) != 8 or len(provider) != 5387
            or any(d.get("sourceCommit") != PT8A_PROJECT_SOURCE for d in project)
            or any(d.get("sourceCommit") != PT8A_PROVIDER_SOURCE for d in provider)):
        fail("PT8A clean corpus historical project/provider source mismatch")


class CleanV4Failure(RuntimeError):
    def __init__(self, receipt: dict[str, object]):
        self.receipt = receipt
        super().__init__("PT8A clean pass failed; preserve partial evidence, no automatic retry or index rebuild")


def clean_reembed_v4(client: JsonHttpClient, embedder: VertexDocumentEmbedder,
                     manifest: dict[str, object], schema: dict[str, object], documents: list[dict[str, object]],
                     checksum: str, coverage: dict[str, object], *, source: str, comment_id: int,
                     authority_reader: JsonHttpClient | None = None,
                     environment: dict[str, str] | None = None) -> dict[str, object]:
    """One explicitly gated full vector overwrite; never create/delete an index or relabel metadata."""
    validate_pt8a_clean_corpus(manifest, documents, checksum, coverage)
    approval = authorize_pt8a_clean_v4(source, comment_id, authority_reader, environment)
    if embedder.model != EMBEDDING_2_MODEL or embedder.dimension != 1536:
        fail("PT8A clean embedder must use gemini-embedding-2 / 1536")
    before = verify_exact_v4(client, manifest, schema, documents, checksum)
    if (before["classification"] != "MODEL_PROVENANCE_UNPROVEN"
            or before["reason"] != "no_authenticated_completed_ingestion_receipt" or not before.get("indexUuid")):
        fail("HUMAN_REQUIRED: DESTRUCTIVE_INDEX_REBUILD_DECISION; non-vector universe/content/mapping is not exact")
    index, vector_field = str(manifest["indexName"]), str(manifest["vectorField"])
    receipt = {**approval, "ingestion_mode": PT8A_CLEAN_MODE, "outcome": "failed",
        "corpus_version": manifest["corpusVersion"], "index_name": index, "checksum": checksum,
        "document_count": 5395, "embedding_model_id": EMBEDDING_2_MODEL, "vector_dimension": 1536,
        "provider_source": PT8A_PROVIDER_SOURCE, "project_decision_source": PT8A_PROJECT_SOURCE,
        "index_uuid": before["indexUuid"], "index_uuid_before": before["indexUuid"],
        "non_vector_content_identity": before["liveContentIdentity"], "pre_content_identity": before["liveContentIdentity"],
        "embedded_this_run": 0, "skipped_existing": 0}
    started, lock, representative = time.monotonic(), Lock(), None

    def overwrite(document: dict[str, object]) -> None:
        nonlocal representative
        vector = embedder.embed(str(document[manifest["contentField"]]), str(document.get("title") or ""))
        vector = validate_embedding({"embedding": {"values": vector}}, 1536)
        # Partial update overwrites only the vector, with no upsert. A disappeared ID is an error,
        # never permission to create a replacement document or rewrite non-vector fields.
        path = f"/{index}/_update/{urllib.parse.quote(str(document['documentId']), safe='')}"
        client.request("POST", path, {"doc": {vector_field: vector}})
        with lock:
            receipt["embedded_this_run"] += 1
            representative = representative if representative is not None else vector

    try:
        receipt["failure_stage"] = "embed_and_overwrite"
        with ThreadPoolExecutor(max_workers=INGESTION_WORKERS) as executor:
            futures = [executor.submit(overwrite, d) for d in documents]
            try:
                for future in as_completed(futures):
                    future.result()
            except Exception:
                for future in futures:
                    future.cancel()
                raise
        client.request("POST", f"/{index}/_refresh", {})
        receipt["failure_stage"] = "post_content_uuid_verification"
        after = verify_exact_v4(client, manifest, schema, documents, checksum)
        receipt.update(index_uuid_after=after.get("indexUuid"), post_content_identity=after.get("liveContentIdentity"))
        if (after["classification"] != "MODEL_PROVENANCE_UNPROVEN"
                or after["reason"] != "no_authenticated_completed_ingestion_receipt"
                or after.get("indexUuid") != before["indexUuid"]
                or after.get("liveContentIdentity") != before["liveContentIdentity"]
                or receipt["embedded_this_run"] != 5395):
            fail("post-overwrite exact content/UUID verification failed")
        receipt["failure_stage"] = "post_live_authority"
        authorize_pt8a_clean_v4(source, comment_id, authority_reader, environment)
        receipt["failure_stage"] = "representative_retrieval"
        _, response = client.request("POST", f"/{index}/_search", {"size": 3, "_source": ["documentId"],
            "query": {"knn": {vector_field: {"vector": representative, "k": 3,
                "filter": {"term": {"corpusVersion": manifest["corpusVersion"]}}}}}})
        hits = [h.get("_source", {}).get("documentId", h.get("_id")) for h in response.get("hits", {}).get("hits", [])]
        if not hits or any(i not in {d["documentId"] for d in documents} for i in hits):
            fail("clean pass representative retrieval is not bound to expected IDs")
        receipt.update(outcome="ingested", representative_hit_ids=hits)
        del receipt["failure_stage"]
        return receipt
    except Exception as exc:
        receipt["failure_class"] = type(exc).__name__
        raise CleanV4Failure(receipt) from exc
    finally:
        receipt["elapsed_seconds"] = round(time.monotonic() - started, 3)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Ingest a supported Terraformers corpus with Vertex document embeddings.")
    parser.add_argument("--corpus-dir", type=Path, default=ROOT / "corpus/terraformers-reference/v3")
    parser.add_argument("--opensearch-endpoint", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--location", default="global")
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--pt8a-clean-v4", action="store_true")
    parser.add_argument("--check-pt8a-clean-authority", action="store_true")
    parser.add_argument("--reviewed-source-sha")
    parser.add_argument("--live-approval-comment-id", type=int)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.check_pt8a_clean_authority and not args.pt8a_clean_v4:
        fail("authority preflight is restricted to explicit PT8A clean mode")
    if args.pt8a_clean_v4:
        approval = authorize_pt8a_clean_v4(args.reviewed_source_sha, args.live_approval_comment_id)
        if args.check_pt8a_clean_authority:
            print(json.dumps(approval, sort_keys=True))
            return
    manifest, schema, documents, checksum = load_corpus(args.corpus_dir)
    coverage = {}
    if args.pt8a_clean_v4:
        coverage = json.loads((args.corpus_dir / "coverage-report.json").read_text())
        validate_pt8a_clean_corpus(manifest, documents, checksum, coverage)
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
    try:
        receipt = (clean_reembed_v4(client, embedder, manifest, schema, documents, checksum, coverage,
                    source=args.reviewed_source_sha, comment_id=args.live_approval_comment_id)
                   if args.pt8a_clean_v4 else ingest(client, embedder, manifest, schema, documents, checksum))
    except CleanV4Failure as exc:
        if args.receipt:
            args.receipt.write_text(json.dumps(exc.receipt, sort_keys=True) + "\n", encoding="utf-8")
        raise
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
