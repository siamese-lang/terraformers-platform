#!/usr/bin/env python3
"""Bounded continuation for the already-started PT-2 realistic baseline."""
import argparse
import importlib.util
import json
import re
import time
from pathlib import Path

BASELINE_FILE = Path(__file__).with_name("pt2-realistic-baseline.py")
_spec = importlib.util.spec_from_file_location("pt2_baseline", BASELINE_FILE)
pt2 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(pt2)

CONTINUATION_PROCEDURE = "pt2-realistic-continuation-v1"
CONTINUATION_PROTOCOL = "docs/evaluation/product-trust-pt-2-continuation-protocol.md"
CONTINUATION_PROTOCOL_SHA256 = "63ce8ca92c56682bd0d60014d1af8b43c162cddf03f3aebc68bb375c0cd736ab"

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


def _read_json(path):
    return json.loads(path.read_text())


def _verify_artifact_inventory(prior_artifact):
    inventory_path = prior_artifact / "artifact-sha256.json"
    if pt2.digest(inventory_path) != PRIOR_INVENTORY_SHA256:
        raise ValueError("prior artifact inventory identity mismatch")
    inventory = _read_json(inventory_path)
    for entry in inventory.get("files", []):
        path = prior_artifact / entry["path"]
        if not path.resolve().is_relative_to(prior_artifact.resolve()):
            raise ValueError("prior artifact inventory path escapes root")
        if not path.is_file() or pt2.digest(path) != entry["sha256"]:
            raise ValueError("prior artifact file checksum mismatch: " + entry["path"])


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
    if (candidate.get("status") != "FROZEN"
            or candidate.get("truthApprovedBy") != "USER"
            or candidate.get("frozenIdentitySha256") != pt2.IDENTITY
            or candidate.get("candidateRevision") != 3
            or state["phaseCompletion"]["PT-1"]["status"] != "COMPLETE"
            or approval.get("decision") != "APPROVED"
            or approval.get("approvedBy") != "USER"
            or approval.get("gate") != "LIVE_REALISTIC_BASELINE"
            or approval.get("candidateRevision") != 3
            or approval.get("candidateIdentitySha256") != pt2.IDENTITY):
        raise ValueError("external frozen truth/live approval mismatch")
    return dataset


def verify(root, prior_artifact):
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
                return job, observed, last_nonterminal_ms
            if status not in ("PENDING", "RUNNING"):
                raise ValueError("unexpected AnalysisJob status")
            last_nonterminal_ms = elapsed
        elif observed["httpStatus"] in (401, 403, 404):
            raise ValueError("accepted job inaccessible; do not resubmit")
        wait(POLL_INTERVAL_SECONDS)
    return None, None, last_nonterminal_ms


def _readback(client, directory, job_id, project_id, terminal, logs, since):
    project_response = client.request("GET", f"/api/projects/{project_id}", directory, "project")
    draft_response = client.request("GET", f"/api/projects/{project_id}/terraform/main.tf", directory, "terraform")
    project, draft = project_response["json"], draft_response["json"]
    if project_response["httpStatus"] == 200:
        if not isinstance(project, dict) or project.get("projectId") != project_id \
                or project.get("latestAnalysisJobId") != job_id:
            raise ValueError("project readback job identity mismatch")
    else:
        project = None
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
                        clock=time.monotonic, wait=time.sleep, drain_deadline=DRAIN_DEADLINE_SECONDS):
    prior_row = prior_observations["cases"][1]
    directory = output / "prior-case2-recovery"
    directory.mkdir()
    start_stamp = pt2.stamp()
    start = clock()
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
        record["status"] = "RECOVERY_TERMINAL_NOT_OBSERVED"
        record["reason"] = "original job is already beyond the 1200-second-from-acceptance drain bound"
        pt2.write_json(directory / "recovery.json", record)
        return False
    if status not in ("SUCCEEDED", "FAILED"):
        raise ValueError("unexpected prior case 02 AnalysisJob status")

    record.update(
        status="RECOVERED_" + status,
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
                 drain_deadline=DRAIN_DEADLINE_SECONDS):
    if read_main() != dispatch_sha:
        raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT; stop before the next case")
    fixture = root / "evaluation" / pt2.DATASET / case["input"]["path"]
    if pt2.digest(fixture) != case["input"]["sha256"]:
        raise ValueError("fixture changed before submission")

    directory = output / case["caseId"]
    directory.mkdir()
    pt2.write_json(directory / "frozen-truth.json", case)
    record.update(status="SUBMISSION_STARTED", uploadAttempts=1, requestStartedAt=pt2.stamp())
    pt2.write_json(directory / "submission-started.json", dict(record))
    client.request("GET", "/actuator/prometheus", directory, "metrics-before")
    upload = client.request("POST", "/api/upload", directory, "upload", fixture=fixture,
                               project_name=run_id + "-" + case["caseId"])
    if upload["transportExitCode"] != 0:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        return False
    if upload["httpStatus"] != 201:
        record.update(status="UPLOAD_REJECTED", uploadHttpStatus=upload["httpStatus"])
        return upload["httpStatus"] == 400

    accepted = upload["json"]
    if not isinstance(accepted, dict) or not isinstance(accepted.get("analysisJobId"), str) \
            or not re.fullmatch(r"[A-Za-z0-9-]{1,128}", accepted["analysisJobId"]) \
            or type(accepted.get("projectId")) is not int or accepted["projectId"] <= 0:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        return False
    job_id, project_id = accepted["analysisJobId"], accepted["projectId"]
    if job_id in seen_jobs or job_id in (CASE1_JOB_ID, CASE2_JOB_ID):
        raise ValueError("duplicate accepted job identity")
    seen_jobs.add(job_id)

    accepted_clock = upload["monotonic"]
    record.update(
        status="ACCEPTED", analysisJobId=job_id, projectId=project_id,
        acceptedResponseObservedAt=upload["observedAt"], serverCreatedAt=accepted.get("createdAt"),
        sourceFileId=accepted.get("sourceFileId"),
    )

    terminal, observed, last_nonterminal = _poll_same_job(
        client, directory, job_id, project_id, "job-poll", accepted_clock,
        measurement_deadline, clock=clock, wait=wait
    )
    censored = terminal is None
    if censored:
        record.update(
            status="TERMINAL_NOT_OBSERVED_AT_MEASUREMENT_DEADLINE",
            censoredObservationMs=max(0, round((clock() - accepted_clock) * 1000)),
            lastNonterminalAfterAcceptanceMs=last_nonterminal,
        )
        terminal, observed, drain_last = _poll_same_job(
            client, directory, job_id, project_id, "drain-job-poll",
            accepted_clock, drain_deadline, clock=clock, wait=wait
        )
        record["lastDrainNonterminalAfterAcceptanceMs"] = drain_last
        if terminal is None:
            record["drainStatus"] = "TERMINAL_NOT_OBSERVED"
            client.request("GET", "/actuator/prometheus", directory, "metrics-after")
            _readback(client, directory, job_id, project_id, None, logs, record["requestStartedAt"])
            return False
        record.update(
            drainStatus="TERMINAL_OBSERVED",
            drainTerminalStatus=terminal["status"],
            drainTerminalObservedAt=observed["observedAt"],
            drainTerminalAfterAcceptanceObservedMs=max(0, round((observed["monotonic"] - accepted_clock) * 1000)),
            quality=terminal.get("quality"),
            failureReason=terminal.get("failureReason"),
        )
    else:
        record.update(
            status=terminal["status"],
            terminalObservedAt=observed["observedAt"],
            acceptedToTerminalObservedMs=max(0, round((observed["monotonic"] - accepted_clock) * 1000)),
            lastNonterminalAfterAcceptanceMs=last_nonterminal,
            quality=terminal.get("quality"),
            failureReason=terminal.get("failureReason"),
        )

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
    return True


