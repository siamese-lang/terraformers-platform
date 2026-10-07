import importlib.util
import json
import math
import copy
import hashlib
import unittest
from pathlib import Path
from unittest.mock import patch
import urllib.parse

ROOT = Path(__file__).parents[2]
SPEC = importlib.util.spec_from_file_location(
    "gcp_ingest", ROOT / "scripts/rag/ingest-gcp-target-corpus.py"
)
gcp_ingest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gcp_ingest)


class RecordingTransport:
    def __init__(self, response=None):
        self.response = response or {
            "predictions": [{"embeddings": {"values": [0.25] * 1024}}]
        }
        self.calls = []

    def request(self, method, path, body=None, accepted=(200,)):
        self.calls.append((method, path, body, accepted))
        return 200, self.response


class FakeEmbedder:
    def __init__(self, dimension=1024):
        self.texts = []
        self.titles = []
        self.dimension = dimension
        self.model = "gemini-embedding-2" if dimension == 1536 else "gemini-embedding-001"

    def embed(self, text, title=None):
        self.texts.append(text)
        self.titles.append(title)
        return [0.25] * self.dimension


class FakeOpenSearch:
    def __init__(
        self,
        schema,
        checksum=None,
        existing_documents=None,
        count=128,
        hits=None,
        corpus_version="terraformers-reference-v3",
    ):
        self.schema = schema
        self.checksum = checksum
        self.exists = checksum is not None
        self.documents = dict(existing_documents or {})
        self.count = count
        self.hits = hits if hits is not None else [{"_id": "doc-1", "_source": {"documentId": "doc-1"}}]
        self.corpus_version = corpus_version
        self.calls = []

    def request(self, method, path, body=None, accepted=(200,)):
        self.calls.append((method, path, body))
        if method == "HEAD" and path.count("/") == 1:
            return (200 if self.exists else 404), {}
        if method == "PUT" and path.count("/") == 1:
            self.exists = True
            return 200, {}
        if method == "GET" and path.endswith("/_mapping"):
            index = path.split("/")[1]
            mapping = json.loads(json.dumps(self.schema["mappings"]))
            mapping["_meta"] = {gcp_ingest.CHECKSUM_META_KEY: {
                "corpus_version": self.corpus_version, "checksum": self.checksum}}
            return 200, {index: {"mappings": mapping}}
        if method == "GET" and path.endswith("/_settings"):
            return 200, {path.split("/")[1]: {"settings": {"index": {"uuid": "test-index-uuid", "knn": "true"}}}}
        if method == "HEAD" and "/_doc/" in path:
            return (200 if path.rsplit("/", 1)[1] in self.documents else 404), {}
        if method == "PUT" and "/_doc/" in path:
            self.documents[path.rsplit("/", 1)[1]] = body
            return 201, {}
        if method == "POST" and "/_update/" in path:
            doc_id = urllib.parse.unquote(path.rsplit("/", 1)[1])
            if doc_id not in self.documents:
                raise RuntimeError("missing existing ID; no upsert")
            self.documents[doc_id].update(body["doc"])
            return 200, {"result": "updated"}
        if path.endswith("/_count"):
            return 200, {"count": self.count}
        if path.endswith("/_search"):
            return 200, {"hits": {"hits": self.hits}}
        return 200, {}


class GcpTargetCorpusIngestionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest, cls.schema, cls.documents, cls.checksum = gcp_ingest.load_corpus(
            ROOT / "corpus/terraformers-reference/v3"
        )

    def test_v3_contract_is_loaded_from_manifest(self):
        expected = gcp_ingest.SUPPORTED_CONTRACTS["terraformers-reference-v3"]
        self.assertEqual(expected, {key: self.manifest[key] for key in expected})
        self.assertEqual(128, len(self.documents))
        self.assertEqual(64, len(self.checksum))

    def test_v4_manifest_contract_is_supported_with_exact_broad_count(self):
        manifest = dict(self.manifest)
        manifest.update({
            "corpusVersion": "terraformers-reference-v4",
            "indexName": "terraformers-reference-v4",
            "embeddingModelId": "gemini-embedding-2",
            "vectorDimension": 1536,
            "documentCount": 5395,
            "chunkCount": 5395,
        })

        expected = gcp_ingest.validate_supported_manifest(manifest)

        self.assertEqual(5395, expected["documentCount"])
        self.assertEqual("terraformers-reference-v4", expected["indexName"])
        self.assertEqual("gemini-embedding-2", expected["embeddingModelId"])
        self.assertEqual(1536, expected["vectorDimension"])

    def test_unknown_corpus_version_fails_closed(self):
        manifest = dict(self.manifest)
        manifest["corpusVersion"] = "terraformers-reference-v99"
        with self.assertRaisesRegex(RuntimeError, "unsupported corpusVersion"):
            gcp_ingest.validate_supported_manifest(manifest)

    def test_v3_vertex_request_preserves_document_semantics_and_1024_dimensions(self):
        transport = RecordingTransport()
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-001", lambda: "token", transport,
            sleeper=lambda _: None, pacing_seconds=0,
        )

        vector = embedder.embed("document text", "ignored title")

        self.assertEqual(1024, len(vector))
        path = transport.calls[0][1]
        body = transport.calls[0][2]
        self.assertTrue(path.endswith("gemini-embedding-001:predict"))
        self.assertEqual("RETRIEVAL_DOCUMENT", body["instances"][0]["task_type"])
        self.assertEqual(1024, body["parameters"]["outputDimensionality"])

    def test_embedding2_global_endpoint_uses_aiplatform_global_host(self):
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-2", lambda: "token",
            sleeper=lambda _: None, pacing_seconds=0, dimension=1536,
        )
        self.assertEqual("https://aiplatform.googleapis.com", embedder.transport.endpoint)

    def test_embedding2_us_multiregion_endpoint_uses_rep_host(self):
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "us", "gemini-embedding-2", lambda: "token",
            sleeper=lambda _: None, pacing_seconds=0, dimension=1536,
        )
        self.assertEqual("https://aiplatform.us.rep.googleapis.com", embedder.transport.endpoint)

    def test_v4_vertex_request_uses_embedding2_inline_document_semantics_and_1536_dimensions(self):
        transport = RecordingTransport({"embedding": {"values": [0.25] * 1536}})
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-2", lambda: "token", transport,
            sleeper=lambda _: None, pacing_seconds=0, dimension=1536,
        )

        vector = embedder.embed("document text", "VPC example")

        self.assertEqual(1536, len(vector))
        path = transport.calls[0][1]
        body = transport.calls[0][2]
        self.assertTrue(path.endswith("gemini-embedding-2:embedContent"))
        self.assertEqual("title: VPC example | text: document text", body["content"]["parts"][0]["text"])
        self.assertNotIn("taskType", body["embedContentConfig"])
        self.assertEqual(1536, body["embedContentConfig"]["outputDimensionality"])

    def test_vertex_retries_transient_429_with_bounded_backoff(self):
        class FlakyTransport:
            def __init__(self):
                self.calls = 0

            def request(self, method, path, body=None, accepted=(200,)):
                self.calls += 1
                if self.calls <= 2:
                    raise gcp_ingest.HttpRequestError(method, path, 429, "RESOURCE_EXHAUSTED")
                return 200, {"predictions": [{"embeddings": {"values": [0.25] * 1024}}]}

        transport = FlakyTransport()
        sleeps = []
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-001", lambda: "token", transport,
            sleeper=sleeps.append, pacing_seconds=0.5,
        )

        vector = embedder.embed("document text")

        self.assertEqual(1024, len(vector))
        self.assertEqual(3, transport.calls)
        self.assertEqual([1.0, 2.0, 0.5], sleeps)

    def test_vertex_does_not_retry_nontransient_http_error(self):
        class FailingTransport:
            def request(self, method, path, body=None, accepted=(200,)):
                raise gcp_ingest.HttpRequestError(method, path, 400, "INVALID_ARGUMENT")

        sleeps = []
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-001", lambda: "token", FailingTransport(),
            sleeper=sleeps.append, pacing_seconds=0,
        )

        with self.assertRaisesRegex(gcp_ingest.HttpRequestError, "400"):
            embedder.embed("document text")
        self.assertEqual([], sleeps)

    def test_sanitized_google_error_keeps_quota_signal(self):
        detail = gcp_ingest.sanitized_google_error({
            "error": {
                "status": "RESOURCE_EXHAUSTED",
                "message": "Quota exceeded.",
                "details": [{
                    "reason": "RATE_LIMIT_EXCEEDED",
                    "metadata": {"quota_metric": "aiplatform.googleapis.com/test_metric"},
                }],
            }
        })
        self.assertIn("RESOURCE_EXHAUSTED", detail)
        self.assertIn("RATE_LIMIT_EXCEEDED", detail)
        self.assertIn("quota_metric=aiplatform.googleapis.com/test_metric", detail)

    def test_malformed_nonfinite_and_wrong_dimension_embeddings_fail(self):
        invalid = [
            {},
            {"predictions": []},
            {"predictions": [{"embeddings": {"values": [0.1] * 1023}}]},
            {"predictions": [{"embeddings": {"values": [0.1] * 1023 + [math.nan]}}]},
        ]
        for response in invalid:
            with self.subTest(response=str(response)[:40]), self.assertRaises(RuntimeError):
                gcp_ingest.validate_embedding(response)

    def test_new_index_uses_committed_schema_and_preserves_id_and_metadata(self):
        client = FakeOpenSearch(self.schema)
        embedder = FakeEmbedder()
        document = self.documents[0]

        receipt = gcp_ingest.ingest(
            client, embedder, self.manifest, self.schema, [document], self.checksum
        )

        create = next(call for call in client.calls if call[:2] == ("PUT", "/terraformers-reference-v3"))
        self.assertEqual(self.schema, create[2])
        indexed = client.documents[document["documentId"]]
        self.assertEqual(document, {key: value for key, value in indexed.items() if key != "embedding"})
        self.assertEqual("ingested", receipt["outcome"])

    def test_incompatible_existing_mapping_fails(self):
        schema = json.loads(json.dumps(self.schema))
        schema["mappings"]["properties"]["embedding"]["dimension"] = 7
        client = FakeOpenSearch(schema, checksum=self.checksum)
        with self.assertRaisesRegex(RuntimeError, "mapping differs"):
            gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, [], self.checksum)

    def test_existing_vector_method_contract_mismatch_fails(self):
        cases = (
            ("engine", "lucene"),
            ("name", "ivf"),
            ("space_type", "l2"),
        )
        for field, value in cases:
            schema = json.loads(json.dumps(self.schema))
            schema["mappings"]["properties"]["embedding"]["method"][field] = value
            client = FakeOpenSearch(schema, checksum=self.checksum)
            with self.subTest(field=field, value=value), self.assertRaisesRegex(
                RuntimeError, "mapping differs"
            ):
                gcp_ingest.ingest(
                    client,
                    FakeEmbedder(),
                    self.manifest,
                    self.schema,
                    [],
                    self.checksum,
                )

    def test_same_version_checksum_mismatch_fails(self):
        client = FakeOpenSearch(self.schema, checksum="different")
        with self.assertRaisesRegex(RuntimeError, "checksum changed"):
            gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, [], self.checksum)

    def test_rerun_does_not_duplicate_documents(self):
        document = self.documents[0]
        client = FakeOpenSearch(
            self.schema, checksum=self.checksum,
            existing_documents={document["documentId"]: document},
        )
        embedder = FakeEmbedder()

        receipt = gcp_ingest.ingest(
            client, embedder, self.manifest, self.schema, [document], self.checksum
        )

        # The existing document is not re-embedded for indexing; one document embedding is
        # intentionally generated for the mandatory readiness query.
        self.assertEqual([document["content"]], embedder.texts)
        self.assertEqual("already-ingested", receipt["outcome"])
        self.assertFalse(any(method == "PUT" and "/_doc/" in path for method, path, _ in client.calls))

    def test_final_count_above_or_below_manifest_count_fails(self):
        for count in (127, 129):
            client = FakeOpenSearch(self.schema, checksum=self.checksum, count=count)
            with self.subTest(count=count), self.assertRaisesRegex(RuntimeError, "exactly 128"):
                gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, [], self.checksum)

    def test_v4_ingest_uses_v4_index_and_manifest_document_count(self):
        manifest = dict(self.manifest)
        manifest.update({
            "corpusVersion": "terraformers-reference-v4",
            "indexName": "terraformers-reference-v4",
            "embeddingModelId": "gemini-embedding-2",
            "vectorDimension": 1536,
            "documentCount": 5395,
            "chunkCount": 5395,
        })
        schema = json.loads(json.dumps(self.schema))
        schema["mappings"]["properties"]["embedding"]["dimension"] = 1536
        client = FakeOpenSearch(
            schema,
            checksum=self.checksum,
            count=5395,
            corpus_version="terraformers-reference-v4",
        )
        receipt = gcp_ingest.ingest(
            client, FakeEmbedder(dimension=1536), manifest, schema, self.documents[:1], self.checksum
        )
        self.assertEqual("terraformers-reference-v4", receipt["corpus_version"])
        self.assertEqual(5395, receipt["document_count"])
        self.assertEqual("terraformers-reference-v4", receipt["index_name"])
        self.assertEqual(1, receipt["indexed_this_run"])
        self.assertEqual(0, receipt["skipped_existing"])
        self.assertEqual("test-index-uuid", receipt["index_uuid"])
        self.assertEqual(gcp_ingest.content_identity(self.documents[:1]), receipt["non_vector_content_identity"])
        self.assertTrue(any(path == "/terraformers-reference-v4/_count" for _, path, _ in client.calls))

    def test_representative_knn_hit_is_required(self):
        client = FakeOpenSearch(self.schema, checksum=self.checksum, hits=[])
        with self.assertRaisesRegex(RuntimeError, "k-NN query returned no hits"):
            gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, self.documents[:1], self.checksum)
        search = next(body for method, path, body in client.calls if path.endswith("/_search"))
        self.assertEqual(1024, len(search["query"]["knn"]["embedding"]["vector"]))


