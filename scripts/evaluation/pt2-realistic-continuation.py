#!/usr/bin/env python3
"""Bounded continuation for the already-started PT-2 realistic baseline."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import re
import stat
import time
import zipfile
from pathlib import Path

BASELINE_FILE = Path(__file__).with_name("pt2-realistic-baseline.py")
_spec = importlib.util.spec_from_file_location("pt2_baseline", BASELINE_FILE)
pt2 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(pt2)

CONTINUATION_PROCEDURE = "pt2-realistic-continuation-v3"
CONTINUATION_PROTOCOL = "docs/evaluation/product-trust-pt-2-continuation-protocol.md"
CONTINUATION_PROTOCOL_SHA256 = "218d9d48503a7a8e986081279fb8b77beb692a279e4393319867558c8ee88281"

PRIOR_RUN_ID = 37480519016
PRIOR_RUN_ATTEMPT = 1
PRIOR_DISPATCH_SHA = "c3e9ed4e854778dfb2eefbc0182ae53a008025bf"
PRIOR_ARTIFACT_ID = 11422455892
PRIOR_ARTIFACT_DIGEST = "sha256:499f31b4311737884a2b7d0fc0405d477be7e41a99e9969217570c91eb5ac561"
PRIOR_INVENTORY_SHA256 = "107d31a942dcbc23bbfaf1f81e7c1b9c6a2fac4224d5689edb216a0bd4378f3d"

CASE1_ID = "pt1-01-serverless-portal"
CASE1_JOB_ID = "2337e6bd-1f70-4382-a515-b4cdb9cba7d9"
CASE2_ID = "pt1-02-order-fanout"
CASE2_JOB_ID = "578c05b9-2905-4d60-8432-ce7593c96133"
CASE2_PROJECT_ID = 6
CASE2_CENSORED_MS = 424846
CONTINUATION_CASE_IDS = pt2.CASE_IDS[2:]

MEASUREMENT_DEADLINE_SECONDS = 420
DRAIN_DEADLINE_SECONDS = 1200
POLL_INTERVAL_SECONDS = 5
CONTINUATION_RUN_TITLE = "PT-2 continuation of 37480519016"
WORKFLOW_PATH = ".github/workflows/gcp-target-evaluation-baseline.yml"
WORKFLOW_ID = 368779787
REPOSITORY = "siamese-lang/terraformers-platform"

# Only this independently reviewed zero-inference preflight is non-consuming. No bypass flag.
ZERO_RUN_ID = 37500000739
ZERO_DISPATCH_SHA = "026adab4dec17fd3b57ee950f174f277ed7c6d80"
ZERO_JOB_ID = 112394279733
ZERO_ARTIFACT_ID = 11429522041
ZERO_ARTIFACT_DIGEST = "sha256:49b017e98cb05a546a2c7900044ae6d742762df2c59fdd10a5abe2a4a64a179f"
ZERO_INVENTORY_SHA256 = "0875d2814b3e42f87eccd6f15cf7d309c2f6684cdd6e09b331ba24447f0d2888"
ZERO_JOB_STEPS_SHA256 = "45b6bb1cf24009a7cdf991988ab7f2155b2bb517ec2d4136f07d3889fb2fd305"
ZERO_FILES = frozenset((
    "dispatch-history.json", "opensearch-count.json", "opensearch-mapping.json",
    "prior-artifact-metadata.json", "prior-run-metadata.json", "retained-configuration.txt",
    "retained-deployment.json", "runtime-identity.json",
))
ZERO_STEPS = {
    43: ("Download and verify authoritative partial PT-2 evidence for continuation", "failure"),
    44: ("Prepare proven ephemeral authenticated JWT fixture for PT-2", "skipped"),
    45: ("Observe one authenticated production AnalysisJob per frozen PT-2 input", "skipped"),
    46: ("Continue PT-2 only for the exact prior censored job and previously NOT_RUN inputs", "skipped"),
    47: ("Restore only the placeholder JWKS fixture after PT-2", "success"),
    48: ("Seal PT-2 continuation evidence inventory including partial failures", "success"),
    51: ("Preserve PT-2 continuation raw and partial evidence on every outcome", "success"),
    53: ("Bind immutable PT-2 continuation run and artifact for independent review", "success"),
    60: ("Delete ephemeral evaluation baseline pod", "success"),
}


def _read_json(path):
    return json.loads(path.read_text())


def verify_prior_binding(archive, run_metadata, artifact_metadata):
    run = _read_json(run_metadata)
    artifact = _read_json(artifact_metadata)
    if (run.get("id") != PRIOR_RUN_ID or run.get("run_attempt") != PRIOR_RUN_ATTEMPT
            or run.get("head_sha") != PRIOR_DISPATCH_SHA or run.get("status") != "completed"
            or run.get("conclusion") != "failure" or run.get("event") != "workflow_dispatch"
            or run.get("path") != ".github/workflows/gcp-target-evaluation-baseline.yml"
            or run.get("repository", {}).get("full_name") != "siamese-lang/terraformers-platform"):
        raise ValueError("prior GitHub run identity mismatch")
    if (artifact.get("id") != PRIOR_ARTIFACT_ID
            or artifact.get("name") != f"pt2-realistic-baseline-{PRIOR_RUN_ID}"
            or artifact.get("expired") is not False
            or artifact.get("digest") != PRIOR_ARTIFACT_DIGEST
            or artifact.get("workflow_run", {}).get("id") != PRIOR_RUN_ID
            or artifact.get("workflow_run", {}).get("head_sha") != PRIOR_DISPATCH_SHA):
        raise ValueError("prior GitHub artifact identity mismatch")
    if "sha256:" + pt2.digest(archive) != PRIOR_ARTIFACT_DIGEST:
        raise ValueError("prior artifact archive digest mismatch")


def extract_prior_archive(archive, prior_artifact, run_metadata, artifact_metadata):
    verify_prior_binding(archive, run_metadata, artifact_metadata)
    _extract_archive(archive, prior_artifact)
    _verify_artifact_inventory(prior_artifact)


def _extract_archive(archive, directory, allowed_files=None):
    # Validate every entry before creating files; never follow links or allow duplicate paths.
    with zipfile.ZipFile(archive) as bundle:
        names = set()
        for entry in bundle.infolist():
            path = Path(entry.filename)
            if (path.is_absolute() or ".." in path.parts or "\\" in entry.filename
                    or str(path) in names or stat.S_ISLNK(entry.external_attr >> 16)):
                raise ValueError("unsafe prior artifact archive path")
            if allowed_files is not None and entry.filename not in allowed_files:
                raise ValueError("zero-inference artifact contains unexpected evidence")
            names.add(str(path))
        directory.mkdir(parents=True, exist_ok=False)
        bundle.extractall(directory)


def _verify_zero_binding(archive, run_metadata, artifact_metadata, job_metadata):
    if not all((archive, run_metadata, artifact_metadata, job_metadata)):
        raise ValueError("exact zero-inference archive/run/artifact/job evidence required")
    run = _read_json(run_metadata)
    if (run.get("id") != ZERO_RUN_ID or type(run.get("run_attempt")) is not int or run.get("run_attempt") != 1
            or run.get("head_sha") != ZERO_DISPATCH_SHA or run.get("head_branch") != "main"
            or run.get("status") != "completed" or run.get("conclusion") != "failure"
            or run.get("event") != "workflow_dispatch" or run.get("path") != WORKFLOW_PATH
            or run.get("workflow_id") != WORKFLOW_ID or run.get("display_title") != CONTINUATION_RUN_TITLE
            or run.get("repository", {}).get("full_name") != REPOSITORY):
        raise ValueError("zero-inference run identity mismatch")
    artifact = _read_json(artifact_metadata)
    if (artifact.get("id") != ZERO_ARTIFACT_ID
            or artifact.get("name") != f"pt2-realistic-continuation-{ZERO_RUN_ID}"
            or artifact.get("expired") is not False or artifact.get("digest") != ZERO_ARTIFACT_DIGEST
            or artifact.get("workflow_run", {}).get("id") != ZERO_RUN_ID
            or artifact.get("workflow_run", {}).get("head_sha") != ZERO_DISPATCH_SHA):
        raise ValueError("zero-inference artifact identity mismatch")
    if "sha256:" + pt2.digest(archive) != ZERO_ARTIFACT_DIGEST:
        raise ValueError("zero-inference archive digest mismatch")
    jobs = _read_json(job_metadata)
    if type(jobs.get("total_count")) is not int or jobs.get("total_count") != 1 or len(jobs.get("jobs", [])) != 1:
        raise ValueError("zero-inference job set mismatch")
    job = jobs["jobs"][0]
    if (job.get("id") != ZERO_JOB_ID or job.get("run_id") != ZERO_RUN_ID
            or type(job.get("run_attempt")) is not int or job.get("run_attempt") != 1 or job.get("head_sha") != ZERO_DISPATCH_SHA
            or job.get("name") != "baseline" or job.get("status") != "completed"
            or job.get("conclusion") != "failure"):
        raise ValueError("zero-inference job identity mismatch")
    steps = job.get("steps", [])
    if len({step.get("number") for step in steps}) != len(steps):
        raise ValueError("zero-inference duplicate job step")
    for number, (name, conclusion) in ZERO_STEPS.items():
        matched = [step for step in steps if step.get("number") == number]
        if (len(matched) != 1 or matched[0].get("name") != name
                or matched[0].get("status") != "completed" or matched[0].get("conclusion") != conclusion):
            raise ValueError("zero-inference job step mismatch: " + str(number))
    canonical_steps = [{key: step.get(key) for key in ("number", "name", "status", "conclusion")}
                       for step in steps]
    if hashlib.sha256(json.dumps(canonical_steps, sort_keys=True, separators=(",", ":")).encode()).hexdigest() != \
            ZERO_JOB_STEPS_SHA256:
        raise ValueError("zero-inference full job-step identity mismatch")


def verify_zero_inference_exception(recovery_artifact=None, recovery_archive=None, recovery_run_metadata=None,
                                   recovery_artifact_metadata=None, recovery_job_metadata=None):
    _verify_zero_binding(recovery_archive, recovery_run_metadata,
                         recovery_artifact_metadata, recovery_job_metadata)
    if recovery_artifact is None:
        raise ValueError("zero-inference extracted artifact required")
    _verify_inventory(recovery_artifact, ZERO_INVENTORY_SHA256)
    if {str(path.relative_to(recovery_artifact)) for path in recovery_artifact.rglob("*")} != \
            ZERO_FILES | {"artifact-sha256.json"}:
        raise ValueError("zero-inference artifact contains unexpected evidence")
    runtime = _read_json(recovery_artifact / "runtime-identity.json")
    if (runtime.get("dispatchSha") != ZERO_DISPATCH_SHA or runtime.get("executionBaseSha") != pt2.BASE
            or runtime.get("protocolSha256") != "1658b5c328fdfde9671542373309e68165d7aae6f4b973d0b781f03890b097ee"
            or runtime.get("productionSourceEquivalent") is not True
            or runtime.get("configurationVerified") is not True):
        raise ValueError("zero-inference runtime/procedure identity mismatch")


def extract_recovery_archive(archive, directory, run_metadata, artifact_metadata, job_metadata):
    _verify_zero_binding(archive, run_metadata, artifact_metadata, job_metadata)
    _extract_archive(archive, directory, ZERO_FILES | {"artifact-sha256.json"})
    verify_zero_inference_exception(directory, archive, run_metadata, artifact_metadata, job_metadata)


def verify_dispatch_history(history_path, run_id, dispatch_sha, **recovery_binding):
    if not re.fullmatch(r"[0-9]+", run_id):
        raise ValueError("invalid continuation run ID")
    if int(run_id) <= ZERO_RUN_ID:
        raise ValueError("zero-inference source run cannot be rerun or reused")
    history = _read_json(history_path)
    if not isinstance(history, list) or not history or not isinstance(history[0], dict):
        raise ValueError("incomplete repository dispatch history")
    count = history[0].get("total_count")
    if (type(count) is not int or count <= 0
            or any(not isinstance(page, dict) or page.get("total_count") != count
                   or not isinstance(page.get("workflow_runs"), list) for page in history)):
        raise ValueError("incomplete repository dispatch history")
    runs = [run for page in history for run in page["workflow_runs"]]
    if (any(not isinstance(run, dict) or type(run.get("id")) is not int for run in runs)
            or len(runs) != count or len({run["id"] for run in runs}) != count):
        raise ValueError("incomplete repository dispatch history")

    def matches(run, sha):
        return (run.get("head_sha") == sha and type(run.get("run_attempt")) is int and run.get("run_attempt") == 1
                and run.get("head_branch") == "main" and run.get("event") == "workflow_dispatch"
                and run.get("workflow_id") == WORKFLOW_ID and run.get("path") == WORKFLOW_PATH
                and run.get("repository", {}).get("full_name") == REPOSITORY)

    prior = [run for run in runs if run.get("id") == PRIOR_RUN_ID]
    if len(prior) != 1 or not matches(prior[0], PRIOR_DISPATCH_SHA):
        raise ValueError("dispatch history does not extend through the authoritative prior run")
    current = [run for run in runs if run.get("id") == int(run_id)]
    if (len(current) != 1 or not matches(current[0], dispatch_sha)
            or current[0].get("display_title") != CONTINUATION_RUN_TITLE):
        raise ValueError("current continuation run identity mismatch")
    exempt = [run for run in runs if run.get("id") == ZERO_RUN_ID]
    if (len(exempt) != 1 or not matches(exempt[0], ZERO_DISPATCH_SHA)
            or exempt[0].get("status") != "completed" or exempt[0].get("conclusion") != "failure"
            or exempt[0].get("display_title") != CONTINUATION_RUN_TITLE):
        raise ValueError("zero-inference source absent or mismatched in dispatch history")
    if any(run.get("display_title") == CONTINUATION_RUN_TITLE and run["id"] not in (ZERO_RUN_ID, int(run_id))
           for run in runs):
        raise ValueError("prior continuation dispatch exists; human review required, never repeat uploads")
    verify_zero_inference_exception(**recovery_binding)


def _verify_artifact_inventory(prior_artifact):
    _verify_inventory(prior_artifact, PRIOR_INVENTORY_SHA256)


def _verify_inventory(prior_artifact, inventory_sha256):
    inventory_path = prior_artifact / "artifact-sha256.json"
    if pt2.digest(inventory_path) != inventory_sha256:
        raise ValueError("prior artifact inventory identity mismatch")
    inventory = _read_json(inventory_path)
    if any(path.is_symlink() for path in prior_artifact.rglob("*")):
        raise ValueError("prior artifact contains a symbolic link")
    expected = set()
    for entry in inventory.get("files", []):
        path = prior_artifact / entry["path"]
        if (path.is_symlink() or not path.resolve().is_relative_to(prior_artifact.resolve())
                or entry["path"] in expected):
            raise ValueError("prior artifact inventory path escapes root")
        expected.add(entry["path"])
        if not path.is_file() or pt2.digest(path) != entry["sha256"]:
            raise ValueError("prior artifact file checksum mismatch: " + entry["path"])
    actual = {str(path.relative_to(prior_artifact)) for path in prior_artifact.rglob("*")
              if path.is_file() and path != inventory_path}
    if actual != expected:
        raise ValueError("prior artifact file set mismatch")


def _verify_frozen_inputs(root):
    directory = root / "evaluation" / pt2.DATASET
    identity_path = directory / "candidate-identity.json"
    if pt2.digest(identity_path) != pt2.IDENTITY:
        raise ValueError("frozen candidate identity mismatch")
    identity = _read_json(identity_path)
    if identity.get("candidateRevision") != 3 or identity.get("datasetVersion") != pt2.DATASET:
        raise ValueError("candidate revision/dataset mismatch")
    if len(identity.get("files", [])) != 26:
        raise ValueError("pinned file count mismatch")
    for entry in identity["files"]:
        path = directory / entry["path"]
        if not path.resolve().is_relative_to(directory.resolve()) or pt2.digest(path) != entry["sha256"]:
            raise ValueError("pinned file mismatch: " + entry["path"])
    dataset = _read_json(directory / "dataset.json")
    if dataset.get("datasetVersion") != pt2.DATASET \
            or tuple(case["caseId"] for case in dataset.get("cases", [])) != pt2.CASE_IDS:
        raise ValueError("frozen case order mismatch")
    for case in dataset["cases"]:
        path = directory / case["input"]["path"]
        if not path.resolve().is_relative_to(directory.resolve()) or pt2.digest(path) != case["input"]["sha256"]:
            raise ValueError("fixture checksum mismatch: " + case["caseId"])
    state = _read_json(root / ".agents/state/product-trust-v1.json")
    candidate = state["candidate"]
    approval = state["liveBaselineApproval"]
    phase = state["phaseExecution"]["PT-2"]
    if (candidate.get("status") != "FROZEN"
            or candidate.get("truthApprovedBy") != "USER"
            or candidate.get("frozenIdentitySha256") != pt2.IDENTITY
            or candidate.get("candidateRevision") != 3
            or state["phaseCompletion"]["PT-1"]["status"] != "COMPLETE"
            or approval.get("decision") != "APPROVED"
            or approval.get("approvedBy") != "USER"
            or approval.get("gate") != "LIVE_REALISTIC_BASELINE"
            or approval.get("candidateRevision") != 3
            or approval.get("candidateIdentitySha256") != pt2.IDENTITY
            or approval.get("executionBaseSha") != pt2.BASE
            or phase.get("executionBaseSha") != pt2.BASE
            or phase.get("workPackage") != "product-trust-pt-2-current-system-realistic-live-baseline-v1"):
        raise ValueError("external frozen truth/live approval mismatch")
    return dataset


def verify(root, prior_artifact, archive, run_metadata, artifact_metadata):
    verify_prior_binding(archive, run_metadata, artifact_metadata)
    dataset = _verify_frozen_inputs(root)
    protocol_path = root / CONTINUATION_PROTOCOL
    if pt2.digest(protocol_path) != CONTINUATION_PROTOCOL_SHA256:
        raise ValueError("continuation protocol changed after review freeze")

    _verify_artifact_inventory(prior_artifact)
    cases_dir = prior_artifact / "cases"
    if (prior_artifact / "observation-error.json").exists() or (cases_dir / "observation-error.json").exists():
        raise ValueError("prior baseline artifact contains observation error")

    procedure = _read_json(cases_dir / "procedure.json")
    if (procedure.get("procedureVersion") != pt2.PROCEDURE
            or procedure.get("protocolSha256") != pt2.digest(root / pt2.PROTOCOL)
            or procedure.get("executionBaseSha") != pt2.BASE
            or procedure.get("runId") != f"pt2-realistic-{PRIOR_RUN_ID}"
            or procedure.get("dispatchSha") != PRIOR_DISPATCH_SHA
            or procedure.get("candidateIdentitySha256") != pt2.IDENTITY):
        raise ValueError("prior baseline procedure identity mismatch")

    observations = _read_json(cases_dir / "observations.json")
    rows = observations.get("cases", [])
    if len(rows) != len(pt2.CASE_IDS):
        raise ValueError("prior baseline case count mismatch")
    if rows[0].get("caseId") != CASE1_ID or rows[0].get("status") != "SUCCEEDED" \
            or rows[0].get("analysisJobId") != CASE1_JOB_ID or rows[0].get("uploadAttempts") != 1:
        raise ValueError("prior case 01 evidence mismatch")
    if rows[1].get("caseId") != CASE2_ID or rows[1].get("status") != "TERMINAL_NOT_OBSERVED" \
            or rows[1].get("analysisJobId") != CASE2_JOB_ID or rows[1].get("projectId") != CASE2_PROJECT_ID \
            or rows[1].get("uploadAttempts") != 1 or rows[1].get("censoredObservationMs") != CASE2_CENSORED_MS:
        raise ValueError("prior case 02 evidence mismatch")
    for expected, row in zip(CONTINUATION_CASE_IDS, rows[2:]):
        if row.get("caseId") != expected or row.get("status") != "NOT_RUN" or row.get("uploadAttempts") != 0:
            raise ValueError("prior NOT_RUN continuation boundary mismatch: " + expected)
    return dataset, observations


def _poll_same_job(client, directory, job_id, project_id, prefix, start_clock, deadline_seconds,
                   clock=time.monotonic, wait=time.sleep):
    poll_number = 0
    last_nonterminal_ms = 0
    while clock() - start_clock < deadline_seconds:
        observed = client.request("GET", "/api/analysis/jobs/" + job_id, directory,
                                  f"{prefix}-{poll_number:03}")
        poll_number += 1
        if observed["transportExitCode"] == 0 and observed["httpStatus"] == 200:
            job = observed["json"]
            if not isinstance(job, dict) or job.get("id") != job_id or job.get("projectId") != project_id:
                raise ValueError("polled job identity mismatch")
            elapsed = max(0, round((observed["monotonic"] - start_clock) * 1000))
            status = job.get("status")
            if status in ("SUCCEEDED", "FAILED"):
                # Caller must distinguish receipt after the measurement/drain deadline.
                return job, observed, last_nonterminal_ms
            if status not in ("PENDING", "RUNNING"):
                raise ValueError("unexpected AnalysisJob status")
            last_nonterminal_ms = elapsed
        elif observed["httpStatus"] in (401, 403, 404):
            raise ValueError("accepted job inaccessible; do not resubmit")
        wait(min(POLL_INTERVAL_SECONDS, max(0, deadline_seconds - (clock() - start_clock))))
    return None, None, last_nonterminal_ms


def _readback(client, directory, job_id, project_id, terminal, logs, since):
    project_response = client.request("GET", f"/api/projects/{project_id}", directory, "project")
    draft_response = client.request("GET", f"/api/projects/{project_id}/terraform/main.tf", directory, "terraform")
    project, draft = project_response["json"], draft_response["json"]
    if project_response["transportExitCode"] == 0 and project_response["httpStatus"] == 200:
        if not isinstance(project, dict) or project.get("projectId") != project_id \
                or project.get("latestAnalysisJobId") != job_id:
            raise ValueError("project readback job identity mismatch")
    else:
        raise ValueError("project readback inaccessible; stop without another upload")
    if (draft_response["transportExitCode"] != 0
            or draft_response["httpStatus"] not in (200, 404)
            or (terminal and terminal["status"] == "SUCCEEDED" and draft_response["httpStatus"] != 200)):
        raise ValueError("Terraform readback inaccessible; stop without another upload")
    if draft_response["httpStatus"] == 200:
        if not isinstance(draft, dict) or draft.get("projectId") != project_id \
                or draft.get("latestAnalysisJobId") != job_id or not isinstance(draft.get("content"), str) \
                or (terminal and draft.get("latestResultObjectKey") != terminal.get("resultObjectKey")):
            raise ValueError("Terraform readback job identity mismatch")
        (directory / "main.tf").write_text(draft["content"])
    try:
        correlated = logs(job_id, since)
    except Exception as error:
        correlated = {"jobId": job_id, "status": "NOT_OBSERVED", "diagnostic": type(error).__name__}
    pt2.write_json(directory / "correlated-backend-logs.json", correlated)
    return project, draft_response


def recover_prior_case2(client, output, prior_observations, logs=pt2.collect_logs,
                        clock=time.monotonic, wait=time.sleep, drain_deadline=DRAIN_DEADLINE_SECONDS,
                        wall_clock=lambda: datetime.now(timezone.utc)):
    prior_row = prior_observations["cases"][1]
    directory = output / "prior-case2-recovery"
    directory.mkdir()
    start_stamp = pt2.stamp()
    start = clock()
    original_age = max(0, (wall_clock() - datetime.fromisoformat(
        prior_row["acceptedResponseObservedAt"])).total_seconds())
    observed = client.request(
        "GET", "/api/analysis/jobs/" + CASE2_JOB_ID, directory, "recovery-job-poll-000"
    )
    if observed["transportExitCode"] != 0 or observed["httpStatus"] != 200:
        raise ValueError("prior accepted case 02 job inaccessible; do not resubmit")
    terminal = observed["json"]
    if not isinstance(terminal, dict) or terminal.get("id") != CASE2_JOB_ID \
            or terminal.get("projectId") != CASE2_PROJECT_ID:
        raise ValueError("prior case 02 recovery identity mismatch")
    status = terminal.get("status")
    record = {
        "caseId": CASE2_ID,
        "analysisJobId": CASE2_JOB_ID,
        "projectId": CASE2_PROJECT_ID,
        "originalRunId": PRIOR_RUN_ID,
        "originalMeasurementStatus": "TERMINAL_NOT_OBSERVED",
        "originalCensoredObservationMs": CASE2_CENSORED_MS,
        "recoveryStartedAt": start_stamp,
        "recoveryTerminalObserved": status in ("SUCCEEDED", "FAILED"),
    }
    if status in ("PENDING", "RUNNING"):
        terminal, observed, _ = _poll_same_job(
            client, directory, CASE2_JOB_ID, CASE2_PROJECT_ID, "recovery-drain-job-poll",
            start - original_age, drain_deadline, clock=clock, wait=wait
        )
        if terminal is not None and observed["monotonic"] - start + original_age <= drain_deadline:
            status = terminal["status"]
        else:
            record["status"] = "RECOVERY_TERMINAL_NOT_OBSERVED"
            record["reason"] = "original acceptance drain bound exhausted; no new upload"
            pt2.write_json(directory / "recovery.json", record)
            return False
    if status not in ("SUCCEEDED", "FAILED"):
        raise ValueError("unexpected prior case 02 AnalysisJob status")

    record.update(
        status="RECOVERED_" + status,
        recoveryTerminalObserved=True,
        recoveryTerminalObservedAt=observed["observedAt"],
        recoveryElapsedMs=max(0, round((observed["monotonic"] - start) * 1000)),
        quality=terminal.get("quality"),
        failureReason=terminal.get("failureReason"),
    )
    pt2.write_json(directory / "terminal-job.json", terminal)
    project, draft_response = _readback(
        client, directory, CASE2_JOB_ID, CASE2_PROJECT_ID, terminal, logs,
        prior_row["requestStartedAt"]
    )
    record["projectAnalysisStatus"] = project.get("analysisStatus") if project else None
    record["terraformReadbackHttpStatus"] = draft_response["httpStatus"]
    pt2.write_json(directory / "recovery.json", record)
    return True


def observe_case(root, case, record, client, run_id, dispatch_sha, output, seen_jobs,
                 read_main=pt2.current_main, logs=pt2.collect_logs, clock=time.monotonic,
                 wait=time.sleep, measurement_deadline=MEASUREMENT_DEADLINE_SECONDS,
                 drain_deadline=DRAIN_DEADLINE_SECONDS, checkpoint=lambda: None, seen_projects=None):
    if (case["caseId"] not in CONTINUATION_CASE_IDS or record.get("caseId") != case["caseId"]
            or record.get("status") != "NOT_RUN" or record.get("uploadAttempts") != 0):
        raise ValueError("only previously NOT_RUN cases 03-10 may receive one first submission")
    expected_case = next(item for item in _read_json(root / "evaluation" / pt2.DATASET / "dataset.json")["cases"]
                         if item["caseId"] == case["caseId"])
    if case != expected_case:
        raise ValueError("continuation case does not match frozen truth/input")
    fixture = root / "evaluation" / pt2.DATASET / case["input"]["path"]
    if pt2.digest(fixture) != case["input"]["sha256"]:
        raise ValueError("fixture changed before submission")

    directory = output / case["caseId"]
    directory.mkdir()
    pt2.write_json(directory / "frozen-truth.json", case)
    client.request("GET", "/actuator/prometheus", directory, "metrics-before")
    if read_main() != dispatch_sha:
        raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT; stop before the next case")
    record.update(status="SUBMISSION_STARTED", uploadAttempts=1, requestStartedAt=pt2.stamp())
    pt2.write_json(directory / "submission-started.json", dict(record))
    checkpoint()
    upload = client.request("POST", "/api/upload", directory, "upload", fixture=fixture,
                               project_name=run_id + "-" + case["caseId"])
    if upload["transportExitCode"] != 0:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        checkpoint()
        return False
    if upload["httpStatus"] != 201:
        record.update(status="UPLOAD_REJECTED", uploadHttpStatus=upload["httpStatus"])
        checkpoint()
        return upload["httpStatus"] == 400

    accepted = upload["json"]
    if not isinstance(accepted, dict) or not isinstance(accepted.get("analysisJobId"), str) \
            or not re.fullmatch(r"[A-Za-z0-9-]{1,128}", accepted["analysisJobId"]) \
            or type(accepted.get("projectId")) is not int or accepted["projectId"] <= 0:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        checkpoint()
        return False
    job_id, project_id = accepted["analysisJobId"], accepted["projectId"]
    if seen_projects is None:
        seen_projects = {5, CASE2_PROJECT_ID}
    if job_id in seen_jobs or job_id in (CASE1_JOB_ID, CASE2_JOB_ID):
        record.update(status="INDETERMINATE_ACCEPTANCE", analysisJobId=job_id, projectId=project_id)
        checkpoint()
        raise ValueError("duplicate accepted job identity")
    if project_id in seen_projects:
        record.update(status="INDETERMINATE_ACCEPTANCE", analysisJobId=job_id, projectId=project_id)
        checkpoint()
        raise ValueError("duplicate accepted project identity")
    seen_jobs.add(job_id)
    seen_projects.add(project_id)

    accepted_clock = upload["monotonic"]
    record.update(
        status="ACCEPTED", analysisJobId=job_id, projectId=project_id,
        acceptedResponseObservedAt=upload["observedAt"], serverCreatedAt=accepted.get("createdAt"),
        sourceFileId=accepted.get("sourceFileId"),
    )
    checkpoint()

    terminal, observed, last_nonterminal = _poll_same_job(
        client, directory, job_id, project_id, "job-poll", accepted_clock,
        measurement_deadline, clock=clock, wait=wait
    )
    censored = terminal is None or observed["monotonic"] - accepted_clock > measurement_deadline
    if censored:
        record.update(
            status="TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE",
            censoredObservationMs=max(0, round((clock() - accepted_clock) * 1000)),
            lastNonterminalAfterAcceptanceMs=last_nonterminal,
        )
        checkpoint()
        drain_last = last_nonterminal
        if terminal is None:
            terminal, observed, drain_last = _poll_same_job(
                client, directory, job_id, project_id, "drain-job-poll",
                accepted_clock, drain_deadline, clock=clock, wait=wait
            )
        record["lastDrainNonterminalAfterAcceptanceMs"] = drain_last
        if terminal is None:
            record["drainStatus"] = "TERMINAL_NOT_OBSERVED"
            checkpoint()
            client.request("GET", "/actuator/prometheus", directory, "metrics-after")
            _readback(client, directory, job_id, project_id, None, logs, record["requestStartedAt"])
            return False
        record.update(
            drainStatus=("TERMINAL_OBSERVED" if observed["monotonic"] - accepted_clock <= drain_deadline
                         else "TERMINAL_OBSERVED_AFTER_DRAIN_DEADLINE"),
            drainTerminalStatus=terminal["status"],
            drainTerminalObservedAt=observed["observedAt"],
            drainTerminalAfterAcceptanceObservedMs=max(0, round((observed["monotonic"] - accepted_clock) * 1000)),
            quality=terminal.get("quality"),
            failureReason=terminal.get("failureReason"),
        )
        checkpoint()
    else:
        record.update(
            status=terminal["status"],
            terminalObservedAt=observed["observedAt"],
            acceptedToTerminalObservedMs=max(0, round((observed["monotonic"] - accepted_clock) * 1000)),
            lastNonterminalAfterAcceptanceMs=last_nonterminal,
            quality=terminal.get("quality"),
            failureReason=terminal.get("failureReason"),
        )
        checkpoint()

    pt2.write_json(directory / "terminal-job.json", terminal)
    project, draft_response = _readback(
        client, directory, job_id, project_id, terminal, logs, record["requestStartedAt"]
    )
    client.request("GET", "/actuator/prometheus", directory, "metrics-after")
    quality = terminal.get("quality") or {}
    pt2.write_json(directory / "observation.json", {
        "caseId": case["caseId"],
        "analysisJobId": job_id,
        "projectId": project_id,
        "measurementCensored": censored,
        "terminalObservedDuringDrain": censored and terminal is not None,
        "terminal": terminal,
        "project": project,
        "terraformReadbackHttpStatus": draft_response["httpStatus"],
        "backendEvidenceBackedClaim": bool(
            terminal["status"] == "SUCCEEDED"
            and quality.get("qualityStatus") == "EVIDENCE_BACKED"
            and quality.get("technicalStatus") == "PASS"
        ),
        "presentationSuccessClaim": bool(project and project.get("analysisStatus") == "SUCCEEDED"),
        "browserExecuted": False,
        "semanticFidelity": "REVIEW_PENDING",
        "falseTrustedSuccess": "REVIEW_PENDING",
        "originalLatencyMeasurementPreserved": True,
        "latency": record,
    })
    return not censored or record["drainStatus"] == "TERMINAL_OBSERVED"


def run(root, prior_artifact, output, run_id, dispatch_sha, client,
        read_main=pt2.current_main, logs=pt2.collect_logs, clock=time.monotonic,
        wait=time.sleep, measurement_deadline=MEASUREMENT_DEADLINE_SECONDS,
        drain_deadline=DRAIN_DEADLINE_SECONDS, archive=None, run_metadata=None, artifact_metadata=None,
        history_path=None, **recovery_binding):
    dataset, prior_observations = verify(root, prior_artifact, archive, run_metadata, artifact_metadata)
    if not 0 < measurement_deadline <= MEASUREMENT_DEADLINE_SECONDS \
            or not measurement_deadline <= drain_deadline <= DRAIN_DEADLINE_SECONDS:
        raise ValueError("measurement/drain bounds exceed the frozen procedure")
    verify_dispatch_history(history_path, run_id.removeprefix("pt2-continuation-"), dispatch_sha,
                            **recovery_binding)
    output.mkdir(parents=True, exist_ok=False)
    pt2.write_json(output / "procedure.json", {
        "procedureVersion": CONTINUATION_PROCEDURE,
        "protocolSha256": CONTINUATION_PROTOCOL_SHA256,
        "dispatchSha": dispatch_sha,
        "runId": run_id,
        "datasetVersion": pt2.DATASET,
        "candidateRevision": 3,
        "candidateIdentitySha256": pt2.IDENTITY,
        "priorRunId": PRIOR_RUN_ID,
        "priorArtifactId": PRIOR_ARTIFACT_ID,
        "priorArtifactDigest": PRIOR_ARTIFACT_DIGEST,
        "priorInventorySha256": PRIOR_INVENTORY_SHA256,
        "zeroInferenceExceptionRunId": ZERO_RUN_ID,
        "zeroInferenceExceptionArtifactId": ZERO_ARTIFACT_ID,
        "zeroInferenceExceptionArtifactDigest": ZERO_ARTIFACT_DIGEST,
        "zeroInferenceExceptionInventorySha256": ZERO_INVENTORY_SHA256,
        "historySource": "complete repository-wide workflow_dispatch history",
        "recoveryJobId": CASE2_JOB_ID,
        "authenticatedFixtureSubject": f"case-c-{PRIOR_RUN_ID}",
        "continuationCaseOrder": CONTINUATION_CASE_IDS,
        "measurementDeadlineSeconds": measurement_deadline,
        "drainDeadlineSeconds": drain_deadline,
        "acceptedUploadsPerCase": 1,
        "outerRetries": 0,
        "standaloneModelInvocations": 0,
    })
    records = [{"caseId": case_id, "status": "NOT_RUN", "uploadAttempts": 0}
               for case_id in CONTINUATION_CASE_IDS]
    seen_jobs = set()
    seen_projects = {5, CASE2_PROJECT_ID}
    checkpoint_number = 0

    def checkpoint():
        nonlocal checkpoint_number
        pt2.write_json(output / f"ledger-{checkpoint_number:03}.json", records)
        checkpoint_number += 1

    checkpoint()
    recovered, errored = False, False
    continuation_cases = [case for case in dataset["cases"] if case["caseId"] in CONTINUATION_CASE_IDS]
    try:
        recovered = recover_prior_case2(
            client, output, prior_observations, logs=logs, clock=clock, wait=wait,
            drain_deadline=drain_deadline
        )
        if not recovered:
            return False
        for case, record in zip(continuation_cases, records):
            if not observe_case(
                root, case, record, client, run_id, dispatch_sha, output, seen_jobs,
                read_main=read_main, logs=logs, clock=clock, wait=wait,
                measurement_deadline=measurement_deadline, drain_deadline=drain_deadline,
                checkpoint=checkpoint, seen_projects=seen_projects
            ):
                break
    except Exception as error:
        errored = True
        pt2.write_json(output / "observation-error.json", {
            "errorClass": type(error).__name__,
            "reason": str(error),
        })
        raise
    finally:
        checkpoint()
        complete = recovered and not errored and all(
            row["status"] in ("SUCCEEDED", "FAILED") or (
                row["status"] == "TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE"
                and row.get("drainStatus") == "TERMINAL_OBSERVED") for row in records)
        pt2.write_json(output / "continuation-observations.json", {
            "runId": run_id,
            "priorRunId": PRIOR_RUN_ID,
            "cases": records,
            "priorCase2Recovered": recovered,
            "continuationComplete": complete,
            "phaseAcceptance": "INDEPENDENT_REVIEW_REQUIRED",
        })
    return complete


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("extract-prior", "extract-recovery", "verify-dispatch", "verify", "run", "inventory"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--prior-artifact", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--token-file", type=Path)
    parser.add_argument("--dispatch-sha")
    parser.add_argument("--prior-archive", type=Path)
    parser.add_argument("--prior-run-metadata", type=Path)
    parser.add_argument("--prior-artifact-metadata", type=Path)
    parser.add_argument("--dispatch-history", type=Path)
    for name in ("artifact", "archive", "run-metadata", "artifact-metadata", "job-metadata"):
        parser.add_argument("--recovery-" + name, type=Path)
    args = parser.parse_args()
    recovery_binding = {"recovery_" + name: getattr(args, "recovery_" + name)
                        for name in ("artifact", "archive", "run_metadata", "artifact_metadata", "job_metadata")}

    if args.action in ("verify", "extract-prior"):
        if not all((args.prior_artifact, args.prior_archive, args.prior_run_metadata, args.prior_artifact_metadata)):
            parser.error("prior verification requires artifact directory, archive and both GitHub metadata files")
        if args.action == "extract-prior":
            extract_prior_archive(args.prior_archive, args.prior_artifact,
                                  args.prior_run_metadata, args.prior_artifact_metadata)
        dataset, _ = verify(args.root, args.prior_artifact, args.prior_archive,
                            args.prior_run_metadata, args.prior_artifact_metadata)
        print(f"Verified prior PT-2 run and {len(dataset['cases'])} frozen cases; no live calls.")
    elif args.action in ("extract-recovery", "verify-dispatch"):
        if not all(recovery_binding.values()):
            parser.error("recovery verification requires exact archive/directory and run/artifact/job metadata")
        if args.action == "extract-recovery":
            extract_recovery_archive(args.recovery_archive, args.recovery_artifact, args.recovery_run_metadata,
                                     args.recovery_artifact_metadata, args.recovery_job_metadata)
        else:
            if not all((args.dispatch_history, args.run_id, args.dispatch_sha)):
                parser.error("dispatch verification requires history, run ID and dispatch SHA")
            verify_dispatch_history(args.dispatch_history, args.run_id.removeprefix("pt2-continuation-"),
                                    args.dispatch_sha, **recovery_binding)
        print("Verified exact zero-inference recovery binding; no live calls.")
    elif args.action == "inventory":
        if not args.output:
            parser.error("inventory requires --output")
        pt2.inventory(args.output)
    else:
        if not all((args.prior_artifact, args.output, args.run_id, args.token_file, args.dispatch_sha,
                    args.prior_archive, args.prior_run_metadata, args.prior_artifact_metadata,
                    args.dispatch_history, *recovery_binding.values())):
            parser.error("run requires prior binding/history files, output, run-id, token-file and dispatch SHA")
        client = pt2.CurlClient(args.token_file)
        try:
            complete = run(
                args.root, args.prior_artifact, args.output, args.run_id,
                args.dispatch_sha, client, archive=args.prior_archive,
                run_metadata=args.prior_run_metadata, artifact_metadata=args.prior_artifact_metadata,
                history_path=args.dispatch_history, **recovery_binding
            )
        finally:
            client.close()
        if not complete:
            raise SystemExit("Partial continuation evidence retained; do not repeat completed cases.")


if __name__ == "__main__":
    main()