def run(root, prior_artifact, output, run_id, dispatch_sha, client,
        read_main=pt2.current_main, logs=pt2.collect_logs, clock=time.monotonic,
        wait=time.sleep, measurement_deadline=MEASUREMENT_DEADLINE_SECONDS,
        drain_deadline=DRAIN_DEADLINE_SECONDS):
    dataset, prior_observations = verify(root, prior_artifact)
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
        "recoveryJobId": CASE2_JOB_ID,
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

    if not recover_prior_case2(
        client, output, prior_observations, logs=logs, clock=clock, wait=wait,
        drain_deadline=drain_deadline
    ):
        pt2.write_json(output / "continuation-observations.json", {
            "runId": run_id,
            "priorRunId": PRIOR_RUN_ID,
            "cases": records,
            "priorCase2Recovered": False,
            "continuationComplete": False,
            "phaseAcceptance": "INDEPENDENT_REVIEW_REQUIRED",
        })
        return False

    continuation_cases = [case for case in dataset["cases"] if case["caseId"] in CONTINUATION_CASE_IDS]
    try:
        for case, record in zip(continuation_cases, records):
            if not observe_case(
                root, case, record, client, run_id, dispatch_sha, output, seen_jobs,
                read_main=read_main, logs=logs, clock=clock, wait=wait,
                measurement_deadline=measurement_deadline, drain_deadline=drain_deadline
            ):
                break
    except Exception as error:
        pt2.write_json(output / "observation-error.json", {
            "errorClass": type(error).__name__,
            "reason": str(error),
        })
        raise
    finally:
        complete = all(row["status"] not in ("NOT_RUN", "SUBMISSION_STARTED", "ACCEPTED", "INDETERMINATE_ACCEPTANCE")
                        and row.get("drainStatus") != "TERMINAL_NOT_OBSERVED"
                        for row in records)
        pt2.write_json(output / "continuation-observations.json", {
            "runId": run_id,
            "priorRunId": PRIOR_RUN_ID,
            "cases": records,
            "priorCase2Recovered": True,
            "continuationComplete": complete,
            "phaseAcceptance": "INDEPENDENT_REVIEW_REQUIRED",
        })
    return complete


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("verify", "run", "inventory"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--prior-artifact", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--token-file", type=Path)
    parser.add_argument("--dispatch-sha")
    args = parser.parse_args()

    if args.action == "verify":
        if not args.prior_artifact:
            parser.error("verify requires --prior-artifact")
        dataset, _ = verify(args.root, args.prior_artifact)
        print(f"Verified prior PT-2 run and {len(dataset['cases'])} frozen cases; no live calls.")
    elif args.action == "inventory":
        if not args.output:
            parser.error("inventory requires --output")
        pt2.inventory(args.output)
    else:
        if not all((args.prior_artifact, args.output, args.run_id, args.token_file, args.dispatch_sha)):
            parser.error("run requires prior artifact, output, run-id, token-file and dispatch SHA")
        client = pt2.CurlClient(args.token_file)
        try:
            complete = run(
                args.root, args.prior_artifact, args.output, args.run_id,
                args.dispatch_sha, client
            )
        finally:
            client.close()
        if not complete:
            raise SystemExit("Partial continuation evidence retained; do not repeat completed cases.")


if __name__ == "__main__":
    main()
