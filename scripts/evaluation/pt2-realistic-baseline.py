#!/usr/bin/env python3
"""Bounded orchestration of the existing launcher; no evaluator or model policy."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
from datetime import datetime, timezone
from urllib.parse import quote

IDENTITY = "04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014"
DATASET = "terraformers-realistic-v1"
CASE_IDS = (
    "pt1-01-serverless-portal", "pt1-02-order-fanout", "pt1-03-parallel-lookup",
    "pt1-04-analytics-catalog", "pt1-05-private-web-fleet", "pt1-06-thumbnail-pipeline",
    "pt1-07-unresolved-design", "pt1-08-partial-export", "pt1-09-sprint-board",
    "pt1-10-workshop-table",
)
NAMESPACE = "terraformers-target"
POD = "terraformers-evaluation-baseline"
REMOTE = "/tmp/workspace/evaluation/terraformers-realistic-v1/dataset.json"
CLASSPATH = "/tmp/workspace/classes:/tmp/workspace/dependency/*"
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
    # Exclusive creation makes accidental replacement/resume fail closed.
    with path.open("x") as handle:
        json.dump(value, handle, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())


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
    candidate = state["candidate"]
    approval = state["liveBaselineApproval"]
    phase = state["phaseExecution"]["PT-2"]
    if (candidate["status"] != "FROZEN" or candidate["truthApprovedBy"] != "USER"
            or candidate["frozenIdentitySha256"] != IDENTITY
            or candidate["candidateRevision"] != 3
            or state["phaseCompletion"]["PT-1"]["status"] != "COMPLETE"
            or approval["decision"] != "APPROVED" or approval["approvedBy"] != "USER"
            or approval["gate"] != "LIVE_REALISTIC_BASELINE" or approval["candidateRevision"] != 3
            or approval["candidateIdentitySha256"] != IDENTITY):
        raise ValueError("external frozen truth/live approval mismatch")
    if (phase["modelUnderTestRunCount"] != 0 or phase["baselineExecutionStarted"]
            or phase["executionBaseSha"] != "3caae454661d21d84c3e469a7d76aff5633d5b26"
            or phase["procedureSha256"] != digest(root / "docs/evaluation/product-trust-pt-2-measurement-protocol.md")):
        raise ValueError("procedure changed or baseline already started; do not repeat")
    return dataset


def exec_pod(arguments):
    return subprocess.run(["kubectl", "-n", NAMESPACE, "exec", POD, "--", *arguments],
                          capture_output=True, text=True, timeout=460, check=False)


def stamp():
    return datetime.now(timezone.utc).isoformat()


def current_main():
    result = subprocess.run(
        ["gh", "api", "repos/siamese-lang/terraformers-platform/git/ref/heads/main", "--jq", ".object.sha"],
        capture_output=True, text=True, timeout=30, check=True)
    return result.stdout.strip()


def valid_trace(raw, case, run_id):
    traces = raw.get("traces", [])
    if raw.get("datasetVersion") != DATASET or raw.get("runId") != run_id or len(traces) != 1:
        raise ValueError("single-case raw identity mismatch")
    trace = traces[0]
    if (trace.get("caseId") != case["caseId"] or trace.get("runId") != run_id
            or trace.get("input", {}).get("sha256") != case["input"]["sha256"]
            or trace.get("configuration") != raw.get("configuration")):
        raise ValueError("trace identity mismatch")
    return trace


def run(root, output, run_id, dispatch_sha, execute=exec_pod, read_main=current_main):
    dataset = verify(root)
    # One attempt directory per workflow run; never silently resume or overwrite it.
    output.mkdir(parents=True, exist_ok=False)
    protocol = root / "docs/evaluation/product-trust-pt-2-measurement-protocol.md"
    write_json(output / "procedure.json", {
        "procedureVersion": "pt2-realistic-baseline-v1", "protocolSha256": digest(protocol),
        "datasetVersion": DATASET, "candidateRevision": 3, "candidateIdentitySha256": IDENTITY,
        "runId": run_id, "profile": PROFILE, "caseOrder": CASE_IDS,
        "dispatchSha": dispatch_sha,
        "invocationsPerCase": 1, "outerRetries": 0, "timeoutSeconds": 420,
        "initialEvidenceBudget": 16,
        "persistedTrustStatus": "NOT_MEASURED", "acceptedToTerminalLatency": "NOT_MEASURED",
    })
    raws, attempts = [], []
    for case in dataset["cases"]:
        if read_main() != dispatch_sha:
            raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT; stop before the next case")
        case_id = case["caseId"]
        case_dir = output / case_id
        case_dir.mkdir()
        remote_raw = f"/tmp/{run_id}-{case_id}.json"
        started = time.monotonic()
        write_json(case_dir / "started.json", {"caseId": case_id, "startedAt": stamp(), "attempt": 1})
        env = {**PROFILE, "EVALUATION_DATASET": REMOTE, "EVALUATION_OUTPUT": remote_raw,
               "EVALUATION_RUN_ID": run_id, "EVALUATION_MODE": "single", "EVALUATION_CASE_ID": case_id,
               "EVALUATION_INITIAL_EVIDENCE_BUDGET": "16"}
        command = ["env", *(f"{key}={value}" for key, value in env.items()),
                   "timeout", "--kill-after=10s", "420s", "java", "-cp", CLASSPATH,
                   "com.terraformers.modernization.evaluation.LiveEvaluationLauncher"]
        # Exceptions (including lost transport) stop the batch: no ambiguous duplicate submission.
        try:
            completed = execute(command)
        except (OSError, subprocess.TimeoutExpired) as error:
            write_json(case_dir / "attempt.json", {
                "caseId": case_id, "attempt": 1, "endedAt": stamp(),
                "pipelineWallMs": round((time.monotonic() - started) * 1000),
                "status": "INDETERMINATE_TRANSPORT", "errorClass": type(error).__name__,
                "rawAvailable": False, "rerunPermitted": False,
            })
            raise
        (case_dir / "stdout.txt").write_text(completed.stdout)
        (case_dir / "stderr.txt").write_text(completed.stderr)
        attempt = {"caseId": case_id, "attempt": 1, "endedAt": stamp(),
                   "pipelineWallMs": round((time.monotonic() - started) * 1000),
                   "exitCode": completed.returncode, "rawAvailable": False,
                   "timedOut": completed.returncode in (124, 137), "status": "NO_RAW_TRACE"}
        fetched = execute(["cat", remote_raw])
        if fetched.returncode == 0:
            (case_dir / "raw.json").write_text(fetched.stdout)
            raw = json.loads(fetched.stdout)
            valid_trace(raw, case, run_id)
            if raws and raw["configuration"] != raws[0]["configuration"]:
                raise ValueError("runtime configuration changed within batch")
            raws.append(raw)
            attempt.update(rawAvailable=True, status="RAW_TRACE_CAPTURED")
        else:
            (case_dir / "raw-unavailable.txt").write_text(fetched.stderr)
        write_json(case_dir / "attempt.json", attempt)
        attempts.append(attempt)
    write_json(output / "attempts.json", {"runId": run_id, "cases": attempts,
                                           "allTenRawTraces": len(raws) == 10})
    if raws:
        combined = {**raws[0], "traces": [t for raw in raws for t in raw["traces"]]}
        write_json(output / "raw.json", combined)
        # No additional vector query/model call: fetch only the documents actually selected.
        ids = set()
        for trace in combined["traces"]:
            retrieval = trace["retrieval"].get("evidence") or {}
            closure = (trace["generation"].get("evidence") or {}).get("groundingClosure") or {}
            hits = retrieval.get("hits", []) + closure.get("finalSelectedReferences", [])
            hits += (closure.get("closureRetrieval") or {}).get("hits", [])
            ids.update(hit["documentId"] for hit in hits)
        documents = output / "reference-documents"
        documents.mkdir()
        for document_id in sorted(ids):
            url = PROFILE["OPENSEARCH_ENDPOINT"] + "/terraformers-reference-v4/_doc/" + quote(document_id, safe="")
            fetched = execute(["curl", "--max-time", "20", "-fsS", url])
            write_json(documents / (hashlib.sha256(document_id.encode()).hexdigest() + ".json"), {
                "documentId": document_id, "exitCode": fetched.returncode,
                "response": fetched.stdout, "diagnostic": fetched.stderr,
            })
    return len(raws) == 10


def inventory(output):
    write_json(output / "artifact-sha256.json", [
        {"path": str(path.relative_to(output)), "sha256": digest(path)}
        for path in sorted(output.rglob("*")) if path.is_file()
    ])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("verify", "run", "inventory"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    parser.add_argument("--run-id")
    parser.add_argument("--dispatch-sha", default=os.environ.get("GITHUB_SHA"))
    args = parser.parse_args()
    if args.mode == "verify":
        verify(args.root)
        print("PASS: revision 3 identity, 26 pinned files, ten ordered fixture checksums")
    elif args.mode == "inventory":
        if args.output is None:
            parser.error("output is required")
        inventory(args.output)
    else:
        if not args.run_id or not args.run_id.replace("-", "").isalnum():
            parser.error("run-id must be alphanumeric/hyphen")
        if args.output is None:
            parser.error("output is required")
        if not args.dispatch_sha or len(args.dispatch_sha) != 40 or any(c not in "0123456789abcdef" for c in args.dispatch_sha):
            parser.error("dispatch-sha must be a full trusted main SHA")
        raise SystemExit(0 if run(args.root, args.output, args.run_id, args.dispatch_sha) else 1)


if __name__ == "__main__":
    main()
