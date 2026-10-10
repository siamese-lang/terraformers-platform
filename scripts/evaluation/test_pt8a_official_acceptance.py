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


# Read-only GitHub jobs API projections from the two real rollout runs at source
# 1ceb3e40fb98004f58dc52c1f00993495489b1a4; no PT8A job executed in either.
ROLLOUT_JOB_FIXTURES = {
    37774612011: {"total_count": 10, "jobs": [
        {'id': 113302233161, 'name': 'backend-revision-rollout', 'status': 'completed', 'conclusion': 'failure'},
        {'id': 113302234183, 'name': 'backend-live-validation', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302234503, 'name': 'backend-deployment', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302234522, 'name': "inputs.operation == 'pt8a-qualified-case' && format('pt8a-qualified-case/{0}', fromJSON(inputs.pt8a_request).caseId) || inputs.operation == 'pt8a-corrective-readiness' && 'pt8a-corrective-readiness' || 'pt8a-official-acceptance'", 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302235153, 'name': 'terraform-preflight', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302235172, 'name': 'kubernetes-prerequisites', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302235180, 'name': 'backend-replacement-durability', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302235187, 'name': 'capacity-baseline', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302235537, 'name': 'terraform-apply', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113302281476, 'name': 'runtime-live-acceptance', 'status': 'completed', 'conclusion': 'skipped'},
    ]},
    37775050213: {"total_count": 10, "jobs": [
        {'id': 113303685557, 'name': 'backend-revision-rollout', 'status': 'completed', 'conclusion': 'success'},
        {'id': 113303686738, 'name': 'capacity-baseline', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303686774, 'name': 'backend-deployment', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303686944, 'name': 'terraform-preflight', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303686991, 'name': 'kubernetes-prerequisites', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303687109, 'name': 'backend-replacement-durability', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303687219, 'name': 'runtime-live-acceptance', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303687438, 'name': "inputs.operation == 'pt8a-qualified-case' && format('pt8a-qualified-case/{0}', fromJSON(inputs.pt8a_request).caseId) || inputs.operation == 'pt8a-corrective-readiness' && 'pt8a-corrective-readiness' || 'pt8a-official-acceptance'", 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303687538, 'name': 'backend-live-validation', 'status': 'completed', 'conclusion': 'skipped'},
        {'id': 113303687629, 'name': 'terraform-apply', 'status': 'completed', 'conclusion': 'skipped'},
    ]},
}


def ledger():
    return [{"caseId": c, "status": "NOT_RUN", "consumed": False} for c in pt8a.CASES]


def accepted():
    return {"httpStatus": 201, "transportExitCode": 0, "json": {"analysisJobId": "job-1", "projectId": 1,
            "sourceFileId": 2, "binaryPersisted": True, "createdAt": "2026-10-07T14:00:00Z"}}


def safe_diagnostics(job, *, category=None):
    failed = job.get("status") == "FAILED"
    category = category or "PROVIDER_TIMEOUT"
    stage = "cli_validate" if category == "VALIDATE_CONFIGURATION" else "initial_generation"
    candidates = ({"final": {"status": "CAPTURED", "sha256": pt8a.sha(b"x" * 123), "bytes": 123},
                   "validated": {"status": "CAPTURED", "sha256": pt8a.sha(b"x" * 123), "bytes": 123}}
                  if not failed or category == "VALIDATE_CONFIGURATION" else {})
    return {"contractVersion": "analysis-diagnostics-v1", "jobId": job["id"], "projectId": job["projectId"],
        "claimGeneration": 1, "complete": True, "status": "AVAILABLE", "evidenceSha256": "b" * 64,
        "factsStatus": "CAPTURED", "expiresAt": "2026-10-17T00:00:00Z", "stages": {stage: {"status": "FAILED" if failed else "CAPTURED"}},
        "candidates": candidates,
        "failure": {"stage": stage, "category": category, "exceptionType": "TerraformValidationFailureException"} if failed else None,
        "cliDiagnostics": {"diagnosticClasses": ["MISSING_REQUIRED_ARGUMENT"], "errorCount": 1, "warningCount": 0,
            "details": [{"diagnosticClass": "MISSING_REQUIRED_ARGUMENT", "summary": "Missing required argument", "line": 7, "column": 3}]}
            if category == "VALIDATE_CONFIGURATION" else None}


def original_diagnostics(evidence):
    original = copy.deepcopy(evidence)
    original["facts"] = {"status": original.pop("factsStatus")}
    original["retrieval"] = {}
    for candidate in original["candidates"].values():
        candidate["content"] = "x" * 123
    return original


def readback_proof(evidence):
    hashes = {name: candidate["sha256"] for name, candidate in evidence["candidates"].items()}
    return {"status": "VERIFIED" if hashes else "NO_CANDIDATE_PRODUCED", "candidateSha256": hashes}


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


