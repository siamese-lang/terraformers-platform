#!/usr/bin/env python3
"""Observe one authenticated production AnalysisJob per frozen input; no model/scoring policy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
from datetime import datetime, timezone

IDENTITY = "04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014"
BASE = "3caae454661d21d84c3e469a7d76aff5633d5b26"
DATASET = "terraformers-realistic-v1"
PROCEDURE = "pt2-realistic-baseline-v2"
PROTOCOL = "docs/evaluation/product-trust-pt-2-measurement-protocol.md"
CASE_IDS = (
    "pt1-01-serverless-portal", "pt1-02-order-fanout", "pt1-03-parallel-lookup",
    "pt1-04-analytics-catalog", "pt1-05-private-web-fleet", "pt1-06-thumbnail-pipeline",
    "pt1-07-unresolved-design", "pt1-08-partial-export", "pt1-09-sprint-board",
    "pt1-10-workshop-table",
)
# Read-only preflight expectations, never provider arguments or runtime overrides.
PROFILE = {
    "GOOGLE_CLOUD_PROJECT": "terraformers-platform", "GOOGLE_CLOUD_LOCATION": "global",
    "ANALYSIS_PROVIDER": "vertex", "EMBEDDING_PROVIDER": "vertex", "RETRIEVAL_MODE": "REQUIRED",
    "VERTEX_GENERATION_MODEL_ID": "gemini-3.8-flash", "VERTEX_EMBEDDING_MODEL_ID": "gemini-embedding-2",
    "OPENSEARCH_ENDPOINT": "http://terraformers-opensearch:9200", "INDEX_NAME": "terraformers-reference-v4",
    "VECTOR_FIELD_NAME": "embedding", "CONTENT_FIELD_NAME": "content", "CORPUS_VERSION": "terraformers-reference-v4",
    "PROVIDER_VERSION": "5.100.0", "EXPECTED_VECTOR_DIMENSION": "1536", "OPENSEARCH_TOP_K": "8",
    "VERTEX_MAX_OUTPUT_TOKENS": "8192",
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, value):
    with path.open("x") as handle:
        json.dump(value, handle, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())


def stamp():
    return datetime.now(timezone.utc).isoformat()


def verify(root):
    directory = root / "evaluation" / DATASET
    identity_path = directory / "candidate-identity.json"
    if digest(identity_path) != IDENTITY:
        raise ValueError("frozen candidate identity mismatch")
    identity = json.loads(identity_path.read_text())
    if identity["candidateRevision"] != 3 or identity["datasetVersion"] != DATASET:
        raise ValueError("candidate revision/dataset mismatch")
    if len(identity["files"]) != 26:
        raise ValueError("pinned file count mismatch")
    for entry in identity["files"]:
        path = directory / entry["path"]
        if not path.resolve().is_relative_to(directory.resolve()) or digest(path) != entry["sha256"]:
            raise ValueError("pinned file mismatch: " + entry["path"])
    dataset = json.loads((directory / "dataset.json").read_text())
    if dataset["datasetVersion"] != DATASET or tuple(c["caseId"] for c in dataset["cases"]) != CASE_IDS:
        raise ValueError("frozen case order mismatch")
    for case in dataset["cases"]:
        path = directory / case["input"]["path"]
        if not path.resolve().is_relative_to(directory.resolve()) or digest(path) != case["input"]["sha256"]:
            raise ValueError("fixture checksum mismatch: " + case["caseId"])
    state = json.loads((root / ".agents/state/product-trust-v1.json").read_text())
    candidate, approval, phase = state["candidate"], state["liveBaselineApproval"], state["phaseExecution"]["PT-2"]
    if (candidate["status"] != "FROZEN" or candidate["truthApprovedBy"] != "USER"
            or candidate["frozenIdentitySha256"] != IDENTITY or candidate["candidateRevision"] != 3
            or state["phaseCompletion"]["PT-1"]["status"] != "COMPLETE"
            or approval["decision"] != "APPROVED" or approval["approvedBy"] != "USER"
            or approval["gate"] != "LIVE_REALISTIC_BASELINE" or approval["candidateRevision"] != 3
            or approval["candidateIdentitySha256"] != IDENTITY):
        raise ValueError("external frozen truth/live approval mismatch")
    if (phase["modelUnderTestRunCount"] != 0 or phase["baselineExecutionStarted"]
            or phase["executionBaseSha"] != BASE or phase["procedureVersion"] != PROCEDURE
            or phase["procedureSha256"] != digest(root / PROTOCOL)):
        raise ValueError("procedure changed or baseline already started; do not repeat")
    return dataset


def current_main():
    result = subprocess.run(
        ["gh", "api", "repos/siamese-lang/terraformers-platform/git/ref/heads/main", "--jq", ".object.sha"],
        capture_output=True, text=True, timeout=30, check=True)
    return result.stdout.strip()


class CurlClient:
    def __init__(self, token_file):
        token = token_file.read_text().strip()
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", token):
            raise ValueError("invalid ephemeral token")
        fd, name = tempfile.mkstemp(prefix="pt2-auth-", dir=token_file.parent)
        self.header_file = Path(name)
        with os.fdopen(fd, "w") as handle:
            handle.write("Authorization: Bearer " + token + "\n")

    def close(self):
        self.header_file.unlink(missing_ok=True)

    def request(self, method, path, directory, name, fixture=None, project_name=None):
        body_file, headers_file = directory / (name + ".body"), directory / (name + ".headers")
        command = ["curl", "-sS", "--connect-timeout", "10", "--max-time", "20", "-X", method,
                   "--header", "@" + str(self.header_file), "-D", str(headers_file),
                   "-o", str(body_file), "-w", "%{http_code}"]
        if fixture:
            command += ["-F", f"file=@{fixture};type=image/png", "-F", "projectName=" + project_name]
        command += ["http://127.0.0.1:18080" + path]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=25, check=False)
            exit_code = result.returncode
            status = int(result.stdout) if result.stdout.isdigit() else 0
            diagnostic = "" if exit_code == 0 else "curl transport failure"
        except (OSError, subprocess.TimeoutExpired) as error:
            exit_code, status, diagnostic = -1, 0, type(error).__name__
        observed, monotonic = stamp(), time.monotonic()
        body = body_file.read_text(errors="replace") if body_file.exists() else ""
        headers = headers_file.read_text(errors="replace").splitlines() if headers_file.exists() else []
        headers = [line for line in headers if not re.match(r"(?i)(authorization|set-cookie):", line)]
        headers_file.unlink(missing_ok=True)
        try:
            parsed = json.loads(body)
        except ValueError:
            parsed = None
        response = {"method": method, "path": path, "httpStatus": status, "transportExitCode": exit_code,
                    "observedAt": observed, "headers": headers, "body": body, "json": parsed,
                    "diagnostic": diagnostic}
        write_json(directory / (name + ".json"), response)
        return {**response, "monotonic": monotonic}


def collect_logs(job_id, since):
    result = subprocess.run(
        ["kubectl", "-n", "terraformers-target", "logs", "deployment/terraformers-backend", "-c", "backend",
         "--since-time=" + since, "--timestamps=true", "--tail=5000"],
        capture_output=True, text=True, timeout=30, check=False)
    lines = [line for line in result.stdout.splitlines() if re.search(r"analysisJobId=" + re.escape(job_id) + r"(?:\s|$)", line)]
    stages = []
    for line in lines:
        match = re.search(r"analysis stage outcome=(\w+) stage=(\w+).*?elapsedMs=(\d+)", line)
        if match:
            stages.append({"outcome": match[1], "stage": match[2], "elapsedMs": int(match[3]), "line": line})
    return {"jobId": job_id, "since": since, "exitCode": result.returncode, "lines": lines,
            "stageTimings": stages, "internalFactRetrievalGenerationTimings": "NOT_OBSERVED",
            "diagnostic": "" if result.returncode == 0 else "log collection failed"}


def run(root, output, run_id, dispatch_sha, client, read_main=current_main,
        logs=collect_logs, clock=time.monotonic, wait=time.sleep, deadline=420):
    dataset = verify(root)
    output.mkdir(parents=True, exist_ok=False)
    write_json(output / "procedure.json", {
        "procedureVersion": PROCEDURE, "protocolSha256": digest(root / PROTOCOL),
        "executionBaseSha": BASE, "dispatchSha": dispatch_sha, "runId": run_id,
        "datasetVersion": DATASET, "candidateRevision": 3, "candidateIdentitySha256": IDENTITY,
        "caseOrder": CASE_IDS, "acceptedUploadsPerCase": 1, "outerRetries": 0,
        "pollDeadlineSeconds": deadline, "pollIntervalSeconds": 5, "standaloneModelInvocations": 0,
        "representativePath": "authenticated upload -> persisted AnalysisJob -> project/Terraform readback",
    })
    records = [{"caseId": case_id, "status": "NOT_RUN", "uploadAttempts": 0} for case_id in CASE_IDS]
    checkpoint_number, seen_jobs, all_terminal = 0, set(), True

    def checkpoint():
        nonlocal checkpoint_number
        write_json(output / f"ledger-{checkpoint_number:03}.json", records)
        checkpoint_number += 1

    checkpoint()
    try:
        for case, record in zip(dataset["cases"], records):
            if read_main() != dispatch_sha:
                raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT; stop before the next case")
            fixture = root / "evaluation" / DATASET / case["input"]["path"]
            if digest(fixture) != case["input"]["sha256"]:
                raise ValueError("fixture changed before submission")
            directory = output / case["caseId"]
            directory.mkdir()
            write_json(directory / "frozen-truth.json", case)
            record.update(status="SUBMISSION_STARTED", uploadAttempts=1, requestStartedAt=stamp())
            checkpoint()
            client.request("GET", "/actuator/prometheus", directory, "metrics-before")
            upload = client.request("POST", "/api/upload", directory, "upload", fixture=fixture,
                                    project_name=run_id + "-" + case["caseId"])
            if upload["transportExitCode"] != 0:
                record["status"] = "INDETERMINATE_ACCEPTANCE"
                all_terminal = False
                break  # A lost POST response never authorizes resubmission.
            if upload["httpStatus"] != 201:
                record.update(status="UPLOAD_REJECTED", uploadHttpStatus=upload["httpStatus"])
                all_terminal = False
                if 200 <= upload["httpStatus"] < 300 or upload["httpStatus"] in (401, 403) or upload["httpStatus"] >= 500:
                    break
                checkpoint()
                continue
            accepted = upload["json"]
            if (not isinstance(accepted, dict)
                    or not isinstance(accepted.get("analysisJobId"), str)
                    or not re.fullmatch(r"[A-Za-z0-9-]{1,128}", accepted["analysisJobId"])
                    or type(accepted.get("projectId")) is not int or accepted["projectId"] <= 0):
                record["status"] = "INDETERMINATE_ACCEPTANCE"
                all_terminal = False
                break
            job_id, project_id = accepted["analysisJobId"], accepted["projectId"]
            if job_id in seen_jobs:
                raise ValueError("duplicate accepted job identity")
            seen_jobs.add(job_id)
            accepted_clock, last_nonterminal = upload["monotonic"], 0
            record.update(status="ACCEPTED", analysisJobId=job_id, projectId=project_id,
                          acceptedResponseObservedAt=upload["observedAt"], serverCreatedAt=accepted.get("createdAt"),
                          sourceFileId=accepted.get("sourceFileId"))
            checkpoint()
            terminal, poll_number = None, 0
            while clock() - accepted_clock < deadline:
                observed = client.request("GET", "/api/analysis/jobs/" + job_id, directory, f"job-poll-{poll_number:03}")
                poll_number += 1
                if observed["transportExitCode"] == 0 and observed["httpStatus"] == 200:
                    job = observed["json"]
                    if not isinstance(job, dict) or job.get("id") != job_id or job.get("projectId") != project_id:
                        raise ValueError("polled job identity mismatch")
                    elapsed = max(0, round((observed["monotonic"] - accepted_clock) * 1000))
                    if job.get("status") in ("SUCCEEDED", "FAILED"):
                        terminal = job
                        record.update(status=job["status"], terminalObservedAt=observed["observedAt"],
                                      acceptedToTerminalObservedMs=elapsed, lastNonterminalAfterAcceptanceMs=last_nonterminal,
                                      quality=job.get("quality"), failureReason=job.get("failureReason"))
                        write_json(directory / "terminal-job.json", job)
                        break
                    if job.get("status") not in ("PENDING", "RUNNING"):
                        raise ValueError("unexpected AnalysisJob status")
                    last_nonterminal = elapsed
                elif observed["httpStatus"] in (401, 403, 404):
                    raise ValueError("accepted job inaccessible; do not resubmit")
                wait(5)  # Read-only polling retries do not invoke another model/job.
            if terminal is None:
                record.update(status="TERMINAL_NOT_OBSERVED", censoredObservationMs=round((clock() - accepted_clock) * 1000))
                all_terminal = False
            project_response = client.request("GET", f"/api/projects/{project_id}", directory, "project")
            draft_response = client.request("GET", f"/api/projects/{project_id}/terraform/main.tf", directory, "terraform")
            project, draft = project_response["json"], draft_response["json"]
            if project_response["httpStatus"] == 200:
                if not isinstance(project, dict) or project.get("projectId") != project_id or project.get("latestAnalysisJobId") != job_id:
                    raise ValueError("project readback job identity mismatch")
            else:
                project = None
            if draft_response["httpStatus"] == 200:
                if (not isinstance(draft, dict) or draft.get("projectId") != project_id
                        or draft.get("latestAnalysisJobId") != job_id or not isinstance(draft.get("content"), str)
                        or (terminal and draft.get("latestResultObjectKey") != terminal.get("resultObjectKey"))):
                    raise ValueError("Terraform readback job identity mismatch")
                (directory / "main.tf").write_text(draft["content"])
            client.request("GET", "/actuator/prometheus", directory, "metrics-after")
            try:
                correlated = logs(job_id, record["requestStartedAt"])
            except (OSError, subprocess.TimeoutExpired) as error:
                correlated = {"jobId": job_id, "status": "NOT_OBSERVED", "diagnostic": type(error).__name__}
            write_json(directory / "correlated-backend-logs.json", correlated)
            quality = (terminal or {}).get("quality") or {}
            write_json(directory / "observation.json", {
                "caseId": case["caseId"], "analysisJobId": job_id, "projectId": project_id,
                "terminal": terminal, "project": project, "terraformReadbackHttpStatus": draft_response["httpStatus"],
                "backendEvidenceBackedClaim": bool(terminal and terminal["status"] == "SUCCEEDED"
                    and quality.get("qualityStatus") == "EVIDENCE_BACKED" and quality.get("technicalStatus") == "PASS"),
                "presentationSuccessClaim": bool(project and project.get("analysisStatus") == "SUCCEEDED"),
                "browserExecuted": False, "semanticFidelity": "REVIEW_PENDING", "falseTrustedSuccess": "REVIEW_PENDING",
                "rawExtractedFacts": "NOT_OBSERVED", "rankedRetrievalHits": "NOT_OBSERVED",
                "initialDraftAndRepairTrace": "NOT_OBSERVED", "internalStageTimings": "NOT_OBSERVED",
                "metricsAttribution": "CUMULATIVE_CONTEXT_ONLY", "latency": record,
            })
            checkpoint()
            if terminal is None:
                break  # Keep the accepted job; do not cancel it or queue cases behind it.
    except Exception as error:
        all_terminal = False
        write_json(output / "observation-error.json", {"errorClass": type(error).__name__, "reason": str(error)})
        raise
    finally:
        checkpoint()
        complete = all_terminal and len(seen_jobs) == len(CASE_IDS)
        write_json(output / "observations.json", {
            "runId": run_id, "datasetVersion": DATASET, "cases": records,
            "allTenTerminalObserved": complete, "phaseAcceptance": "INDEPENDENT_REVIEW_REQUIRED",
        })
    return complete


def inventory(output):
    entries = [{"path": str(path.relative_to(output)), "sha256": digest(path)}
               for path in sorted(output.rglob("*")) if path.is_file() and path.name != "artifact-sha256.json"]
    write_json(output / "artifact-sha256.json", {"files": entries})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("verify", "run", "inventory"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--token-file", type=Path)
    parser.add_argument("--dispatch-sha", default=os.environ.get("GITHUB_SHA"))
    args = parser.parse_args()
    if args.action == "verify":
        dataset = verify(args.root)
        print(f"Verified {len(dataset['cases'])} frozen cases and 26 pinned files; no live calls.")
    elif args.action == "inventory":
        if not args.output:
            parser.error("inventory requires --output")
        inventory(args.output)
    else:
        if not all((args.output, args.run_id, args.token_file, args.dispatch_sha)):
            parser.error("run requires --output, --run-id, --token-file and dispatch SHA")
        verify(args.root)
        client = CurlClient(args.token_file)
        try:
            complete = run(args.root, args.output, args.run_id, args.dispatch_sha, client)
        finally:
            client.close()
        if not complete:
            raise SystemExit("Partial baseline evidence retained; do not rerun.")


if __name__ == "__main__":
    main()