class ExactV4ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.manifest = {"corpusVersion": "terraformers-reference-v4", "chunkCount": 5395,
                         **gcp_ingest.SUPPORTED_CONTRACTS["terraformers-reference-v4"]}
        self.schema = {"mappings": {"properties": {
            "embedding": {"type": "knn_vector", "dimension": 1536,
                          "method": {"name": "hnsw", "engine": "faiss", "space_type": "cosinesimil", "parameters": {}}},
            "documentId": {"type": "keyword"}, "content": {"type": "text"}}}}
        self.documents = [{"documentId": f"doc-{i:04}", "content": "source", "corpusVersion": "terraformers-reference-v4",
                           "title": "AWS", "resourceTypes": ["aws_vpc"], "sourceUrl": "https://example.test/official"}
                          for i in range(5395)]
        self.provenance = {"githubArtifactBindingVerified": True, "runId": 123, "artifactId": 456,
            "receipt": {"corpus_version": "terraformers-reference-v4", "index_name": "terraformers-reference-v4",
                "checksum": "abc", "document_count": 5395, "index_uuid": "test-index-uuid",
                "embedding_model_id": "gemini-embedding-2", "vector_dimension": 1536,
                "indexed_this_run": 5395, "skipped_existing": 0, "outcome": "ingested",
                "non_vector_content_identity": gcp_ingest.content_identity(self.documents)}}

    def client(self, documents=None, schema=None, checksum="abc"):
        class Snapshot(FakeOpenSearch):
            def request(inner, method, path, body=None, accepted=(200,)):
                if path.endswith("/_search?scroll=1m"):
                    inner.calls.append((method, path, body))
                    self.assertEqual({"excludes": ["embedding"]}, body["_source"])
                    inner.offset = 0
                    return 200, {"_scroll_id": "snapshot", "hits": {
                        "total": {"value": 5395, "relation": "eq"}, "hits": inner.page()}}
                if path == "/_search/scroll":
                    inner.calls.append((method, path, body))
                    return (200, {"hits": {"hits": inner.page()}}) if method == "POST" else (200, {})
                return super(Snapshot, inner).request(method, path, body, accepted)

            def page(inner):
                docs = inner.live[inner.offset:inner.offset + 500]
                inner.offset += 500
                return [{"_id": d["documentId"], "_source": {k: v for k, v in d.items() if k != "embedding"}} for d in docs]

        client = Snapshot(schema or self.schema, checksum=checksum, count=5395,
                          corpus_version="terraformers-reference-v4")
        client.live = json.loads(json.dumps(self.documents if documents is None else documents))
        client.documents = {d["documentId"]: d for d in client.live}
        client.hits = [{"_id": client.live[0]["documentId"], "_source": {"documentId": client.live[0]["documentId"]}}]
        return client

    def verify(self, client=None, provenance=None):
        return gcp_ingest.verify_exact_v4(client or self.client(), self.manifest, self.schema,
                                         self.documents, "abc", self.provenance if provenance is None else provenance)

    def test_exact_universe_all_source_fields_and_model_receipt_pass_without_embeddings(self):
        client = self.client()
        result = self.verify(client)
        self.assertEqual("EXACT_REUSABLE_COMPLETED_V4", result["classification"])
        self.assertEqual(result["expectedContentIdentity"], result["liveContentIdentity"])
        self.assertEqual(0, result["embeddingRequests"])
        self.assertFalse(any(method == "PUT" or "knn" in str(body) for method, _, body in client.calls))
        self.assertTrue(any(method == "DELETE" and path == "/_search/scroll" for method, path, _ in client.calls))
        self.assertNotIn("source", json.dumps(result))

    def test_same_ids_and_count_but_changed_content_or_metadata_fail(self):
        for field, value in [("content", "changed"), ("sourceUrl", "https://wrong.test/"),
                             ("resourceTypes", ["aws_s3_bucket"]), ("extra", "stale")]:
            docs = json.loads(json.dumps(self.documents))
            docs[0][field] = value
            with self.subTest(field=field):
                result = self.verify(self.client(docs))
                self.assertEqual("STALE_OR_MIXED_MODEL_SPACE", result["classification"])
                self.assertEqual(1, result["changedCount"])

    def test_one_missing_one_unexpected_even_at_equal_total_fails(self):
        docs = json.loads(json.dumps(self.documents))
        docs[0]["documentId"] = "stale-id"
        result = self.verify(self.client(docs))
        self.assertEqual("PARTIAL_INDEX", result["classification"])
        self.assertEqual(1, result["missingCount"])
        self.assertEqual(1, result["unexpectedCount"])

    def test_wrong_dimension_method_and_checksum_fail(self):
        schema = json.loads(json.dumps(self.schema))
        schema["mappings"]["properties"]["embedding"]["dimension"] = 1024
        self.assertEqual("WRONG_MODEL_OR_DIMENSION", self.verify(self.client(schema=schema))["classification"])
        schema["mappings"]["properties"]["embedding"]["dimension"] = 1536
        schema["mappings"]["properties"]["embedding"]["method"]["engine"] = "lucene"
        self.assertEqual("STALE_OR_MIXED_MODEL_SPACE", self.verify(self.client(schema=schema))["classification"])
        for checksum in (None, "wrong"):
            client = self.client(checksum=checksum)
            client.exists = True
            self.assertEqual("STALE_OR_MIXED_MODEL_SPACE", self.verify(client)["classification"])

    def test_missing_or_historical_partial_model_provenance_never_reusable(self):
        variants = [{}, {"githubArtifactBindingVerified": False},
                    {"githubArtifactBindingVerified": True, "receipt": self.provenance["receipt"] | {"index_uuid": None}},
                    {"githubArtifactBindingVerified": True, "receipt": self.provenance["receipt"] | {"skipped_existing": 1}},
                    {"githubArtifactBindingVerified": True, "receipt": self.provenance["receipt"] | {"indexed_this_run": 5394}}]
        for provenance in variants:
            with self.subTest(provenance=provenance):
                self.assertEqual("MODEL_PROVENANCE_UNPROVEN", self.verify(provenance=provenance)["classification"])
        for changed in ({"embedding_model_id": "gemini-embedding-001"}, {"vector_dimension": 1024}):
            self.assertEqual("WRONG_MODEL_OR_DIMENSION", self.verify(provenance={"githubArtifactBindingVerified": True,
                "receipt": self.provenance["receipt"] | changed})["classification"])

    def test_missing_index_and_duplicate_identity_fail(self):
        client = self.client(); client.exists = False
        self.assertEqual("MISSING_INDEX", self.verify(client)["classification"])
        docs = json.loads(json.dumps(self.documents)); docs[1] = docs[0]
        client = self.client(docs)
        self.assertEqual("STALE_OR_MIXED_MODEL_SPACE", self.verify(client)["classification"])
        self.assertTrue(any(method == "DELETE" for method, _, _ in client.calls))
        docs[0]["documentId"] = None
        self.assertEqual("STALE_OR_MIXED_MODEL_SPACE", self.verify(self.client(docs))["classification"])