class QualifiedCaseChainContracts(unittest.TestCase):
    def setUp(self):
        # Entire chain is synthetic metadata; no official bytes, model or cloud are contacted.
        self.origin = RiskQualifiedCorrectiveContracts(); self.origin.setUp()
        self.ready_id = pt8a.ORIGIN_FAILED_RUN + 100
        self.ready_artifact, self.ready_review, self.ready_approval, self.campaign_id = 901, 902, 903, 904
        self.chain_decision = {"decision": "APPROVED_REPOSITORY_ONLY_IMPLEMENTATION",
            "reviewed_execution_base_sha": "c9caeafd900b8310eae8ead467b20f8e358599db", "candidate_identity": pt8a.IDENTITY,
            "frozen_v2_sha256": pt8a.V2_SHA256, "strict_v2_mode": "UNCHANGED", "case_dispatch_budget": "ONE_EACH_NO_RETRY",
            "independent_review_each_stage": "REQUIRED", "approved_action_scope": "REPOSITORY_ONLY"}
        self.artifacts, self.bindings, self.reviews = {}, {}, {}
        self.release = {"sourceSha": SOURCE, "image": IMAGE, "sourceTagDigest": IMAGE.split("@")[1],
            "readyReplicas": 1, "noRuntimeConfigurationMutation": True,
            "deployedRuntimeIdentity": SOURCE + "|terraformers-reference-v4|terraformers-reference-v4|gemini-embedding-2|1536|1536"
                "|https://identity.example.test/case-c|http://terraformers-jwks:8080/jwks.json|vertex|vertex|REQUIRED"
                "|gemini-3.8-flash|case-c-runtime-client|gcs|gcs|http://terraformers-opensearch:9200|5.100.0"}
        self.snapshot = {"classification": "RISK_QUALIFIED_RETAINED_V4", "indexUuid": pt8a.ORIGIN_UUID,
            "liveContentIdentity": pt8a.ORIGIN_CONTENT, "vectorDocumentsChecked": 5395, "vectorDimension": 1536,
            "originSourceSha": pt8a.ORIGIN_SOURCE, "originApprovalCommentId": pt8a.ORIGIN_APPROVAL,
            "originArtifactDigest": pt8a.ORIGIN_CLEAN_DIGEST, "vectorWriteContinuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
            "cryptographicVectorContinuityProven": False, "embeddingRequests": 0, "indexWrites": 0}
        self.inputs = pt8a.frozen_inputs()
        for index in range(-1, 5): self.make_artifact(index)
        self.request = self.make_request(0)
        self.campaign = pt8a.qualified_live_fields(self.request, SOURCE, IMAGE, self.bindings[self.ready_id]["digest"])
        self.addCleanup(patch.stopall)
        patch.object(pt8a.urllib.request, "build_opener", side_effect=AssertionError("no real image fetch")).start()
        patch.object(pt8a.rag.JsonHttpClient, "request", side_effect=AssertionError("no OpenSearch/cloud request")).start()

    def reference(self, index):
        return {"runId": self.ready_id + index + 1, "artifactId": self.ready_artifact + 10 * (index + 1),
                "reviewCommentId": self.ready_review + 10 * (index + 1)}

    def record(self, index):
        return {"caseId": "readiness-only" if index < 0 else pt8a.CASES[index], "status": "REVIEW_PENDING",
            "consumed": True, "uploadAttempts": 1, "jobId": "synthetic-job-" + str(index),
            "projectId": index + 10, "sourceFileId": index + 20, "terminalState": "SUCCEEDED", "acceptedToTerminalMs": 1234}

    def make_artifact(self, index):
        ref = self.reference(index); record = self.record(index)
        quality = {"contractVersion": "evidence-quality-v1", "technicalStatus": "PASS", "knowledgeStatus": "COMPLETE"}
        job = {"id": record["jobId"], "projectId": record["projectId"], "sourceFileId": record["sourceFileId"],
            "status": "SUCCEEDED", "resultFileId": 123, "resultObjectKey": "synthetic.tf", "quality": quality}
        digest = "sha256:" + pt8a.sha(str(ref).encode())
        binding = {"runId": ref["runId"], "artifactId": ref["artifactId"], "digest": digest,
                   "sourceSha": SOURCE, "conclusion": "success"}
        rows = [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in pt8a.CASES]
        for previous in range(index + 1): rows[previous] = self.record(previous) | {"status": "PASS" if previous < index else "REVIEW_PENDING"}
        hcl_sha = pt8a.sha(b"terraform {}")
        data = {"inventory.json": {"files": [{"path": "observation/main.tf", "sha256": hcl_sha, "sizeBytes": 12}]},
            "binding.json": {"sourceSha": SOURCE, "image": IMAGE, "candidateIdentity": pt8a.IDENTITY,
                "procedureSha256": pt8a.sha((pt8a.ROOT / pt8a.CORRECTIVE_PROCEDURE).read_bytes()),
                "mode": pt8a.CORRECTIVE_MODE if index < 0 else pt8a.QUALIFIED_MODE,
                "caseId": None if index < 0 else pt8a.CASES[index], "runId": ref["runId"],
                "liveApprovalCommentId": self.ready_approval if index < 0 else self.campaign_id},
            "ledger.json": rows, "readiness.json": self.snapshot | {"correlatedBackendRetrieval": True},
            "post-admission.json": self.snapshot.copy(), "release.json": self.release.copy(),
            "origin-bindings.json": {"clean": self.origin.clean_binding.copy(), "failed": self.origin.failed_binding.copy()},
            "observation/accepted.json": record | {"status": "ACCEPTED"}, "observation/job.json": job,
            "observation/cli.json": {"terraformVersion": "1.8.5", "providerVersion": "5.100.0", "initValidateExitCode": 0,
                                     "AWSPlanApply": False, "hclSha256": hcl_sha},
            "observation/retrieval.json": {"jobId": job["id"], "officialHitCount": 1},
            "observation/presentation.json": {"projectId": job["projectId"], "analysisStatus": "SUCCEEDED",
                "latestAnalysisJobId": job["id"], "quality": quality.copy(), "latestResultObjectKey": job["resultObjectKey"]},
            "observation/draft-identity.json": {"hclSha256": hcl_sha, "resultObjectKey": job["resultObjectKey"]}}
        if index < 0: data["readiness-job.json"] = record
        else:
            data["input-identity.json"] = self.inputs[index].copy()
            data["qualified-chain.json"] = {"campaignApprovalCommentId": self.campaign_id, "readinessRunId": self.ready_id,
                "readinessArtifactId": self.ready_artifact, "readinessReviewCommentId": self.ready_review,
                "prior": self.reference(index - 1)}
        dimensions = ("readiness",) if index < 0 else pt8a.SCORING
        self.reviews[ref["reviewCommentId"]] = {"decision": "ACCEPTED", "material_defect": "false", "false_trusted_success": "0",
            "reviewed_source_sha": SOURCE, "candidate_identity": pt8a.IDENTITY, "procedure_sha256": data["binding.json"]["procedureSha256"],
            "run_id": str(ref["runId"]), "artifact_id": str(ref["artifactId"]), "artifact_digest": digest,
            "case_id": record["caseId"], "admission_class": "RISK_QUALIFIED_RETAINED_V4",
            "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
            "accepted_residual_risk": "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED",
            "origin_clean_artifact_digest": pt8a.ORIGIN_CLEAN_DIGEST, "origin_clean_source_sha": pt8a.ORIGIN_SOURCE,
            "failed_readiness_artifact_digest": pt8a.ORIGIN_FAILED_DIGEST, "original_failure_consumed": "true",
            **{"dimension_" + d: "PASS" for d in dimensions}}
        self.artifacts[ref["runId"]] = data; self.bindings[ref["runId"]] = binding

    def make_request(self, index):
        prior = self.reference(index - 1)
        return {"mode": pt8a.QUALIFIED_MODE, "caseId": pt8a.CASES[index], "liveApprovalCommentId": self.campaign_id,
            "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN, "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
            "readinessRunId": self.ready_id, "readinessArtifactId": self.ready_artifact,
            "readinessReviewCommentId": self.ready_review, "readinessLiveApprovalCommentId": self.ready_approval,
            "priorRunId": prior["runId"], "priorArtifactId": prior["artifactId"], "priorReviewCommentId": prior["reviewCommentId"]}

    def authority(self, comment_id, marker):
        if comment_id == pt8a.CHAIN_DECISION: return self.chain_decision
        if comment_id == pt8a.RECOVERY_DECISION: return self.origin.decision
        if comment_id == self.ready_approval: return self.origin.authority
        if comment_id == self.campaign_id: return self.campaign
        return self.reviews[comment_id]

    def download(self, run_id, artifact_id, names, private, workflow, **kwargs):
        self.assertTrue(kwargs["verify_inventory"])
        self.assertEqual(self.bindings[run_id]["artifactId"], artifact_id)
        return self.artifacts[run_id], self.bindings[run_id]

    def histories(self, index):
        current_id = self.reference(index)["runId"]
        self.runtime = [{"id": pt8a.ORIGIN_FAILED_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        self.job_rows, self.artifact_rows = {}, {}
        for stage in range(-1, index + 1):
            ref = self.reference(stage); name = pt8a.CORRECTIVE_OPERATION if stage < 0 else pt8a.QUALIFIED_OPERATION + "/" + pt8a.CASES[stage]
            self.runtime.append({"id": ref["runId"], "head_sha": SOURCE, "run_attempt": 1,
                "status": "in_progress" if stage == index else "completed", "conclusion": None if stage == index else "success"})
            self.job_rows[ref["runId"]] = {"total_count": 1, "jobs": [{"name": name, "conclusion": None if stage == index else "success"}]}
            self.artifact_rows[ref["runId"]] = {"total_count": 1, "artifacts": [{"id": ref["artifactId"],
                "name": "pt8a-official-" + str(ref["runId"]), "digest": self.bindings[ref["runId"]]["digest"]}]}
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        return current_id

    def api(self, path):
        self.assertNotIn("head_sha=", path)
        if "gcp-target-runtime-dependencies.yml/runs?" in path: return {"total_count": len(self.runtime), "workflow_runs": self.runtime}
        if "gcp-target-corpus-ingestion.yml/runs?" in path: return {"total_count": len(self.writes), "workflow_runs": self.writes}
        run_id = int(path.split("/")[2])
        return self.job_rows[run_id] if "/jobs?" in path else self.artifact_rows[run_id]

    def chain(self, index):
        request = self.make_request(index); current = self.histories(index)
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, "github", side_effect=self.api), \
             patch.object(pt8a, "authority_comment", side_effect=self.authority), \
             patch.object(pt8a.transport, "current_main", return_value=SOURCE), \
             patch.object(pt8a, "bound_artifact", side_effect=self.download):
            campaign, _ = pt8a.qualified_request_contract(request, SOURCE, IMAGE, 1)
            history = pt8a.qualified_history(request, SOURCE, current)
            return pt8a.qualified_predecessor(request, SOURCE, IMAGE, campaign, history, Path(d))

    def test_accepted_readiness_and_each_ten_dimension_review_unlocks_only_next_case_through_e(self):
        for index in range(5):
            with self.subTest(case=pt8a.CASES[index]):
                rows = self.chain(index)
                self.assertEqual(["PASS"] * index + ["NOT_RUN"] * (5 - index), [r["status"] for r in rows])
                self.assertEqual([self.record(i)["jobId"] for i in range(index)], [r["jobId"] for r in rows[:index]])
        for changed in ({"decision": "CHANGES_REQUIRED"}, {"dimension_readiness": "FAIL"}, {"false_trusted_success": "1"},
                        {"material_defect": "true"}, {"vector_write_continuity": "EXACT_VECTOR_CONTINUITY"}):
            saved=self.reviews[self.ready_review]; self.reviews[self.ready_review]=saved|changed
            try:
                with self.assertRaises(ValueError): self.chain(0)
            finally: self.reviews[self.ready_review]=saved
        for index in range(1,5):
            prior_review=self.reference(index-1)["reviewCommentId"]
            for dimension in pt8a.SCORING:
                saved=self.reviews[prior_review]; self.reviews[prior_review]=saved|{"dimension_"+dimension:"FAIL"}
                try:
                    with self.subTest(case=index,dimension=dimension), self.assertRaises(ValueError): self.chain(index)
                finally: self.reviews[prior_review]=saved

    def test_readiness_or_case_failure_tamper_censor_or_missing_artifact_blocks_chain(self):
        mutations=[("binding.json","sourceSha","c"*40), ("binding.json","image",IMAGE+"wrong"),
            ("binding.json","procedureSha256","wrong"), ("binding.json","candidateIdentity","wrong"),
            ("readiness.json","classification","EXACT_REUSABLE_COMPLETED_V4"),
            ("readiness.json","correlatedBackendRetrieval",False), ("readiness.json","cryptographicVectorContinuityProven",True),
            ("post-admission.json","indexUuid","replacement"), ("post-admission.json","vectorDocumentsChecked",5394),
            ("observation/job.json","status","FAILED"), ("observation/accepted.json","consumed",False),
            ("observation/cli.json","initValidateExitCode",1), ("observation/retrieval.json","officialHitCount",0)]
        for index in (-1,0):
            data=self.artifacts[self.reference(index)["runId"]]
            for filename,key,value in mutations:
                saved=data[filename];data[filename]=saved|{key:value}
                try:
                    with self.subTest(index=index,key=key),self.assertRaises(ValueError):self.chain(index+1)
                finally:data[filename]=saved
            record=data["readiness-job.json"] if index<0 else data["ledger.json"][index]
            record["censoredObservationMs"]=540000
            with self.assertRaises(ValueError):self.chain(index+1)
            del record["censoredObservationMs"]
            item=data.pop("post-admission.json")
            with self.assertRaises(KeyError):self.chain(index+1)
            data["post-admission.json"]=item
        data=self.artifacts[self.ready_id]
        data["origin-bindings.json"]["clean"]["sourceSha"]=SOURCE
        with self.assertRaises(ValueError):self.chain(0)

    def test_earlier_case_review_and_individual_observation_cannot_be_forged_in_later_ledger(self):
        review=self.reviews[self.reference(0)["reviewCommentId"]];review["decision"]="CHANGES_REQUIRED"
        with self.assertRaises(ValueError):self.chain(4)
        review["decision"]="ACCEPTED"
        data=self.artifacts[self.reference(3)["runId"]];data["ledger.json"][0]["jobId"]="replacement"
        with self.assertRaisesRegex(ValueError,"rewrote"):self.chain(4)

    def test_separate_campaign_exact_binding_required_readiness_or_repository_approval_not_live(self):
        with patch.object(pt8a,"authority_comment",side_effect=self.authority),patch.object(pt8a.transport,"current_main",return_value=SOURCE):
            pt8a.qualified_request_contract(self.request,SOURCE,IMAGE,1)
            for key in pt8a.qualified_live_fields(self.request,SOURCE,IMAGE,self.campaign["corrective_readiness_artifact_digest"]):
                saved=self.campaign.pop(key)
                try:
                    with self.subTest(key=key),self.assertRaises(ValueError):pt8a.qualified_request_contract(self.request,SOURCE,IMAGE,1)
                finally:self.campaign[key]=saved
            for changed in ({"liveApprovalCommentId":self.ready_approval},{"liveApprovalCommentId":pt8a.CHAIN_DECISION},
                            {"mode":"case"},{"truth":{}},{"caseId":"aws-official-f"}):
                with self.assertRaises(ValueError):pt8a.qualified_request_contract(self.request|changed,SOURCE,IMAGE,1)
            with self.assertRaises(ValueError):pt8a.qualified_request_contract(self.request,SOURCE,IMAGE,2)
            with self.assertRaises(ValueError):pt8a.request_contract(self.request,SOURCE,IMAGE,1)
        self.campaign["corrective_readiness_artifact_digest"]="sha256:"+"f"*64
        with self.assertRaises(ValueError):self.chain(0)

    def test_all_source_history_blocks_second_case_attempt_preflight_failure_and_incomplete_history(self):
        for index in range(5):
            current=self.histories(index); old_id=current+100
            self.runtime.append({"id":old_id,"head_sha":"c"*40,"run_attempt":1,"status":"completed","conclusion":"failure"})
            self.job_rows[old_id]={"total_count":1,"jobs":[{"name":pt8a.QUALIFIED_OPERATION+"/"+pt8a.CASES[index],"conclusion":"failure"}]}
            with patch.object(pt8a,"github",side_effect=self.api),self.assertRaisesRegex(ValueError,"across sources"):
                pt8a.qualified_history(self.make_request(index),SOURCE,current)
        current=self.histories(1)
        self.artifact_rows[self.ready_id]['total_count']=2
        with patch.object(pt8a,"github",side_effect=self.api),self.assertRaisesRegex(ValueError,"incomplete"):
            pt8a.qualified_history(self.make_request(1),SOURCE,current)
        self.artifact_rows[self.ready_id]['total_count']=1
        self.job_rows[self.ready_id]['total_count']=2
        with patch.object(pt8a,"github",side_effect=self.api),self.assertRaisesRegex(ValueError,"incomplete"):
            pt8a.qualified_history(self.make_request(1),SOURCE,current)
        current=self.histories(0);self.writes.append({'id':pt8a.ORIGIN_CLEAN_RUN+1,'head_sha':'c'*40})
        with patch.object(pt8a,"github",side_effect=self.api),self.assertRaisesRegex(ValueError,"writer"):
            pt8a.qualified_history(self.request,SOURCE,current)

    def test_real_rollout_skips_admitted_but_unknown_or_consumed_history_still_blocks(self):
        current = self.histories(0)
        for run_id, jobs in ROLLOUT_JOB_FIXTURES.items():
            self.runtime.append({"id": run_id, "head_sha": "1ceb3e40fb98004f58dc52c1f00993495489b1a4",
                "run_attempt": 1, "status": "completed", "conclusion": jobs["jobs"][0]["conclusion"]})
            self.job_rows[run_id] = copy.deepcopy(jobs)

        def check_both_histories(expect_error=False):
            calls = [lambda: pt8a.qualified_history(self.request, SOURCE, current),
                     lambda: pt8a.corrective_history(SOURCE, current)]
            for index, call in enumerate(calls):
                # Corrective readiness has no predecessor; case A has the accepted readiness.
                saved = self.runtime
                if index == 1:
                    self.runtime = [r for r in saved if r["id"] != self.ready_id]
                try:
                    if expect_error:
                        with self.assertRaises((ValueError, RuntimeError)):
                            call()
                    else:
                        history = call()
                        self.assertTrue(history["allSourcesInspected"])
                        if index == 0:
                            self.assertEqual({"readiness-only"}, set(history["predecessors"]))
                finally:
                    self.runtime = saved

        with patch.object(pt8a, "github", side_effect=self.api):
            check_both_histories()
            for run_id, original in ROLLOUT_JOB_FIXTURES.items():
                skipped = next(i for i, j in enumerate(original["jobs"]) if j["id"] in
                               (113302234522, 113303687438))
                # Neither raw expressions nor known operations may hide a consumed failure.
                for changed in ({"conclusion": "failure"}, {"conclusion": None},
                                {"status": "in_progress"}, {"name": "unknown operation"},
                                {"name": pt8a.QUALIFIED_OPERATION + "/unknown-case"},
                                {"name": "prefix " + original["jobs"][skipped]["name"]},
                                *({"name": name, "conclusion": "failure"} for name in
                                  (pt8a.CORRECTIVE_OPERATION, "pt8a-official-acceptance",
                                   pt8a.QUALIFIED_OPERATION + "/" + pt8a.CASES[0]))):
                    with self.subTest(run=run_id, changed=changed):
                        self.job_rows[run_id]["jobs"][skipped].update(changed)
                        check_both_histories(expect_error=True)
                        self.job_rows[run_id] = copy.deepcopy(original)
                for defect in ("missing", "extra", "incomplete"):
                    with self.subTest(run=run_id, defect=defect):
                        jobs = self.job_rows[run_id]
                        if defect == "missing":
                            jobs["jobs"].pop(skipped); jobs["total_count"] -= 1
                        elif defect == "extra":
                            jobs["jobs"].append({"name": pt8a.CORRECTIVE_OPERATION, "conclusion": "skipped"})
                            jobs["total_count"] += 1
                        else:
                            jobs["total_count"] += 1
                        check_both_histories(expect_error=True)
                        self.job_rows[run_id] = copy.deepcopy(original)
            # A current qualified job must still have its evaluated per-case identity.
            self.job_rows[current] = copy.deepcopy(ROLLOUT_JOB_FIXTURES[37775050213])
            with self.assertRaisesRegex(ValueError, "current qualified"):
                pt8a.qualified_history(self.request, SOURCE, current)
        with patch.object(pt8a, "github", side_effect=RuntimeError("history unavailable")):
            check_both_histories(expect_error=True)

    def test_qualified_cli_preflight_checks_chain_before_cloud_and_operation_modes_do_not_mix(self):
        with tempfile.TemporaryDirectory() as d,ExitStack() as stack:
            request_file=Path(d)/'request.json';request_file.write_text(json.dumps(self.request))
            stack.enter_context(patch.dict(os.environ,{'GITHUB_SHA':SOURCE,'BACKEND_IMAGE':IMAGE,'GITHUB_RUN_ATTEMPT':'1',
                'GITHUB_RUN_ID':str(self.histories(0)),'OPERATION':pt8a.QUALIFIED_OPERATION}))
            stack.enter_context(patch.object(sys,'argv',['pt8a','preflight','--request-file',str(request_file)]))
            stack.enter_context(patch.object(pt8a,'authority_comment',side_effect=self.authority))
            stack.enter_context(patch.object(pt8a,'github',side_effect=self.api))
            stack.enter_context(patch.object(pt8a.transport,'current_main',return_value=SOURCE))
            stack.enter_context(patch.object(pt8a,'bound_artifact',side_effect=self.download))
            stack.enter_context(patch.object(pt8a,'corrective_origins'))
            cloud=stack.enter_context(patch.object(pt8a.subprocess,'run',side_effect=AssertionError('no cloud')))
            observe=stack.enter_context(patch.object(pt8a,'observe',side_effect=AssertionError('no upload')))
            pt8a.main();cloud.assert_not_called();observe.assert_not_called()
            self.reviews[self.ready_review]['decision']='CHANGES_REQUIRED'
            with self.assertRaises(ValueError):pt8a.main()
            for operation,mode in ((pt8a.CORRECTIVE_OPERATION,pt8a.QUALIFIED_MODE),(pt8a.QUALIFIED_OPERATION,'case')):
                os.environ['OPERATION']=operation;request_file.write_text(json.dumps(self.request|{'mode':mode}))
                with self.assertRaisesRegex(ValueError,'operation'):pt8a.main()

    def test_qualified_cli_one_upload_no_retry_future_cases_not_run_and_final_e_never_self_accepted(self):
        for index,status in [(0,'REVIEW_PENDING'),(1,'REVIEW_PENDING'),(4,'REVIEW_PENDING'),(0,'NOT_PASS')]:
            with self.subTest(index=index,status=status),tempfile.TemporaryDirectory() as d,ExitStack() as stack:
                root=Path(d);request=self.make_request(index);request_file=root/'request.json';out=root/'evidence'
                request_file.write_text(json.dumps(request));(root/'coverage-report.json').write_text('{}')
                (root/'pt8a-release.json').write_text(json.dumps(self.release))
                stack.enter_context(patch.dict(os.environ,{'GITHUB_SHA':SOURCE,'BACKEND_IMAGE':IMAGE,'GITHUB_RUN_ATTEMPT':'1',
                    'GITHUB_RUN_ID':str(self.histories(index)),'OPERATION':pt8a.QUALIFIED_OPERATION,'RUNNER_TEMP':d}))
                stack.enter_context(patch.object(sys,'argv',['pt8a','run','--request-file',str(request_file),'--corpus',d,'--output',str(out)]))
                stack.enter_context(patch.object(pt8a,'authority_comment',side_effect=self.authority))
                stack.enter_context(patch.object(pt8a,'github',side_effect=self.api))
                stack.enter_context(patch.object(pt8a.transport,'current_main',return_value=SOURCE))
                stack.enter_context(patch.object(pt8a,'bound_artifact',side_effect=self.download))
                stack.enter_context(patch.object(pt8a,'corrective_origins',return_value=(self.origin.clean['receipt.json'],self.origin.clean_binding,self.origin.failed_binding)))
                stack.enter_context(patch.object(pt8a.rag,'load_corpus',return_value=({}, {}, [],pt8a.CORPUS_CHECKSUM)))
                stack.enter_context(patch.object(pt8a.rag,'validate_pt8a_clean_corpus'))
                scan=stack.enter_context(patch.object(pt8a,'risk_qualified_admission',return_value=self.snapshot.copy()))
                stack.enter_context(patch.object(pt8a.subprocess,'run'))
                stack.enter_context(patch.object(pt8a.transport,'CurlClient'))
                def fake_input(case,private):
                    self.assertEqual(self.inputs[index],case);p=private/'input.png';p.write_bytes(b'synthetic-test-only');return p
                fetch=stack.enter_context(patch.object(pt8a,'acquire',side_effect=fake_input))
                def observed(client,fixture,directory,record,documents,pod):record.update(self.record(index)|{'status':status})
                observe=stack.enter_context(patch.object(pt8a,'observe',side_effect=observed))
                if status=='NOT_PASS':
                    with self.assertRaises(ValueError):pt8a.main()
                else:pt8a.main()
                fetch.assert_called_once();observe.assert_called_once()
                self.assertEqual(1 if status=='NOT_PASS' else 2,scan.call_count)
                rows=json.loads((out/'ledger.json').read_text())
                self.assertEqual(['PASS']*index+[status]+['NOT_RUN']*(4-index),[r['status'] for r in rows])
                self.assertEqual(pt8a.QUALIFIED_MODE,json.loads((out/'binding.json').read_text())['mode'])
                self.assertEqual(self.campaign_id,json.loads((out/'qualified-chain.json').read_text())['campaignApprovalCommentId'])
                self.assertEqual(status=='NOT_PASS',(out/'error.json').exists())


class DiagnosticContinuationContracts(unittest.TestCase):
    def setUp(self):
        # Reuse the existing authenticated-chain fixtures; no image/model/cloud access.
        self.base = QualifiedCaseChainContracts(); self.base.setUp()
        self.addCleanup(patch.stopall)
        self.artifacts, self.bindings, self.reviews = {}, {}, {}
        original = copy.deepcopy(self.base.artifacts[self.base.reference(0)["runId"]])
        original["ledger.json"][0]["status"] = "NOT_PASS"
        original["binding.json"].update(sourceSha=pt8a.CASE_A_SOURCE, runId=pt8a.CASE_A_RUN)
        self.artifacts[pt8a.CASE_A_RUN] = original
        self.bindings[pt8a.CASE_A_RUN] = {"runId": pt8a.CASE_A_RUN, "artifactId": pt8a.CASE_A_ARTIFACT,
            "digest": pt8a.CASE_A_DIGEST, "sourceSha": pt8a.CASE_A_SOURCE, "conclusion": "failure"}
        self.reviews[pt8a.CASE_A_REVIEW] = {"decision": "REJECTED", "reviewed_source_sha": pt8a.CASE_A_SOURCE,
            "candidate_identity": pt8a.IDENTITY, "procedure_sha256": pt8a.V3_SHA256, "case_id": pt8a.CASES[0],
            "run_id": str(pt8a.CASE_A_RUN), "artifact_id": str(pt8a.CASE_A_ARTIFACT), "artifact_digest": pt8a.CASE_A_DIGEST}
        self.live = pt8a.diagnostic_live_fields(SOURCE, IMAGE)
        for index in range(1, 5):
            data = copy.deepcopy(self.base.artifacts[self.base.reference(index)["runId"]])
            ref = self.reference(index)
            data["binding.json"].update(mode=pt8a.DIAGNOSTIC_MODE, runId=ref["runId"], liveApprovalCommentId=800,
                procedureSha256=pt8a.diagnostic_contract_sha256(), diagnosticContractSha256=pt8a.diagnostic_contract_sha256(),
                classification="DIAGNOSTIC_ONLY")
            rows = copy.deepcopy(original["ledger.json"])
            for previous in range(1, index + 1):
                rows[previous] = self.base.record(previous) | {"status": "NOT_PASS", "classification": "DIAGNOSTIC_ONLY", "dispatchConsumed": True}
            data["ledger.json"] = rows
            data["diagnostic-chain.json"] = {"prior": self.reference(index - 1)}
            data["diagnostic-disposition.json"] = {"classification": "DIAGNOSTIC_ONLY", "officialAcceptance": "NOT_ACCEPTANCE",
                "observedStatus": "NOT_PASS", "dispatchConsumed": True, "evidenceValidity": "COMPLETE_AWAITING_INDEPENDENT_CLASSIFICATION"}
            quality = {"technicalStatus": "UNKNOWN", "knowledgeStatus": "DEGRADED"}
            data["observation/job.json"]["quality"] = quality.copy()
            data["observation/presentation.json"]["quality"] = quality.copy()
            data["observation/cli.json"]["initValidateExitCode"] = 1
            data["observation/draft-identity.json"]["hclPresent"] = True
            self.bindings[ref["runId"]] = {"runId": ref["runId"], "artifactId": ref["artifactId"],
                "digest": "sha256:" + pt8a.sha(str(ref).encode()), "sourceSha": SOURCE, "conclusion": "failure"}
            bound = self.bindings[ref["runId"]]
            self.reviews[ref["reviewCommentId"]] = {"decision": "ACKNOWLEDGED_DIAGNOSTIC_ONLY", "observation_class": "PRODUCT_OBSERVATION",
                "infrastructure_auth_provenance": "VERIFIED", "official_acceptance": "NOT_ACCEPTANCE", "allow_next_diagnostic": "true",
                "reviewed_source_sha": SOURCE, "backend_image": IMAGE, "candidate_identity": pt8a.IDENTITY,
                "diagnostic_contract_sha256": pt8a.diagnostic_contract_sha256(), "run_id": str(bound["runId"]),
                "artifact_id": str(bound["artifactId"]), "artifact_digest": bound["digest"], "case_id": pt8a.CASES[index],
                "observed_status": "NOT_PASS", "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
                **{"dimension_" + d: "FAIL" if d == "directed_relationships" else "UNKNOWN" for d in pt8a.SCORING}}
            self.artifacts[ref["runId"]] = data

    def reference(self, index):
        return {"runId": pt8a.CASE_A_RUN, "artifactId": pt8a.CASE_A_ARTIFACT, "reviewCommentId": pt8a.CASE_A_REVIEW} if index == 0 else {
            "runId": pt8a.CASE_A_RUN + 100 * index, "artifactId": 400 + index, "reviewCommentId": 500 + index}

    def request(self, index):
        prior = self.reference(index - 1)
        return {"mode": pt8a.DIAGNOSTIC_MODE, "caseId": pt8a.CASES[index], "liveApprovalCommentId": 800,
            "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN, "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
            "priorRunId": prior["runId"], "priorArtifactId": prior["artifactId"], "priorReviewCommentId": prior["reviewCommentId"]}

    def authority(self, comment, marker):
        self.assertEqual("[HUMAN_GATE_APPROVAL:v1]" if comment == 800 else
            "[PRODUCT_TRUST_REVIEW:v1]" if comment == pt8a.CASE_A_REVIEW else "[PT8A_DIAGNOSTIC_REVIEW:v1]", marker)
        return self.live if comment == 800 else self.reviews[comment]

    def download(self, run, artifact, names, private, workflow, **kwargs):
        self.assertTrue(kwargs["verify_inventory"])
        self.assertEqual(self.bindings[run]["artifactId"], artifact)
        return self.artifacts[run], self.bindings[run]

    def histories(self, index):
        self.runtime = [{"id": pt8a.ORIGIN_FAILED_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        self.jobs, self.archive_rows = {}, {}
        for stage in range(index + 1):
            ref = self.reference(stage); current = stage == index
            self.runtime.append({"id": ref["runId"], "head_sha": SOURCE if stage else pt8a.CASE_A_SOURCE, "run_attempt": 1,
                "status": "in_progress" if current else "completed", "conclusion": None if current else "failure"})
            self.jobs[ref["runId"]] = {"total_count": 1, "jobs": [{"name": (pt8a.DIAGNOSTIC_OPERATION if stage else pt8a.QUALIFIED_OPERATION)
                + "/" + pt8a.CASES[stage], "status": "in_progress" if current else "completed", "conclusion": None if current else "failure"}]}
            self.archive_rows[ref["runId"]] = {"total_count": 1, "artifacts": [{"id": ref["artifactId"],
                "name": "pt8a-diagnostic-" + str(ref["runId"]), "digest": self.bindings[ref["runId"]]["digest"]}]}
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        return self.reference(index)["runId"]

    def api(self, path):
        self.assertNotIn("head_sha=", path)
        if path.startswith("compare/"): return {"status": "ahead"}
        if "gcp-target-runtime-dependencies.yml/runs?" in path: return {"total_count": len(self.runtime), "workflow_runs": self.runtime}
        if "gcp-target-corpus-ingestion.yml/runs?" in path: return {"total_count": len(self.writes), "workflow_runs": self.writes}
        run = int(path.split("/")[2])
        return self.jobs[run] if "/jobs?" in path else self.archive_rows[run]

    def chain(self, index):
        current = self.histories(index)
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, "github", side_effect=self.api), \
             patch.object(pt8a, "authority_comment", side_effect=self.authority), \
             patch.object(pt8a, "bound_artifact", side_effect=self.download), \
             patch.object(pt8a.transport, "current_main", return_value=SOURCE):
            request = self.request(index)
            pt8a.diagnostic_request_contract(request, SOURCE, IMAGE, 1)
            history = pt8a.diagnostic_history(request, SOURCE, current)
            return pt8a.diagnostic_predecessor(request, history, Path(d))

    def test_rejected_a_is_preserved_and_only_new_exact_live_authority_permits_b(self):
        rows = self.chain(1)
        self.assertEqual(self.artifacts[pt8a.CASE_A_RUN]["ledger.json"], rows)
        self.assertEqual("NOT_PASS", rows[0]["status"]); self.assertTrue(rows[0]["consumed"])
        with patch.object(pt8a, "authority_comment", side_effect=self.authority), \
             patch.object(pt8a, "github", side_effect=self.api), patch.object(pt8a.transport, "current_main", return_value=SOURCE):
            for field in self.live:
                saved = self.live; self.live = saved | {field: "wrong"}
                with self.subTest(field=field), self.assertRaises(ValueError):
                    pt8a.diagnostic_request_contract(self.request(1), SOURCE, IMAGE, 1)
                self.live = saved
            for altered in (self.request(1) | {"caseId": pt8a.CASES[0]}, self.request(1) | {"mode": pt8a.QUALIFIED_MODE}):
                with self.assertRaises(ValueError): pt8a.diagnostic_request_contract(altered, SOURCE, IMAGE, 1)
            with self.assertRaises(ValueError): pt8a.diagnostic_request_contract(self.request(1), SOURCE, IMAGE, 2)
        # The original acceptance helper still requires A PASS; diagnostic reviews cannot change it.
        with self.assertRaises(ValueError): pt8a.next_case(rows, pt8a.CASES[1], {"decision": "ACCEPTED"})
        self.base.artifacts[self.base.reference(0)["runId"]]["ledger.json"][0]["status"] = "NOT_PASS"
        with self.assertRaises(ValueError): self.base.chain(1)
        self.artifacts[pt8a.CASE_A_RUN]["ledger.json"][0]["status"] = "PASS"
        with self.assertRaises(ValueError): self.chain(1)

    def test_negative_product_observations_require_exact_independent_review_without_pass_promotion(self):
        for index in range(2, 5):
            rows = self.chain(index)
            self.assertEqual(["NOT_PASS"] * index + ["NOT_RUN"] * (5-index), [r["status"] for r in rows])
            self.assertEqual("UNKNOWN", self.artifacts[self.reference(index-1)["runId"]]["observation/job.json"]["quality"]["technicalStatus"])
        prior = self.reference(1); saved = copy.deepcopy(self.reviews[prior["reviewCommentId"]])
        for changed in ({"decision": "ACCEPTED"}, {"official_acceptance": "PASS"}, {"observation_class": "INFRASTRUCTURE_FAILURE"},
                        {"infrastructure_auth_provenance": "UNKNOWN"}, {"allow_next_diagnostic": "false"}, {"artifact_digest": "wrong"},
                        {"dimension_directed_relationships": None}, {"observed_status": "REVIEW_PENDING"}):
            self.reviews[prior["reviewCommentId"]] = saved | changed
            with self.subTest(changed=changed), self.assertRaises(ValueError): self.chain(2)
        self.reviews[prior["reviewCommentId"]] = saved
        before = copy.deepcopy(self.artifacts[prior["runId"]])
        for path, changed in (("observation/accepted.json", {"status": "INDETERMINATE_ACCEPTANCE"}),
                              ("diagnostic-disposition.json", {"evidenceValidity": "INCOMPLETE"}),
                              ("observation/draft-identity.json", {"hclPresent": False}),
                              ("post-admission.json", {"indexWrites": 1}), ("binding.json", {"sourceSha": "c"*40})):
            self.artifacts[prior["runId"]][path].update(changed)
            with self.subTest(path=path), self.assertRaises(ValueError): self.chain(2)
            self.artifacts[prior["runId"]] = copy.deepcopy(before)
        self.artifacts[prior["runId"]]["ledger.json"][1]["censoredObservationMs"] = 540000
        with self.assertRaises(ValueError): self.chain(2)

    def test_all_source_duplicates_out_of_order_preflight_failures_and_incomplete_history_block(self):
        for previous_case in (pt8a.CASES[1], pt8a.CASES[3]):
            current = self.histories(1); extra = current - 1
            self.runtime.append({"id": extra, "head_sha": "c"*40, "run_attempt": 1, "status": "completed", "conclusion": "failure"})
            self.jobs[extra] = {"total_count": 1, "jobs": [{"name": pt8a.DIAGNOSTIC_OPERATION + "/" + previous_case, "conclusion": "failure"}]}
            with patch.object(pt8a, "github", side_effect=self.api), self.assertRaises(ValueError):
                pt8a.diagnostic_history(self.request(1), SOURCE, current)
            self.jobs[extra]["jobs"][0]["conclusion"] = "skipped"
            with patch.object(pt8a, "github", side_effect=self.api), self.assertRaises(ValueError):
                pt8a.diagnostic_history(self.request(1), SOURCE, current)
        current = self.histories(2)
        for run, jobs in ROLLOUT_JOB_FIXTURES.items():
            self.runtime.append({"id": run, "head_sha": "c"*40}); self.jobs[run] = copy.deepcopy(jobs)
        with patch.object(pt8a, "github", side_effect=self.api):
            self.assertTrue(pt8a.diagnostic_history(self.request(2), SOURCE, current)["allSourcesInspected"])
            self.archive_rows[self.reference(1)["runId"]]["artifacts"] = []
            with self.assertRaises(ValueError): pt8a.diagnostic_history(self.request(2), SOURCE, current)
        current = self.histories(1); self.jobs[current]["total_count"] = 2
        with patch.object(pt8a, "github", side_effect=self.api), self.assertRaises(ValueError):
            pt8a.diagnostic_history(self.request(1), SOURCE, current)

    def test_predecessor_keeps_its_original_source_and_live_approval_across_revisions(self):
        previous_source = "c"*40; ref = self.reference(1); data = self.artifacts[ref["runId"]]
        data["binding.json"].update(sourceSha=previous_source, liveApprovalCommentId=801)
        self.bindings[ref["runId"]]["sourceSha"] = previous_source
        data["release.json"].update(sourceSha=previous_source,
            deployedRuntimeIdentity=data["release.json"]["deployedRuntimeIdentity"].replace(SOURCE, previous_source))
        self.reviews[ref["reviewCommentId"]]["reviewed_source_sha"] = previous_source
        current = self.histories(2)
        next(r for r in self.runtime if r["id"] == ref["runId"])["head_sha"] = previous_source
        authority = lambda comment, marker: pt8a.diagnostic_live_fields(previous_source, IMAGE) if comment == 801 else self.authority(comment, marker)
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, "github", side_effect=self.api), \
             patch.object(pt8a, "bound_artifact", side_effect=self.download), patch.object(pt8a, "authority_comment", side_effect=authority):
            history = pt8a.diagnostic_history(self.request(2), SOURCE, current)
            self.assertEqual("NOT_PASS", pt8a.diagnostic_predecessor(self.request(2), history, Path(d))[1]["status"])
            data["binding.json"]["liveApprovalCommentId"] = 800
            with self.assertRaises(ValueError): pt8a.diagnostic_predecessor(self.request(2), history, Path(d))

    def test_terminal_provider_failure_can_be_acknowledged_but_cannot_hide_cli_for_an_existing_draft(self):
        data = self.artifacts[self.reference(1)["runId"]]
        data["ledger.json"][1]["terminalState"] = "FAILED"
        data["observation/job.json"].update(status="FAILED", resultObjectKey=None, failureReason="PROVIDER_OUTPUT_TRUNCATED")
        data["observation/presentation.json"].update(analysisStatus="FAILED", latestResultObjectKey=None)
        data["observation/draft-identity.json"] = {"hclPresent": False}
        data["observation/cli.json"] = {"status": "NOT_RUN", "reason": "BACKEND_FAILED", "AWSPlanApply": False}
        data["inventory.json"]["files"] = []
        rows = self.chain(2)
        self.assertEqual("NOT_PASS", rows[1]["status"]); self.assertEqual("FAILED", rows[1]["terminalState"])
        data["inventory.json"]["files"] = [{"path": "observation/main.tf", "sha256": "unvalidated"}]
        with self.assertRaises(ValueError): self.chain(2)

    def test_preflight_blocks_mixed_modes_and_unreviewed_diagnostic_before_cloud(self):
        with tempfile.TemporaryDirectory() as d, ExitStack() as stack:
            request_file = Path(d)/"request.json"; request_file.write_text(json.dumps(self.request(2)))
            stack.enter_context(patch.object(sys, "argv", ["pt8a", "preflight", "--request-file", str(request_file)]))
            stack.enter_context(patch.dict(os.environ, {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE,
                "GITHUB_RUN_ATTEMPT": "1", "GITHUB_RUN_ID": str(self.histories(2)), "OPERATION": pt8a.DIAGNOSTIC_OPERATION}))
            stack.enter_context(patch.object(pt8a, "github", side_effect=self.api))
            stack.enter_context(patch.object(pt8a, "authority_comment", side_effect=self.authority))
            stack.enter_context(patch.object(pt8a, "bound_artifact", side_effect=self.download))
            stack.enter_context(patch.object(pt8a, "corrective_origins"))
            stack.enter_context(patch.object(pt8a.transport, "current_main", return_value=SOURCE))
            observe = stack.enter_context(patch.object(pt8a, "observe"))
            cloud = stack.enter_context(patch.object(pt8a.subprocess, "run"))
            self.reviews[self.reference(1)["reviewCommentId"]]["decision"] = "PENDING"
            with self.assertRaises(ValueError): pt8a.main()
            with patch.dict(os.environ, {"OPERATION": pt8a.QUALIFIED_OPERATION}), self.assertRaises(ValueError): pt8a.main()
            request_file.write_text(json.dumps(self.base.make_request(1)))
            with self.assertRaises(ValueError): pt8a.main()
            observe.assert_not_called(); cloud.assert_not_called()

    def test_new_authority_reuses_read_only_admission_with_unproven_vector_continuity(self):
        snapshot = self.base.snapshot | {"classification": "EXACT_REUSABLE_COMPLETED_V4"}
        with patch.object(pt8a.rag, "verify_exact_v4", return_value=snapshot) as verify:
            result = pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                self.base.origin.clean["receipt.json"], self.base.origin.clean_binding, self.live)
        self.assertTrue(verify.call_args.kwargs["inspect_vectors"])
        self.assertEqual(0, result["embeddingRequests"]); self.assertEqual(0, result["indexWrites"])
        self.assertFalse(result["cryptographicVectorContinuityProven"])

    def test_diagnostic_main_preserves_failure_runs_one_observation_and_rechecks_admission(self):
        for index in (1, 4):
            with self.subTest(case=index), tempfile.TemporaryDirectory() as d, ExitStack() as stack:
                root = Path(d); request_file = root / "request.json"; output = root / "evidence"
                request_file.write_text(json.dumps(self.request(index)))
                (root / "coverage-report.json").write_text("{}")
                (root / "pt8a-release.json").write_text(json.dumps(self.base.release))
                stack.enter_context(patch.dict(os.environ, {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE,
                    "GITHUB_RUN_ATTEMPT": "1", "GITHUB_RUN_ID": str(self.histories(index)),
                    "OPERATION": pt8a.DIAGNOSTIC_OPERATION, "RUNNER_TEMP": d}))
                stack.enter_context(patch.object(sys, "argv", ["pt8a", "run", "--request-file", str(request_file), "--corpus", d, "--output", str(output)]))
                stack.enter_context(patch.object(pt8a, "github", side_effect=self.api))
                stack.enter_context(patch.object(pt8a, "authority_comment", side_effect=self.authority))
                stack.enter_context(patch.object(pt8a, "bound_artifact", side_effect=self.download))
                stack.enter_context(patch.object(pt8a.transport, "current_main", return_value=SOURCE))
                stack.enter_context(patch.object(pt8a, "corrective_origins", return_value=(self.base.origin.clean["receipt.json"],
                    self.base.origin.clean_binding, self.base.origin.failed_binding)))
                stack.enter_context(patch.object(pt8a.rag, "load_corpus", return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
                stack.enter_context(patch.object(pt8a.rag, "validate_pt8a_clean_corpus"))
                scans = stack.enter_context(patch.object(pt8a, "risk_qualified_admission", return_value=self.base.snapshot.copy()))
                stack.enter_context(patch.object(pt8a.subprocess, "run"))
                stack.enter_context(patch.object(pt8a.transport, "CurlClient"))
                def acquire(case, private):
                    self.assertEqual(self.base.inputs[index], case)
                    fixture = private / "input.png"; fixture.write_bytes(b"synthetic-test-only"); return fixture
                fetch = stack.enter_context(patch.object(pt8a, "acquire", side_effect=acquire))
                def observed(client, fixture, directory, record, documents, pod, **kwargs):
                    self.assertEqual({"diagnostic": True}, kwargs)
                    record.update(self.artifacts[self.reference(index)["runId"]]["ledger.json"][index])
                observation = stack.enter_context(patch.object(pt8a, "observe", side_effect=observed))
                with self.assertRaisesRegex(ValueError, "material technical/product failure"): pt8a.main()
                observation.assert_called_once(); fetch.assert_called_once(); self.assertEqual(2, scans.call_count)
                rows = json.loads((output / "ledger.json").read_text())
                self.assertEqual(["NOT_PASS"]*(index+1)+["NOT_RUN"]*(4-index), [r["status"] for r in rows])
                self.assertEqual(self.artifacts[pt8a.CASE_A_RUN]["ledger.json"][0], rows[0])
                self.assertEqual("DIAGNOSTIC_ONLY", json.loads((output / "binding.json").read_text())["classification"])
                self.assertEqual("NOT_ACCEPTANCE", json.loads((output / "diagnostic-disposition.json").read_text())["officialAcceptance"])
                self.assertTrue((output / "error.json").is_file()); self.assertTrue((output / "inventory.json").is_file())

    def test_diagnostic_workflow_is_gated_before_wif_and_keeps_distinct_artifact_and_no_new_workflow(self):
        workflow = yaml.safe_load((pt8a.ROOT / ".github/workflows/gcp-target-runtime-dependencies.yml").read_text())
        job = workflow["jobs"]["pt8a-official-acceptance"]
        self.assertEqual(pt8a.SKIPPED_POST_CORRECTION_JOB_NAME, job["name"][4:-3])
        steps = job["steps"]; gate = next(i for i,s in enumerate(steps) if "preflight --request-file" in s.get("run", ""))
        wif = next(i for i,s in enumerate(steps) if s.get("uses") == "google-github-actions/auth@v3")
        self.assertLess(gate, wif); self.assertIn("RUN_REVIEWED_PT8A_B_TO_E_DIAGNOSTIC_ONLY", steps[gate]["run"])
        artifact = next(s for s in steps if s.get("id") == "pt8a-artifact")
        self.assertIn("pt8a-diagnostic", artifact["with"]["name"])
        self.assertEqual("always() && inputs.operation != 'pt8a-recovery-campaign'", artifact["if"])
        for step in steps:
            if "run" in step:
                parsed = subprocess.run(["bash", "-n"], input=step["run"], text=True, capture_output=True)
                self.assertEqual(0, parsed.returncode, parsed.stderr)


class DraftMeasurementContracts(unittest.TestCase):
    def setUp(self):
        # Reuse the real observer chain fixtures; all transport remains deterministic/offline.
        self.base = QualifiedCaseChainContracts(); self.base.setUp()
        self.addCleanup(patch.stopall)
        self.live_id, self.policy_id, self.review_id, self.pr_id = 800, 801, 802, 803
        self.request_a = self.request(0)
        self.live = pt8a.measurement_live_fields(self.request_a, SOURCE, IMAGE)
        self.policy = pt8a.measurement_policy_fields() | {"reviewed_head": "d" * 40}
        self.review = {"decision": "ACCEPTED", "reviewed_head": "d" * 40,
            "execution_base_sha": pt8a.MEASUREMENT_BASE, "measurement_contract_sha256": pt8a.measurement_contract_sha256(),
            "diagnostic_evidence_amendment_sha256": pt8a.diagnostic_evidence_amendment_sha256(),
            "amendment_sha256": pt8a.sha((pt8a.ROOT / pt8a.MEASUREMENT_EPISODE_PROCEDURE).read_bytes()),
            "episode_id": pt8a.MEASUREMENT_EPISODE, "start_after_run_id": str(pt8a.MEASUREMENT_START_AFTER_RUN)}
        self.pr = {"merged": True, "base": {"ref": "main"}, "head": {"sha": "d" * 40}, "merge_commit_sha": "e" * 40}
        self.artifacts, self.bindings, self.reviews = {}, {}, {}
        for index, case in enumerate(pt8a.CASES):
            ref = self.reference(index)
            data = copy.deepcopy(self.base.artifacts[self.base.reference(index)["runId"]])
            rows = [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in pt8a.CASES]
            for previous in range(index + 1):
                rows[previous] = self.base.record(previous) | {"status": "NOT_PASS" if previous in (0, 2) else "REVIEW_PENDING", "diagnosticEvidence": "AVAILABLE"}
                if previous == 2: rows[previous]["terminalState"] = "FAILED"
            record = rows[index]
            data["ledger.json"] = rows
            data["cleanup.json"] = {"JWKSRestored": True}
            data["binding.json"].update(mode=pt8a.MEASUREMENT_MODE, runId=ref["runId"],
                episodeId=pt8a.MEASUREMENT_EPISODE, startAfterRunId=pt8a.MEASUREMENT_START_AFTER_RUN,
                procedureSha256=pt8a.measurement_contract_sha256(), measurementContractSha256=pt8a.measurement_contract_sha256(),
                classification="REVIEWABLE_DRAFT_MEASUREMENT_ONLY", liveApprovalCommentId=self.live_id,
                amendmentPullRequest=self.pr_id, amendmentReviewCommentId=self.review_id, policyApprovalCommentId=self.policy_id)
            data["measurement-chain.json"] = {"prior": self.reference(index - 1)}
            data["measurement-disposition.json"] = {"classification": "REVIEWABLE_DRAFT_MEASUREMENT_ONLY",
                "officialAcceptance": "NOT_ACCEPTANCE", "dispatchConsumed": True, "observedStatus": record["status"],
                "evidenceValidity": "COMPLETE_AWAITING_INDEPENDENT_CLASSIFICATION"}
            data["release.json"].update(modelProject="terraformers-platform", modelLocation="global")
            data["observation/draft-identity.json"]["hclPresent"] = True
            if index == 0: data["observation/cli.json"]["initValidateExitCode"] = 1
            if index == 2:
                data["observation/job.json"].update(status="FAILED", resultObjectKey=None)
                data["observation/presentation.json"].update(analysisStatus="FAILED", latestResultObjectKey=None)
                data["observation/cli.json"] = {"status": "NOT_RUN", "reason": "BACKEND_FAILED", "AWSPlanApply": False}
                data["observation/draft-identity.json"] = {"hclPresent": False}
                data["inventory.json"]["files"] = []
            disposition = "FAIL" if index == 0 else "UNKNOWN" if index == 1 else "NOT_OBSERVED" if index == 2 else "PARTIAL" if index == 3 else "PASS"
            review = {"decision": "EVIDENCE_VALID_MEASUREMENT_ONLY", "observation_class": "PRODUCT_OBSERVATION",
                "infrastructure_auth_provenance": "VERIFIED", "official_acceptance": "NOT_ACCEPTANCE",
                "allow_next_measurement": "false" if index == 4 else "true", "reviewed_source_sha": SOURCE,
                "backend_image": IMAGE, "candidate_identity": pt8a.IDENTITY,
                "measurement_contract_sha256": pt8a.measurement_contract_sha256(), "run_id": str(ref["runId"]),
                "artifact_id": str(ref["artifactId"]), "artifact_digest": "sha256:" + pt8a.sha(str(ref).encode()),
                "case_id": case, "observed_status": record["status"], "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
                "material_defect": "true" if index in (0, 3) else "false", "false_trusted_success": "0",
                **{"dimension_" + d: disposition for d in pt8a.SCORING}}
            data["observation/diagnostics.json"] = safe_diagnostics(data["observation/job.json"],
                category="VALIDATE_CONFIGURATION" if index == 2 else None)
            data["observation/diagnostics.json"]["ownerScopedOriginalReadback"] = readback_proof(data["observation/diagnostics.json"])
            record["diagnosticEvidence"] = "AVAILABLE"
            review["failure_diagnosability"] = "VERIFIED"
            review["diagnostic_evidence_sha256"] = data["observation/diagnostics.json"]["evidenceSha256"]
            proof = data["observation/diagnostics.json"]["ownerScopedOriginalReadback"]
            review["private_original_readback"] = proof["status"]
            review["candidate_readback_sha256"] = pt8a.sha(json.dumps(proof, sort_keys=True, separators=(",", ":")).encode())
            if index == 2:
                review["failure_cause_review"] = "VERIFIED_FROM_SAFE_STAGE_CATEGORY_AND_AVAILABLE_CLI_DETAILS"
                review["failure_stage"] = data["observation/diagnostics.json"]["failure"]["stage"]
                review["failure_category"] = data["observation/diagnostics.json"]["failure"]["category"]
                review["safe_cli_diagnostics_sha256"] = pt8a.sha(json.dumps(
                    data["observation/diagnostics.json"]["cliDiagnostics"], sort_keys=True, separators=(",", ":")).encode())
            review["product_quality"] = pt8a.measurement_quality(record, review)
            self.reviews[ref["reviewCommentId"]] = review
            self.bindings[ref["runId"]] = {"runId": ref["runId"], "artifactId": ref["artifactId"],
                "digest": review["artifact_digest"], "sourceSha": SOURCE, "conclusion": "failure" if record["status"] == "NOT_PASS" else "success"}
            self.artifacts[ref["runId"]] = data

    def reference(self, index):
        return {"runId": 0, "artifactId": 0, "reviewCommentId": 0} if index < 0 else {
            "runId": pt8a.MEASUREMENT_START_AFTER_RUN + 100 * (index + 1), "artifactId": 400 + index, "reviewCommentId": 500 + index}

    def request(self, index, final=False):
        ref = self.reference(index if final else index - 1)
        return {"mode": pt8a.MEASUREMENT_MODE, "caseId": pt8a.CASES[index], "liveApprovalCommentId": 800,
            "episodeId": pt8a.MEASUREMENT_EPISODE, "startAfterRunId": pt8a.MEASUREMENT_START_AFTER_RUN,
            "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN, "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
            "amendmentPullRequest": 803, "amendmentReviewCommentId": 802, "policyApprovalCommentId": 801,
            "priorRunId": ref["runId"], "priorArtifactId": ref["artifactId"], "priorReviewCommentId": ref["reviewCommentId"]}

    def authority(self, comment, marker):
        if comment == self.live_id: return self.live
        if comment == self.policy_id: return self.policy
        if comment == self.review_id: return self.review
        self.assertEqual("[PT8A_DRAFT_MEASUREMENT_REVIEW:v1]", marker)
        return self.reviews[comment]

    def history(self, index, final=False):
        self.runtime, self.jobs, self.archive_rows = [], {}, {}
        for old_index, (run_id, artifact_id, digest, review_id, conclusion) in enumerate(pt8a.STOPPED_MEASUREMENT):
            self.runtime.append({"id": run_id, "head_sha": pt8a.STOPPED_MEASUREMENT_SOURCE,
                "run_attempt": 1, "status": "completed", "conclusion": conclusion})
            self.jobs[run_id] = {"total_count": 1, "jobs": [{"name": pt8a.MEASUREMENT_OPERATION + "/" + pt8a.CASES[old_index],
                "status": "completed", "conclusion": conclusion}]}
        self.runtime.append({"id": pt8a.MEASUREMENT_START_AFTER_RUN, "head_sha": pt8a.STOPPED_MEASUREMENT_SOURCE,
            "run_attempt": 1, "status": "completed", "conclusion": "cancelled"})
        self.jobs[pt8a.MEASUREMENT_START_AFTER_RUN] = {"total_count": 1, "jobs": [{"name": pt8a.MEASUREMENT_OPERATION + "/" + pt8a.CASES[2],
            "status": "completed", "conclusion": "cancelled"}]}
        self.archive_rows[pt8a.MEASUREMENT_START_AFTER_RUN] = {"total_count": 0, "artifacts": []}
        for stage in range(index + 1):
            ref = self.reference(stage); bound = self.bindings[ref["runId"]]; current = stage == index and not final
            self.runtime.append({"id": ref["runId"], "head_sha": SOURCE, "run_attempt": 1,
                "status": "in_progress" if current else "completed", "conclusion": None if current else bound["conclusion"]})
            self.jobs[ref["runId"]] = {"total_count": 1, "jobs": [{"name": pt8a.MEASUREMENT_OPERATION + "/" + pt8a.CASES[stage],
                "status": "in_progress" if current else "completed", "conclusion": None if current else bound["conclusion"],
                "steps": [{"name": "Remove only the owned ephemeral validation pod", "conclusion": "success"}]}]}
            self.archive_rows[ref["runId"]] = {"total_count": 1, "artifacts": [{"id": ref["artifactId"],
                "name": "pt8a-measurement-" + str(ref["runId"]), "digest": bound["digest"]}]}
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        return self.reference(index)["runId"]

    def api(self, path):
        self.assertNotIn("head_sha=", path)
        if path.startswith("pulls/"): return self.pr
        if path.startswith("issues/comments/"):
            return {"issue_url": f"https://api.github.com/repos/siamese-lang/terraformers-platform/issues/{self.pr_id}"}
        if path.startswith("compare/"): return {"status": "ahead"}
        if "gcp-target-runtime-dependencies.yml/runs?" in path: return {"total_count": len(self.runtime), "workflow_runs": self.runtime}
        if "gcp-target-corpus-ingestion.yml/runs?" in path: return {"total_count": len(self.writes), "workflow_runs": self.writes}
        run = int(path.split("/")[2]); return self.jobs[run] if "/jobs?" in path else self.archive_rows[run]

    def download(self, run, artifact, names, private, workflow, **kwargs):
        self.assertTrue(kwargs["verify_inventory"])
        self.assertEqual(self.bindings[run]["artifactId"], artifact)
        self.assertTrue(set(names).issubset(self.artifacts[run]))
        return self.artifacts[run], self.bindings[run]

    def patches(self, stack):
        stack.enter_context(patch.object(pt8a, "github", side_effect=self.api))
        stack.enter_context(patch.object(pt8a, "authority_comment", side_effect=self.authority))
        stack.enter_context(patch.object(pt8a, "bound_artifact", side_effect=self.download))
        stack.enter_context(patch.object(pt8a.transport, "current_main", return_value=SOURCE))

    def test_amendment_acceptance_user_policy_merge_and_new_exact_live_authority_are_all_required(self):
        with ExitStack() as stack:
            self.patches(stack)
            self.assertEqual(self.live, pt8a.measurement_request_contract(self.request_a, SOURCE, IMAGE, 1))
            for container in (self.live, self.policy, self.review):
                for key in container:
                    with self.subTest(key=key), patch.dict(container, {key: "wrong"}), self.assertRaises(ValueError):
                        pt8a.measurement_request_contract(self.request_a, SOURCE, IMAGE, 1)
            with patch.dict(self.pr, {"merged": False}), self.assertRaises(ValueError):
                pt8a.measurement_request_contract(self.request_a, SOURCE, IMAGE, 1)
            for request in (self.request_a | {"priorRunId": 1}, self.request(1) | {"priorReviewCommentId": 0},
                            self.request_a | {"mode": pt8a.QUALIFIED_MODE}, self.request_a | {"liveApprovalCommentId": True},
                            self.request_a | {"episodeId": "stopped-episode"}, self.request_a | {"startAfterRunId": 0}):
                with self.subTest(request=request), self.assertRaises(ValueError):
                    pt8a.measurement_request_contract(request, SOURCE, IMAGE, 1)
            with self.assertRaises(ValueError):
                pt8a.measurement_request_contract(self.request_a, pt8a.STOPPED_MEASUREMENT_SOURCE, IMAGE, 1)
            with self.assertRaises(ValueError): pt8a.measurement_request_contract(self.request_a, SOURCE, IMAGE, 2)
            with patch.object(pt8a.transport, "current_main", return_value="c" * 40), self.assertRaisesRegex(ValueError, "MAIN_DRIFT"):
                pt8a.measurement_request_contract(self.request_a, SOURCE, IMAGE, 1)

    def test_diagnostic_storage_loss_or_missing_cause_review_blocks_next_paid_case(self):
        for defect in ("storage", "candidate", "readback", "review"):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
                self.patches(stack)
                ref = self.reference(0)
                data = self.artifacts[ref["runId"]]
                original = copy.deepcopy(data["observation/diagnostics.json"])
                old_review = self.reviews[ref["reviewCommentId"]].copy()
                try:
                    if defect == "storage": data["observation/diagnostics.json"]["status"] = "DIAGNOSTIC_EVIDENCE_INCOMPLETE"
                    if defect == "candidate": data["observation/diagnostics.json"]["candidates"] = {}
                    if defect == "readback": data["observation/diagnostics.json"]["ownerScopedOriginalReadback"] = {"status": "VERIFIED", "candidateSha256": {}}
                    if defect == "review": self.reviews[ref["reviewCommentId"]].pop("failure_diagnosability")
                    history = pt8a.measurement_history(self.request(1), SOURCE, self.history(1))
                    with self.assertRaisesRegex(ValueError, "DIAGNOSTIC_EVIDENCE_INCOMPLETE"):
                        pt8a.diagnostic_predecessor(self.request(1), history, Path(directory), measurement=True)
                finally:
                    data["observation/diagnostics.json"] = original
                    self.reviews[ref["reviewCommentId"]] = old_review

    def test_negative_and_unknown_predecessors_progress_without_rewriting_quality_or_observations(self):
        for index in range(5):
            with self.subTest(index=index), tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
                current = self.history(index); self.patches(stack)
                history = pt8a.measurement_history(self.request(index), SOURCE, current)
                reviewed = pt8a.diagnostic_predecessor(self.request(index), history, Path(directory), measurement=True)
                self.assertEqual(index, len(reviewed["reviews"]))
                if index:
                    self.assertEqual(self.artifacts[self.reference(index - 1)["runId"]]["ledger.json"], reviewed["ledger"])
                    self.assertEqual("NOT_PASS", reviewed["ledger"][0]["status"])
                self.assertTrue(all(r["status"] == "NOT_RUN" for r in reviewed["ledger"][index:]))

    def test_stopped_real_measurement_episode_blocks_new_source_case_a_without_episode_authority(self):
        current = self.history(0)
        with ExitStack() as stack:
            self.patches(stack)
            admitted = pt8a.measurement_history(self.request(0), SOURCE, current)
            self.assertEqual([row[0] for row in pt8a.STOPPED_MEASUREMENT] + [pt8a.MEASUREMENT_START_AFTER_RUN],
                admitted["authenticatedStoppedRunIds"])
            self.assertEqual({}, admitted["predecessors"])
            for run_id in admitted["authenticatedStoppedRunIds"]:
                with self.subTest(run_id=run_id), patch.dict(self.runtime[next(i for i, r in enumerate(self.runtime) if r["id"] == run_id)],
                        {"run_attempt": 2}), self.assertRaises(ValueError):
                    pt8a.measurement_history(self.request(0), SOURCE, current)

    def test_stopped_episode_archives_reviews_approval_and_cancelled_boundary_are_authenticated(self):
        old = {}
        prior = {"runId": 0, "artifactId": 0, "reviewCommentId": 0}
        for index, (run_id, artifact_id, digest, review_id, conclusion) in enumerate(pt8a.STOPPED_MEASUREMENT):
            status = "REVIEW_PENDING" if index == 0 else "NOT_PASS"
            job_id = "original-" + str(index)
            rows = [{"caseId": case, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for case in pt8a.CASES]
            rows[index] = {"caseId": pt8a.CASES[index], "status": status, "consumed": True,
                "uploadAttempts": 1, "terminalState": "SUCCEEDED" if index == 0 else "FAILED", "jobId": job_id}
            old[run_id] = ({"binding.json": {"sourceSha": pt8a.STOPPED_MEASUREMENT_SOURCE,
                    "image": pt8a.STOPPED_MEASUREMENT_IMAGE, "runId": run_id, "mode": pt8a.MEASUREMENT_MODE,
                    "caseId": pt8a.CASES[index], "candidateIdentity": pt8a.IDENTITY,
                    "measurementContractSha256": pt8a.STOPPED_MEASUREMENT_CONTRACT_SHA256,
                    "liveApprovalCommentId": pt8a.STOPPED_MEASUREMENT_APPROVAL},
                "ledger.json": rows, "measurement-chain.json": {"prior": prior},
                "observation/accepted.json": {"status": "ACCEPTED", "consumed": True,
                    "uploadAttempts": 1, "jobId": job_id},
                "observation/job.json": {"id": job_id, "status": rows[index]["terminalState"]},
                "measurement-disposition.json": {"observedStatus": status}},
                {"runId": run_id, "artifactId": artifact_id, "digest": digest,
                 "sourceSha": pt8a.STOPPED_MEASUREMENT_SOURCE, "conclusion": conclusion})
            prior = {"runId": run_id, "artifactId": artifact_id, "reviewCommentId": review_id}
        def comment(comment_id, marker):
            if comment_id == pt8a.STOPPED_MEASUREMENT_APPROVAL:
                return {"decision": "APPROVED", "purpose": pt8a.MEASUREMENT_PURPOSE,
                    "reviewed_source_sha": pt8a.STOPPED_MEASUREMENT_SOURCE,
                    "backend_image": pt8a.STOPPED_MEASUREMENT_IMAGE,
                    "candidate_identity": pt8a.IDENTITY,
                    "measurement_contract_sha256": pt8a.STOPPED_MEASUREMENT_CONTRACT_SHA256,
                    "amendment_pr": "271"}
            if comment_id == 6094591490:
                return {"decision": "STOP_FURTHER_PAID_MEASUREMENTS",
                    "reviewed_source_sha": pt8a.STOPPED_MEASUREMENT_SOURCE,
                    "case_b_run_id": str(pt8a.STOPPED_MEASUREMENT[1][0]),
                    "case_b_artifact_id": str(pt8a.STOPPED_MEASUREMENT[1][1]),
                    "case_b_review_comment_id": str(pt8a.STOPPED_MEASUREMENT[1][3]),
                    "case_c_dispatched_run_id": str(pt8a.MEASUREMENT_START_AFTER_RUN),
                    "case_c_status": "CANCELLED_BEFORE_PRODUCT_OBSERVATION"}
            row = next(r for r in pt8a.STOPPED_MEASUREMENT if r[3] == comment_id)
            index = pt8a.STOPPED_MEASUREMENT.index(row)
            return {"decision": "EVIDENCE_VALID_MEASUREMENT_ONLY", "reviewed_source_sha": pt8a.STOPPED_MEASUREMENT_SOURCE,
                "backend_image": pt8a.STOPPED_MEASUREMENT_IMAGE,
                "measurement_contract_sha256": pt8a.STOPPED_MEASUREMENT_CONTRACT_SHA256,
                "run_id": str(row[0]), "artifact_id": str(row[1]), "artifact_digest": row[2],
                "case_id": pt8a.CASES[index], "observed_status": "REVIEW_PENDING" if index == 0 else "NOT_PASS"}
        def download(run_id, artifact_id, *args, **kwargs):
            self.assertEqual(pt8a.STOPPED_MEASUREMENT[0 if run_id == pt8a.STOPPED_MEASUREMENT[0][0] else 1][1], artifact_id)
            self.assertTrue(kwargs["verify_inventory"])
            return old[run_id]
        with tempfile.TemporaryDirectory() as directory, patch.object(pt8a, "authority_comment", side_effect=comment), \
                patch.object(pt8a, "bound_artifact", side_effect=download):
            result = pt8a.stopped_measurement_originals(Path(directory))
            self.assertFalse(result["mergedIntoNewLedger"])
            self.assertEqual("NOT_PASS", result["A_B"][1]["observedStatus"])
            self.assertEqual("CANCELLED_NO_ARTIFACT", result["C"]["status"])
            with patch.dict(old[pt8a.STOPPED_MEASUREMENT[1][0]][1], {"digest": "sha256:" + "0" * 64}), self.assertRaises(ValueError):
                pt8a.stopped_measurement_originals(Path(directory))
            with patch.dict(old[pt8a.STOPPED_MEASUREMENT[1][0]][0]["ledger.json"][1], {"consumed": False}), self.assertRaises(ValueError):
                pt8a.stopped_measurement_originals(Path(directory))

    def test_duplicates_other_sources_preflight_skips_reruns_ambiguous_history_and_writers_fail_closed(self):
        current = self.history(1)
        with ExitStack() as stack:
            self.patches(stack)
            for changed in ({"head_sha": "c" * 40}, {"run_attempt": 2}, {"status": "in_progress"}, {"conclusion": "skipped"}):
                with self.subTest(changed=changed), patch.dict(self.runtime[0], changed), self.assertRaises(ValueError):
                    pt8a.measurement_history(self.request(1), SOURCE, current)
            ref = self.reference(0)
            with patch.dict(self.jobs[ref["runId"]]["jobs"][0], {"conclusion": "skipped"}), self.assertRaises(ValueError):
                pt8a.measurement_history(self.request(1), SOURCE, current)
            with patch.dict(self.jobs[ref["runId"]], {"total_count": 2}), self.assertRaises(ValueError):
                pt8a.measurement_history(self.request(1), SOURCE, current)
            with patch.dict(self.jobs[ref["runId"]]["jobs"][0]["steps"][0], {"conclusion": "failure"}), self.assertRaises(ValueError):
                pt8a.measurement_history(self.request(1), SOURCE, current)
            with patch.dict(self.archive_rows[ref["runId"]], {"artifacts": []}), self.assertRaises(ValueError):
                pt8a.measurement_history(self.request(1), SOURCE, current)
            self.runtime.append(self.runtime[0] | {"id": current + 1})
            self.jobs[current + 1] = self.jobs[ref["runId"]]
            with self.assertRaises(ValueError): pt8a.measurement_history(self.request(1), SOURCE, current)
            self.runtime.pop()
            self.writes.append({"id": pt8a.ORIGIN_CLEAN_RUN + 1})
            with self.assertRaises(ValueError): pt8a.measurement_history(self.request(1), SOURCE, current)

    def test_integrity_auth_censor_review_or_quality_forgery_stops_next_observation(self):
        current = self.history(1); ref = self.reference(0); data = self.artifacts[ref["runId"]]
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            self.patches(stack)
            history = pt8a.measurement_history(self.request(1), SOURCE, current)
            targets = [(data["ledger.json"][0], {"consumed": False}),
                (data["ledger.json"][0], {"censoredObservationMs": 540001}),
                (data["binding.json"], {"mode": pt8a.DIAGNOSTIC_MODE}),
                (data["binding.json"], {"image": IMAGE[:-1] + "c"}),
                (data["readiness.json"], {"indexWrites": 1}),
                (data["cleanup.json"], {"JWKSRestored": False}),
                (data["observation/accepted.json"], {"status": "INDETERMINATE_ACCEPTANCE"}),
                (data["observation/cli.json"], {"terraformVersion": "1.9.0"}),
                (self.reviews[ref["reviewCommentId"]], {"decision": "ACCEPTED"}),
                (self.reviews[ref["reviewCommentId"]], {"infrastructure_auth_provenance": "UNKNOWN"}),
                (self.reviews[ref["reviewCommentId"]], {"product_quality": "PASS"}),
                (self.reviews[ref["reviewCommentId"]], {"artifact_digest": "sha256:" + "0" * 64})]
            for target, changed in targets:
                with self.subTest(changed=changed), patch.dict(target, changed), self.assertRaises(ValueError):
                    pt8a.diagnostic_predecessor(self.request(1), history, Path(directory), measurement=True)

    def test_final_e_review_closes_measurement_with_negative_quality_not_release_acceptance(self):
        current = self.history(4, final=True); request = self.request(4, final=True)
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            self.patches(stack)
            history = pt8a.measurement_history(request, SOURCE, current, final=True)
            reviewed = pt8a.diagnostic_predecessor(request, history, Path(directory), measurement=True, final=True)
            result = pt8a.measurement_results(reviewed["ledger"], reviewed["reviews"])
            self.assertEqual("COMPLETE", result["measurementState"])
            self.assertEqual("NOT_PASS", result["productQuality"])
            self.assertEqual(1, result["terminalProductFailures"])
            self.assertEqual("NOT_GRANTED", result["releaseAcceptance"])
            self.assertFalse(result["officialAcceptancePromotion"])
            del reviewed["reviews"][pt8a.CASES[-1]]
            self.assertEqual("INCOMPLETE", pt8a.measurement_results(reviewed["ledger"], reviewed["reviews"])["measurementState"])
            ref = self.reference(4)
            with patch.dict(self.reviews[ref["reviewCommentId"]], {"decision": "UNKNOWN"}), self.assertRaises(ValueError):
                pt8a.diagnostic_predecessor(request, history, Path(directory), measurement=True, final=True)

    def test_complete_measurement_can_have_pass_or_undetermined_quality_without_promoting_old_samples(self):
        rows = [self.base.record(i) for i in range(5)]
        for dimension, expected in (("PASS", "PASS"), ("UNKNOWN", "UNDETERMINED")):
            reviews = {case: {"material_defect": "false", "false_trusted_success": "0",
                **{"dimension_" + d: dimension for d in pt8a.SCORING}} for case in pt8a.CASES}
            result = pt8a.measurement_results(rows, reviews)
            self.assertEqual("COMPLETE", result["measurementState"])
            self.assertEqual(expected, result["productQuality"])
            self.assertFalse(result["officialAcceptancePromotion"])
            reviews[pt8a.CASES[0]]["false_trusted_success"] = "1"
            self.assertEqual("NOT_PASS", pt8a.measurement_results(rows, reviews)["productQuality"])

    def test_original_successful_diagnostic_is_bound_to_its_original_source_digest_and_consumption(self):
        job = {"id": "original-job", "status": "SUCCEEDED", "projectId": 12, "sourceFileId": 17}
        data = {"binding.json": {"sourceSha": pt8a.GENERATION_OBSERVATION_SOURCE,
                "mode": pt8a.GENERATION_DIAGNOSTIC_MODE, "caseId": pt8a.CASES[0],
                "candidateIdentity": pt8a.IDENTITY, "diagnosticContractSha256": pt8a.generation_diagnostic_contract_sha256()},
            "record.json": {"consumed": True, "uploadAttempts": 1, "jobId": job["id"]},
            "observation/accepted.json": {"status": "ACCEPTED", "consumed": True, "uploadAttempts": 1,
                "jobId": job["id"], "projectId": 12, "sourceFileId": 17}, "observation/job.json": job}
        bound = {"runId": pt8a.GENERATION_OBSERVATION_RUN, "artifactId": pt8a.GENERATION_OBSERVATION_ARTIFACT,
            "sourceSha": pt8a.GENERATION_OBSERVATION_SOURCE, "digest": pt8a.GENERATION_OBSERVATION_DIGEST, "conclusion": "success"}
        with tempfile.TemporaryDirectory() as directory, patch.object(pt8a, "recovery_originals", return_value={}), \
                patch.object(pt8a, "generation_diagnostic_origin", return_value={"consumed": True}), \
                patch.object(pt8a, "stopped_measurement_originals", return_value={"status": "AUTHENTICATED"}), \
                patch.object(pt8a, "bound_artifact", return_value=(data, bound)) as download:
            self.assertEqual(bound, pt8a.measurement_originals(Path(directory))["consumedGenerationDiagnostic"])
            self.assertTrue(download.call_args.kwargs["verify_inventory"])
            for target, changed in ((bound, {"sourceSha": SOURCE}), (bound, {"digest": "sha256:" + "0" * 64}),
                    (data["binding.json"], {"mode": pt8a.MEASUREMENT_MODE}), (data["record.json"], {"consumed": False}),
                    (data["observation/accepted.json"], {"uploadAttempts": 2}), (job, {"status": "FAILED"})):
                with self.subTest(changed=changed), patch.dict(target, changed), self.assertRaises(ValueError):
                    pt8a.measurement_originals(Path(directory))

    def test_real_main_observes_one_negative_case_seals_evidence_and_finish_is_read_only(self):
        for final in (False, True):
            with self.subTest(final=final), tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
                root = Path(directory); index = 4 if final else 0; current = self.history(index, final)
                self.patches(stack)
                request_file = root / "request.json"; request_file.write_text(json.dumps(self.request(index, final)))
                (root / "coverage-report.json").write_text("{}")
                (root / "pt8a-release.json").write_text(json.dumps(self.artifacts[self.reference(index)["runId"]]["release.json"]))
                output = root / "evidence"
                stack.enter_context(patch.dict(os.environ, {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE,
                    "GITHUB_RUN_ATTEMPT": "1", "GITHUB_RUN_ID": str(current), "OPERATION": pt8a.MEASUREMENT_OPERATION, "RUNNER_TEMP": directory}))
                stack.enter_context(patch.object(sys, "argv", ["pt8a", "finish" if final else "run",
                    "--request-file", str(request_file), "--corpus", directory, "--output", str(output)]))
                stack.enter_context(patch.object(pt8a, "measurement_originals", return_value={"originals": "UNCHANGED_CONSUMED"}))
                stack.enter_context(patch.object(pt8a, "corrective_origins", return_value=(self.base.origin.clean["receipt.json"],
                    self.base.origin.clean_binding, self.base.origin.failed_binding)))
                stack.enter_context(patch.object(pt8a.rag, "load_corpus", return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
                stack.enter_context(patch.object(pt8a.rag, "validate_pt8a_clean_corpus"))
                scans = stack.enter_context(patch.object(pt8a, "risk_qualified_admission", return_value=self.base.snapshot.copy()))
                processes = stack.enter_context(patch.object(pt8a.subprocess, "run"))
                client = stack.enter_context(patch.object(pt8a.transport, "CurlClient"))
                def acquire(case, private):
                    fixture = private / "input.png"; fixture.write_bytes(b"synthetic-only"); return fixture
                fetch = stack.enter_context(patch.object(pt8a, "acquire", side_effect=acquire))
                def observed(client, fixture, directory, record, documents, pod, **kwargs):
                    self.assertEqual({"diagnostic": True, "require_diagnostics": True}, kwargs)
                    record.update(self.artifacts[self.reference(index)["runId"]]["ledger.json"][index])
                observe = stack.enter_context(patch.object(pt8a, "observe", side_effect=observed))
                if final:
                    pt8a.main()
                    observe.assert_not_called(); fetch.assert_not_called(); scans.assert_not_called()
                    client.assert_not_called(); processes.assert_not_called()
                    self.assertEqual("COMPLETE", json.loads((output / "measurement-summary.json").read_text())["measurementState"])
                else:
                    with self.assertRaisesRegex(ValueError, "material technical/product failure"): pt8a.main()
                    observe.assert_called_once(); fetch.assert_called_once(); self.assertEqual(2, scans.call_count)
                    self.assertEqual("REVIEWABLE_DRAFT_MEASUREMENT_ONLY", json.loads((output / "binding.json").read_text())["classification"])
                    rows = json.loads((output / "ledger.json").read_text())
                    self.assertEqual(["NOT_PASS", "NOT_RUN", "NOT_RUN", "NOT_RUN", "NOT_RUN"], [r["status"] for r in rows])
                self.assertTrue((output / "inventory.json").is_file())


class SingleGenerationDiagnosticContracts(unittest.TestCase):
    def setUp(self):
        self.base = QualifiedCaseChainContracts(); self.base.setUp()
        self.addCleanup(patch.stopall)
        self.request = {"mode": pt8a.GENERATION_DIAGNOSTIC_MODE, "caseId": pt8a.CASES[0], "liveApprovalCommentId": 900,
            "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN, "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
            "priorRunId": pt8a.TIMEOUT_RUN, "priorArtifactId": pt8a.TIMEOUT_ARTIFACT, "priorReviewCommentId": pt8a.TIMEOUT_REVIEW}
        self.live = pt8a.generation_diagnostic_live_fields(SOURCE, IMAGE)
        self.current = pt8a.TIMEOUT_RUN + 100
        self.runtime = [{"id": pt8a.TIMEOUT_RUN, "head_sha": pt8a.TIMEOUT_SOURCE, "run_attempt": 1, "status": "completed", "conclusion": "failure"},
            {"id": self.current, "head_sha": SOURCE, "run_attempt": 1, "status": "in_progress", "conclusion": None}]
        self.jobs = {self.current: {"total_count": 1, "jobs": [{"name": pt8a.DIAGNOSTIC_OPERATION + "/" + pt8a.CASES[0],
            "status": "in_progress", "conclusion": None}]}}
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]

    def api(self, path):
        self.assertNotIn("head_sha=", path)
        if path.startswith("compare/"): return {"status": "ahead"}
        if "gcp-target-runtime-dependencies.yml/runs?" in path: return {"total_count": len(self.runtime), "workflow_runs": self.runtime}
        if "gcp-target-corpus-ingestion.yml/runs?" in path: return {"total_count": len(self.writes), "workflow_runs": self.writes}
        return self.jobs[int(path.split('/')[2])]

    def test_distinct_authority_binds_every_field_and_cannot_authorize_other_modes_or_cases(self):
        body = '[HUMAN_GATE_APPROVAL:v1]\n' + '\n'.join(k + ': ' + v for k,v in self.live.items())
        self.assertEqual(self.live, pt8a.fields(body, '[HUMAN_GATE_APPROVAL:v1]'))
        with patch.object(pt8a.transport, 'current_main', return_value=SOURCE), patch.object(pt8a, 'github', side_effect=self.api):
            for key in self.live:
                with self.subTest(field=key), patch.object(pt8a, 'authority_comment', return_value=self.live | {key: 'wrong'}), self.assertRaises(ValueError):
                    pt8a.generation_diagnostic_request_contract(self.request, SOURCE, IMAGE, 1)
            with patch.object(pt8a, 'authority_comment', return_value=self.live):
                pt8a.generation_diagnostic_request_contract(self.request, SOURCE, IMAGE, 1)
                for changed in ({'mode': pt8a.DIAGNOSTIC_MODE}, {'caseId': pt8a.CASES[1]}, {'priorRunId': pt8a.CASE_A_RUN},
                                {'priorArtifactId': 1}, {'priorReviewCommentId': 1}, {'liveApprovalCommentId': True}):
                    with self.subTest(changed=changed), self.assertRaises(ValueError):
                        pt8a.generation_diagnostic_request_contract(self.request | changed, SOURCE, IMAGE, 1)
                with self.assertRaises(ValueError): pt8a.generation_diagnostic_request_contract(self.request, SOURCE, IMAGE, 2)
                with self.assertRaises(ValueError): pt8a.diagnostic_request_contract(self.request | {'mode': pt8a.DIAGNOSTIC_MODE}, SOURCE, IMAGE, 1)
            for old in (pt8a.diagnostic_live_fields(SOURCE, IMAGE), {'purpose': pt8a.RECOVERY_PURPOSE}):
                with patch.object(pt8a, 'authority_comment', return_value=old), self.assertRaises(ValueError):
                    pt8a.generation_diagnostic_request_contract(self.request, SOURCE, IMAGE, 1)
            with patch.object(pt8a.transport, 'current_main', return_value='c'*40), patch.object(pt8a, 'authority_comment', return_value=self.live), self.assertRaisesRegex(ValueError, 'MAIN_DRIFT'):
                pt8a.generation_diagnostic_request_contract(self.request, SOURCE, IMAGE, 1)

    def test_history_admits_unrelated_skips_but_blocks_any_second_episode_across_sources(self):
        with patch.object(pt8a, 'github', side_effect=self.api):
            self.runtime.append({'id': self.current-1, 'head_sha': 'd'*40, 'run_attempt': 1})
            self.jobs[self.current-1] = {'total_count': 1, 'jobs': [{'name': pt8a.SKIPPED_RECOVERY_JOB_NAME,
                'status': 'completed', 'conclusion': 'skipped'}]}
            pt8a.generation_diagnostic_history(SOURCE, self.current)
            for name in (pt8a.DIAGNOSTIC_OPERATION+'/'+pt8a.CASES[0], pt8a.QUALIFIED_OPERATION+'/'+pt8a.CASES[0], pt8a.RECOVERY_OPERATION):
                for outcome in ('failure', 'success', 'skipped', None):
                    self.jobs[self.current-1]['jobs'][0].update(name=name, conclusion=outcome)
                    with self.subTest(name=name, outcome=outcome), self.assertRaises(ValueError):
                        pt8a.generation_diagnostic_history(SOURCE, self.current)
            self.jobs[self.current-1]['jobs'][0].update(name=pt8a.SKIPPED_RECOVERY_JOB_NAME, status='in_progress', conclusion='skipped')
            with self.assertRaises(ValueError): pt8a.generation_diagnostic_history(SOURCE, self.current)
            self.jobs[self.current-1]['jobs'][0].update(status='completed', conclusion='skipped')
            self.jobs[self.current-1]['total_count'] = 2
            with self.assertRaises(ValueError): pt8a.generation_diagnostic_history(SOURCE, self.current)
            self.jobs[self.current-1]['total_count'] = 1
            self.writes.append({'id': pt8a.ORIGIN_CLEAN_RUN+1, 'head_sha': SOURCE})
            with self.assertRaises(ValueError): pt8a.generation_diagnostic_history(SOURCE, self.current)
            self.writes.pop(); self.runtime[0]['conclusion'] = 'success'
            with self.assertRaises(ValueError): pt8a.generation_diagnostic_history(SOURCE, self.current)

    def timeout_fixture(self):
        original = {'mode': pt8a.RECOVERY_MODE, 'campaignId': 'pt8a-recovery-1d33cf88-v1', 'liveApprovalCommentId': 6081758174,
            'resumeRunId': 0, 'liveBounds': {'maxUploads': 5, 'maxModelCalls': 30, 'maxDispatches': 2, 'wallclockMinutes': 90, 'modelLocation': 'global'}}
        data = copy.deepcopy(self.base.artifacts[self.base.reference(0)['runId']])
        record = self.base.record(0) | {'status': 'NOT_PASS', 'jobId': 'a195d5ff-fce0-4728-80d5-ac2ebaf75d1d', 'terminalState': 'FAILED'}
        data.update({'binding.json': pt8a.recovery_binding(original, pt8a.TIMEOUT_SOURCE, IMAGE, pt8a.TIMEOUT_RUN, pt8a.TIMEOUT_RUN) | {'caseId': pt8a.CASES[0]},
            'record.json': record, 'cleanup.json': {'JWKSRestored': True}, 'observation/accepted.json': record | {'status': 'ACCEPTED'},
            'observation/draft-identity.json': {'hclPresent': False}, 'observation/cli.json': {'status': 'NOT_RUN', 'reason': 'BACKEND_FAILED'},
            'inventory.json': {'files': []}, 'observation/retrieval.json': {'jobId': record['jobId']}})
        data['observation/job.json'].update(id=record['jobId'], status='FAILED', resultFileId=None, resultObjectKey=None,
            quality={'contractVersion': 'evidence-quality-v1', 'technicalStatus': 'FAIL', 'knowledgeStatus': 'UNKNOWN', 'reasons': ['PROVIDER_TIMEOUT']})
        data['observation/presentation.json'].update(latestAnalysisJobId=record['jobId'], analysisStatus='FAILED', latestResultObjectKey=None)
        bound = {'runId': pt8a.TIMEOUT_RUN, 'artifactId': pt8a.TIMEOUT_ARTIFACT, 'digest': pt8a.TIMEOUT_DIGEST, 'sourceSha': pt8a.TIMEOUT_SOURCE, 'conclusion': 'failure'}
        review = {'decision': 'AUTHENTIC_CAMPAIGN_TECHNICAL_FAILURE_INCOMPLETE', 'reviewed_run_id': str(pt8a.TIMEOUT_RUN),
            'reviewed_run_attempt': '1', 'reviewed_source_sha': pt8a.TIMEOUT_SOURCE, 'reviewed_approval_comment_id': '6081758174',
            'case_a_status': 'TECHNICAL_FAILURE_CONSUMED', 'case_a_failure': 'PROVIDER_TIMEOUT_INITIAL_GENERATION', 'cases_b_to_e': 'NOT_RUN'}
        approval = pt8a.recovery_live_fields(original, pt8a.TIMEOUT_SOURCE, IMAGE)
        return data, bound, review, approval

    def test_original_accepted_timeout_is_authenticated_and_never_rebound(self):
        data, bound, review, approval = self.timeout_fixture()
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, 'recovery_originals', return_value={}), \
             patch.object(pt8a, 'bound_artifact', return_value=(data, bound)) as archive, \
             patch.object(pt8a, 'authority_comment', side_effect=lambda ident,marker: review if ident==pt8a.TIMEOUT_REVIEW else approval):
            before = copy.deepcopy(data)
            result = pt8a.generation_diagnostic_origin(Path(d))
            self.assertEqual(bound, result['consumedTimeout']); self.assertEqual(before, data)
            self.assertTrue(archive.call_args.kwargs['verify_inventory']); self.assertTrue(archive.call_args.kwargs['all_json'])
            for target,key,value in ((bound,'sourceSha',SOURCE),(bound,'digest','sha256:'+'0'*64),(review,'decision','ACCEPTED'),
                                     (data['record.json'],'consumed',False),(data['observation/accepted.json'],'uploadAttempts',0),
                                     (data['cleanup.json'],'JWKSRestored',False),(data['binding.json'],'liveApprovalCommentId',900)):
                old = target[key]; target[key] = value
                with self.subTest(key=key), self.assertRaises(ValueError): pt8a.generation_diagnostic_origin(Path(d))
                target[key] = old

    def test_main_collects_one_distinct_record_and_preserves_failure_without_changing_original_ledger(self):
        for status in ('NOT_PASS', 'REVIEW_PENDING', 'INDETERMINATE_ACCEPTANCE'):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as d, ExitStack() as stack:
                root=Path(d); request_file=root/'request.json'; output=root/'evidence'
                request_file.write_text(json.dumps(self.request)); (root/'coverage-report.json').write_text('{}')
                (root/'pt8a-release.json').write_text(json.dumps(self.base.release | {'modelProject':'terraformers-platform','modelLocation':'global'}))
                stack.enter_context(patch.dict(os.environ, {'GITHUB_SHA':SOURCE,'BACKEND_IMAGE':IMAGE,'GITHUB_RUN_ATTEMPT':'1',
                    'GITHUB_RUN_ID':str(self.current),'OPERATION':pt8a.DIAGNOSTIC_OPERATION,'RUNNER_TEMP':d}))
                stack.enter_context(patch.object(sys,'argv',['pt8a','run','--request-file',str(request_file),'--corpus',d,'--output',str(output)]))
                stack.enter_context(patch.object(pt8a,'github',side_effect=self.api))
                stack.enter_context(patch.object(pt8a,'authority_comment',return_value=self.live))
                stack.enter_context(patch.object(pt8a.transport,'current_main',return_value=SOURCE))
                original={'consumedTimeout':{'runId':pt8a.TIMEOUT_RUN,'digest':pt8a.TIMEOUT_DIGEST},'record':{'consumed':True,'status':'NOT_PASS'}}
                stack.enter_context(patch.object(pt8a,'generation_diagnostic_origin',return_value=original))
                stack.enter_context(patch.object(pt8a,'corrective_origins',return_value=(self.base.origin.clean['receipt.json'],self.base.origin.clean_binding,self.base.origin.failed_binding)))
                stack.enter_context(patch.object(pt8a.rag,'load_corpus',return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
                stack.enter_context(patch.object(pt8a.rag,'validate_pt8a_clean_corpus'))
                scans=stack.enter_context(patch.object(pt8a,'risk_qualified_admission',return_value=self.base.snapshot.copy()))
                stack.enter_context(patch.object(pt8a.subprocess,'run')); stack.enter_context(patch.object(pt8a.transport,'CurlClient'))
                def acquire(case,private):
                    self.assertEqual(self.base.inputs[0],case)
                    fixture=private/'input.png';fixture.write_bytes(b'synthetic-test-only');return fixture
                stack.enter_context(patch.object(pt8a,'acquire',side_effect=acquire))
                def observed(client,fixture,directory,record,documents,pod,**kwargs):
                    self.assertEqual({'diagnostic':True},kwargs);record.update(status=status,uploadAttempts=1)
                    if status=='INDETERMINATE_ACCEPTANCE':raise ValueError('ambiguous upload acceptance; never resubmit')
                    record.update(consumed=True,terminalState='FAILED' if status=='NOT_PASS' else 'SUCCEEDED')
                observation=stack.enter_context(patch.object(pt8a,'observe',side_effect=observed))
                if status=='REVIEW_PENDING':pt8a.main()
                else:
                    with self.assertRaises(ValueError):pt8a.main()
                observation.assert_called_once();self.assertEqual(1 if status=='INDETERMINATE_ACCEPTANCE' else 2,scans.call_count)
                self.assertEqual(original,json.loads((output/'original-observations.json').read_text()))
                self.assertFalse((output/'ledger.json').exists())
                self.assertEqual(status,json.loads((output/'record.json').read_text())['status'])
                binding=json.loads((output/'binding.json').read_text())
                self.assertEqual(pt8a.GENERATION_DIAGNOSTIC_MODE,binding['mode'])
                self.assertEqual(pt8a.generation_diagnostic_contract_sha256(),binding['diagnosticContractSha256'])
                self.assertEqual('NOT_ACCEPTANCE',json.loads((output/'diagnostic-disposition.json').read_text())['officialAcceptance'])
                self.assertTrue((output/'inventory.json').exists())


class AutonomousRecoveryContracts(unittest.TestCase):
    def setUp(self):
        self.base = QualifiedCaseChainContracts(); self.base.setUp()
        self.addCleanup(patch.stopall)
        # These are explicit synthetic approval values, never deployment/cost defaults.
        self.request = {"mode": pt8a.RECOVERY_MODE, "campaignId": "synthetic-recovery-1", "liveApprovalCommentId": 800,
            "resumeRunId": 0, "liveBounds": {"maxUploads": 5, "maxModelCalls": 30, "maxDispatches": 4,
                "wallclockMinutes": 90, "modelLocation": "global"}}
        self.current = pt8a.CASE_B_RUN + 10000
        self.runtime = [{"id": self.current, "head_sha": SOURCE, "run_attempt": 1, "status": "in_progress"}]
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        self.jobs = {self.current: {"total_count": 1, "jobs": [{"name": pt8a.RECOVERY_OPERATION, "conclusion": None,
            "started_at": pt8a.datetime.now().astimezone().isoformat()}]}}
        self.artifacts, self.downloads = {}, {}
        self.release = self.base.release | {"modelProject": "terraformers-platform", "modelLocation": "global"}

    def checkpoint(self, index):
        data = copy.deepcopy(self.base.artifacts[self.base.reference(index)["runId"]])
        record = self.base.record(index) | {"acceptedAt": "2026-10-09T09:31:00Z", "campaignState": "PRODUCT_REVIEW_REQUIRED"}
        data.update({"record.json": record, "cleanup.json": {"JWKSRestored": True}, "release.json": self.release})
        data["observation/accepted.json"] = record | {"status": "ACCEPTED"}
        data["observation/draft-identity.json"]["hclPresent"] = True
        return data

    def prior(self, cases, *, run=None):
        run = run or self.current - 1
        owner = min([r["id"] for r in self.runtime if r["id"] < self.current] + [run])
        self.runtime.append({"id": run, "head_sha": SOURCE, "run_attempt": 1, "status": "completed", "conclusion": "failure"})
        self.request["resumeRunId"] = run
        steps = [{"name": "Observe recovery case " + c[-1].upper(), "status": "completed",
                  "conclusion": "success" if c in cases else "skipped"} for c in pt8a.CASES]
        self.jobs[run] = {"total_count": 1, "jobs": [{"name": pt8a.RECOVERY_OPERATION, "steps": steps, "conclusion": "failure"}]}
        arts = []
        for suffix,data in {"start": {"campaign.json": {}}, **{c[-1]: d for c,d in cases.items()}}.items():
            aid = run * 10 + len(arts)
            bound = {"runId": run, "artifactId": aid, "digest": "sha256:" + str(aid).zfill(64), "sourceSha": SOURCE, "conclusion": "failure"}
            arts.append({"id": aid, "name": f"pt8a-recovery-{run}-{suffix}", "digest": bound["digest"]})
            data["binding.json"] = pt8a.recovery_binding(self.request, SOURCE, IMAGE, run, owner) | (
                {"caseId": "aws-official-" + suffix} if suffix != "start" else {})
            self.downloads[aid] = (data, bound)
        self.artifacts[run] = {"total_count": len(arts), "artifacts": arts}
        return run

    def api(self, path):
        self.assertNotIn("head_sha=", path)
        if "gcp-target-runtime-dependencies.yml/runs?" in path: return {"total_count": len(self.runtime), "workflow_runs": self.runtime}
        if "gcp-target-corpus-ingestion.yml/runs?" in path: return {"total_count": len(self.writes), "workflow_runs": self.writes}
        rid = int(path.split('/')[2])
        return self.jobs[rid] if '/jobs?' in path else self.artifacts[rid]

    def history(self):
        with tempfile.TemporaryDirectory() as d, patch.object(pt8a, 'github', side_effect=self.api), \
             patch.object(pt8a, 'bound_artifact', side_effect=lambda r,a,*args,**kwargs: self.downloads[a]):
            return pt8a.recovery_history(self.request, SOURCE, IMAGE, self.current, Path(d))

    def test_explicit_exact_live_budgets_and_risk_required_repository_approval_cannot_execute(self):
        live = pt8a.recovery_live_fields(self.request, SOURCE, IMAGE)
        # Verify every required field is actually parseable from a future USER comment.
        text = '[HUMAN_GATE_APPROVAL:v1]\n' + '\n'.join(k+': '+v for k,v in live.items())
        self.assertEqual(live, pt8a.fields(text, '[HUMAN_GATE_APPROVAL:v1]'))
        with patch.object(pt8a.transport, 'current_main', return_value=SOURCE):
            for field in ('purpose','decision','backend_image','reviewed_source_sha','campaign_id','recovery_contract_sha256',
                          'maximum_total_uploads','maximum_model_calls','model_location','accepted_residual_risk'):
                with self.subTest(field=field), patch.object(pt8a,'authority_comment',return_value=live | {field:'wrong'}), self.assertRaises(ValueError):
                    pt8a.recovery_request_contract(self.request,SOURCE,IMAGE,1)
            with patch.object(pt8a,'authority_comment',return_value=live):
                pt8a.recovery_request_contract(self.request,SOURCE,IMAGE,1)
                for change in ({'maxModelCalls':None},{'maxUploads':6},{'wallclockMinutes':None},{'modelLocation':None}):
                    with self.subTest(change=change),self.assertRaises(ValueError):
                        pt8a.recovery_request_contract(self.request | {'liveBounds':self.request['liveBounds'] | change},SOURCE,IMAGE,1)
                with self.assertRaises(ValueError):pt8a.recovery_request_contract(self.request,SOURCE,IMAGE,2)

    def test_partial_checkpoint_resumes_next_case_without_restarting_or_promoting_a(self):
        data=self.checkpoint(0)
        data['record.json']['status']='NOT_PASS'
        data['observation/job.json']['quality']={'technicalStatus':'UNKNOWN','knowledgeStatus':'DEGRADED'}
        data['observation/cli.json']['initValidateExitCode']=1
        self.prior({pt8a.CASES[0]:data})
        history=self.history()
        self.assertEqual('PRODUCT_REVIEW_REQUIRED',pt8a.recovery_checkpoint(history['records'][pt8a.CASES[0]],pt8a.CASES[0]))
        self.assertEqual('NOT_PASS',history['records'][pt8a.CASES[0]]['record.json']['status'])
        self.assertNotIn(pt8a.CASES[1],history['records'])
        for r in ('resumeRunId','liveApprovalCommentId','campaignId'):
            saved=self.request[r];self.request[r]=0 if r!='campaignId' else 'changed-campaign'
            with self.subTest(field=r),self.assertRaises(ValueError):self.history()
            self.request[r]=saved

    def test_duplicates_source_drift_missing_checkpoint_and_out_of_order_fail_closed(self):
        data=self.checkpoint(0);run=self.prior({pt8a.CASES[0]:data})
        baseline=copy.deepcopy((self.runtime,self.jobs,self.artifacts,self.downloads,self.request))
        def reset():self.runtime,self.jobs,self.artifacts,self.downloads,self.request=copy.deepcopy(baseline)
        self.prior({pt8a.CASES[0]:self.checkpoint(0)},run=run+1)
        with self.assertRaises(ValueError):self.history()
        reset();self.runtime[-1]['head_sha']='c'*40
        with self.assertRaises(ValueError):self.history()
        reset();self.artifacts[run]['artifacts'].pop();self.artifacts[run]['total_count']=1
        with self.assertRaises(ValueError):self.history()
        reset();self.jobs[run]['jobs'][0]['steps'].pop()
        with self.assertRaises(ValueError):self.history()
        reset();self.runtime[-1]['run_attempt']=2
        with self.assertRaises(ValueError):self.history()
        self.runtime=self.runtime[:1];self.request['resumeRunId']=0
        self.prior({pt8a.CASES[1]:self.checkpoint(1)},run=run)
        with self.assertRaises(ValueError):self.history()

    def test_one_read_only_drain_keeps_original_censor_and_cannot_be_a_second_upload(self):
        data=self.checkpoint(0)
        data['record.json'].pop('terminalState');data['record.json']['censoredObservationMs']=541000
        run=self.prior({pt8a.CASES[0]:data},run=self.current-2)
        ref=self.history()['references'][pt8a.CASES[0]]
        late=self.checkpoint(0);late['record.json'].update(readOnlyDrain=True,originalObservation=ref,censoredObservationMs=541000)
        self.prior({pt8a.CASES[0]:late},run=run+1)
        state=self.history();self.assertEqual(541000,state['records'][pt8a.CASES[0]]['record.json']['censoredObservationMs'])
        late['record.json']['censoredObservationMs']=1000
        with self.assertRaises(ValueError):self.history()
        late['record.json']['censoredObservationMs']=541000;late['record.json']['readOnlyDrain']=False
        with self.assertRaises(ValueError):self.history()

    def test_failed_provider_ambiguous_post_and_incomplete_provenance_never_unlock_next_case(self):
        for kind in ('provider','ambiguous','missing-post-admission','cleanup'):
            data=self.checkpoint(0)
            if kind=='provider':
                data['record.json']['terminalState']='FAILED';data['observation/job.json'].update(status='FAILED',failureReason='PROVIDER_RATE_LIMITED')
            elif kind=='ambiguous':data['record.json']['status']='INDETERMINATE_ACCEPTANCE'
            elif kind=='missing-post-admission':del data['post-admission.json']
            else:data['cleanup.json']['JWKSRestored']=False
            self.assertIn(pt8a.recovery_checkpoint(data,pt8a.CASES[0]) if kind!='cleanup' else 'BLOCKED',('TECHNICAL_FAILURE','BLOCKED'))
            if kind=='cleanup':
                with self.assertRaises(ValueError):pt8a.recovery_checkpoint(data,pt8a.CASES[0])
            self.runtime=self.runtime[:1];self.prior({pt8a.CASES[0]:data,pt8a.CASES[1]:self.checkpoint(1)})
            with self.assertRaises(ValueError):self.history()

    def test_budget_exhaustion_and_unknown_history_cannot_reset_campaign(self):
        self.prior({pt8a.CASES[0]:self.checkpoint(0)})
        self.request['liveBounds']['maxDispatches']=1
        with self.assertRaises(ValueError):self.history()
        self.request['liveBounds']['maxDispatches']=4
        self.jobs[self.current-1]['jobs'][0]['name']='unknown job'
        with self.assertRaises(ValueError):self.history()

    def test_preflight_without_artifact_needs_complete_precloud_proof_and_consumes_dispatch_budget(self):
        run = self.current - 1
        self.runtime.append({'id': run, 'head_sha': SOURCE, 'run_attempt': 1,
            'status': 'completed', 'conclusion': 'failure'})
        self.request['resumeRunId'] = run
        workflow = yaml.safe_load((pt8a.ROOT / '.github/workflows/gcp-target-runtime-dependencies.yml').read_text())
        steps = [{'name': step.get('name', 'Run ' + step.get('uses', '')), 'number': n,
            'status': 'completed', 'conclusion': 'skipped'}
            for n, step in enumerate(workflow['jobs']['pt8a-official-acceptance']['steps'], 1)]
        steps[0]['conclusion'] = 'success'
        steps[1]['conclusion'] = 'failure'
        job = {'id': 42, 'name': pt8a.RECOVERY_OPERATION, 'status': 'completed',
            'conclusion': 'failure', 'steps': steps}
        self.jobs[run] = {'total_count': 1, 'jobs': [job]}
        self.artifacts[run] = {'total_count': 0, 'artifacts': []}
        history = self.history()
        self.assertEqual({}, history['records'])
        self.assertEqual(self.current, history['ownerRunId'])  # No unbound prior owner or image claim.
        proof = history['provenNoPostPreflightFailures'][0]
        self.assertEqual((run, SOURCE, 0, 0), (proof['runId'], proof['sourceSha'], proof['uploadAttempts'], proof['modelCalls']))
        self.assertEqual('UNAVAILABLE_PRE_CLOUD_NOT_REBOUND', proof['requestBinding'])
        self.request['liveBounds']['maxDispatches'] = 1
        with self.assertRaisesRegex(ValueError, 'dispatch budget'): self.history()
        self.request['liveBounds']['maxDispatches'] = 4
        for change in ('cloud-started', 'missing-cloud-step', 'unknown-started-step', 'unknown-before-gate', 'incomplete-step', 'wrong-source'):
            with self.subTest(change=change):
                saved = copy.deepcopy((self.runtime, self.jobs))
                if change == 'cloud-started': steps[3]['conclusion'] = 'success'
                elif change == 'missing-cloud-step': steps.pop(3)
                elif change == 'unknown-started-step': steps.append({'name': 'unrecognized execution',
                    'number': 999, 'status': 'completed', 'conclusion': 'success'})
                elif change == 'unknown-before-gate': steps[0]['name'] = 'unrecognized preflight execution'
                elif change == 'incomplete-step': steps[-1]['status'] = 'in_progress'
                else: self.runtime[-1]['head_sha'] = 'c' * 40
                with self.assertRaises(ValueError): self.history()
                self.runtime, self.jobs = saved
                job = self.jobs[run]['jobs'][0]; steps = job['steps']

    def test_zero_post_interruption_retries_only_unsubmitted_case_and_ambiguous_post_stays_consumed(self):
        # Execute the actual runner twice around a failed image acquisition. A is already
        # complete, B has no POST; after resume B's ambiguous POST must never be repeated.
        original_a = self.checkpoint(0)
        self.prior({pt8a.CASES[0]: original_a}, run=self.current - 2)
        with tempfile.TemporaryDirectory() as d, ExitStack() as stack:
            root = Path(d); output = root / 'first'; outputs = root / 'outputs'; outputs.touch()
            (root / 'coverage-report.json').write_text('{}')
            (root / 'pt8a-release.json').write_text(json.dumps(self.release))
            env = {'GITHUB_SHA': SOURCE, 'BACKEND_IMAGE': IMAGE, 'GITHUB_RUN_ATTEMPT': '1',
                'GITHUB_RUN_ID': str(self.current), 'OPERATION': pt8a.RECOVERY_OPERATION,
                'RUNNER_TEMP': d, 'GITHUB_ENV': str(root / 'env'), 'GITHUB_OUTPUT': str(outputs)}
            stack.enter_context(patch.dict(os.environ, env))
            stack.enter_context(patch.object(pt8a.transport, 'current_main', return_value=SOURCE))
            stack.enter_context(patch.object(pt8a, 'authority_comment', side_effect=lambda *a:pt8a.recovery_live_fields(self.request, SOURCE, IMAGE)))
            stack.enter_context(patch.object(pt8a, 'github', side_effect=self.api))
            stack.enter_context(patch.object(pt8a, 'bound_artifact', side_effect=lambda r,a,*args,**kw:self.downloads[a]))
            stack.enter_context(patch.object(pt8a, 'recovery_originals', return_value={'A':'consumed','B':'consumed'}))
            stack.enter_context(patch.object(pt8a, 'corrective_origins', return_value=(self.base.origin.clean['receipt.json'],
                self.base.origin.clean_binding, self.base.origin.failed_binding)))
            stack.enter_context(patch.object(pt8a.rag, 'load_corpus', return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
            stack.enter_context(patch.object(pt8a.rag, 'validate_pt8a_clean_corpus'))
            stack.enter_context(patch.object(pt8a, 'risk_qualified_admission', return_value=self.base.snapshot))
            stack.enter_context(patch.object(pt8a.subprocess, 'run'))
            fetch = stack.enter_context(patch.object(pt8a, 'acquire', side_effect=ValueError('synthetic pre-POST acquisition failure')))
            client = stack.enter_context(patch.object(pt8a.transport, 'CurlClient'))
            client.return_value.request.return_value = {'transportExitCode':0, 'httpStatus':500, 'json':{}}
            def call(action, case=None):
                (root / 'request.json').write_text(json.dumps(self.request))
                argv = ['pt8a', action, '--request-file', str(root / 'request.json'), '--output', str(output), '--corpus', d]
                if case: argv += ['--case-id', case]
                with patch.object(sys, 'argv', argv): pt8a.main()
            call('preflight'); call('run', pt8a.CASES[0]); call('run', pt8a.CASES[1])
            self.assertEqual(1, fetch.call_count); client.assert_not_called()
            pt8a.write(output / 'b/cleanup.json', {'JWKSRestored':True}); call('checkpoint', pt8a.CASES[1])
            failed = {p.relative_to(output / 'b').as_posix():json.loads(p.read_text()) for p in (output / 'b').rglob('*.json')}
            self.assertEqual('PROVEN_NO_POST', pt8a.recovery_checkpoint(failed, pt8a.CASES[1]))
            for change in ({'submissionBoundary':'ENTERED'}, {'modelCalls':None}, {'consumed':True}, {'jobId':'maybe-accepted'}):
                with self.subTest(change=change):
                    bad = copy.deepcopy(failed); bad['record.json'].update(change)
                    self.assertEqual('BLOCKED', pt8a.recovery_checkpoint(bad, pt8a.CASES[1]))
            bad = copy.deepcopy(failed); bad['observation/attempt.json'] = {'status':'SUBMISSION_STARTED'}
            self.assertEqual('BLOCKED', pt8a.recovery_checkpoint(bad, pt8a.CASES[1]))
            previous_run = self.current
            self.runtime = [r for r in self.runtime if r['id'] != previous_run]
            self.current += 1
            self.runtime.insert(0, {'id':self.current, 'head_sha':SOURCE, 'run_attempt':1, 'status':'in_progress'})
            self.jobs[self.current] = {'total_count':1,'jobs':[{'name':pt8a.RECOVERY_OPERATION,
                'conclusion':None,'started_at':pt8a.datetime.now().astimezone().isoformat()}]}
            self.prior({pt8a.CASES[1]:failed}, run=previous_run)
            os.environ['GITHUB_RUN_ID'] = str(self.current); output = root / 'resumed'
            fetch.side_effect = lambda case,private: private / 'input.png'
            call('preflight'); call('run', pt8a.CASES[0]); call('run', pt8a.CASES[1])
            client.return_value.request.assert_called_once()
            self.assertEqual('POST', client.return_value.request.call_args.args[0])
            pt8a.write(output / 'b/cleanup.json', {'JWKSRestored':True}); call('checkpoint', pt8a.CASES[1])
            state = json.loads((root / 'pt8a-recovery-resume.json').read_text())
            b = state['records'][pt8a.CASES[1]]
            self.assertEqual('INDETERMINATE_ACCEPTANCE', b['record.json']['status'])
            self.assertEqual(1, b['record.json']['uploadAttempts'])
            self.assertEqual(previous_run, b['record.json']['priorZeroPostFailure']['runId'])
            self.assertEqual(original_a, state['records'][pt8a.CASES[0]])
            call('run', pt8a.CASES[1])  # Same ambiguous request never receives another POST.
            self.assertEqual(1, client.return_value.request.call_count)
            with self.assertRaisesRegex(ValueError, 'predecessor unresolved'): call('run', pt8a.CASES[2])

    def test_input_rejection_and_generated_technical_defect_are_not_provider_infrastructure_failures(self):
        data=self.checkpoint(0);data['record.json'].update(terminalState='FAILED',status='NOT_PASS')
        data['observation/job.json'].update(status='FAILED',resultObjectKey=None,quality={'contractVersion':'evidence-quality-v1',
            'technicalStatus':'PASS','knowledgeStatus':'NOT_APPLICABLE','qualityStatus':'NOT_APPLICABLE'})
        data['observation/draft-identity.json']={'hclPresent':False}
        data['observation/cli.json']={'status':'NOT_RUN','reason':'BACKEND_FAILED','AWSPlanApply':False}
        data['inventory.json']['files']=[]
        self.assertEqual('PRODUCT_REVIEW_REQUIRED',pt8a.recovery_checkpoint(data,pt8a.CASES[0]))
        data['observation/job.json']['quality']={'reasons':['TERRAFORM_EXECUTABLE_FAILURE'],'technicalStatus':'FAIL'}
        self.assertEqual('PRODUCT_REVIEW_REQUIRED',pt8a.recovery_checkpoint(data,pt8a.CASES[0]))
        data['observation/job.json']['quality']={'reasons':['PROVIDER_RATE_LIMITED'],'technicalStatus':'FAIL'}
        self.assertEqual('TECHNICAL_FAILURE',pt8a.recovery_checkpoint(data,pt8a.CASES[0]))
        data['observation/job.json']['quality']={'reasons':[],'technicalStatus':'FAIL'}
        self.assertEqual('BLOCKED',pt8a.recovery_checkpoint(data,pt8a.CASES[0]))

    def test_real_observer_read_only_drain_never_posts_or_replaces_censored_latency(self):
        record={'caseId':pt8a.CASES[0],'status':'NOT_PASS','uploadAttempts':1,'consumed':True,'jobId':'job-1',
            'projectId':1,'sourceFileId':2,'acceptedAt':'2026-10-09T09:31:00Z','censoredObservationMs':541000,'readOnlyDrain':True}
        calls=[]
        class Client:
            def request(inner,method,path,*args,**kwargs):
                calls.append((method,path));self.assertEqual('GET',method)
                if '/analysis/jobs/' in path:return {'httpStatus':200,'json':{'id':'job-1','projectId':1,'sourceFileId':2,'status':'FAILED','resultObjectKey':None}}
                return {'httpStatus':200,'json':{'projectId':1,'latestAnalysisJobId':'job-1','latestResultObjectKey':None}}
        with tempfile.TemporaryDirectory() as d,patch.object(pt8a,'retrieval_evidence',return_value={'jobId':'job-1'}):
            pt8a.observe(Client(),Path(d)/'input.png',Path(d)/'obs',record,[],'unused',diagnostic=True,resume_accepted=True)
        self.assertEqual(541000,record['censoredObservationMs']);self.assertEqual(1,record['uploadAttempts'])
        self.assertTrue(all(method=='GET' for method,path in calls))

    def test_workflow_uses_same_protected_job_and_artifact_barriers_before_next_upload(self):
        workflow=yaml.safe_load((pt8a.ROOT/'.github/workflows/gcp-target-runtime-dependencies.yml').read_text())
        job=workflow['jobs']['pt8a-official-acceptance'];steps=job['steps']
        self.assertEqual('gcp-target-apply',job['environment']);self.assertEqual('read',workflow['permissions']['actions'])
        gate=next(i for i,s in enumerate(steps) if 'preflight --request-file' in s.get('run',''))
        start=next(i for i,s in enumerate(steps) if s.get('id')=='recovery-start-artifact')
        wif=next(i for i,s in enumerate(steps) if s.get('uses')=='google-github-actions/auth@v3')
        self.assertLess(gate,start);self.assertLess(start,wif)
        for i,letter in enumerate('abcde'):
            step=next(s for s in steps if s.get('id')=='recovery-'+letter)
            artifact=next(s for s in steps if s.get('id')=='recovery-'+letter+'-artifact')
            if i:
                self.assertIn('outputs.proceed',step['if']);self.assertIn('artifact.outcome',step['if'])
            self.assertIn('cleanup.json',step['run']);self.assertIn(' checkpoint ',step['run'])
            self.assertIn('restore',step['run']);self.assertIn('always()',artifact['if'])
            self.assertEqual('error',artifact['with']['if-no-files-found'])
        self.assertNotIn('/dispatches',json.dumps(job))

    def test_owner_override_is_restricted_before_any_kubectl_call(self):
        script=str(pt8a.ROOT/'scripts/smoke/ephemeral-jwks-fixture.sh')
        with tempfile.TemporaryDirectory() as d:
            env=os.environ|{'RUNNER_TEMP':d,'OPERATION':pt8a.RECOVERY_OPERATION,'NAMESPACE':'unused',
                'ISSUER_URI':'https://identity.example.test/case-c','CLIENT_ID':'case-c-runtime-client',
                'GITHUB_RUN_ID':'100','IDENTITY_RUN_ID':'200','PT8A_RECOVERY_OWNER_RUN_ID':'201'}
            result=subprocess.run(['bash',script,'prepare'],env=env,text=True,capture_output=True)
        self.assertNotEqual(0,result.returncode);self.assertIn('admitted original campaign',result.stderr)
        self.assertNotIn('kubectl',result.stderr)

    def test_actual_cli_collects_five_negative_drafts_with_checkpoints_and_reuses_completed_case(self):
        # Exercise the real runner/observer and CLI-validation command construction. External
        # transport/process boundaries are deterministic; no model, image or cloud is accessed.
        with tempfile.TemporaryDirectory() as d,ExitStack() as stack:
            root=Path(d);out=root/'campaign';outputs=root/'github.output';outputs.touch()
            (root/'coverage-report.json').write_text('{}');(root/'pt8a-release.json').write_text(json.dumps(self.release))
            env={'GITHUB_SHA':SOURCE,'BACKEND_IMAGE':IMAGE,'GITHUB_RUN_ATTEMPT':'1','GITHUB_RUN_ID':str(self.current),
                'OPERATION':pt8a.RECOVERY_OPERATION,'RUNNER_TEMP':d,'GITHUB_ENV':str(root/'github.env'),'GITHUB_OUTPUT':str(outputs)}
            stack.enter_context(patch.dict(os.environ,env))
            stack.enter_context(patch.object(pt8a.transport,'current_main',return_value=SOURCE))
            stack.enter_context(patch.object(pt8a,'authority_comment',side_effect=lambda *a:pt8a.recovery_live_fields(self.request,SOURCE,IMAGE)))
            stack.enter_context(patch.object(pt8a,'github',side_effect=self.api))
            stack.enter_context(patch.object(pt8a,'recovery_originals',return_value={'originalA':'consumed','originalB':'consumed'}))
            stack.enter_context(patch.object(pt8a,'corrective_origins',return_value=(self.base.origin.clean['receipt.json'],self.base.origin.clean_binding,self.base.origin.failed_binding)))
            stack.enter_context(patch.object(pt8a.rag,'load_corpus',return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
            stack.enter_context(patch.object(pt8a.rag,'validate_pt8a_clean_corpus'))
            scans=stack.enter_context(patch.object(pt8a,'risk_qualified_admission',return_value=self.base.snapshot))
            stack.enter_context(patch.object(pt8a,'retrieval_evidence',side_effect=lambda job,*a:{'jobId':job,'officialHitCount':0,'events':[]}))
            selected=[0];posts=[];commands=[]
            def command(args,**kwargs):
                commands.append(args)
                return subprocess.CompletedProcess(args,1 if 'validate' in args else 0,'deterministic invalid draft','')
            stack.enter_context(patch.object(pt8a.subprocess,'run',side_effect=command))
            def acquire(case,private):
                self.assertEqual(set(case),{'caseId','imageUrl','sha256','sizeBytes','mediaType','width','height'})
                fixture=private/'input.png';fixture.write_bytes(b'synthetic-only');return fixture
            fetch=stack.enter_context(patch.object(pt8a,'acquire',side_effect=acquire))
            class Client:
                def __init__(inner,*a):pass
                def close(inner):pass
                def request(inner,method,path,*a,**kw):
                    i=selected[0];job='job-'+str(i);project=i+1;obj='draft-'+str(i)
                    quality={'contractVersion':'evidence-quality-v1','technicalStatus':'UNKNOWN','knowledgeStatus':'DEGRADED'}
                    if method=='POST':
                        posts.append(i)
                        return accepted()|{'json':accepted()['json']|{'analysisJobId':job,'projectId':project}}
                    if '/analysis/jobs/' in path:
                        return {'httpStatus':200,'json':{'id':job,'projectId':project,'sourceFileId':2,'status':'SUCCEEDED','resultObjectKey':obj,'quality':quality}}
                    if path.endswith('main.tf'):return {'httpStatus':200,'json':{'latestAnalysisJobId':job,'latestResultObjectKey':obj,'content':'invalid Terraform'}}
                    return {'httpStatus':200,'json':{'projectId':project,'latestAnalysisJobId':job,'analysisStatus':'SUCCEEDED','latestResultObjectKey':obj,'quality':quality}}
            stack.enter_context(patch.object(pt8a.transport,'CurlClient',Client))
            def call(action,case=None):
                argv=['pt8a',action,'--request-file',str(root/'request.json'),'--output',str(out),'--corpus',d]
                if case:argv+=['--case-id',case]
                (root/'request.json').write_text(json.dumps(self.request))
                with patch.object(sys,'argv',argv):pt8a.main()
            call('preflight')
            for i,case in enumerate(pt8a.CASES):
                selected[0]=i;call('run',case)
                self.assertTrue(outputs.read_text().endswith('proceed=false\ncheckpoint=true\n'))
                pt8a.write(out/case[-1]/'cleanup.json',{'JWKSRestored':True});call('checkpoint',case)
                self.assertTrue(outputs.read_text().endswith('proceed=true\ncheckpoint=true\n'))
                self.assertEqual('NOT_PASS',json.loads((out/case[-1]/'record.json').read_text())['status'])
            self.assertEqual([0,1,2,3,4],posts);self.assertEqual(5,fetch.call_count)
            self.assertEqual(5,sum('validate' in c for c in commands));self.assertEqual(10,scans.call_count)
            call('run',pt8a.CASES[0]);self.assertEqual(5,len(posts));self.assertEqual(5,fetch.call_count)
            self.assertTrue(outputs.read_text().endswith('proceed=true\ncheckpoint=false\n'))
            call('finish');summary=json.loads((out/'summary/summary.json').read_text())
            self.assertEqual('EVIDENCE_COLLECTED_AWAITING_INDEPENDENT_REVIEW',summary['outcome'])
            self.assertEqual('NOT_ESTABLISHED',summary['semanticAcceptance']);self.assertIsNone(summary['rates'])
            self.assertEqual(5,summary['uploadsUsed']);self.assertEqual(list(pt8a.SCORING),summary['requiredIndependentDimensions'])
            # Actual cleanup failure is never replaced by optimistic provider/CLI evidence.
            pt8a.write(out/'e/cleanup.json',{'JWKSRestored':False});call('checkpoint',pt8a.CASES[-1])
            self.assertTrue(outputs.read_text().endswith('proceed=false\ncheckpoint=true\n'))
            with self.assertRaises(ValueError):call('finish')

    def test_original_b_cannot_be_rebound_or_reclassified(self):
        diagnostic=DiagnosticContinuationContracts();diagnostic.setUp()
        data=copy.deepcopy(diagnostic.artifacts[diagnostic.reference(1)['runId']])
        data['binding.json'].update(sourceSha=pt8a.CASE_B_SOURCE,image=pt8a.CASE_B_IMAGE,runId=pt8a.CASE_B_RUN)
        data['observation/job.json'].update(status='FAILED',resultObjectKey=None,quality={'reasons':['PROVIDER_RATE_LIMITED']})
        bound={'runId':pt8a.CASE_B_RUN,'artifactId':pt8a.CASE_B_ARTIFACT,'digest':pt8a.CASE_B_DIGEST,'sourceSha':pt8a.CASE_B_SOURCE,'conclusion':'failure'}
        review={'decision':'ACKNOWLEDGED_DIAGNOSTIC_ONLY','official_acceptance':'NOT_ACCEPTANCE','infrastructure_auth_provenance':'VERIFIED',
            'reviewed_source_sha':pt8a.CASE_B_SOURCE,'run_id':str(pt8a.CASE_B_RUN),'artifact_id':str(pt8a.CASE_B_ARTIFACT),
            'artifact_digest':pt8a.CASE_B_DIGEST,'case_id':pt8a.CASES[1],'observed_status':'NOT_PASS'}
        with tempfile.TemporaryDirectory() as d,patch.object(pt8a,'diagnostic_origin',return_value=(diagnostic.artifacts[pt8a.CASE_A_RUN]['ledger.json'],{})),patch.object(pt8a,'bound_artifact',return_value=(data,bound)),patch.object(pt8a,'authority_comment',side_effect=lambda cid,marker:pt8a.diagnostic_live_fields(pt8a.CASE_B_SOURCE,pt8a.CASE_B_IMAGE) if marker=='[HUMAN_GATE_APPROVAL:v1]' else review):
            pt8a.recovery_originals(Path(d))
            data['observation/job.json']['quality']['reasons']=['SUCCESS']
            with self.assertRaises(ValueError):pt8a.recovery_originals(Path(d))
            data['observation/job.json']['quality']['reasons']=['PROVIDER_RATE_LIMITED']
            bound['sourceSha']=SOURCE
            with self.assertRaises(ValueError):pt8a.recovery_originals(Path(d))


class ArtifactAndObservationContracts(unittest.TestCase):
    def test_failed_candidate_and_error_are_required_not_merely_archive_integrity(self):
        job = {"id": "job-1", "projectId": 1, "status": "FAILED"}
        evidence = safe_diagnostics(job, category="VALIDATE_CONFIGURATION")
        pt8a.require_diagnostic_evidence(evidence, job)
        for changed in ({"status": "DIAGNOSTIC_EVIDENCE_INCOMPLETE"}, {"candidates": {}},
                        {"cliDiagnostics": None}, {"failure": None}, {"projectId": 2},
                        {"complete": False}, {"prompt": "private-user-value"}):
            with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, "DIAGNOSTIC_EVIDENCE_INCOMPLETE"):
                pt8a.require_diagnostic_evidence(evidence | changed, job)
        with tempfile.TemporaryDirectory() as directory:
            client = unittest.mock.Mock()
            client.request.side_effect = [{"httpStatus": 200, "json": copy.deepcopy(evidence)},
                {"httpStatus": 200, "json": original_diagnostics(evidence)}]
            saved = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory))
            self.assertEqual("AVAILABLE", saved["status"])
            self.assertEqual("VERIFIED", saved["ownerScopedOriginalReadback"]["status"])
            self.assertNotIn("content", (Path(directory)/"diagnostics.json").read_text())
            client.reset_mock(); client.request.side_effect = None
            client.request.return_value = {"httpStatus": 503, "json": {"content": "private-user-HCL"}}
            saved = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory))
            self.assertEqual("DIAGNOSTIC_EVIDENCE_INCOMPLETE", saved["status"])
            self.assertNotIn("private-user-HCL", (Path(directory)/"diagnostics.json").read_text())
            self.assertEqual(["GET"], [call.args[0] for call in client.request.call_args_list])
            pending = {"jobId": job["id"], "projectId": job["projectId"], "storageStatus": "PENDING"}
            client.reset_mock(); client.request.side_effect = [{"httpStatus": 200, "json": pending},
                {"httpStatus": 200, "json": copy.deepcopy(evidence)}, {"httpStatus": 200, "json": original_diagnostics(evidence)}]
            waits = []
            saved = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory), wait=waits.append)
            self.assertEqual("AVAILABLE", saved["status"]); self.assertEqual([5], waits)
            client.reset_mock(); client.request.side_effect = None; client.request.return_value = {"httpStatus": 200, "json": pending}
            waits = []
            saved = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory), wait=waits.append)
            self.assertEqual("DIAGNOSTIC_EVIDENCE_INCOMPLETE", saved["status"])
            self.assertEqual(7, client.request.call_count); self.assertEqual(30, sum(waits))

    def test_private_original_readback_is_required_and_mismatch_never_exports_hcl(self):
        job = {"id": "job-1", "projectId": 1, "status": "FAILED"}
        evidence = safe_diagnostics(job, category="VALIDATE_CONFIGURATION")
        with tempfile.TemporaryDirectory() as directory:
            client = unittest.mock.Mock()
            for response in ({"httpStatus": 403, "json": {"content": "secret"}},
                    {"httpStatus": 200, "json": original_diagnostics(evidence) | {"evidenceSha256": "0" * 64}},
                    {"httpStatus": 200, "json": original_diagnostics(evidence)}):
                if response["httpStatus"] == 200 and response["json"].get("evidenceSha256") == evidence["evidenceSha256"]:
                    response["json"]["candidates"]["final"]["content"] = "private-secret"
                client.request.side_effect = [{"httpStatus": 200, "json": copy.deepcopy(evidence)}, response]
                captured = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory))
                self.assertEqual("DIAGNOSTIC_EVIDENCE_INCOMPLETE", captured["status"])
                self.assertNotIn("private-secret", (Path(directory) / "diagnostics.json").read_text())
                self.assertNotIn("secret", (Path(directory) / "diagnostics.json").read_text())
            no_candidate = safe_diagnostics(job, category="PROVIDER_TIMEOUT")
            client.request.side_effect = [{"httpStatus": 200, "json": copy.deepcopy(no_candidate)},
                {"httpStatus": 200, "json": original_diagnostics(no_candidate)}]
            captured = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory))
            self.assertEqual("AVAILABLE", captured["status"])
            self.assertEqual("NO_CANDIDATE_PRODUCED", captured["ownerScopedOriginalReadback"]["status"])

    def test_unclassified_or_internal_category_never_counts_as_complete_cause_evidence(self):
        job = {"id": "job-1", "projectId": 1, "status": "FAILED"}
        for category in ("UNCLASSIFIED_INTERNAL_FAILURE", "INTERNAL"):
            for has_candidate in (False, True):
                with self.subTest(category=category, has_candidate=has_candidate):
                    evidence = safe_diagnostics(job, category=category)
                    evidence["failure"]["exceptionType"] = "IllegalStateException"
                    if has_candidate:
                        evidence["candidates"] = {"final": {"status": "CAPTURED", "sha256": "a" * 64, "bytes": 123}}
                    with self.assertRaisesRegex(ValueError, "DIAGNOSTIC_EVIDENCE_INCOMPLETE"):
                        pt8a.require_diagnostic_evidence(evidence, job)
                    with tempfile.TemporaryDirectory() as directory:
                        client = unittest.mock.Mock()
                        client.request.return_value = {"httpStatus": 200, "json": evidence}
                        captured = pt8a.capture_diagnostic_evidence(client, job, Path(directory), Path(directory))
                        self.assertEqual("DIAGNOSTIC_EVIDENCE_INCOMPLETE", captured["status"])
                        client.request.assert_called_once()

    def test_measurement_observer_exports_failed_diagnostic_hashes_without_raw_hcl_or_resubmission(self):
        job = {"id": "job-1", "projectId": 1, "sourceFileId": 2, "status": "FAILED", "resultObjectKey": None}
        calls = []
        class Client:
            def request(inner, method, path, *args, **kwargs):
                calls.append((method, path))
                if method == "POST": return accepted()
                if path.endswith("/diagnostics"): return {"httpStatus": 200, "json": safe_diagnostics(job, category="VALIDATE_CONFIGURATION")}
                if path.endswith("/diagnostics?includeContent=true"):
                    return {"httpStatus": 200, "json": original_diagnostics(safe_diagnostics(job, category="VALIDATE_CONFIGURATION"))}
                if "/analysis/jobs/" in path: return {"httpStatus": 200, "json": job}
                return {"httpStatus": 200, "json": {"projectId": 1, "latestAnalysisJobId": "job-1", "latestResultObjectKey": None}}
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GITHUB_RUN_ID": "1"}), \
             patch.object(pt8a, "retrieval_evidence", return_value={"jobId": "job-1"}):
            out = Path(directory)/"observation"; record = ledger()[1]
            pt8a.observe(Client(), Path(directory)/"input.png", out, record, [], "unused",
                         diagnostic=True, require_diagnostics=True)
            self.assertEqual("NOT_PASS", record["status"]); self.assertTrue(record["consumed"])
            self.assertEqual("AVAILABLE", record["diagnosticEvidence"])
            self.assertFalse((out/"main.tf").exists())
            self.assertEqual("VALIDATE_CONFIGURATION", json.loads((out/"diagnostics.json").read_text())["failure"]["category"])
            self.assertEqual(1, sum(method == "POST" for method, path in calls))
            self.assertEqual(1, sum("includeContent=true" in path for method, path in calls))


    def test_provider_call_diagnostics_preserve_measured_duration_and_safe_sdk_types_without_guessing_old_calls(self):
        base = ("Vertex provider call stage=initial_generation compact=false outcome=failure "
                "finishReason=UNAVAILABLE outputTokens=unknown thinkingTokens=unknown totalTokens=unknown "
                "errorClass=AnalysisProviderTimeoutException upstreamHttpStatus=null")
        lines = [base, base + " elapsedMs=220014 configuredTimeoutMs=220000 "
                 "sdkExceptionTypes=GenAiIOException,InterruptedIOException PRIVATE_BODY",
                 base + " elapsedMs=-1 configuredTimeoutMs=999999 "
                 "sdkExceptionTypes=private.request.response"]
        with patch.object(pt8a.transport, "collect_logs", return_value={"lines": lines}):
            result = pt8a.retrieval_evidence("job", "since", [])
        old, measured, malformed = result["providerCalls"]
        self.assertNotIn("elapsedMs", old)
        self.assertNotIn("sdkExceptionTypes", old)
        self.assertEqual(220014, measured["elapsedMs"])
        self.assertEqual(220000, measured["configuredTimeoutMs"])
        self.assertEqual(["GenAiIOException", "InterruptedIOException"], measured["sdkExceptionTypes"])
        self.assertIsNone(measured["upstreamHttpStatus"])
        for field in ("elapsedMs", "configuredTimeoutMs", "sdkExceptionTypes"):
            self.assertNotIn(field, malformed)
        self.assertNotIn("PRIVATE_BODY", json.dumps(result))

    def test_diagnostic_censor_keeps_partial_job_and_retrieval_without_late_observation_or_retry(self):
        calls = []
        class Client:
            def request(inner, method, path, *args, **kwargs):
                calls.append((method, path))
                return accepted() if method == "POST" else {"httpStatus": 200, "json": {"id": "job-1", "projectId": 1, "status": "RUNNING"}}
        with tempfile.TemporaryDirectory() as d, patch.dict(os.environ, {"GITHUB_RUN_ID": "1"}), \
             patch.object(pt8a, "retrieval_evidence", return_value={"jobId": "job-1", "events": []}):
            ticks = iter([0, 0, 541, 541]); out = Path(d)/"observation"; record = ledger()[1]
            pt8a.observe(Client(), Path(d)/"input.png", out, record, [], "unused", clock=lambda: next(ticks), wait=lambda _:None, diagnostic=True)
            self.assertEqual(["POST", "GET"], [m for m,p in calls]); self.assertEqual(541000, record["censoredObservationMs"])
            self.assertNotIn("acceptedToTerminalMs", record)
            self.assertEqual("RUNNING", json.loads((out/"job.json").read_text())["status"])
            self.assertEqual("NOT_RUN", json.loads((out/"cli.json").read_text())["status"])
            self.assertTrue((out/"retrieval.json").exists())

    def test_diagnostic_one_upload_keeps_provider_failure_unknown_empty_and_invalid_draft(self):
        for outcome in ("provider-timeout", "empty", "invalid"):
            with self.subTest(outcome=outcome), tempfile.TemporaryDirectory() as d, ExitStack() as stack:
                failed = outcome == "provider-timeout"
                job = {"id": "job-1", "projectId": 1, "sourceFileId": 2, "status": "FAILED" if failed else "SUCCEEDED",
                    "resultObjectKey": None if failed else "result.tf", "failureReason": "PROVIDER_TIMEOUT" if failed else None,
                    "quality": {"technicalStatus": "UNKNOWN", "knowledgeStatus": "DEGRADED"}}
                calls = []
                class Client:
                    def request(inner, method, path, *args, **kwargs):
                        calls.append((method, path))
                        if method == "POST": return accepted()
                        if "/analysis/jobs/" in path: return {"httpStatus": 200, "json": job}
                        if path.endswith("main.tf"): return {"httpStatus": 200, "json": {
                            "latestAnalysisJobId": "job-1", "latestResultObjectKey": "result.tf", "content": "" if outcome == "empty" else "invalid Terraform"}}
                        return {"httpStatus": 200, "json": {"projectId": 1, "latestAnalysisJobId": "job-1",
                            "analysisStatus": job["status"], "latestResultObjectKey": job["resultObjectKey"], "quality": job["quality"]}}
                stack.enter_context(patch.dict(os.environ, {"GITHUB_RUN_ID": "1"}))
                stack.enter_context(patch.object(pt8a, "retrieval_evidence", return_value={"jobId": "job-1", "officialHitCount": 0, "events": [], "providerCalls": []}))
                commands = stack.enter_context(patch.object(pt8a.subprocess, "run", side_effect=[subprocess.CompletedProcess([], 0),
                    subprocess.CompletedProcess([], 0, "init", ""), subprocess.CompletedProcess([], 1, "", "invalid Terraform")]))
                record = {"caseId": pt8a.CASES[1], "status": "NOT_RUN", "consumed": False}; out = Path(d) / "observation"
                pt8a.observe(Client(), Path(d)/"input.png", out, record, [], "owned-pod", diagnostic=True)
                self.assertEqual(1, sum(m == "POST" for m,p in calls)); self.assertEqual("NOT_PASS", record["status"])
                self.assertEqual(job["quality"], json.loads((out / "job.json").read_text())["quality"])
                cli = json.loads((out / "cli.json").read_text())
                if outcome == "invalid":
                    self.assertEqual(1, cli["initValidateExitCode"])
                    self.assertIn("validate", commands.call_args_list[-1].args[0])
                    self.assertEqual("invalid Terraform", (out / "main.tf").read_text())
                else:
                    commands.assert_not_called(); self.assertEqual("NOT_RUN", cli["status"])
                    self.assertFalse((out / "main.tf").exists())
                self.assertFalse(cli["AWSPlanApply"])

    def test_inventory_verifies_unselected_hcl_and_rejects_unlisted_or_duplicate_members(self):
        for defect in (None, "hcl_hash", "unlisted", "duplicate"):
            receipt, hcl = b'{}', b'terraform {}'
            files = [{"path": "binding.json", "sha256": pt8a.sha(receipt), "sizeBytes": len(receipt)},
                     {"path": "observation/main.tf", "sha256": "wrong" if defect == "hcl_hash" else pt8a.sha(hcl), "sizeBytes": len(hcl)}]
            if defect == "duplicate": files.append(files[0])
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w") as zipped:
                zipped.writestr("binding.json", receipt); zipped.writestr("observation/main.tf", hcl)
                zipped.writestr("inventory.json", json.dumps({"files": files}))
                if defect == "unlisted": zipped.writestr("error.json", '{}')
            archive = buf.getvalue()
            run = {"event": "workflow_dispatch", "head_branch": "main", "run_attempt": 1,
                "path": ".github/workflows/gcp-target-runtime-dependencies.yml", "status": "completed",
                "head_sha": SOURCE, "conclusion": "success"}
            artifact = {"expired": False, "digest": "sha256:" + pt8a.sha(archive), "workflow_run": {"id": 1, "head_sha": SOURCE}}
            def download(command, stdout, check): stdout.write(archive)
            with tempfile.TemporaryDirectory() as d, patch.object(pt8a.subprocess, "run", side_effect=download), \
                 patch.object(pt8a, "github", side_effect=[run, artifact]):
                if defect:
                    with self.assertRaises(ValueError): pt8a.bound_artifact(1, 2, ["binding.json", "inventory.json"], Path(d), run["path"], verify_inventory=True)
                else:
                    pt8a.bound_artifact(1, 2, ["binding.json", "inventory.json"], Path(d), run["path"], verify_inventory=True)

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
            "Vertex provider call stage=repair compact=false outcome=failure finishReason=UNAVAILABLE outputTokens=unknown thinkingTokens=unknown totalTokens=unknown errorClass=AnalysisProviderFailureException upstreamHttpStatus=429 secret=DO_NOT_PUBLISH",
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
        self.assertEqual(429, result["providerCalls"][4]["upstreamHttpStatus"])
        self.assertIsNone(result["providerCalls"][0]["upstreamHttpStatus"])
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
                                "GITHUB_ENV": str(github_env), "NAMESPACE": "unused", "PT8A_TEST_CALLS": str(calls),
                                "OPERATION": "pt8a-official-acceptance"}
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


