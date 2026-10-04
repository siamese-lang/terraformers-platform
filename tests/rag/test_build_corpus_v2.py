import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).parents[2]
SPEC = importlib.util.spec_from_file_location(
    "build_corpus_v2", ROOT / "scripts/rag/build-corpus-v2.py"
)
build = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(build)

V4_SPEC = importlib.util.spec_from_file_location(
    "build_corpus_v4", ROOT / "scripts/rag/build-corpus-v4.py"
)
build_v4 = importlib.util.module_from_spec(V4_SPEC)
V4_SPEC.loader.exec_module(build_v4)

COVERAGE_SPEC = importlib.util.spec_from_file_location(
    "report_corpus_coverage", ROOT / "scripts/rag/report-corpus-coverage.py"
)
report_coverage = importlib.util.module_from_spec(COVERAGE_SPEC)
COVERAGE_SPEC.loader.exec_module(report_coverage)


class BuildCorpusV2Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.workspace = Path(self.temp.name)
        self.provider = self.workspace / "provider"
        docs = self.provider / "website/docs/r"
        docs.mkdir(parents=True)
        (docs / "vpc.html.markdown").write_text(
            """---
page_title: "AWS: aws_vpc"
---
# Resource: aws_vpc

Provides a VPC resource.

## Example Usage

Basic usage:

```terraform
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
  password   = "unsafe-example"
}
```

Public usage:

```terraform
resource "aws_vpc" "public" {
  cidr_block = "0.0.0.0/0"
}
```

## Argument Reference

* `cidr_block` - (Optional) VPC CIDR.
""",
            encoding="utf-8",
        )
        self.schema = self.workspace / "schema.json"
        self.schema.write_text(
            json.dumps(
                {
                    "provider_schemas": {
                        build.PROVIDER_ADDRESS: {
                            "resource_schemas": {
                                "aws_vpc": {
                                    "version": 1,
                                    "block": {
                                        "attributes": {
                                            "cidr_block": {
                                                "type": "string",
                                                "optional": True,
                                            },
                                            "id": {
                                                "type": "string",
                                                "computed": True,
                                            },
                                        },
                                        "block_types": {},
                                    },
                                }
                            }
                        }
                    }
                }
            ),
            encoding="utf-8",
        )
        self.v1 = self.workspace / "v1"
        self.v1.mkdir()
        (self.v1 / "documents.jsonl").write_text(
            json.dumps(
                {
                    "documentId": "tfref-v1-private-entry",
                    "title": "Private entry",
                    "documentType": "TERRAFORMERS_PATTERN",
                    "content": "CloudFront is the only public entry.",
                    "services": ["cloudfront"],
                    "resourceTypes": ["aws_cloudfront_distribution"],
                    "architecturePattern": "cloudfront-only-public-entry",
                    "securityConsiderations": ["private-origin"],
                    "sourceVersion": "terraformers-reference-v1",
                    "providerVersion": build.PROVIDER_VERSION,
                    "sourcePath": "docs/source-rag-gitops-reuse-plan.md",
                    "corpusVersion": "terraformers-reference-v1",
                }
            )
            + "\n",
            encoding="utf-8",
        )

    def tearDown(self):
        self.temp.cleanup()

    def build_documents(self):
        provider_schema = build.schema_provider(self.schema)
        provider_docs, provider_sources = build.provider_documents(
            self.provider,
            provider_schema,
            "a" * 40,
            ["aws_vpc"],
        )
        project_docs, project_sources = build.project_pattern_documents(
            self.v1,
            "b" * 40,
        )
        return provider_docs + project_docs, provider_sources + project_sources

    def test_preserves_complete_hcl_blocks_and_tags_risky_examples(self):
        documents, _ = self.build_documents()
        examples = [
            document
            for document in documents
            if document["documentType"] == "AWS_PROVIDER_EXAMPLE"
        ]
        self.assertEqual(2, len(examples))
        secret_example = next(
            document for document in examples if "plaintext-secret" in document["riskTags"]
        )
        self.assertIn('password   = "<sensitive-value>"', secret_example["content"])
        self.assertNotIn("unsafe-example", secret_example["content"])
        self.assertEqual(
            secret_example["content"].count("```"),
            2,
            "a generated example must contain one complete fenced HCL block",
        )
        public_example = next(
            document for document in examples if "public-cidr" in document["riskTags"]
        )
        self.assertIn("0.0.0.0/0", public_example["content"])

    def test_generates_schema_and_high_priority_project_documents(self):
        documents, _ = self.build_documents()
        schema_document = next(
            document
            for document in documents
            if document["documentType"] == "AWS_PROVIDER_SCHEMA"
        )
        self.assertEqual("PROVIDER_SCHEMA", schema_document["authority"])
        self.assertIn("`cidr_block`: optional", schema_document["content"])
        project_document = next(
            document
            for document in documents
            if document["documentType"] == "TERRAFORMERS_PATTERN"
        )
        self.assertEqual("PROJECT_DECISION", project_document["authority"])
        self.assertEqual(100, project_document["priority"])
        self.assertEqual(build.CORPUS_VERSION, project_document["sourceVersion"])

    def test_written_v2_corpus_passes_contract_validator(self):
        documents, sources = self.build_documents()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "v2"
            build.write_corpus(output, documents, sources)
            completed = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts/checks/rag-corpus-contract-verification.py"),
                    "--corpus-dir",
                    str(output),
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            manifest = json.loads(
                (output / "corpus-manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual(len(documents), manifest["documentCount"])
            mapping = json.loads(
                (output / "index-schema.json").read_text(encoding="utf-8")
            )["mappings"]["properties"]
            self.assertEqual("keyword", mapping["authority"]["type"])
            self.assertEqual("integer", mapping["priority"]["type"])


class BuildCorpusV4Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.workspace = Path(self.temp.name)
        self.provider = self.workspace / "provider"
        docs = self.provider / "website/docs/r"
        docs.mkdir(parents=True)
        (docs / "vpc.html.markdown").write_text(
            """---
subcategory: "VPC (Virtual Private Cloud)"
layout: "aws"
page_title: "AWS: aws_vpc"
---
# Resource: aws_vpc

Provides a VPC resource.

## Example Usage

Basic:

```terraform
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
}
```
""",
            encoding="utf-8",
        )
        (docs / "lambda_function.html.markdown").write_text(
            """---
subcategory: "Lambda"
layout: "aws"
page_title: "AWS: aws_lambda_function"
---
# Resource: aws_lambda_function

Provides a Lambda Function resource.

## Example Usage

Basic:

```terraform
resource "aws_lambda_function" "example" {
  function_name = "example"
  role          = aws_iam_role.example.arn
  handler       = "index.handler"
}
```
""",
            encoding="utf-8",
        )
        (docs / "unparsed_resource.html.markdown").write_text(
            """---
subcategory: "Test"
layout: "aws"
page_title: "AWS: aws_unparsed_resource"
---
# Unexpected Heading

This source file exists but intentionally has no parser-recognized resource overview
or Terraform/HCL example.
""",
            encoding="utf-8",
        )
        self.schema = self.workspace / "schema.json"
        self.schema.write_text(
            json.dumps(
                {
                    "provider_schemas": {
                        build_v4.PROVIDER_ADDRESS: {
                            "resource_schemas": {
                                "aws_vpc": {
                                    "block": {
                                        "attributes": {
                                            "cidr_block": {"type": "string", "optional": True}
                                        },
                                        "block_types": {},
                                    }
                                },
                                "aws_lambda_function": {
                                    "block": {
                                        "attributes": {
                                            "function_name": {"type": "string", "required": True},
                                            "role": {"type": "string", "required": True},
                                            "handler": {"type": "string", "optional": True},
                                        },
                                        "block_types": {},
                                    }
                                },
                                "aws_schema_only_resource": {
                                    "block": {
                                        "attributes": {
                                            "name": {"type": "string", "required": True}
                                        },
                                        "block_types": {},
                                    }
                                },
                                "aws_unparsed_resource": {
                                    "block": {
                                        "attributes": {
                                            "name": {"type": "string", "required": True}
                                        },
                                        "block_types": {},
                                    }
                                },
                            }
                        }
                    }
                }
            ),
            encoding="utf-8",
        )
        self.v3 = self.workspace / "v3"
        self.v3.mkdir()
        (self.v3 / "documents.jsonl").write_text(
            "\n".join(
                [
                    json.dumps(
                        {
                            "documentId": "tfaws-5.100.0-aws_vpc-schema",
                            "documentType": "AWS_PROVIDER_SCHEMA",
                            "resourceTypes": ["aws_vpc"],
                        }
                    ),
                    json.dumps(
                        {
                            "documentId": "tfref-v2-private-entry",
                            "title": "Private entry",
                            "documentType": "TERRAFORMERS_PATTERN",
                            "authority": "PROJECT_DECISION",
                            "priority": 100,
                            "sectionType": "project-pattern",
                            "riskTags": [],
                            "content": "CloudFront is the only public entry.",
                            "services": ["cloudfront"],
                            "resourceTypes": ["aws_cloudfront_distribution"],
                            "architecturePattern": "cloudfront-only-public-entry",
                            "securityConsiderations": ["private-origin"],
                            "sourceVersion": "terraformers-reference-v3",
                            "providerVersion": build_v4.PROVIDER_VERSION,
                            "sourcePath": "docs/reference-retrieval.md",
                            "sourceCommit": "c" * 40,
                            "corpusVersion": "terraformers-reference-v3",
                        }
                    ),
                ]
            )
            + "\n",
            encoding="utf-8",
        )

    def tearDown(self):
        self.temp.cleanup()

    def args(self, output, resources):
        return SimpleNamespace(
            provider_source_dir=self.provider,
            provider_schema_json=self.schema,
            provider_source_commit="a" * 40,
            project_source_commit="b" * 40,
            v3_corpus_dir=self.v3,
            output_dir=output,
            resource=resources,
            all_documented_resources=False,
        )

    def test_v4_builds_resource_absent_from_v3_without_source_allowlist(self):
        output = self.workspace / "v4"
        summary = build_v4.build(self.args(output, ["aws_lambda_function"]))
        self.assertEqual("terraformers-reference-v4", summary["corpusVersion"])
        self.assertEqual(1, summary["selectedResourceCount"])
        self.assertEqual(1, summary["newResourceCountComparedWithV3"])

        coverage = json.loads((output / "coverage-report.json").read_text(encoding="utf-8"))
        self.assertEqual(4, coverage["providerSchemaResourceCount"])
        self.assertEqual(3, coverage["officialDocumentationResourceCount"])
        self.assertEqual(1, coverage["v3ProviderResourceCount"])
        self.assertEqual(["aws_lambda_function"], coverage["newResourceTypesComparedWithV3"])

        documents = [
            json.loads(line)
            for line in (output / "documents.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        lambda_documents = [
            document
            for document in documents
            if "aws_lambda_function" in document.get("resourceTypes", [])
        ]
        self.assertTrue(lambda_documents)
        self.assertTrue(
            any(document["documentType"] == "AWS_PROVIDER_DOC" for document in lambda_documents)
        )
        self.assertTrue(
            any(document["documentType"] == "AWS_PROVIDER_SCHEMA" for document in lambda_documents)
        )

        completed = subprocess.run(
            [
                sys.executable,
                str(ROOT / "scripts/checks/rag-corpus-contract-verification.py"),
                "--corpus-dir",
                str(output),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_v4_reports_schema_resource_without_official_doc_as_knowledge_gap(self):
        output = self.workspace / "v4-schema-only"
        build_v4.build(self.args(output, ["aws_schema_only_resource"]))
        coverage = json.loads((output / "coverage-report.json").read_text(encoding="utf-8"))
        self.assertEqual(
            ["aws_schema_only_resource"],
            coverage["selectedMissingOfficialDocumentationResourceTypes"],
        )
        self.assertEqual(0, coverage["selectedOfficialDocumentationResourceCount"])

        documents = [
            json.loads(line)
            for line in (output / "documents.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        target = [
            document
            for document in documents
            if "aws_schema_only_resource" in document.get("resourceTypes", [])
        ]
        self.assertEqual(["AWS_PROVIDER_SCHEMA"], [document["documentType"] for document in target])

    def test_v4_does_not_count_unparsed_official_file_as_indexed_evidence(self):
        output = self.workspace / "v4-unparsed"
        build_v4.build(self.args(output, ["aws_unparsed_resource"]))
        coverage = json.loads((output / "coverage-report.json").read_text(encoding="utf-8"))

        self.assertEqual(1, coverage["selectedOfficialDocumentationResourceCount"])
        self.assertEqual(0, coverage["selectedOfficialEvidenceResourceCount"])
        self.assertEqual([], coverage["selectedMissingOfficialDocumentationResourceTypes"])
        self.assertEqual(
            ["aws_unparsed_resource"],
            coverage["selectedOfficialEvidenceExtractionGapResourceTypes"],
        )

        documents = [
            json.loads(line)
            for line in (output / "documents.jsonl").read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        target = [
            document
            for document in documents
            if "aws_unparsed_resource" in document.get("resourceTypes", [])
        ]
        self.assertEqual(["AWS_PROVIDER_SCHEMA"], [document["documentType"] for document in target])

        report = report_coverage.report(
            SimpleNamespace(
                corpus_dir=output,
                provider_schema_json=self.schema,
                provider_source_dir=self.provider,
                provider_source_commit="a" * 40,
                output=None,
            )
        )
        self.assertEqual(0, report["corpusOfficialEvidenceResourceCount"])
        self.assertEqual(
            ["aws_unparsed_resource"],
            report["corpusOfficialEvidenceExtractionGapResourceTypes"],
        )
        self.assertEqual(1, report["corpusOfficialEvidenceExtractionGapCount"])
        self.assertEqual(0.0, report["corpusCoverageOfOfficialDocumentedResources"])
        self.assertEqual(
            3,
            report["officialDocumentedProviderSchemaResourcesWithoutCorpusEvidenceCount"],
        )

        completed = subprocess.run(
            [
                sys.executable,
                str(ROOT / "scripts/checks/rag-corpus-contract-verification.py"),
                "--corpus-dir",
                str(output),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_v4_all_documented_mode_is_schema_intersection_not_allowlist(self):
        args = self.args(self.workspace / "v4-all", None)
        args.all_documented_resources = True
        provider_schema = build_v4.schema_provider(self.schema)
        self.assertEqual(
            ["aws_lambda_function", "aws_unparsed_resource", "aws_vpc"],
            build_v4.select_resources(args, provider_schema),
        )


if __name__ == "__main__":
    unittest.main()
