import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parents[2]
V2 = ROOT / "corpus/terraformers-reference/v2"
V3 = ROOT / "corpus/terraformers-reference/v3"
SPEC = importlib.util.spec_from_file_location(
    "derive_corpus_v3", ROOT / "scripts/rag/derive-corpus-v3.py"
)
derive_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(derive_module)


def directory_digest(directory):
    digest = hashlib.sha256()
    for path in sorted(directory.iterdir()):
        digest.update(path.name.encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def documents(directory):
    return {
        item["documentId"]: item
        for item in (
            json.loads(line)
            for line in (directory / "documents.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    }


class DeriveCorpusV3Tests(unittest.TestCase):
    def test_derivation_is_reproducible_and_preserves_v2_knowledge(self):
        before = directory_digest(V2)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "v3"
            summary = derive_module.derive(V2, output)
            self.assertEqual(before, directory_digest(V2), "derivation must not modify committed v2")
            self.assertEqual({"corpusVersion": "terraformers-reference-v3", "documentCount": 128}, summary)
            for filename in ("corpus-manifest.json", "documents.jsonl", "index-schema.json", "source-manifest.json"):
                self.assertEqual((V3 / filename).read_bytes(), (output / filename).read_bytes())

            v2_docs, v3_docs = documents(V2), documents(output)
            self.assertEqual(128, len(v2_docs))
            self.assertEqual(sorted(v2_docs), sorted(v3_docs))
            for document_id, v2_document in v2_docs.items():
                v3_document = v3_docs[document_id]
                self.assertEqual("terraformers-reference-v3", v3_document["corpusVersion"])
                self.assertEqual("5.100.0", v3_document["providerVersion"])
                approved = {"corpusVersion"}
                if v2_document["documentType"] == "TERRAFORMERS_PATTERN":
                    approved.add("sourceVersion")
                self.assertEqual(
                    {key: value for key, value in v2_document.items() if key not in approved},
                    {key: value for key, value in v3_document.items() if key not in approved},
                )
            v2_sources = json.loads((V2 / "source-manifest.json").read_text(encoding="utf-8"))["sources"]
            v3_sources = json.loads((output / "source-manifest.json").read_text(encoding="utf-8"))["sources"]
            self.assertEqual(len(v2_sources), len(v3_sources))
            for v2_source, v3_source in zip(v2_sources, v3_sources):
                approved = {"sourceVersion"} if v2_source["sourceType"] == "TERRAFORMERS_PATTERN" else set()
                self.assertEqual(
                    {key: value for key, value in v2_source.items() if key not in approved},
                    {key: value for key, value in v3_source.items() if key not in approved},
                )

    def test_committed_contract_and_gcp_defaults_match(self):
        completed = subprocess.run(
            [sys.executable, str(ROOT / "scripts/checks/rag-corpus-contract-verification.py"), "--corpus-dir", str(V3)],
            capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        manifest = json.loads((V3 / "corpus-manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(
            {
                "corpusVersion": "terraformers-reference-v3", "indexName": "terraformers-reference-v3",
                "embeddingModelId": "gemini-embedding-001", "vectorDimension": 1024,
                "vectorField": "embedding", "contentField": "content",
            },
            {key: manifest[key] for key in ("corpusVersion", "indexName", "embeddingModelId", "vectorDimension", "vectorField", "contentField")},
        )
        vector = json.loads((V3 / "index-schema.json").read_text(encoding="utf-8"))["mappings"]["properties"]["embedding"]
        self.assertEqual({"type": "knn_vector", "dimension": 1024}, {key: vector[key] for key in ("type", "dimension")})
        config = (ROOT / "backend/src/main/resources/application-gcp-target.yml").read_text(encoding="utf-8")
        for expected in (
            "INDEX_NAME:terraformers-reference-v3", "CORPUS_VERSION:terraformers-reference-v3",
            "VERTEX_EMBEDDING_MODEL_ID:gemini-embedding-001", "EXPECTED_VECTOR_DIMENSION:1024",
            "VERTEX_EMBEDDING_DIMENSION:1024", "VECTOR_FIELD_NAME:embedding", "CONTENT_FIELD_NAME:content",
            "PROVIDER_VERSION:5.100.0",
        ):
            self.assertIn(expected, config)


if __name__ == "__main__":
    unittest.main()