class KeylessReadOnlyDiagnosticContracts(unittest.TestCase):
    """Run the actual workflow's sanitizer with only its Logging transport replaced."""

    @classmethod
    def setUpClass(cls):
        cls.workflow = yaml.safe_load((pt8a.ROOT / ".github/workflows/gcp-target-runtime-dependencies.yml").read_text())
        cls.job = cls.workflow["jobs"]["backend-read-only-diagnostics"]
        cls.step = next(step for step in cls.job["steps"] if step.get("id") == "logs")
        cls.script = cls.step["run"].split("python3 - <<'PY'\n", 1)[1].rsplit("\nPY", 1)[0]
        cls.request = {"analysisJobId": "a195d5ff-fce0-4728-80d5-ac2ebaf75d1d",
                       "startUtc": "2026-10-09T13:26:40Z", "endUtc": "2026-10-09T13:31:15Z"}
        cls.module = {"__name__": "workflow_contract_test"}
        with patch.dict(os.environ, cls.query_env(cls.request)):
            exec(compile(cls.script, "workflow-read-only-diagnostics", "exec"), cls.module)

    @staticmethod
    def query_env(request):
        return {"DIAGNOSTIC_JOB_ID": request["analysisJobId"], "DIAGNOSTIC_START_UTC": request["startUtc"],
                "DIAGNOSTIC_END_UTC": request["endUtc"]}

    def entry(self, message=None, **changes):
        return {"resource": {"type": "k8s_container", "labels": self.module["LABELS"]},
                "timestamp": "2026-10-09T13:30:59.125Z",
                "textPayload": "2026-10-09T13:30:59.125Z WARN [analysis-job-1] VertexGenerationStage "
                    f"analysisJobId={self.module['JOB']} source_revision={SOURCE} - "
                    + (message or "Vertex provider call stage=initial_generation compact=false outcome=failure "
                       "finishReason=UNAVAILABLE errorClass=AnalysisProviderTimeoutException upstreamHttpStatus=null"),
                **changes}

    def run_query(self, responses, request=None):
        requests = []

        class Response:
            status = 200
            def __init__(self, body): self.body = json.dumps(body).encode()
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, bound): return self.body[:bound]

        class Opener:
            def open(inner, request, timeout):
                requests.append((request, timeout))
                value = responses[len(requests) - 1]
                if isinstance(value, Exception):
                    raise value
                return Response(value)

        with tempfile.TemporaryDirectory() as directory:
            env = {"GITHUB_SHA": SOURCE, "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "1",
                   "SERVICE_ACCOUNT": "existing-runtime@terraformers-platform.iam.gserviceaccount.com",
                   "LOGGING_ACCESS_TOKEN": "secret-test-access-token", "RUNNER_TEMP": directory,
                   "GITHUB_OUTPUT": str(Path(directory) / "outputs"), **self.query_env(request or self.request)}
            with patch.dict(os.environ, env), patch.object(self.module["urllib"].request, "build_opener", return_value=Opener()), \
                    patch("sys.stdout", new_callable=io.StringIO) as output:
                module = {"__name__": "workflow_contract_test"}
                exec(compile(self.script, "workflow-read-only-diagnostics", "exec"), module)
                result = module["main"]()
            receipt = (Path(directory) / "gcp-read-only-diagnostics.json").read_text()
            self.assertEqual({"outputs", "gcp-read-only-diagnostics.json"}, {p.name for p in Path(directory).iterdir()})
            self.assertEqual("receipt=true\n", (Path(directory) / "outputs").read_text())
            self.assertEqual(json.loads(receipt), json.loads(output.getvalue()))
            self.assertNotIn(env["LOGGING_ACCESS_TOKEN"], receipt + output.getvalue())
            return result, json.loads(receipt), requests

    def test_fixed_read_scope_pagination_and_only_safe_observed_fields(self):
        secret = "PROMPT_PRIVATE eyJcredential.signature IMAGE_BYTES request=private response=private"
        result, receipt, requests = self.run_query([
            {"entries": [self.entry() | {"unrelated": secret}], "nextPageToken": "page-2"},
            {"entries": [self.entry("analysis stage outcome=failure stage=analysis_execution category=timeout "
                                    "errorClass=AnalysisProviderTimeoutException elapsedMs=263819 " + secret),
                         self.entry("Vertex provider call stage=repair outcome=failure errorClass=ApiException upstreamHttpStatus=429")]},
        ])
        self.assertEqual(0, result)
        self.assertTrue(receipt["complete"])
        self.assertEqual([200, 200], receipt["apiStatuses"])
        self.assertEqual({"timestamp": "2026-10-09T13:30:59.125Z", "stage": "initial_generation",
                          "exceptionType": "AnalysisProviderTimeoutException"}, receipt["events"][0])
        self.assertEqual(263819, receipt["events"][1]["elapsedMs"])
        self.assertEqual(429, receipt["events"][2]["httpStatus"])
        self.assertNotIn(secret, json.dumps(receipt))

        self.assertNotIn("requestTimeoutMs", json.dumps(receipt))  # Never invent the configured 220s as observed.
        for index, (request, timeout) in enumerate(requests):
            self.assertEqual(self.module["ENDPOINT"], request.full_url)
            self.assertEqual("POST", request.method)
            self.assertEqual(20, timeout)
            body = json.loads(request.data)
            self.assertEqual(["projects/terraformers-platform"], body["resourceNames"])
            for fixed in (self.module["JOB"], self.module["START"], self.module["END"], 'container_name="backend"'):
                self.assertIn(fixed, body["filter"])
            self.assertEqual("page-2" if index else None, body.get("pageToken"))


    def test_measured_provider_diagnostics_survive_existing_sanitizer_without_raw_causes(self):
        base = ("Vertex provider call stage=initial_generation compact=false outcome=failure "
                "errorClass=AnalysisProviderTimeoutException upstreamHttpStatus=null")
        result, receipt, _ = self.run_query([{"entries": [
            self.entry(base + " elapsedMs=220014 configuredTimeoutMs=220000 "
                       "sdkExceptionTypes=GenAiIOException,InterruptedIOException message=PRIVATE_BODY"),
            self.entry(base + " configuredTimeoutMs=0 sdkExceptionTypes=private.request.response"),
            self.entry(base + " configuredTimeoutMs=999999 sdkExceptionTypes=" + ",".join(["Exception"] * 9)),
        ]}])
        self.assertEqual(0, result)
        event = receipt["events"][0]
        self.assertEqual(220014, event["elapsedMs"])
        self.assertEqual(220000, event["configuredTimeoutMs"])
        self.assertEqual(["GenAiIOException", "InterruptedIOException"], event["sdkExceptionTypes"])
        self.assertNotIn("httpStatus", event)  # Client timeout does not establish an upstream status.
        for malformed in receipt["events"][1:]:
            self.assertNotIn("configuredTimeoutMs", malformed)
            self.assertNotIn("sdkExceptionTypes", malformed)
        self.assertNotIn("PRIVATE_BODY", json.dumps(receipt))

    def test_unrelated_scope_and_untrusted_payloads_are_not_saved(self):
        wrong_resource = {"type": "k8s_container", "labels": self.module["LABELS"] | {"namespace_name": "other"}}
        forged = self.entry("user prompt contains Vertex provider call stage=repair errorClass=FakeException")
        entries = [self.entry(resource=wrong_resource), self.entry(timestamp="2026-10-09T13:31:16Z"),
                   self.entry(timestamp="2026-10-09T13:31:15.000000001Z"),
                   self.entry(textPayload=self.entry()["textPayload"].replace(self.module["JOB"], "other-job")),
                   self.entry(textPayload=f"user prompt analysisJobId={self.module['JOB']} Vertex provider call stage=repair"),
                   forged, self.entry("Vertex provider call stage=initial_generation errorClass=eyJsecret upstreamHttpStatus=secret elapsedMs=secret")]
        result, receipt, _ = self.run_query([{"entries": entries}])
        self.assertEqual(0, result)
        self.assertEqual([{"timestamp": "2026-10-09T13:30:59.125Z", "stage": "initial_generation"}], receipt["events"])
        self.assertNotIn("secret", json.dumps(receipt))

    def test_actual_403_records_bound_principal_and_permission_without_retry_or_error_body(self):
        import urllib.error
        for status in (403, 401, 429, 500):
            with self.subTest(status=status):
                failure = urllib.error.HTTPError(self.module["ENDPOINT"], status, "PRIVATE_SERVER_MESSAGE", {}, io.BytesIO(b"PRIVATE_BODY"))
                result, receipt, requests = self.run_query([failure])
                self.assertEqual(1, result)
                self.assertEqual(1, len(requests))
                self.assertFalse(receipt["complete"])
                self.assertEqual([status], receipt["apiStatuses"])
                self.assertNotIn("PRIVATE", json.dumps(receipt))
                if status == 403:
                    self.assertEqual("ACCESS_DENIED", receipt["outcome"])
                    self.assertEqual("logging.logEntries.list", receipt["requiredPermission"])
                    self.assertEqual("roles/logging.viewer", receipt["requiredRole"])
                    self.assertEqual("terraformers-platform", receipt["roleProject"])
                    self.assertEqual("existing-runtime@terraformers-platform.iam.gserviceaccount.com", receipt["serviceAccount"])
                else:
                    self.assertEqual("HTTP_FAILURE", receipt["outcome"])
                    self.assertNotIn("requiredRole", receipt)

    def test_incomplete_or_invalid_query_never_becomes_complete_and_redirect_is_refused(self):
        pages = [{"entries": [self.entry()], "nextPageToken": f"page-{i}"} for i in range(self.module["MAX_PAGES"])]
        for responses in (pages, [{"entries": [], "nextPageToken": "same"}] * 2,
                          [{"entries": [self.entry(timestamp="PRIVATE_TIMESTAMP")]}],
                          [ValueError("PRIVATE_TRANSPORT_ERROR")]):
            with self.subTest(length=len(responses)):
                result, receipt, requests = self.run_query(responses)
                self.assertEqual(1, result)
                self.assertFalse(receipt["complete"])
                self.assertNotIn("PRIVATE", json.dumps(receipt))
                self.assertLessEqual(len(requests), self.module["MAX_PAGES"])
        with self.assertRaisesRegex(ValueError, "redirect refused"):
            self.module["NoRedirect"]().redirect_request(None, None, 302, "", {}, "https://evil.test")

    def test_existing_protection_wif_and_single_read_only_job_are_preserved(self):
        self.assertEqual("gcp-target-apply", self.job["environment"])
        self.assertEqual({"contents": "read", "id-token": "write"}, self.job["permissions"])
        auth = next(step for step in self.job["steps"] if step.get("id") == "auth")
        self.assertEqual("google-github-actions/auth@v3", auth["uses"])
        self.assertEqual("${{ vars.GCP_WIF_PROVIDER }}", auth["with"]["workload_identity_provider"])
        self.assertEqual("${{ vars.GCP_TF_APPLY_SERVICE_ACCOUNT }}", auth["with"]["service_account"])
        self.assertEqual("https://www.googleapis.com/auth/logging.read", auth["with"]["access_token_scopes"])
        self.assertFalse(auth["with"]["create_credentials_file"])
        guard = self.job["steps"][0]["run"]
        self.assertNotIn("${{ inputs.pt8a_request }}", json.dumps(self.job))  # No unvalidated request in step env logs.
        for check in ('"$EXPECTED_SHA" == "$GITHUB_SHA"', '"$GITHUB_RUN_ATTEMPT" == 1',
                      '"$remote_main" == "$EXPECTED_SHA"', "READ_REVIEWED_BACKEND_DIAGNOSTIC_LOGS_1"):
            self.assertIn(check, guard)
        for name, job in self.workflow["jobs"].items():
            if name != "backend-read-only-diagnostics":
                self.assertNotIn("backend-read-only-diagnostics", job.get("if", ""))
        history = {"total_count": 2, "jobs": [
            {"name": "backend-read-only-diagnostics", "status": "completed", "conclusion": "success"},
            {"name": pt8a.SKIPPED_RECOVERY_JOB_NAME, "status": "completed", "conclusion": "skipped"}]}
        self.assertEqual("skipped", pt8a.pt8a_history_job(history)["conclusion"])
        job_text = json.dumps(self.job)
        for forbidden in ("kubectl", "terraform apply", "generateContent", "embedContent", "add-iam-policy-binding", "setup-gcloud"):
            self.assertNotIn(forbidden, job_text)
        upload = self.job["steps"][-1]
        self.assertEqual("${{ runner.temp }}/gcp-read-only-diagnostics.json", upload["with"]["path"])
        self.assertIn("always()", upload["if"])

    def test_real_request_guard_rejects_drift_wrong_authority_and_rerun_before_authentication(self):
        with tempfile.TemporaryDirectory() as directory:
            gh = Path(directory) / "gh"
            gh.write_text('#!/bin/sh\nprintf "%s\\n" "$TEST_REMOTE_SHA"\n')
            gh.chmod(0o700)
            event = Path(directory) / "event.json"
            event.write_text(json.dumps({"inputs": {"pt8a_request": json.dumps(self.request)}}))
            env = {"PATH": directory + ":" + os.environ["PATH"], "GITHUB_REPOSITORY": "siamese-lang/terraformers-platform",
                   "GITHUB_REF": "refs/heads/main", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_SHA": SOURCE,
                   "EXPECTED_SHA": SOURCE, "TEST_REMOTE_SHA": SOURCE,
                   "CONFIRMATION": "READ_REVIEWED_BACKEND_DIAGNOSTIC_LOGS_1", "WIF_PROVIDER": "existing-wif",
                   "SERVICE_ACCOUNT": "existing-runtime@terraformers-platform.iam.gserviceaccount.com",
                   "GITHUB_EVENT_PATH": str(event), "GITHUB_ENV": str(Path(directory) / "env")}
            guard = self.job["steps"][0]["run"]
            self.assertEqual(0, subprocess.run(["bash", "-c", guard], env=env, capture_output=True).returncode)
            for changes in ({"TEST_REMOTE_SHA": "b" * 40}, {"EXPECTED_SHA": "b" * 40},
                            {"GITHUB_RUN_ATTEMPT": "2"}, {"GITHUB_REF": "refs/heads/unreviewed"},
                            {"GITHUB_REPOSITORY": "other/repository"}, {"CONFIRMATION": "APPLY_SOMETHING"},
                            {"WIF_PROVIDER": ""}, {"SERVICE_ACCOUNT": "other@unrelated.iam.gserviceaccount.com"}):
                with self.subTest(changes=changes):
                    self.assertNotEqual(0, subprocess.run(["bash", "-c", guard], env=env | changes, capture_output=True).returncode)

    def test_reusable_job_window_validation_precedes_wif_and_binds_normalized_receipt(self):
        guard = self.job["steps"][0]["run"].split("python3 - <<'PY'\n", 1)[1].split("\nPY", 1)[0]
        second = {"analysisJobId": "b12e8435-7d70-4b74-b7fd-cb73dc965c02",
                  "startUtc": "2026-10-08T01:00:00.000Z", "endUtc": "2026-10-08T01:15:00.000000000Z"}
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "env"
            event = Path(directory) / "event.json"
            event.write_text(json.dumps({"inputs": {"pt8a_request": json.dumps(second)}}))
            with patch.dict(os.environ, {"GITHUB_EVENT_PATH": str(event), "GITHUB_ENV": str(output)}):
                exec(compile(guard, "workflow-diagnostic-request-guard", "exec"), {})
            normalized = dict(line.split("=", 1) for line in output.read_text().splitlines())
            request = {"analysisJobId": normalized["DIAGNOSTIC_JOB_ID"],
                       "startUtc": normalized["DIAGNOSTIC_START_UTC"], "endUtc": normalized["DIAGNOSTIC_END_UTC"]}
            self.assertEqual(second["analysisJobId"], request["analysisJobId"])
            self.assertEqual("2026-10-08T01:00:00Z", request["startUtc"])
            self.assertEqual("2026-10-08T01:15:00Z", request["endUtc"])
            entry = self.entry(timestamp="2026-10-08T01:04:00Z")
            entry["textPayload"] = entry["textPayload"].replace(self.request["analysisJobId"], request["analysisJobId"])
            result, receipt, calls = self.run_query([
                {"entries": [entry, self.entry(timestamp="2026-10-08T01:04:00Z")]}], request=request)
            self.assertEqual(0, result)
            for key, value in request.items():
                self.assertEqual(value, receipt[key])
            self.assertEqual(1, len(receipt["events"]))
            self.assertEqual("2026-10-08T01:04:00Z", receipt["events"][0]["timestamp"])
            self.assertIn(request["analysisJobId"], json.loads(calls[0][0].data)["filter"])
            self.assertNotIn(self.request["analysisJobId"], json.loads(calls[0][0].data)["filter"])

    def test_invalid_diagnostic_requests_fail_before_wif_without_exposing_untrusted_strings(self):
        guard = self.job["steps"][0]["run"].split("python3 - <<'PY'\n", 1)[1].split("\nPY", 1)[0]
        invalid = [self.request | {"analysisJobId": value} for value in (
            "PRIVATE_SECRET", self.request["analysisJobId"].upper(), self.request["analysisJobId"].replace("-", ""), None)]
        invalid += [self.request | change for change in (
            {"startUtc": self.request["endUtc"]}, {"endUtc": self.request["startUtc"]},
            {"endUtc": "2026-10-09T13:41:40.000000001Z"}, {"startUtc": "2026-02-30T00:00:00Z"},
            {"startUtc": "2026-10-09T13:26:40+00:00"}, {"startUtc": "2026-10-09 13:26:40Z"},
            {"startUtc": "2099-01-01T00:00:00Z", "endUtc": "2099-01-01T00:01:00Z"},
            {"project": "PRIVATE_SECRET"}, {"filter": "PRIVATE_SECRET"}, {"serviceAccount": "PRIVATE_SECRET"})]
        serialized = [json.dumps(item) for item in invalid]
        serialized += ["PRIVATE_SECRET", "[]", json.dumps(self.request).replace(
            '"analysisJobId":', '"analysisJobId":"PRIVATE_SECRET", "analysisJobId":'), " " * 1025]
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "env"
            event = Path(directory) / "event.json"
            for raw in serialized:
                event.write_text(json.dumps({"inputs": {"pt8a_request": raw}}))
                with self.subTest(raw_length=len(raw)), patch.dict(os.environ, {"GITHUB_EVENT_PATH": str(event), "GITHUB_ENV": str(output)}), \
                        patch.object(self.module["urllib"].request, "build_opener", side_effect=AssertionError("no cloud access")):
                    with self.assertRaises(SystemExit) as failure:
                        exec(compile(guard, "workflow-diagnostic-request-guard", "exec"), {})
                    self.assertNotIn("PRIVATE_SECRET", str(failure.exception))
                    self.assertFalse(output.exists())