class Pt8aCleanV4Tests(unittest.TestCase):
    def setUp(self):
        self.snapshot = ExactV4ReadinessTests()
        self.snapshot.setUp()
        self.manifest, self.schema = self.snapshot.manifest, self.snapshot.schema
        self.documents = self.snapshot.documents
        for i, document in enumerate(self.documents):
            provider = i < 5387
            document.update(documentType="AWS_PROVIDER_DOC" if provider else "TERRAFORMERS_PATTERN",
                            sourceCommit=gcp_ingest.PT8A_PROVIDER_SOURCE if provider else gcp_ingest.PT8A_PROJECT_SOURCE)
        self.source, self.comment_id = "a" * 40, 123
        self.env = {"GITHUB_SHA": self.source, "GITHUB_REF": "refs/heads/main",
                    "GITHUB_REPOSITORY": "siamese-lang/terraformers-platform", "GITHUB_RUN_ATTEMPT": "1"}
        self.fields = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
            "reviewed_source_sha": self.source, "candidate_identity": gcp_ingest.PT8A_CANDIDATE,
            "procedure_sha256": hashlib.sha256((ROOT / gcp_ingest.PT8A_PROCEDURE).read_bytes()).hexdigest(),
            "purpose": "PT8A_CLEAN_V4_REEMBED_ALL_5395", "corpus_checksum": gcp_ingest.PT8A_CHECKSUM,
            "provider_source": gcp_ingest.PT8A_PROVIDER_SOURCE, "project_decision_source": gcp_ingest.PT8A_PROJECT_SOURCE,
            "embedding_model": "gemini-embedding-2", "vector_dimension": "1536"}
        self.reader = self.authority()
        self.client = self.snapshot.client(checksum=gcp_ingest.PT8A_CHECKSUM)
        # Retained IDs/vectors already exist. Only vectors may be overwritten.
        old_vector = [-0.5] * 1536
        for document in self.client.live:
            document["embedding"] = old_vector
        self.embedder = FakeEmbedder(1536)

    def authority(self, changed=None, main=None, owner="siamese-lang", marker="[HUMAN_GATE_APPROVAL:v1]"):
        fields = self.fields | (changed or {})
        body = marker + "\n" + "\n".join(f"{k}: {v}" for k, v in fields.items())
        class Authority:
            def request(inner, method, path, body=None):
                if path == f"/issues/comments/{self.comment_id}":
                    return 200, {"user": {"login": owner}, "body": fields.get("raw_body", body_text)}
                if path == "/git/ref/heads/main":
                    return 200, {"object": {"sha": main or self.source}}
                raise AssertionError(path)
        body_text = body
        return Authority()

    def run_clean(self, **changed):
        args = {"client": self.client, "embedder": self.embedder, "manifest": self.manifest,
                "schema": self.schema, "documents": self.documents, "checksum": gcp_ingest.PT8A_CHECKSUM,
                "coverage": gcp_ingest.PT8A_COVERAGE, "source": self.source, "comment_id": self.comment_id,
                "authority_reader": self.reader, "environment": self.env} | changed
        return gcp_ingest.clean_reembed_v4(**args)

    def assert_no_overwrite(self):
        self.assertEqual([], self.embedder.texts)
        self.assertFalse(any("/_update/" in p or m == "PUT" for m, p, _ in self.client.calls))

    def provenance(self, receipt):
        return {"githubArtifactBindingVerified": True, "sourceSha": self.source,
                "runId": 1234, "artifactId": 5678, "receipt": receipt}

    def verify(self, receipt):
        return gcp_ingest.verify_exact_v4(self.client, self.manifest, self.schema, self.documents,
                                        gcp_ingest.PT8A_CHECKSUM, self.provenance(receipt))

    def test_ordinary_v4_rerun_keeps_existing_id_skips(self):
        receipt = gcp_ingest.ingest(self.client, self.embedder, self.manifest, self.schema,
                                    self.documents, gcp_ingest.PT8A_CHECKSUM)
        self.assertEqual(5395, receipt["skipped_existing"])
        self.assertEqual(0, receipt["indexed_this_run"])
        self.assertEqual("already-ingested", receipt["outcome"])
        self.assertEqual(1, len(self.embedder.texts))  # Existing representative query only.
        self.assertFalse(any("/_update/" in p or (m == "PUT" and "/_doc/" in p) for m, p, _ in self.client.calls))
        self.assertTrue(all(d["embedding"][0] == -0.5 for d in self.client.live))

    def test_clean_overwrites_every_existing_id_once_and_receipt_is_exact_reusable(self):
        receipt = self.run_clean()
        updates = [c for c in self.client.calls if "/_update/" in c[1]]
        self.assertEqual(5395, len(updates))
        self.assertEqual({d["documentId"] for d in self.documents}, {urllib.parse.unquote(p.rsplit("/", 1)[1]) for _, p, _ in updates})
        self.assertTrue(all(m == "POST" and set(b) == {"doc"} and set(b["doc"]) == {"embedding"} for m, _, b in updates))
        self.assertFalse(any(m == "PUT" or (m == "HEAD" and "/_doc/" in p) or (m == "DELETE" and p != "/_search/scroll") for m, p, _ in self.client.calls))
        self.assertEqual(5395, len(self.embedder.texts))  # No extra query embedding.
        self.assertEqual(5395, receipt["embedded_this_run"])
        self.assertEqual(0, receipt["skipped_existing"])
        self.assertEqual(receipt["index_uuid_before"], receipt["index_uuid_after"])
        self.assertEqual(receipt["pre_content_identity"], receipt["post_content_identity"])
        self.assertEqual(gcp_ingest.content_identity(self.documents), receipt["non_vector_content_identity"])
        self.assertTrue(all(d["embedding"][0] == 0.25 for d in self.client.live))
        self.assertEqual("EXACT_REUSABLE_COMPLETED_V4", self.verify(receipt)["classification"])
        for changed in ({"embedded_this_run": 5394}, {"skipped_existing": 1}, {"outcome": "failed"},
                        {"index_uuid_after": "another-index"}, {"post_content_identity": "different"},
                        {"procedure_sha256": "superseded"}, {"reviewed_source_sha": "b" * 40},
                        {"project_decision_source": "b" * 40}, {"live_approval_comment_id": None}):
            with self.subTest(changed=changed):
                self.assertEqual("MODEL_PROVENANCE_UNPROVEN", self.verify(receipt | changed)["classification"])

    def test_v3_historical_source_checksum_or_coverage_mismatch_fails_before_embedding(self):
        v3, _, _, _ = gcp_ingest.load_corpus(ROOT / "corpus/terraformers-reference/v3")
        for changed in ({"manifest": v3}, {"checksum": "wrong"},
                        {"coverage": gcp_ingest.PT8A_COVERAGE | {"projectDecisionCount": 9}}):
            with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                self.run_clean(**changed)
        for index in (0, 5394):
            docs = copy.deepcopy(self.documents); docs[index]["sourceCommit"] = self.source
            with self.subTest(index=index), self.assertRaisesRegex(RuntimeError, "historical"):
                self.run_clean(documents=docs)
        self.assert_no_overwrite()
        self.assertEqual([], self.client.calls)

    def test_missing_wrong_or_unbound_live_authority_stops_before_any_index_read_or_embedding(self):
        for key in self.fields:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, "live approval"):
                self.run_clean(authority_reader=self.authority({key: "wrong"}))
        for changed in ({"comment_id": None}, {"environment": self.env | {"GITHUB_RUN_ATTEMPT": "2"}},
                        {"authority_reader": self.authority(owner="someone-else")},
                        {"authority_reader": self.authority(marker="INGEST_A7_7_REFERENCE_V4")},
                        {"authority_reader": self.authority(main="b" * 40)}):
            with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                self.run_clean(**changed)
        self.assert_no_overwrite()
        self.assertEqual([], self.client.calls)

    def test_non_vector_or_id_universe_mismatch_never_overwrites(self):
        for field, value in (("content", "changed"), ("sourceCommit", self.source), ("documentId", "unexpected")):
            docs = copy.deepcopy(self.documents); docs[0][field] = value
            client = self.snapshot.client(documents=docs, checksum=gcp_ingest.PT8A_CHECKSUM)
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, "DESTRUCTIVE_INDEX_REBUILD_DECISION"):
                self.run_clean(client=client)
            self.assertFalse(any("/_update/" in p or m == "PUT" for m, p, _ in client.calls))
        self.assert_no_overwrite()

    def test_wrong_mapping_or_model_cannot_overwrite(self):
        for field in ("dimension", "analyzer", "parameter"):
            schema = copy.deepcopy(self.schema)
            if field == "dimension": schema["mappings"]["properties"]["embedding"]["dimension"] = 1024
            elif field == "analyzer": schema["mappings"]["properties"]["content"]["analyzer"] = "changed"
            else: schema["mappings"]["properties"]["embedding"]["method"]["parameters"]["m"] = 8
            client = self.snapshot.client(schema=schema, checksum=gcp_ingest.PT8A_CHECKSUM)
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, "DESTRUCTIVE_INDEX_REBUILD_DECISION"):
                self.run_clean(client=client)
            self.assertFalse(any("/_update/" in p for _, p, _ in client.calls))
        wrong = FakeEmbedder(1024)
        with self.assertRaisesRegex(RuntimeError, "embedder"):
            self.run_clean(embedder=wrong)
        self.assert_no_overwrite()

    def test_partial_embedding_failure_preserves_non_reusable_receipt_without_retry(self):
        def fail_after_one(text, title=None):
            self.embedder.texts.append(text)
            if len(self.embedder.texts) > 1:
                raise RuntimeError("injected embedding failure")
            return [0.25] * 1536
        self.embedder.embed = fail_after_one
        with patch.object(gcp_ingest, "INGESTION_WORKERS", 1), self.assertRaises(gcp_ingest.CleanV4Failure) as failure:
            self.run_clean()
        receipt = failure.exception.receipt
        self.assertEqual(1, receipt["embedded_this_run"])
        self.assertEqual("failed", receipt["outcome"])
        self.assertEqual("embed_and_overwrite", receipt["failure_stage"])
        self.assertEqual("MODEL_PROVENANCE_UNPROVEN", self.verify(receipt)["classification"])

    def test_uuid_change_after_overwrite_never_emits_completed_receipt(self):
        original = self.client.request
        refreshed = False
        def drift(method, path, body=None, accepted=(200,)):
            nonlocal refreshed
            status, result = original(method, path, body, accepted)
            if path.endswith("/_refresh"): refreshed = True
            if path.endswith("/_settings") and refreshed:
                result[self.manifest["indexName"]]["settings"]["index"]["uuid"] = "replacement-uuid"
            return status, result
        self.client.request = drift
        with self.assertRaises(gcp_ingest.CleanV4Failure) as failure:
            self.run_clean()
        self.assertEqual(5395, failure.exception.receipt["embedded_this_run"])
        self.assertEqual("failed", failure.exception.receipt["outcome"])
        self.assertEqual("post_content_uuid_verification", failure.exception.receipt["failure_stage"])
        self.assertNotEqual("EXACT_REUSABLE_COMPLETED_V4", self.verify(failure.exception.receipt)["classification"])

    def test_non_vector_change_after_overwrite_never_emits_completed_receipt(self):
        original = self.client.request
        def drift(method, path, body=None, accepted=(200,)):
            result = original(method, path, body, accepted)
            if path.endswith("/_refresh"):
                self.client.live[0]["content"] = "intervening writer"
            return result
        self.client.request = drift
        with self.assertRaises(gcp_ingest.CleanV4Failure) as failure:
            self.run_clean()
        self.assertEqual(5395, failure.exception.receipt["embedded_this_run"])
        self.assertEqual("failed", failure.exception.receipt["outcome"])
        self.assertNotEqual(failure.exception.receipt["pre_content_identity"], failure.exception.receipt["post_content_identity"])
        self.assertNotEqual("EXACT_REUSABLE_COMPLETED_V4", self.verify(failure.exception.receipt)["classification"])

    def test_existing_workflow_rejects_old_token_and_v3_clean_before_cloud_auth(self):
        import os
        import subprocess
        import yaml
        workflow = yaml.safe_load((ROOT / ".github/workflows/gcp-target-corpus-ingestion.yml").read_text())
        steps = workflow["jobs"]["ingest"]["steps"]
        validation = next(s for s in steps if s.get("name") == "Validate trusted ingestion request")
        auth_index = next(i for i, s in enumerate(steps) if s.get("uses") == "google-github-actions/auth@v3")
        self.assertLess(steps.index(validation), auth_index)
        for version, token in (("terraformers-reference-v4", "INGEST_A7_7_REFERENCE_V4"),
                               ("terraformers-reference-v3", "REEMBED_REVIEWED_PT8A_CLEAN_V4")):
            env = os.environ | self.env | {"EXPECTED_SHA": self.source, "INGESTION_MODE": "pt8a-clean-v4",
                "CORPUS_VERSION": version, "CONFIRMATION": token}
            result = subprocess.run(["bash", "-c", validation["run"]], cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("Frozen official identity/order: PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
