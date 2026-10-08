"""Deterministic contracts only: no official image fetch, model, service or cloud execution."""
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import yaml

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


class PreObservationReadinessHistoryContracts(unittest.TestCase):
    def setUp(self):
        self.source = "a0d1d5e82dd872c20b92b46130a331a390efef6a"
        self.run = {"id": 37704835680, "head_sha": self.source, "event": "workflow_dispatch",
                    "head_branch": "main", "path": ".github/workflows/gcp-target-runtime-dependencies.yml",
                    "run_attempt": 1, "status": "completed", "conclusion": "failure"}
        names = ["Validate frozen procedure and explicit live authority before cloud access",
                 "Verify exact deployed release and existing runtime identity",
                 "Rebuild expected corpus from pinned authority without embedding",
                 "Start owned validation pod with the already deployed immutable image",
                 "Observe read-only exact readiness and at most one accepted product job",
                 "Remove only the owned ephemeral validation pod"]
        self.jobs = {"total_count": 1, "jobs": [{"id": 113076606857, "name": "pt8a-official-acceptance",
                     "status": "completed", "conclusion": "failure", "steps": [
                         {"name": name, "number": i + 1, "status": "completed", "conclusion": conclusion}
                         for i, (name, conclusion) in enumerate(zip(names, ["success", "success", "failure", "skipped", "skipped", "skipped"]))]}]}
        self.artifacts = {"total_count": 0, "artifacts": []}
        review = {"decision": "CHANGES_REQUIRED", "reviewed_source_sha": self.source,
                  "run_id": "37704835680", "run_attempt": "1", "job_id": "113076606857",
                  "failure_class": "PRE_OBSERVATION_READINESS_CORPUS_DIRECTORY_CONTRACT_MISMATCH",
                  "artifact_count": "0", "official_case_consumed": "false",
                  "readiness_product_observation_executed": "false", "model_inference_executed": "false",
                  "official_input_fetches": "0", "official_input_uploads": "0", "rerun_authorized": "false"}
        self.comment = {"user": {"login": "siamese-lang"}, "body": "[PT8A_READINESS_FAILURE_REVIEW:v1]\n" +
                        "".join(f"{key}: {value}\n" for key, value in review.items())}

    def history(self, request=None, earlier_consuming_run=False):
        def response(path):
            if "/runs?" in path:
                runs = [self.run]
                if earlier_consuming_run:
                    runs.append(self.run | {"id": 100, "conclusion": "success"})
                return {"workflow_runs": runs}
            if "actions/runs/100/jobs?" in path:
                return {"total_count": 1, "jobs": [{"name": "pt8a-official-acceptance", "conclusion": "success"}]}
            if "actions/runs/100/artifacts?" in path:
                return {"total_count": 1, "artifacts": [{"name": "pt8a-official-100", "id": 200}]}
            if "/jobs?" in path: return self.jobs
            if "/artifacts?" in path: return self.artifacts
            if path == "issues/comments/6049279548": return self.comment
            raise AssertionError(path)
        with patch.object(pt8a, "github", side_effect=response):
            pt8a.ensure_latest_dispatch(request or {"mode": "readiness"}, self.source, 37704835681)

    def test_reviewed_pre_observation_failure_is_non_consuming_for_readiness_only(self):
        self.history()
        with self.assertRaises(ValueError):
            self.history({"mode": "case", "priorRunId": self.run["id"], "priorArtifactId": 200})
        with self.assertRaisesRegex(ValueError, "stale prior artifact"):
            self.history(earlier_consuming_run=True)

    def test_started_or_ambiguous_pod_observation_and_other_failure_are_not_ignored(self):
        original = copy.deepcopy(self.jobs)
        for index in (2, 3, 4):
            for change in ({"conclusion": "success"}, {"conclusion": "failure"},
                           {"status": "in_progress", "conclusion": None}, {"number": None}, {"number": 10}):
                if all(original["jobs"][0]["steps"][index].get(key) == value for key, value in change.items()):
                    continue  # Rebuild failure is the positive baseline, not a negative mutation.
                with self.subTest(index=index, change=change), self.assertRaises(ValueError):
                    self.jobs = copy.deepcopy(original)
                    self.jobs["jobs"][0]["steps"][index].update(change)
                    self.history()
        for index in (3, 4):
            with self.subTest(missing=index), self.assertRaises(ValueError):
                self.jobs = copy.deepcopy(original)
                self.jobs["jobs"][0]["steps"].pop(index)
                self.history()
        self.jobs = copy.deepcopy(original)
        self.jobs["jobs"][0]["steps"].append(copy.deepcopy(self.jobs["jobs"][0]["steps"][4]))
        with self.assertRaises(ValueError): self.history()

    def test_run_lineage_attempt_completion_and_job_identity_fail_closed(self):
        original = self.run.copy()
        for change in ({"run_attempt": 2}, {"status": "in_progress"}, {"conclusion": "success"},
                       {"event": "pull_request"}, {"head_branch": "other"}, {"path": "other.yml"}, {"id": 37704835679}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.run = original | change
                self.history()
        self.run = original | {"head_sha": SOURCE}
        with patch.object(pt8a, "authority_comment") as review:
            self.assertFalse(pt8a.reviewed_pre_observation_readiness_failure(self.run, self.jobs["jobs"][0], self.artifacts))
            review.assert_not_called()
        self.run = original
        original_job = copy.deepcopy(self.jobs["jobs"][0])
        for change in ({"id": 1}, {"status": "in_progress"}, {"conclusion": "cancelled"}):
            with self.subTest(job=change), self.assertRaises(ValueError):
                self.jobs["jobs"][0] = original_job | change
                self.history()

    def test_complete_job_and_artifact_history_is_mandatory(self):
        original = copy.deepcopy(self.jobs)
        for jobs in ({}, {"total_count": 101, "jobs": original["jobs"]},
                     {"total_count": 2, "jobs": original["jobs"]}, {"total_count": 0, "jobs": []},
                     {"total_count": 2, "jobs": original["jobs"] * 2},
                     {"total_count": 1, "jobs": [{"name": "unknown"}]}):
            with self.subTest(jobs=jobs), self.assertRaises(ValueError):
                self.jobs = jobs
                self.history()
        self.jobs = original
        for artifacts in ({}, {"total_count": 1, "artifacts": []},
                          {"total_count": 0, "artifacts": [{"name": "unexpected"}]},
                          {"total_count": 101, "artifacts": []},
                          {"total_count": 1, "artifacts": [{"name": "unrelated"}]}):
            with self.subTest(artifacts=artifacts), self.assertRaises(ValueError):
                self.artifacts = artifacts
                self.history()

    def test_independent_non_consumption_review_must_still_match(self):
        original = copy.deepcopy(self.comment)
        for old, new in ((self.source, SOURCE), ("artifact_count: 0", "artifact_count: 1"),
                         ("model_inference_executed: false", "model_inference_executed: true"),
                         ("readiness_product_observation_executed: false", "readiness_product_observation_executed: true"),
                         ("[PT8A_READINESS_FAILURE_REVIEW:v1]", "[OTHER]")):
            with self.subTest(field=old), self.assertRaises(ValueError):
                self.comment = copy.deepcopy(original)
                self.comment["body"] = self.comment["body"].replace(old, new)
                self.history()
        self.comment = original | {"user": {"login": "other"}}
        with self.assertRaises(ValueError): self.history()


class ArtifactAndObservationContracts(unittest.TestCase):
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