class PostCorrectionSingleObservationContracts(unittest.TestCase):
    def setUp(self):
        self.request = {"mode": pt8a.POST_CORRECTION_MODE, "caseId": pt8a.CASES[0],
            "liveApprovalCommentId": 910, "provenanceRunId": pt8a.ORIGIN_CLEAN_RUN,
            "provenanceArtifactId": pt8a.ORIGIN_CLEAN_ARTIFACT,
            "priorRunId": pt8a.POST_CORRECTION_ORIGIN_RUN,
            "priorArtifactId": pt8a.POST_CORRECTION_ORIGIN_ARTIFACT,
            "priorReviewCommentId": pt8a.POST_CORRECTION_ORIGIN_REVIEW,
            "amendmentPullRequest": 911, "amendmentReviewCommentId": 912}
        self.live = pt8a.post_correction_live_fields(self.request, SOURCE, IMAGE)
        self.review = {"decision": "ACCEPTED", "reviewed_head": "d" * 40,
            "execution_base_sha": pt8a.POST_CORRECTION_MERGE,
            "post_correction_contract_sha256": pt8a.post_correction_contract_sha256()}
        self.pr = {"merged": True, "base": {"ref": "main"},
            "head": {"sha": "d" * 40}, "merge_commit_sha": "e" * 40}
        self.current = pt8a.POST_CORRECTION_ORIGIN_RUN + 1000
        self.runs = [
            {"id": pt8a.POST_CORRECTION_ORIGIN_RUN, "head_sha": pt8a.POST_CORRECTION_ORIGIN_SOURCE,
             "run_attempt": 1, "status": "completed", "conclusion": "success"},
            {"id": self.current, "head_sha": SOURCE, "run_attempt": 1,
             "status": "in_progress", "conclusion": None}]
        self.jobs = {
            pt8a.POST_CORRECTION_ORIGIN_RUN: {"total_count": 1, "jobs": [{
                "name": pt8a.MEASUREMENT_OPERATION + "/" + pt8a.CASES[0],
                "status": "completed", "conclusion": "success"}]},
            self.current: {"total_count": 1, "jobs": [{"name": pt8a.POST_CORRECTION_OPERATION,
                "status": "in_progress", "conclusion": None}]}}
        self.writes = [{"id": pt8a.ORIGIN_CLEAN_RUN, "head_sha": pt8a.ORIGIN_SOURCE}]
        self.origin_review = {"decision": "EVIDENCE_VALID_MEASUREMENT_ONLY",
            "reviewed_source_sha": pt8a.POST_CORRECTION_ORIGIN_SOURCE,
            "backend_image": pt8a.POST_CORRECTION_ORIGIN_IMAGE,
            "candidate_identity": pt8a.IDENTITY,
            "measurement_contract_sha256": pt8a.POST_CORRECTION_ORIGIN_CONTRACT,
            "episode_id": pt8a.MEASUREMENT_EPISODE,
            "run_id": str(pt8a.POST_CORRECTION_ORIGIN_RUN),
            "artifact_id": str(pt8a.POST_CORRECTION_ORIGIN_ARTIFACT),
            "artifact_digest": pt8a.POST_CORRECTION_ORIGIN_DIGEST,
            "case_id": pt8a.CASES[0], "product_quality": "NOT_PASS",
            "dimension_directed_relationships": "FAIL", "allow_next_measurement": "false",
            "diagnostic_evidence_sha256": pt8a.POST_CORRECTION_ORIGIN_DIAGNOSTIC}

    def api(self, path):
        if path.startswith("pulls/"): return self.pr
        if path.startswith("issues/comments/"):
            return {"issue_url": "https://api.github.com/repos/siamese-lang/terraformers-platform/issues/911"}
        if path.startswith("compare/"): return {"status": "ahead"}
        if "gcp-target-runtime-dependencies.yml/runs?" in path:
            return {"total_count": len(self.runs), "workflow_runs": self.runs}
        if "gcp-target-corpus-ingestion.yml/runs?" in path:
            return {"total_count": len(self.writes), "workflow_runs": self.writes}
        return self.jobs[int(path.split("/")[2])]

    def authority(self, comment_id, marker):
        if comment_id == self.request["liveApprovalCommentId"]: return self.live
        if comment_id == pt8a.POST_CORRECTION_ORIGIN_APPROVAL and hasattr(self, "origin_live"):
            return self.origin_live
        if comment_id == 6096529026: return self.live | {"purpose": "OLD_EPISODE_AUTHORITY"}
        if comment_id == self.request["amendmentReviewCommentId"]: return self.review
        if comment_id == pt8a.POST_CORRECTION_ORIGIN_REVIEW: return self.origin_review
        raise AssertionError("unexpected authority")

    def test_new_source_requires_separate_exact_live_and_merged_independent_authority(self):
        with patch.object(pt8a, "github", side_effect=self.api), \
             patch.object(pt8a, "authority_comment", side_effect=self.authority), \
             patch.object(pt8a.transport, "current_main", return_value=SOURCE):
            self.assertEqual(self.live, pt8a.post_correction_request_contract(self.request, SOURCE, IMAGE, 1))
            for changed in ({"mode": pt8a.MEASUREMENT_MODE}, {"caseId": pt8a.CASES[1]},
                    {"priorRunId": 0}, {"liveApprovalCommentId": 6096529026}):
                with self.subTest(changed=changed), self.assertRaises(ValueError):
                    pt8a.post_correction_request_contract(self.request | changed, SOURCE, IMAGE, 1)
            for source, image, attempt in ((pt8a.POST_CORRECTION_ORIGIN_SOURCE, IMAGE, 1),
                    (SOURCE, IMAGE + "wrong", 1), (SOURCE, IMAGE, 2)):
                with self.assertRaises(ValueError):
                    pt8a.post_correction_request_contract(self.request, source, image, attempt)
            with patch.dict(self.pr, {"merged": False}), self.assertRaises(ValueError):
                pt8a.post_correction_request_contract(self.request, SOURCE, IMAGE, 1)
            with patch.dict(self.live, {"backend_image": "wrong"}), self.assertRaises(ValueError):
                pt8a.post_correction_request_contract(self.request, SOURCE, IMAGE, 1)
            with patch.dict(self.review, {"decision": "CHANGES_REQUIRED"}), self.assertRaises(ValueError):
                pt8a.post_correction_request_contract(self.request, SOURCE, IMAGE, 1)
            with patch.object(pt8a.transport, "current_main", return_value="f" * 40), self.assertRaisesRegex(ValueError, "MAIN_DRIFT"):
                pt8a.post_correction_request_contract(self.request, SOURCE, IMAGE, 1)

    def test_history_preserves_consumed_origin_and_rejects_second_or_ambiguous_dispatch(self):
        with patch.object(pt8a, "github", side_effect=self.api):
            self.assertTrue(pt8a.post_correction_history(SOURCE, self.current)["singleDispatchConsumed"])
            other = {"id": self.current - 1, "head_sha": "f" * 40, "run_attempt": 1,
                "status": "completed", "conclusion": "failure"}
            self.runs.insert(1, other)
            self.jobs[other["id"]] = {"total_count": 1, "jobs": [{"name": pt8a.POST_CORRECTION_OPERATION,
                "status": "completed", "conclusion": "failure"}]}
            with self.assertRaises(ValueError): pt8a.post_correction_history(SOURCE, self.current)
            self.jobs[other["id"]] = {"total_count": 1, "jobs": [{
                "name": pt8a.SKIPPED_POST_CORRECTION_JOB_NAME,
                "status": "completed", "conclusion": "skipped"}]}
            with self.assertRaises(ValueError): pt8a.post_correction_history(SOURCE, self.current)
            self.jobs[other["id"]]["total_count"] = 2
            self.jobs[other["id"]]["jobs"].append({"name": "backend-revision-rollout",
                "status": "completed", "conclusion": "success"})
            pt8a.post_correction_history(SOURCE, self.current)
            self.runs[0]["run_attempt"] = 2
            with self.assertRaises(ValueError): pt8a.post_correction_history(SOURCE, self.current)
            self.runs[0]["run_attempt"] = 1
            self.writes.append({"id": self.current + 1, "head_sha": SOURCE})
            with self.assertRaises(ValueError): pt8a.post_correction_history(SOURCE, self.current)

    def test_original_not_pass_and_no_next_review_remain_immutable(self):
        rows = [{"caseId": c, "status": "NOT_RUN"} for c in pt8a.CASES]
        rows[0].update(status="REVIEW_PENDING", consumed=True, uploadAttempts=1)
        data = {"inventory.json": {"files": []},
            "binding.json": {"sourceSha": pt8a.POST_CORRECTION_ORIGIN_SOURCE,
                "image": pt8a.POST_CORRECTION_ORIGIN_IMAGE,
                "runId": pt8a.POST_CORRECTION_ORIGIN_RUN, "mode": pt8a.MEASUREMENT_MODE,
                "caseId": pt8a.CASES[0], "episodeId": pt8a.MEASUREMENT_EPISODE,
                "measurementContractSha256": pt8a.POST_CORRECTION_ORIGIN_CONTRACT,
                "liveApprovalCommentId": pt8a.POST_CORRECTION_ORIGIN_APPROVAL,
                "amendmentPullRequest": 273, "amendmentReviewCommentId": 6096463681,
                "policyApprovalCommentId": 6096487884,
                "candidateIdentity": pt8a.IDENTITY},
            "ledger.json": rows, "measurement-disposition.json": {"officialAcceptance": "NOT_ACCEPTANCE"},
            "cleanup.json": {"JWKSRestored": True},
            "observation/accepted.json": {"status": "ACCEPTED", "consumed": True,
                "uploadAttempts": 1, "jobId": pt8a.POST_CORRECTION_ORIGIN_JOB},
            "observation/job.json": {"id": pt8a.POST_CORRECTION_ORIGIN_JOB, "status": "SUCCEEDED"},
            "observation/diagnostics.json": {"evidenceSha256": pt8a.POST_CORRECTION_ORIGIN_DIAGNOSTIC}}
        bound = {"runId": pt8a.POST_CORRECTION_ORIGIN_RUN,
            "artifactId": pt8a.POST_CORRECTION_ORIGIN_ARTIFACT,
            "digest": pt8a.POST_CORRECTION_ORIGIN_DIGEST,
            "sourceSha": pt8a.POST_CORRECTION_ORIGIN_SOURCE, "conclusion": "success"}
        self.origin_live = pt8a.measurement_live_fields(data["binding.json"],
            pt8a.POST_CORRECTION_ORIGIN_SOURCE, pt8a.POST_CORRECTION_ORIGIN_IMAGE)
        with tempfile.TemporaryDirectory() as directory, \
             patch.object(pt8a, "measurement_originals", return_value={"historical": "unchanged"}), \
             patch.object(pt8a, "bound_artifact", return_value=(data, bound)), \
             patch.object(pt8a, "authority_comment", side_effect=self.authority):
            self.assertFalse(pt8a.post_correction_origin(Path(directory))["allowNextMeasurement"])
            for item, key, value in ((bound, "digest", "wrong"), (rows[0], "consumed", False),
                    (self.origin_review, "allow_next_measurement", "true"),
                    (self.origin_live, "backend_image", "wrong"),
                    (data["observation/diagnostics.json"], "evidenceSha256", "wrong"),
                    (data["cleanup.json"], "JWKSRestored", False)):
                old = item[key]; item[key] = value
                with self.assertRaises(ValueError): pt8a.post_correction_origin(Path(directory))
                item[key] = old

    def test_existing_workflow_routes_only_one_distinct_protected_observation(self):
        workflow = yaml.safe_load((pt8a.ROOT / ".github/workflows/gcp-target-runtime-dependencies.yml").read_text())
        options = workflow[True]["workflow_dispatch"]["inputs"]["operation"]["options"]
        self.assertIn(pt8a.POST_CORRECTION_OPERATION, options)
        job = workflow["jobs"]["pt8a-official-acceptance"]
        self.assertEqual("gcp-target-apply", job["environment"])
        self.assertIn(pt8a.POST_CORRECTION_OPERATION, job["name"])
        scripts = "\n".join(step.get("run", "") for step in job["steps"])
        self.assertIn("RUN_REVIEWED_PT8A_POST_CORRECTION_OBSERVATION_ONCE", scripts)
        self.assertIn("pt8a-official-acceptance.py preflight", scripts)
        self.assertIn("pt8a-official-acceptance.py run", scripts)
        self.assertIn("ephemeral-jwks-fixture.sh restore", scripts)
        self.assertIn("pt8a-post-correction", str(job))

    def test_new_purpose_uses_existing_read_only_vector_admission(self):
        snapshot = {"classification": "EXACT_REUSABLE_COMPLETED_V4", "indexUuid": pt8a.ORIGIN_UUID,
            "liveContentIdentity": pt8a.ORIGIN_CONTENT, "vectorDocumentsChecked": 5395,
            "embeddingRequests": 0}
        receipt = {"reviewed_source_sha": pt8a.ORIGIN_SOURCE}
        binding = {"sourceSha": pt8a.ORIGIN_SOURCE, "digest": pt8a.ORIGIN_CLEAN_DIGEST}
        with patch.object(pt8a.rag, "verify_exact_v4", return_value=snapshot) as verify:
            result = pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                receipt, binding, self.live)
            self.assertTrue(verify.call_args.kwargs["inspect_vectors"])
            self.assertEqual(0, result["indexWrites"])
            self.assertFalse(result["cryptographicVectorContinuityProven"])
            verify.return_value = snapshot | {"vectorDocumentsChecked": 5394}
            with self.assertRaises(ValueError):
                pt8a.risk_qualified_admission(None, {}, {}, [], pt8a.CORPUS_CHECKSUM,
                    receipt, binding, self.live)


    def test_runner_seals_distinct_observation_without_rewriting_episode_two(self):
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            root = Path(directory)
            request_file = root / "request.json"; request_file.write_text(json.dumps(self.request))
            (root / "coverage-report.json").write_text("{}")
            (root / "pt8a-release.json").write_text(json.dumps({
                "sourceSha": SOURCE, "image": IMAGE,
                "modelProject": "terraformers-platform", "modelLocation": "global"}))
            original = {"historicalEpisodes": "unchanged", "consumedEpisode2CaseA": {
                "runId": pt8a.POST_CORRECTION_ORIGIN_RUN}, "productQuality": "NOT_PASS",
                "allowNextMeasurement": False}
            admission = {"classification": "RISK_QUALIFIED_RETAINED_V4"}
            output = root / "evidence"
            stack.enter_context(patch.dict(os.environ, {"GITHUB_SHA": SOURCE, "BACKEND_IMAGE": IMAGE,
                "GITHUB_RUN_ATTEMPT": "1", "GITHUB_RUN_ID": str(self.current),
                "OPERATION": pt8a.POST_CORRECTION_OPERATION, "RUNNER_TEMP": directory}))
            stack.enter_context(patch.object(sys, "argv", ["pt8a", "run", "--request-file", str(request_file),
                "--corpus", directory, "--output", str(output)]))
            stack.enter_context(patch.object(pt8a.transport, "current_main", return_value=SOURCE))
            stack.enter_context(patch.object(pt8a, "post_correction_request_contract", return_value=self.live))
            stack.enter_context(patch.object(pt8a, "post_correction_history", return_value={"singleDispatchConsumed": True}))
            stack.enter_context(patch.object(pt8a, "post_correction_origin", return_value=original))
            stack.enter_context(patch.object(pt8a, "corrective_origins", return_value=(
                {"ingestion_mode": "pt8a-clean-v4"}, {}, {})))
            stack.enter_context(patch.object(pt8a, "corrective_release"))
            stack.enter_context(patch.object(pt8a.rag, "load_corpus", return_value=({}, {}, [], pt8a.CORPUS_CHECKSUM)))
            stack.enter_context(patch.object(pt8a.rag, "validate_pt8a_clean_corpus"))
            scans = stack.enter_context(patch.object(pt8a, "risk_qualified_admission", return_value=admission))
            stack.enter_context(patch.object(pt8a.subprocess, "run"))
            stack.enter_context(patch.object(pt8a.transport, "CurlClient"))
            def synthetic_input(case, private):
                image = private / "input.png"; image.write_bytes(b"synthetic-input"); return image
            stack.enter_context(patch.object(pt8a, "acquire", side_effect=synthetic_input))
            def observed(client, fixture, evidence_dir, record, documents, pod, **kwargs):
                self.assertEqual({"diagnostic": True, "require_diagnostics": True}, kwargs)
                record.update(status="REVIEW_PENDING", consumed=True, uploadAttempts=1,
                    terminalState="SUCCEEDED", diagnosticEvidence="AVAILABLE")
            observe = stack.enter_context(patch.object(pt8a, "observe", side_effect=observed))
            pt8a.main()
            observe.assert_called_once()
            self.assertEqual(2, scans.call_count)
            self.assertEqual(original, json.loads((output / "original-observations.json").read_text()))
            self.assertFalse((output / "ledger.json").exists())
            self.assertEqual("POST_CORRECTION_SINGLE_OBSERVATION_ONLY",
                json.loads((output / "binding.json").read_text())["classification"])
            self.assertEqual("REVIEW_PENDING", json.loads((output / "record.json").read_text())["status"])
            self.assertEqual("COMPLETE_AWAITING_INDEPENDENT_CLASSIFICATION",
                json.loads((output / "diagnostic-disposition.json").read_text())["evidenceValidity"])
            self.assertTrue((output / "inventory.json").is_file())


if __name__ == "__main__":
    unittest.main()
