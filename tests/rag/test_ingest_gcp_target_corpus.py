import importlib.util
import json
import math
import unittest
from pathlib import Path

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
                return [{"_id": d["documentId"], "_source": d} for d in docs]

        client = Snapshot(schema or self.schema, checksum=checksum, count=5395,
                          corpus_version="terraformers-reference-v4")
        client.live = json.loads(json.dumps(self.documents if documents is None else documents))
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


if __name__ == "__main__":
    unittest.main()
