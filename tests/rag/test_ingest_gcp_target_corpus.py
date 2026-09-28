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
    def __init__(self):
        self.texts = []

    def embed(self, text):
        self.texts.append(text)
        return [0.25] * 1024


class FakeOpenSearch:
    def __init__(self, schema, checksum=None, existing_documents=None, count=128, hits=None):
        self.schema = schema
        self.checksum = checksum
        self.exists = checksum is not None
        self.documents = dict(existing_documents or {})
        self.count = count
        self.hits = hits if hits is not None else [{"_id": "doc-1", "_source": {"documentId": "doc-1"}}]
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
                "corpus_version": "terraformers-reference-v3", "checksum": self.checksum}}
            return 200, {index: {"mappings": mapping}}
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
        self.assertEqual(gcp_ingest.EXPECTED_CONTRACT, {
            key: self.manifest[key] for key in gcp_ingest.EXPECTED_CONTRACT
        })
        self.assertEqual(128, len(self.documents))
        self.assertEqual(64, len(self.checksum))

    def test_vertex_request_uses_document_semantics_and_1024_dimensions(self):
        transport = RecordingTransport()
        embedder = gcp_ingest.VertexDocumentEmbedder(
            "project", "global", "gemini-embedding-001", lambda: "token", transport,
            sleeper=lambda _: None, pacing_seconds=0,
        )

        vector = embedder.embed("document text")

        self.assertEqual(1024, len(vector))
        body = transport.calls[0][2]
        self.assertEqual("RETRIEVAL_DOCUMENT", body["instances"][0]["task_type"])
        self.assertEqual(1024, body["parameters"]["outputDimensionality"])

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

    def test_final_count_above_or_below_128_fails(self):
        for count in (127, 129):
            client = FakeOpenSearch(self.schema, checksum=self.checksum, count=count)
            with self.subTest(count=count), self.assertRaisesRegex(RuntimeError, "exactly 128"):
                gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, [], self.checksum)

    def test_representative_knn_hit_is_required(self):
        client = FakeOpenSearch(self.schema, checksum=self.checksum, hits=[])
        with self.assertRaisesRegex(RuntimeError, "k-NN query returned no hits"):
            gcp_ingest.ingest(client, FakeEmbedder(), self.manifest, self.schema, self.documents[:1], self.checksum)
        search = next(body for method, path, body in client.calls if path.endswith("/_search"))
        self.assertEqual(1024, len(search["query"]["knn"]["embedding"]["vector"]))


if __name__ == "__main__":
    unittest.main()
