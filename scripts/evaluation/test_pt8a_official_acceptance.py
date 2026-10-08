"""Deterministic contracts only: no official image fetch, model, service or cloud execution."""
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import yaml
from contextlib import ExitStack

SPEC = importlib.util.spec_from_file_location("pt8a", Path(__file__).with_name("pt8a-official-acceptance.py"))
pt8a = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pt8a)
SOURCE = "a" * 40
IMAGE = "asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:" + "b" * 64


def ledger():
    return [{"caseId": c, "status": "NOT_RUN", "consumed": False} for c in pt8a.CASES]


def accepted():
    return {"httpStatus": 201, "transportExitCode": 0, "json": {"analysisJobId": "job-1", "projectId": 1,
            "sourceFileId": 2, "binaryPersisted": True, "createdAt": "2026-10-07T14:00:00Z"}}


class InputAndRequestContracts(unittest.TestCase):
    def test_structured_authority_parser_keeps_sha256_and_dimension_keys(self):
        values = pt8a.fields("[HUMAN_GATE_APPROVAL:v1]\nprocedure_sha256: pinned\nvector_dimension: 1536\n",
                             "[HUMAN_GATE_APPROVAL:v1]")
        self.assertEqual({"procedure_sha256": "pinned", "vector_dimension": "1536"}, values)

    def test_readiness_requires_clean_receipt_at_same_source_and_live_approval(self):
        receipt = {"ingestion_mode": pt8a.rag.PT8A_CLEAN_MODE, "reviewed_source_sha": SOURCE,
                   "live_approval_comment_id": 123}
        binding = {"sourceSha": SOURCE, "conclusion": "success"}
        request = {"liveApprovalCommentId": 123}
        pt8a.require_clean_receipt(receipt, binding, request, SOURCE)
        for changed in ({"ingestion_mode": None}, {"ingestion_mode": "ordinary"},
                        {"reviewed_source_sha": "c" * 40}, {"live_approval_comment_id": 124}):
            with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, "MODEL_PROVENANCE_UNPROVEN"):
                pt8a.require_clean_receipt(receipt | changed, binding, request, SOURCE)
        for changed in ({"sourceSha": "c" * 40}, {"conclusion": "failure"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                pt8a.require_clean_receipt(receipt, binding | changed, request, SOURCE)

    def setUp(self):
        # Synthetic IHDR bytes, not a fetched/copied official fixture.
        self.png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 7, 9) + bytes(9)
        self.case = {"imageUrl": "https://docs.aws.amazon.com/synthetic.png", "sha256": pt8a.sha(self.png),
                     "sizeBytes": len(self.png), "width": 7, "height": 9, "mediaType": "image/png"}

    def test_frozen_parser_has_only_acquisition_fields_no_truth(self):
        with patch.object(pt8a.urllib.request, "build_opener", side_effect=AssertionError("no network")):
            inputs = pt8a.frozen_inputs()
        self.assertEqual(pt8a.CASES, tuple(c["caseId"] for c in inputs))
        self.assertEqual({"caseId", "imageUrl", "sha256", "sizeBytes", "mediaType", "width", "height"}, set(inputs[0]))
        self.assertNotIn("truth", json.dumps(inputs))

    def test_exact_hash_size_media_dimensions_fail_closed(self):
        pt8a.verify_image(self.case, self.png, "image/png")
        for changed in ({"sha256": "0" * 64}, {"sizeBytes": len(self.png) + 1}, {"width": 8}, {"height": 8}, {"mediaType": "image/jpeg"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                pt8a.verify_image(self.case | changed, self.png, "image/png")
        with self.assertRaises(ValueError):
            pt8a.verify_image(self.case, self.png, "application/octet-stream")

    def test_host_redirect_and_http_identity_fail_before_upload(self):
        for url in ("http://docs.aws.amazon.com/a", "https://docs.aws.amazon.com.evil.test/a",
                    "https://docs.aws.amazon.com:443/a", "https://user@docs.aws.amazon.com/a"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                pt8a.image_host(self.case | {"imageUrl": url})
        with self.assertRaisesRegex(ValueError, "redirect"):
            pt8a.NoRedirect().redirect_request(None, None, 302, "", {}, "https://docs.aws.amazon.com/replaced")
        class Response:
            status, url = 200, "https://docs.aws.amazon.com/replaced"
            def __enter__(self): return self
            def __exit__(self, *args): pass
        class Opener:
            def open(self, *args, **kwargs): return Response()
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a.urllib.request, "build_opener", return_value=Opener()):
            with self.assertRaisesRegex(ValueError, "HTTP identity"):
                pt8a.acquire(self.case, Path(d))
            self.assertFalse(list(Path(d).iterdir()))

    def test_rerun_truth_payload_unbound_gate_and_main_drift_rejected(self):
        request = {"mode": "readiness", "liveApprovalCommentId": 1, "provenanceRunId": 2, "provenanceArtifactId": 3}
        approval = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
                    "reviewed_source_sha": SOURCE, "backend_image": IMAGE, "candidate_identity": pt8a.IDENTITY,
                    "procedure_sha256": pt8a.sha((pt8a.ROOT / pt8a.PROCEDURE).read_bytes())}
        with patch.object(pt8a, "authority_comment", return_value=approval), patch.object(pt8a.transport, "current_main", return_value=SOURCE):
            pt8a.request_contract(request, SOURCE, IMAGE, "1")
            for value, attempt in ((request, "2"), (request | {"expectedComponents": ["S3"]}, "1")):
                with self.assertRaises(ValueError): pt8a.request_contract(value, SOURCE, IMAGE, attempt)
        with patch.object(pt8a, "authority_comment", return_value=approval | {"procedure_sha256": "wrong"}):
            with self.assertRaisesRegex(ValueError, "live approval"):
                pt8a.request_contract(request, SOURCE, IMAGE, 1)
        with patch.object(pt8a, "authority_comment", return_value=approval), patch.object(pt8a.transport, "current_main", return_value="c" * 40):
            with self.assertRaisesRegex(ValueError, "MAIN_DRIFT"):
                pt8a.request_contract(request, SOURCE, IMAGE, 1)

    def test_real_transport_upload_contains_only_file_and_neutral_name_no_retry(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d); token = directory / "token"; token.write_text("synthetic.token.value")
            fixture = directory / "input.png"; fixture.write_bytes(self.png)
            client = pt8a.transport.CurlClient(token)
            with patch.object(pt8a.transport.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "201", "")) as run:
                client.request("POST", "/api/upload", directory, "upload", fixture, "pt8a-123")
            command = run.call_args.args[0]
            self.assertEqual([f"file=@{fixture};type=image/png", "projectName=pt8a-123"], [command[i+1] for i, v in enumerate(command) if v == "-F"])
            self.assertNotIn("--retry", command)
            self.assertNotIn("truth", " ".join(command))
            client.close(); self.assertFalse(client.header_file.exists())


class OnceOnlyAndReviewContracts(unittest.TestCase):
    def test_accepted_consumes_case_and_second_submission_cannot_start(self):
        rows = ledger(); pt8a.mark_upload(rows[0], accepted())
        self.assertTrue(rows[0]["consumed"])
        with self.assertRaisesRegex(ValueError, "already attempted"):
            pt8a.next_case(rows, pt8a.CASES[0], {})
        self.assertEqual(["NOT_RUN"] * 4, [r["status"] for r in rows[1:]])

    def test_definite_preacceptance_rejection_not_consumed_and_ambiguous_acceptance_never_resubmitted(self):
        for status, exit_code, expected in ((400, 0, "PRE_ACCEPTANCE_REJECTED"), (201, 28, "INDETERMINATE_ACCEPTANCE"),
                                             (503, 0, "INDETERMINATE_ACCEPTANCE"), (202, 0, "INDETERMINATE_ACCEPTANCE")):
            rows = ledger()
            with self.assertRaises(ValueError):
                pt8a.mark_upload(rows[0], accepted() | {"httpStatus": status, "transportExitCode": exit_code})
            self.assertFalse(rows[0]["consumed"])
            self.assertEqual(expected, rows[0]["status"])
            with self.assertRaises(ValueError): pt8a.next_case(rows, pt8a.CASES[0], {})

    def test_malformed_accepted_identity_and_timestamp_are_indeterminate(self):
        for changed in ({"projectId": 0}, {"sourceFileId": None}, {"createdAt": None}, {"createdAt": "bad"}, {"createdAt": "2026-10-07"}):
            rows = ledger()
            with self.assertRaises(ValueError): pt8a.mark_upload(rows[0], accepted() | {"json": accepted()["json"] | changed})
            self.assertEqual("INDETERMINATE_ACCEPTANCE", rows[0]["status"])

    def test_material_failure_or_unreviewed_prior_leaves_later_not_run(self):
        review = {"decision": "ACCEPTED", "material_defect": "false", "false_trusted_success": "0"}
        for status in ("NOT_PASS", "REVIEW_PENDING", "INDETERMINATE_ACCEPTANCE"):
            rows = ledger(); rows[0]["status"] = status
            with self.assertRaisesRegex(ValueError, "prior case"):
                pt8a.next_case(rows, pt8a.CASES[1], review)
            self.assertEqual(["NOT_RUN"] * 4, [r["status"] for r in rows[1:]])
        rows = ledger(); rows[0]["status"] = "PASS"
        pt8a.next_case(rows, pt8a.CASES[1], review)
        with self.assertRaises(ValueError): pt8a.next_case(rows, pt8a.CASES[1], review | {"false_trusted_success": "1"})

    def test_review_must_bind_source_case_artifact_and_all_ten_dimensions(self):
        binding = {"sourceSha": SOURCE, "procedureSha256": "d" * 64}
        review = {"reviewed_source_sha": SOURCE, "procedure_sha256": "d" * 64, "candidate_identity": pt8a.IDENTITY,
                  "run_id": "1", "artifact_id": "2", "artifact_digest": "sha256:x", "case_id": pt8a.CASES[0],
                  **{"dimension_" + d: "PASS" for d in pt8a.SCORING}}
        pt8a.reviewed_prior(review, binding, 1, 2, "sha256:x", pt8a.CASES[0])
        for changed in ({"case_id": pt8a.CASES[1]}, {"artifact_digest": "wrong"}, {"reviewed_source_sha": "wrong"},
                        {"dimension_directed_relationships": "NOT_PASS"}, {"dimension_official_evidence_closure": "UNPROVEN"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                pt8a.reviewed_prior(review | changed, binding, 1, 2, "sha256:x", pt8a.CASES[0])

    def history(self, request, conclusion="success", artifact=True, status="completed", attempt=1):
        def response(path):
            if "/runs?" in path: return {"workflow_runs": [{"id": 100, "head_sha": SOURCE, "status": status, "run_attempt": attempt}]}
            if "/jobs?" in path: return {"total_count": 1, "jobs": [{"name": "pt8a-official-acceptance", "conclusion": conclusion}]}
            if "/artifacts?" in path: return {"total_count": int(artifact), "artifacts": [{"id": 200, "name": "pt8a-official-100"}] if artifact else []}
            raise AssertionError(path)
        with patch.object(pt8a, "github", side_effect=response):
            pt8a.ensure_latest_dispatch(request, SOURCE, 101)

    def test_latest_history_prevents_old_readiness_reset_and_missing_evidence_resubmission(self):
        request = {"mode": "case", "priorRunId": 100, "priorArtifactId": 200}
        self.history(request)
        for changed in ({"priorRunId": 99}, {"priorArtifactId": 199}, {"mode": "readiness"}):
            with self.assertRaises(ValueError): self.history(request | changed)
        for kwargs in ({"artifact": False}, {"status": "in_progress"}, {"attempt": 2}):
            with self.assertRaises(ValueError): self.history(request, **kwargs)
        self.history({"mode": "readiness"}, conclusion="skipped")
        with self.assertRaises(ValueError): self.history(request, conclusion="skipped")


class RiskQualifiedCorrectiveContracts(unittest.TestCase):
    def setUp(self):
        self.request = {"mode": pt8a.CORRECTIVE_MODE, "liveApprovalCommentId": 123,
                        "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN, "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT}
        self.authority = pt8a.corrective_live_fields(SOURCE, IMAGE)
        self.decision = {"decision": "APPROVED_REPOSITORY_ONLY_IMPLEMENTATION",
                         "approved_main_sha": "9ef9d7dadbd4dcc3089c27e9fe58d6a7e2bdf447",
                         "accepted_residual_risk": "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED",
                         "once_only": "ONE_DISTINCT_CORRECTIVE_READINESS_ATTEMPT_ACROSS_SOURCES"}
        root = pt8a.ROOT / "docs/evidence/product-trust-pt-8a/corrections/repair-budget-and-recovery-1/original-artifact-members"
        self.clean = {p: json.loads((root / "clean-v4" / p).read_text())
                      for p in ("receipt.json", "clean-run-binding.json", "contract.json")}
        self.clean_binding = {"runId": pt8a.ORIGIN_CLEAN_RUN, "artifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
                              "digest": pt8a.ORIGIN_CLEAN_DIGEST, "sourceSha": pt8a.ORIGIN_SOURCE, "conclusion": "success"}
        self.failed = {p: json.loads((root / "readiness-failure" / p).read_text())
                       for p in ("observation/accepted.json", "observation/job.json")}
        self.failed.update({"ledger.json": [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False}
                                           for c in pt8a.CASES],
                           "binding.json": {"sourceSha": pt8a.ORIGIN_SOURCE, "image": pt8a.ORIGIN_IMAGE,
                               "candidateIdentity": pt8a.IDENTITY, "procedureSha256": pt8a.V2_SHA256,
                               "mode": "readiness", "liveApprovalCommentId": pt8a.ORIGIN_APPROVAL,
                               "runId": pt8a.ORIGIN_FAILED_RUN}})
        self.failed_binding = {"runId": pt8a.ORIGIN_FAILED_RUN, "artifactId": pt8a.ORIGIN_FAILED_ARTIFACT,
                               "digest": pt8a.ORIGIN_FAILED_DIGEST, "sourceSha": pt8a.ORIGIN_SOURCE, "conclusion": "failure"}
        self.origin_approval = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
            "reviewed_source_sha": pt8a.ORIGIN_SOURCE, "backend_image": pt8a.ORIGIN_IMAGE,
            "candidate_identity": pt8a.IDENTITY, "procedure_sha256": pt8a.V2_SHA256,
            "purpose": "PT8A_CLEAN_V4_REEMBED_ALL_5395", "corpus_checksum": pt8a.CORPUS_CHECKSUM,
            "provider_source": pt8a.rag.PT8A_PROVIDER_SOURCE, "project_decision_source": pt8a.CORPUS_SOURCE,
            "embedding_model": "gemini-embedding-2", "vector_dimension": "1536"}

    def authority_reader(self, comment_id, marker):
        if comment_id == pt8a.RECOVERY_DECISION: return self.decision
        if comment_id == pt8a.ORIGIN_APPROVAL: return self.origin_approval
        return self.authority

    def test_separate_exact_live_authority_and_explicit_risk_required_no_official_case_payload(self):
        with patch.object(pt8a, "authority_comment", side_effect=self.authority_reader), \
             patch.object(pt8a.transport, "current_main", return_value=SOURCE):
            self.assertEqual(self.authority, pt8a.corrective_request_contract(self.request, SOURCE, IMAGE, 1))
            for field in ("accepted_residual_risk", "vector_write_continuity", "purpose", "procedure_sha256",
                          "frozen_v2_procedure_sha256", "original_clean_source_sha", "original_clean_artifact_digest",
                          "failed_readiness_artifact_digest", "once_only", "backend_image", "index_uuid"):
                with self.subTest(field=field):
                    saved = self.authority.pop(field)
                    try:
                        with self.assertRaises(ValueError): pt8a.corrective_request_contract(self.request, SOURCE, IMAGE, 1)
                    finally: self.authority[field] = saved
            for request, attempt in ((self.request | {"caseId": pt8a.CASES[0]}, 1),
                                     (self.request | {"mode": "case"}, 1), (self.request, 2),
                                     (self.request | {"provenanceArtifactId": 1}, 1)):
                with self.assertRaises(ValueError): pt8a.corrective_request_contract(request, SOURCE, IMAGE, attempt)
            # The amended approval does not satisfy strict v2 or grant case A access.
            with self.assertRaisesRegex(ValueError, "live approval"):
                pt8a.request_contract(self.request | {"mode": "case", "caseId": pt8a.CASES[0],
                    "priorRunId": 1, "priorArtifactId": 2, "priorReviewCommentId": 3}, SOURCE, IMAGE, 1)
        with patch.object(pt8a, "authority_comment", side_effect=ValueError("missing live approval")):
            with self.assertRaises(ValueError): pt8a.corrective_request_contract(self.request, SOURCE, IMAGE, 1)

    def origins(self):
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, "authority_comment", side_effect=self.authority_reader), \
             patch.object(pt8a, "bound_artifact", side_effect=[(self.clean, self.clean_binding), (self.failed, self.failed_binding)]) as download:
            result = pt8a.corrective_origins(Path(d))
            self.assertTrue(download.call_args_list[1].kwargs["verify_inventory"])
            return result

    def test_original_receipt_and_consumed_failure_bindings_preserved_relabel_or_tamper_rejected(self):
        receipt, binding, failed = self.origins()
        self.assertEqual(pt8a.ORIGIN_SOURCE, receipt["reviewed_source_sha"])
        self.assertEqual(pt8a.ORIGIN_CLEAN_DIGEST, binding["digest"])
        self.assertEqual("failure", failed["conclusion"])
        targets = [(self.clean["receipt.json"], "reviewed_source_sha", SOURCE),
                   (self.clean_binding, "sourceSha", SOURCE), (self.clean_binding, "digest", "sha256:wrong"),
                   (self.origin_approval, "reviewed_source_sha", SOURCE),
                   (self.failed["observation/accepted.json"], "consumed", False),
                   (self.failed["observation/accepted.json"], "jobId", "another-job"),
                   (self.failed["observation/job.json"], "status", "SUCCEEDED"),
                   (self.failed_binding, "digest", "sha256:wrong"),
                   (self.failed["binding.json"], "sourceSha", SOURCE),
                   (self.failed["ledger.json"][0], "status", "PASS")]
        for target, field, changed in targets:
            with self.subTest(field=field):
                saved = target[field]; target[field] = changed
                try:
                    with self.assertRaises(ValueError): self.origins()
                finally: target[field] = saved

    def test_qualified_read_only_admission_is_not_exact_vector_continuity(self):
        snapshot = {"classification": "EXACT_REUSABLE_COMPLETED_V4", "indexUuid": pt8a.ORIGIN_UUID,
                    "liveContentIdentity": pt8a.ORIGIN_CONTENT, "vectorDocumentsChecked": 5395, "embeddingRequests": 0}
        with patch.object(pt8a.rag, "verify_exact_v4", return_value=snapshot) as verify:
            result = pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                self.clean["receipt.json"], self.clean_binding, self.authority)
            self.assertTrue(verify.call_args.kwargs["inspect_vectors"])
            self.assertEqual(pt8a.ORIGIN_SOURCE, verify.call_args.args[-1]["sourceSha"])
            self.assertEqual("RISK_QUALIFIED_RETAINED_V4", result["classification"])
            self.assertFalse(result["cryptographicVectorContinuityProven"])
            self.assertEqual("VECTOR_WRITE_CONTINUITY_UNPROVEN", result["vectorWriteContinuity"])
            self.assertEqual(0, result["indexWrites"])
            for changed in ({"classification": "PARTIAL_INDEX"}, {"classification": "STALE_OR_MIXED_MODEL_SPACE"},
                            {"classification": "WRONG_MODEL_OR_DIMENSION"}, {"indexUuid": "replacement"},
                            {"liveContentIdentity": "changed"}, {"vectorDocumentsChecked": 5394}):
                verify.return_value = snapshot | changed
                with self.assertRaises(ValueError):
                    pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                        self.clean["receipt.json"], self.clean_binding, self.authority)
        for authority in (None, {}, self.authority | {"accepted_residual_risk": "NOT_APPROVED"}):
            with patch.object(pt8a.rag, "verify_exact_v4") as verify, self.assertRaises(ValueError):
                pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                    self.clean["receipt.json"], self.clean_binding, authority)
            verify.assert_not_called()
        with self.assertRaises(ValueError):
            pt8a.risk_qualified_admission(None, {}, {}, [], "wrong-checksum",
                self.clean["receipt.json"], self.clean_binding, self.authority)

    def test_history_does_not_reset_with_source_and_incomplete_or_known_writer_history_stops(self):
        current_id = pt8a.ORIGIN_FAILED_RUN + 1000
        runtime = [{"id": current_id, "head_sha": SOURCE, "run_attempt": 1},
                   {"id": pt8a.ORIGIN_FAILED_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        clean = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        def api(path):
            if "gcp-target-runtime-dependencies.yml/runs?" in path:
                self.assertNotIn("head_sha=", path)
                return {"total_count": len(runtime), "workflow_runs": runtime}
            if "gcp-target-corpus-ingestion.yml/runs?" in path:
                return {"total_count": len(clean), "workflow_runs": clean}
            return {"total_count": 1, "jobs": [{"name": pt8a.CORRECTIVE_OPERATION, "conclusion": "failure"}]}
        with patch.object(pt8a, "github", side_effect=api):
            self.assertFalse(pt8a.corrective_history(SOURCE, current_id)["completeAllWriterAuditAvailable"])
            runtime.append({"id": current_id - 1, "head_sha": "c" * 40})
            with self.assertRaisesRegex(ValueError, "across sources"): pt8a.corrective_history(SOURCE, current_id)
            runtime.pop()
            clean.append({"id": pt8a.ORIGIN_CLEAN_RUN + 1, "head_sha": "c" * 40})
            with self.assertRaisesRegex(ValueError, "writer-history"): pt8a.corrective_history(SOURCE, current_id)
            clean.pop()
        with patch.object(pt8a, "github", return_value={"total_count": 2, "workflow_runs": runtime[:1]}):
            with self.assertRaisesRegex(ValueError, "incomplete"): pt8a.corrective_history(SOURCE, current_id)
        with patch.object(pt8a, "github", return_value={"total_count": 1000,
                "workflow_runs": [{"id": i} for i in range(100)]}):
            with self.assertRaisesRegex(ValueError, "bound exhausted"): pt8a.complete_dispatch_history("workflow")
        runtime.append({"id": current_id - 1, "head_sha": "c" * 40})
        def incomplete_jobs(path):
            if "/jobs?" in path: return {"total_count": 2, "jobs": []}
            return api(path)
        with patch.object(pt8a, "github", side_effect=incomplete_jobs):
            with self.assertRaisesRegex(ValueError, "incomplete"): pt8a.corrective_history(SOURCE, current_id)

    def test_runtime_release_source_digest_model_index_readback_must_match(self):
        release = {"sourceSha": SOURCE, "image": IMAGE, "sourceTagDigest": IMAGE.split("@")[1],
            "readyReplicas": 1, "noRuntimeConfigurationMutation": True,
            "deployedRuntimeIdentity": SOURCE + "|terraformers-reference-v4|terraformers-reference-v4|gemini-embedding-2|1536|1536"
                "|https://identity.example.test/case-c|http://terraformers-jwks:8080/jwks.json|vertex|vertex|REQUIRED"
                "|gemini-3.8-flash|case-c-runtime-client|gcs|gcs|http://terraformers-opensearch:9200|5.100.0"}
        pt8a.corrective_release(release, SOURCE, IMAGE)
        for field in release:
            with self.subTest(field=field), self.assertRaises(ValueError):
                pt8a.corrective_release(release | {field: "wrong"}, SOURCE, IMAGE)

    def test_corrective_cli_preflight_rejects_case_or_wrong_operation_before_any_cloud_or_upload(self):
        with tempfile.TemporaryDirectory() as d:
            request_file = Path(d) / "request.json"
            env = {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE, "GITHUB_RUN_ATTEMPT": "1",
                   "GITHUB_RUN_ID": str(pt8a.ORIGIN_FAILED_RUN + 1000), "OPERATION": pt8a.CORRECTIVE_OPERATION}
            for request, operation in ((self.request | {"mode": "case", "caseId": pt8a.CASES[0]}, pt8a.CORRECTIVE_OPERATION),
                                       (self.request, "pt8a-official-acceptance")):
                request_file.write_text(json.dumps(request))
                with patch.dict(os.environ, env | {"OPERATION": operation}), \
                     patch.object(sys, "argv", ["pt8a", "preflight", "--request-file", str(request_file)]), \
                     patch.object(pt8a, "github") as gh, patch.object(pt8a.transport, "CurlClient") as client:
                    with self.assertRaisesRegex(ValueError, "operation"): pt8a.main()
                    gh.assert_not_called(); client.assert_not_called()
            request_file.write_text(json.dumps(self.request))
            with patch.dict(os.environ, env), patch.object(sys, "argv", ["pt8a", "preflight", "--request-file", str(request_file)]), \
                 patch.object(pt8a, "authority_comment", side_effect=self.authority_reader), \
                 patch.object(pt8a.transport, "current_main", return_value=SOURCE), \
                 patch.object(pt8a, "corrective_history", return_value={}), \
                 patch.object(pt8a, "corrective_origins", side_effect=ValueError("original failure missing")), \
                 patch.object(pt8a.transport, "CurlClient") as client:
                with self.assertRaisesRegex(ValueError, "original failure missing"): pt8a.main()
                client.assert_not_called()

    def test_corrective_cli_runs_only_one_existing_observation_and_keeps_official_ledger_not_run(self):
        for status in ("REVIEW_PENDING", "NOT_PASS"):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as d, ExitStack() as stack:
                root = Path(d); out = root / "evidence"; request_file = root / "request.json"
                request_file.write_text(json.dumps(self.request)); (root / "coverage-report.json").write_text("{}")
                (root / "pt8a-release.json").write_text("{}")
                env = {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE, "GITHUB_RUN_ATTEMPT": "1",
                    "GITHUB_RUN_ID": str(pt8a.ORIGIN_FAILED_RUN + 1000), "OPERATION": pt8a.CORRECTIVE_OPERATION,
                    "RUNNER_TEMP": d}
                stack.enter_context(patch.dict(os.environ, env))
                stack.enter_context(patch.object(sys, "argv", ["pt8a", "run", "--request-file", str(request_file),
                    "--corpus", d, "--output", str(out)]))
                stack.enter_context(patch.object(pt8a, "authority_comment", side_effect=self.authority_reader))
                stack.enter_context(patch.object(pt8a.transport, "current_main", return_value=SOURCE))
                history = stack.enter_context(patch.object(pt8a, "corrective_history", return_value={"completeAllWriterAuditAvailable": False}))
                stack.enter_context(patch.object(pt8a, "corrective_origins", return_value=(
                    self.clean["receipt.json"], self.clean_binding, self.failed_binding)))
                stack.enter_context(patch.object(pt8a, "corrective_release"))
                stack.enter_context(patch.object(pt8a.rag, "load_corpus", return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
                stack.enter_context(patch.object(pt8a.rag, "validate_pt8a_clean_corpus"))
                scan = stack.enter_context(patch.object(pt8a, "risk_qualified_admission", return_value={
                    "classification": "RISK_QUALIFIED_RETAINED_V4", "cryptographicVectorContinuityProven": False}))
                stack.enter_context(patch.object(pt8a.subprocess, "run"))
                stack.enter_context(patch.object(pt8a.transport, "CurlClient"))
                fetch = stack.enter_context(patch.object(pt8a, "acquire", side_effect=AssertionError("no official inputs")))
                def observation(client, fixture, directory, record, documents, pod):
                    self.assertEqual("input.png", fixture.name)
                    self.assertEqual((pt8a.ROOT / pt8a.READINESS_FIXTURE).read_bytes(), fixture.read_bytes())
                    record.update(status=status, consumed=True, uploadAttempts=1, jobId="new-corrective-job")
                observe = stack.enter_context(patch.object(pt8a, "observe", side_effect=observation))
                if status == "NOT_PASS":
                    with self.assertRaisesRegex(ValueError, "material technical/product failure"): pt8a.main()
                else:
                    pt8a.main()
                observe.assert_called_once(); fetch.assert_not_called()
                self.assertEqual(2 if status == "REVIEW_PENDING" else 1, scan.call_count)
                self.assertEqual(3 if status == "REVIEW_PENDING" else 2, history.call_count)
                self.assertEqual(["NOT_RUN"] * 5, [r["status"] for r in json.loads((out / "ledger.json").read_text())])
                self.assertEqual(pt8a.CORRECTIVE_MODE, json.loads((out / "binding.json").read_text())["mode"])
                self.assertEqual(pt8a.sha((pt8a.ROOT / pt8a.CORRECTIVE_PROCEDURE).read_bytes()),
                                 json.loads((out / "binding.json").read_text())["procedureSha256"])
                if status == "NOT_PASS": self.assertTrue((out / "error.json").is_file())


class ArtifactAndObservationContracts(unittest.TestCase):
    def test_original_selected_inventory_hashes_must_match_even_a_valid_archive_digest(self):
        for altered in (False, True):
            data = b'{"status":"ACCEPTED","consumed":true}'
            inventory = {"files": [{"path": "accepted.json", "sha256": "wrong" if altered else pt8a.sha(data),
                                    "sizeBytes": len(data)}]}
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w") as zipped:
                zipped.writestr("accepted.json", data); zipped.writestr("inventory.json", json.dumps(inventory))
            archive = buf.getvalue()
            run = {"event": "workflow_dispatch", "head_branch": "main", "run_attempt": 1,
                "path": ".github/workflows/gcp-target-runtime-dependencies.yml", "status": "completed",
                "head_sha": pt8a.ORIGIN_SOURCE, "conclusion": "failure"}
            artifact = {"expired": False, "digest": "sha256:" + pt8a.sha(archive),
                        "workflow_run": {"id": 1, "head_sha": pt8a.ORIGIN_SOURCE}}
            def download(command, stdout, check): stdout.write(archive)
            with tempfile.TemporaryDirectory() as d, patch.object(pt8a.subprocess, "run", side_effect=download), \
                 patch.object(pt8a, "github", side_effect=[run, artifact]):
                if altered:
                    with self.assertRaisesRegex(ValueError, "inventory/member hash"):
                        pt8a.bound_artifact(1, 2, ["inventory.json", "accepted.json"], Path(d), run["path"], verify_inventory=True)
                else:
                    result, binding = pt8a.bound_artifact(1, 2, ["inventory.json", "accepted.json"], Path(d), run["path"], verify_inventory=True)
                    self.assertTrue(result["accepted.json"]["consumed"])

    def test_safe_provider_and_closure_metadata_retained_without_payload_or_invented_usage(self):
        raw = {"lines": [
            "Vertex provider call stage=facts outcome=received finishReason=STOP outputTokens=400 thinkingTokens=20 totalTokens=420 maxOutputTokens=800",
            "Vertex provider call stage=initial_generation compact=false outcome=received finishReason=MAX_TOKENS outputTokens=8000 thinkingTokens=192 totalTokens=8192 maxOutputTokens=8192",
            "Vertex provider call stage=initial_generation compact=true outcome=received finishReason=STOP outputTokens=7000 thinkingTokens=100 totalTokens=7100 maxOutputTokens=8192",
            "Vertex grounding stage=closure outcome=success finishReason=NOT_APPLICABLE outputTokens=NOT_APPLICABLE hitCount=7 elapsedMs=807",
            "Vertex provider call stage=repair compact=false outcome=received finishReason=MAX_TOKENS outputTokens=15000 thinkingTokens=1384 totalTokens=16384 maxOutputTokens=16384",
            "Vertex provider call stage=repair compact=false outcome=failure finishReason=UNAVAILABLE outputTokens=unknown thinkingTokens=unknown totalTokens=unknown errorClass=AnalysisProviderFailureException secret=DO_NOT_PUBLISH",
            "Vertex grounding stage=closure outcome=failure finishReason=NOT_APPLICABLE outputTokens=NOT_APPLICABLE errorClass=RuntimeException raw=DO_NOT_PUBLISH",
            "prompt=DO_NOT_PUBLISH model response=DO_NOT_PUBLISH",
        ]}
        with patch.object(pt8a.transport, "collect_logs", return_value=raw):
            result = pt8a.retrieval_evidence("job", "time", [])
        self.assertEqual(["facts", "initial_generation", "initial_generation", "repair", "repair"],
                         [call["stage"] for call in result["providerCalls"]])
        self.assertEqual([None, False, True, False, False], [call["compact"] for call in result["providerCalls"]])
        self.assertEqual(15000, result["providerCalls"][3]["outputTokens"])
        self.assertEqual(1384, result["providerCalls"][3]["thinkingTokens"])
        self.assertEqual(16384, result["providerCalls"][3]["totalTokens"])
        self.assertEqual("MAX_TOKENS", result["providerCalls"][3]["finishReason"])
        self.assertIsNone(result["providerCalls"][4]["outputTokens"])
        self.assertEqual([{"stage": "closure", "outcome": "success", "hitCount": 7, "elapsedMs": 807},
                          {"stage": "closure", "outcome": "failure", "hitCount": None, "elapsedMs": None}],
                         result["groundingStages"])
        self.assertEqual(0, result["officialHitCount"])
        self.assertNotIn("DO_NOT_PUBLISH", json.dumps(result))

    def test_observed_uppercase_vertex_initial_and_closure_logs_keep_exact_identity_checks(self):
        fixture = json.loads((pt8a.ROOT / "docs/evidence/product-trust-pt-8a/corrections/repair-budget-and-recovery-1/retrieval-log-fixture.json").read_text())
        ids = [value.strip() for line in fixture["lines"]
               for value in re.search(r"documentIds=\[([^\]]*)\]", line)[1].split(",")]
        # Synthetic catalog metadata for the actual observed IDs. This tests parser and
        # provenance accounting; it does not substitute for a live exact corpus snapshot.
        documents = [{"documentId": doc_id, "authority": "PROVIDER_DOCUMENTATION",
                      "documentType": "AWS_PROVIDER_EXAMPLE" if "-example-" in doc_id else "AWS_PROVIDER_DOC",
                      "sourceCommit": "f7a3b98da589ab1d52756b0dcee0dbf2de83d635"} for doc_id in ids]
        raw = {"lines": fixture["lines"]}
        with patch.object(pt8a.transport, "collect_logs", return_value=raw) as collect:
            evidence = pt8a.retrieval_evidence(fixture["jobId"], "2026-10-08T07:40:26.993958Z", documents)
        collect.assert_called_once_with(fixture["jobId"], "2026-10-08T07:40:26.993958Z")
        self.assertEqual([8, 7], [event["hitCount"] for event in evidence["events"]])
        self.assertEqual([1811, 807], [event["elapsedMs"] for event in evidence["events"]])
        self.assertEqual(15, evidence["officialHitCount"])
        self.assertEqual(ids, [doc["documentId"] for event in evidence["events"] for doc in event["documents"]])
        self.assertNotIn("lines", evidence)
        for replacement in ("index=wrong", "hitCount=9", "documentIds=[unknown-id,"):
            changed = [fixture["lines"][0].replace("index=terraformers-reference-v4", replacement)
                       if replacement.startswith("index") else fixture["lines"][0].replace(
                           "hitCount=8" if replacement.startswith("hitCount") else "documentIds=[", replacement)]
            with self.subTest(replacement=replacement), patch.object(pt8a.transport, "collect_logs", return_value={"lines": changed}), self.assertRaises(ValueError):
                pt8a.retrieval_evidence(fixture["jobId"], "time", documents)
        for raw in ({"lines": []}, {"lines": fixture["lines"]}):
            wrong_source = [doc | {"sourceCommit": "unproven"} for doc in documents]
            with patch.object(pt8a.transport, "collect_logs", return_value=raw):
                self.assertEqual(0, pt8a.retrieval_evidence(fixture["jobId"], "time", wrong_source)["officialHitCount"])

    def test_actual_readiness_shell_blocks_share_canonical_corpus_path(self):
        workflow = yaml.safe_load((pt8a.ROOT / ".github/workflows/gcp-target-runtime-dependencies.yml").read_text())
        steps = workflow["jobs"]["pt8a-official-acceptance"]["steps"]
        rebuild = next(s["run"] for s in steps if s.get("name") == "Rebuild expected corpus from pinned authority without embedding")
        observe = next(s["run"] for s in steps if s.get("name") == "Observe read-only exact readiness and at most one accepted product job")
        real_bash = shutil.which("bash")
        # Execute the extracted workflow, replacing external commands only. No network,
        # cloud, JWKS or observer operation occurs; argument/path propagation is real Bash.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); runner = root / "runner temp"; runner.mkdir()
            bins = root / "bin"; bins.mkdir(); calls = root / "calls.jsonl"; github_env = root / "github.env"
            stub = "#!" + sys.executable + "\n" + '''import json, os, sys
from pathlib import Path
name = Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ["PT8A_TEST_CALLS"], "a") as stream:
    stream.write(json.dumps([name, args]) + "\\n")
def option(flag): return Path(args[args.index(flag) + 1])
if name == "git" and "rev-parse" in args:
    print("f7a3b98da589ab1d52756b0dcee0dbf2de83d635")
elif name == "python3":
    if args[0] == "scripts/rag/build-corpus-v4.py":
        output = option("--output-dir"); output.mkdir(parents=True)
        (output / "coverage-report.json").write_text("{}")
    elif args[0] == "scripts/checks/rag-corpus-contract-verification.py":
        assert option("--corpus-dir").is_dir()
        option("--summary-json").write_text("{}")
    elif args[0] == "scripts/evaluation/pt8a-official-acceptance.py":
        assert option("--corpus").is_dir()
    else: raise AssertionError(args)
elif name == "jq":
    json.loads(Path(args[-1]).read_text())
'''
            for command in ("git", "kubectl", "python3", "jq", "curl", "bash"):
                path = bins / command; path.write_text(stub); path.chmod(0o755)
            env = os.environ | {"PATH": str(bins) + os.pathsep + os.environ["PATH"], "RUNNER_TEMP": str(runner),
                                "GITHUB_ENV": str(github_env), "NAMESPACE": "unused", "PT8A_TEST_CALLS": str(calls)}
            for block in (rebuild, observe):
                subprocess.run([real_bash, "-n"], input=block, text=True, check=True, capture_output=True)
            subprocess.run([real_bash, "-c", rebuild], env=env, check=True, capture_output=True, text=True)
            env.update(line.split("=", 1) for line in github_env.read_text().splitlines())
            subprocess.run([real_bash, "-c", observe], env=env, check=True, capture_output=True, text=True)
            recorded = [json.loads(line) for line in calls.read_text().splitlines()]
            paths = []
            for command, args in recorded:
                for flag in ("--output-dir", "--corpus-dir", "--corpus"):
                    if command == "python3" and flag in args: paths.append(Path(args[args.index(flag) + 1]))
            coverage = [Path(args[-1]).parent for command, args in recorded
                        if command == "jq" and Path(args[-1]).name == "coverage-report.json"]
            self.assertEqual([runner / "v4"] * 3, paths)
            self.assertEqual([runner / "v4"], coverage)
            self.assertEqual(str(runner / "v4"), env["PT8A_EXPECTED_CORPUS_DIR"])
    def test_artifact_bound_to_exact_run_source_workflow_and_archive_digest(self):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z: z.writestr("receipt.json", '{"outcome":"ingested"}')
        archive = buf.getvalue()
        run = {"event": "workflow_dispatch", "head_branch": "main", "run_attempt": 1,
               "path": ".github/workflows/gcp-target-corpus-ingestion.yml", "status": "completed", "head_sha": SOURCE, "conclusion": "success"}
        artifact = {"expired": False, "digest": "sha256:" + pt8a.sha(archive), "workflow_run": {"id": 1, "head_sha": SOURCE}}
        def download(command, stdout, check): stdout.write(archive)
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a.subprocess, "run", side_effect=download):
            with patch.object(pt8a, "github", side_effect=[run, artifact]):
                result, binding = pt8a.bound_artifact(1, 2, ["receipt.json"], Path(d), run["path"])
            self.assertEqual("ingested", result["receipt.json"]["outcome"])
            self.assertEqual(artifact["digest"], binding["digest"])
            for changed in ({"digest": "sha256:" + "0" * 64}, {"expired": True}, {"workflow_run": {"id": 9, "head_sha": SOURCE}}):
                with patch.object(pt8a, "github", side_effect=[run, artifact | changed]), self.assertRaises(ValueError):
                    pt8a.bound_artifact(1, 2, ["receipt.json"], Path(d), run["path"])
            with patch.object(pt8a, "github", side_effect=[run | {"run_attempt": 2}, artifact]), self.assertRaises(ValueError):
                pt8a.bound_artifact(1, 2, ["receipt.json"], Path(d), run["path"])

    def test_failed_job_observation_uses_one_upload_preserves_failure_and_no_later_cases(self):
        class Client:
            def __init__(self): self.calls = []
            def request(inner, method, path, *args, **kwargs):
                inner.calls.append((method, path))
                if method == "POST": return accepted()
                if path == "/api/projects/1": return {"httpStatus": 200, "json": {"projectId": 1, "latestAnalysisJobId": "job-1", "analysisStatus": "FAILED"}}
                return {"httpStatus": 200, "json": {"id": "job-1", "projectId": 1, "status": "FAILED",
                        "failureReason": "PROVIDER_TIMEOUT", "timing": {"terminalAt": "2026-10-07T14:08:00Z", "acceptedToTerminalMs": 480000}}}
        with tempfile.TemporaryDirectory() as d, patch.dict(os.environ, {"GITHUB_RUN_ID": "1"}), patch.object(pt8a, "retrieval_evidence", return_value={"events": []}):
            rows = ledger(); client = Client(); private = Path(d); fixture = private / "input.png"
            pt8a.observe(client, fixture, private / "observation", rows[0], [], "unused-pod")
            self.assertEqual(1, len([c for c in client.calls if c[0] == "POST"]))
            self.assertTrue(rows[0]["consumed"]); self.assertEqual("NOT_PASS", rows[0]["status"])
            self.assertEqual(["NOT_RUN"] * 4, [r["status"] for r in rows[1:]])
            self.assertEqual("PROVIDER_TIMEOUT", json.loads((private / "observation/job.json").read_text())["failureReason"])
            self.assertEqual("FAILED", json.loads((private / "observation/presentation.json").read_text())["analysisStatus"])

    def test_censored_observation_not_replaced_with_later_terminal_or_retry(self):
        class Client:
            def __init__(self): self.calls = []
            def request(inner, method, path, *args, **kwargs):
                inner.calls.append(method)
                return accepted() if method == "POST" else {"httpStatus": 200, "json": {"id": "job-1", "projectId": 1, "status": "RUNNING"}}
        ticks = iter([0, 0, 541, 541])
        with tempfile.TemporaryDirectory() as d, patch.dict(os.environ, {"GITHUB_RUN_ID": "1"}):
            record = ledger()[0]; client = Client()
            pt8a.observe(client, Path(d) / "input.png", Path(d) / "observation", record, [], "unused", clock=lambda: next(ticks), wait=lambda _: None)
            self.assertEqual(["POST", "GET"], client.calls)
            self.assertEqual("TERMINAL_NOT_OBSERVED", record["observation"])
            self.assertEqual(541000, record["censoredObservationMs"])
            self.assertNotIn("acceptedToTerminalMs", record)

    def test_correlated_retrieval_provenance_and_stage_limits_no_raw_logs(self):
        doc = {"documentId": "doc-1", "authority": "PROVIDER_DOCUMENTATION", "documentType": "AWS_PROVIDER_DOC",
               "sourcePath": "website/docs/r/vpc.html.markdown", "sourceCommit": "f7a3b98da589ab1d52756b0dcee0dbf2de83d635"}
        raw = {"lines": ["reference retrieval outcome=success mode=REQUIRED embeddingProvider=vertex index=terraformers-reference-v4 topK=8 hitCount=1 documentIds=[doc-1] elapsedMs=12",
                         "analysis stage outcome=success stage=generation elapsedMs=200"]}
        with patch.object(pt8a.transport, "collect_logs", return_value=raw):
            evidence = pt8a.retrieval_evidence("job-1", "time", [doc])
        self.assertEqual("doc-1", evidence["events"][0]["documents"][0]["documentId"])
        self.assertEqual(1, evidence["officialHitCount"])
        with patch.object(pt8a.transport, "collect_logs", return_value=raw):
            non_official = pt8a.retrieval_evidence("job-1", "time", [doc | {"authority": "PROJECT_DECISION"}])
        self.assertEqual(0, non_official["officialHitCount"])
        self.assertEqual("NOT_EXPOSED_BY_CURRENT_RUNTIME", evidence["rawVisionFacts"])
        self.assertNotIn("lines", evidence)
        with patch.object(pt8a.transport, "collect_logs", return_value={"lines": [raw["lines"][0].replace("doc-1", "wrong-id")]}), self.assertRaises(ValueError):
            pt8a.retrieval_evidence("job-1", "time", [doc])

    def test_real_cli_commands_no_stub_no_plan_apply_and_secret_hcl_rejected(self):
        hcl = 'variable "account_id" { type = string }\n'
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a.subprocess, "run", side_effect=[
                subprocess.CompletedProcess([], 0), subprocess.CompletedProcess([], 0, "init OK", ""),
                subprocess.CompletedProcess([], 0, "valid", "")]) as run:
            result = pt8a.validate_draft(hcl, Path(d), "owned-pod")
            calls = [c.args[0] for c in run.call_args_list]
            self.assertIn("terraform", calls[1]); self.assertIn("init", calls[1]); self.assertIn("validate", calls[2])
            self.assertIn("-plugin-dir=/opt/terraform-plugins", calls[1])
            self.assertNotIn("apply", str(calls)); self.assertNotIn("plan", str(calls))
            self.assertEqual(0, result["initValidateExitCode"])
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a.subprocess, "run") as run:
            with self.assertRaises(ValueError): pt8a.validate_draft('password = "actual-secret"', Path(d), "unused")
            run.assert_not_called(); self.assertFalse(list(Path(d).iterdir()))

    def test_artifact_redaction_inventory_and_workflow_gate_contract(self):
        with tempfile.TemporaryDirectory() as d:
            output = Path(d); pt8a.write(output / "job.json", {"failureReason": "eyJtoken.part.signature"})
            pt8a.seal(output); inventory = json.loads((output / "inventory.json").read_text())
            self.assertNotIn("eyJtoken.part.signature", (output / "job.json").read_text())
            self.assertEqual(pt8a.sha((output / "job.json").read_bytes()), inventory["files"][0]["sha256"])
            self.assertFalse(inventory["officialImageBytes"])
        workflow = (pt8a.ROOT / ".github/workflows/gcp-target-runtime-dependencies.yml").read_text()
        job = workflow[workflow.index("  pt8a-official-acceptance:\n"):]
        for marker in ("github.ref == 'refs/heads/main'", "environment: gcp-target-apply", "RUN_REVIEWED_PT8A_OFFICIAL_ACCEPTANCE_V1",
                       "ephemeral-jwks-fixture.sh restore", "GITHUB_RUN_ATTEMPT", "pt8a-release.json", "if: always()"):
            self.assertIn(marker, job)
        self.assertNotIn("ephemeral-jwks-fixture.sh prepare", job)  # Observer defers it until exact readiness PASS.
        for forbidden in ("terraform apply", "kubectl apply", "ingest-gcp-target-corpus.py --", "gcloud projects add-iam-policy-binding"):
            self.assertNotIn(forbidden, job)


if __name__ == "__main__":
    unittest.main()
