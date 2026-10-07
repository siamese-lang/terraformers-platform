#!/usr/bin/env python3
"""Bounded, manually gated PT-8A observation; never a model judge or production adapter."""
import argparse
from datetime import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
IDENTITY = "3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757"
CASES = tuple("aws-official-" + c for c in "abcde")
PROCEDURE = "docs/evaluation/product-trust-pt-8a-official-acceptance-procedure.md"
SCORING = ("architecture_classification", "core_components_services", "directed_relationships",
           "containment_cardinality", "forbidden_invented_interpretations", "resource_intent",
           "personalized_inputs", "official_evidence_closure", "real_cli_reviewable_draft", "persisted_visible_trust")
CORPUS_SOURCE = "1ae69d589ac3965733818819792d98c5638e0ae5"
CORPUS_CHECKSUM = "da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a"


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


rag = module("pt8a_rag", "scripts/rag/ingest-gcp-target-corpus.py")
transport = module("pt8a_transport", "scripts/evaluation/pt2-realistic-baseline.py")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps(value, indent=2, allow_nan=False)
    # Free-form product/error strings are evidence, never a reason to publish credentials.
    data = re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|ya29\.[A-Za-z0-9_-]+|AKIA[A-Z0-9]{16}", "[REDACTED_CREDENTIAL]", data)
    data = re.sub(r"-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]", data)
    path.write_text(data + "\n")


def frozen_inputs(root=ROOT):
    """Return acquisition fields only. Truth is never returned to request construction."""
    identity = json.loads((root / "evaluation/terraformers-aws-official-v1/candidate-identity.json").read_text())
    core = {key: identity[key] for key in ("datasetId", "candidateRevision", "files")}
    if sha(json.dumps(core, sort_keys=True, separators=(",", ":")).encode()) != IDENTITY:
        raise ValueError("frozen candidate identity mismatch")
    for entry in identity["files"]:
        path = root / entry["path"]
        if not path.resolve().is_relative_to(root.resolve()) or sha(path.read_bytes()) != entry["sha256"]:
            raise ValueError("frozen truth/manifest bytes changed")
    manifest = json.loads((root / "evaluation/terraformers-aws-official-v1/candidate-manifest.json").read_text())
    if manifest["candidateRevision"] != 2 or tuple(c["caseId"] for c in manifest["cases"]) != CASES:
        raise ValueError("frozen revision/order mismatch")
    return [{key: c[key] for key in ("caseId", "imageUrl", "sha256", "sizeBytes", "mediaType", "width", "height")}
            for c in manifest["cases"]]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, url):
        raise ValueError("frozen image redirects are forbidden")


def image_host(case):
    parsed = urllib.parse.urlsplit(case["imageUrl"])
    if parsed.scheme != "https" or parsed.netloc != "docs.aws.amazon.com" or parsed.fragment or parsed.username:
        raise ValueError("frozen AWS host mismatch")


def verify_image(case, data, media_type):
    image_host(case)
    if sha(data) != case["sha256"] or len(data) != case["sizeBytes"]:
        raise ValueError("raw image hash/size mismatch")
    # All frozen revision-2 inputs are PNG. No decoding/restyling or third-party image dependency.
    if (media_type != case["mediaType"] or media_type != "image/png" or len(data) < 33
            or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR"
            or struct.unpack(">II", data[16:24]) != (case["width"], case["height"])):
        raise ValueError("media type or frozen dimensions mismatch")


def acquire(case, directory):
    # Do not follow redirects or fetch documentation/truth; exact raw image URL only.
    image_host(case)
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(case["imageUrl"], timeout=30) as response:
        if response.status != 200 or response.url != case["imageUrl"]:
            raise ValueError("frozen image HTTP identity mismatch")
        data = response.read(case["sizeBytes"] + 1)
        verify_image(case, data, response.headers.get_content_type())
    path = directory / "input.png"
    path.write_bytes(data)
    return path


def fields(body, prefix):
    if not body.startswith(prefix + "\n"):
        raise ValueError("structured authority marker missing")
    return dict(re.findall(r"^([a-z_][a-z0-9_]*):\s*([^\n]+)$", body, re.M))


def github(path):
    return json.loads(subprocess.check_output(["gh", "api", "repos/siamese-lang/terraformers-platform/" + path], text=True))


def authority_comment(comment_id, prefix):
    comment = github(f"issues/comments/{int(comment_id)}")
    if comment["user"]["login"] != "siamese-lang":
        raise ValueError("authority comment is not repository USER/reviewer")
    return fields(comment["body"], prefix)


def request_contract(request, source, image, attempt):
    allowed = {"mode", "caseId", "liveApprovalCommentId", "provenanceRunId", "provenanceArtifactId",
               "priorRunId", "priorArtifactId", "priorReviewCommentId"}
    if set(request) - allowed or request.get("mode") not in ("readiness", "case") or str(attempt) != "1":
        raise ValueError("invalid request or GitHub rerun; no inference")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or not re.fullmatch(
            r"asia-northeast3-docker\.pkg\.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:[0-9a-f]{64}", image):
        raise ValueError("exact source/digest required")
    if request["mode"] == "case" and request.get("caseId") not in CASES:
        raise ValueError("unknown official case")
    for key in ("liveApprovalCommentId", "provenanceRunId", "provenanceArtifactId"):
        if type(request.get(key)) is not int or request[key] <= 0:
            raise ValueError("exact GitHub evidence IDs required")
    if request["mode"] == "case":
        for key in ("priorRunId", "priorArtifactId", "priorReviewCommentId"):
            if type(request.get(key)) is not int or request[key] <= 0:
                raise ValueError("prior accepted artifact/review required")
    approval = authority_comment(request["liveApprovalCommentId"], "[HUMAN_GATE_APPROVAL:v1]")
    expected = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
                "reviewed_source_sha": source, "backend_image": image, "candidate_identity": IDENTITY,
                "procedure_sha256": sha((ROOT / PROCEDURE).read_bytes())}
    if any(approval.get(key) != value for key, value in expected.items()):
        raise ValueError("live approval does not bind this exact procedure/source/image/candidate")
    if transport.current_main() != source:
        raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT")


def ensure_latest_dispatch(request, source, current_run_id):
    """Existing serial workflow + authoritative history, not a new lock/queue service.

    An older readiness artifact cannot reset the once-only case ledger. A missing artifact
    after a non-skipped PT8A job is indeterminate, not permission to submit again.
    """
    previous = []
    for page in range(1, 11):
        runs = github("actions/workflows/gcp-target-runtime-dependencies.yml/runs?"
                      f"head_sha={source}&event=workflow_dispatch&branch=main&per_page=100&page={page}")["workflow_runs"]
        previous.extend(r for r in runs if r["id"] != current_run_id and r["head_sha"] == source)
        if len(runs) < 100:
            break
    else:
        raise ValueError("PT8A history inspection bound exhausted; no upload")
    for run in sorted(previous, key=lambda r: r["id"], reverse=True):
        jobs = github(f"actions/runs/{run['id']}/jobs?per_page=100")
        if jobs["total_count"] > 100:
            raise ValueError("job history inspection incomplete; no upload")
        candidates = [j for j in jobs["jobs"] if j["name"] == "pt8a-official-acceptance"]
        if len(candidates) != 1:
            raise ValueError("prior dispatch operation unproven; no upload")
        if candidates[0]["conclusion"] == "skipped":
            continue
        if run["status"] != "completed" or run["run_attempt"] != 1:
            raise ValueError("prior PT8A dispatch incomplete or rerun; no upload")
        artifacts = github(f"actions/runs/{run['id']}/artifacts?per_page=100")
        matches = [a for a in artifacts["artifacts"] if a["name"] == f"pt8a-official-{run['id']}"]
        if artifacts["total_count"] > 100 or len(matches) != 1:
            raise ValueError("prior PT8A evidence unavailable; no upload or resubmission")
        if request["mode"] == "readiness" or (request["priorRunId"], request["priorArtifactId"]) != (run["id"], matches[0]["id"]):
            raise ValueError("stale prior artifact would reset once-only history; no upload")
        return
    if request["mode"] == "case":
        raise ValueError("no prior deployed readiness dispatch; no official upload")


def bound_artifact(run_id, artifact_id, names, directory, workflow):
    """Authenticated immutable artifact download; extract only explicitly selected bounded JSON."""
    import zipfile
    run, artifact = github(f"actions/runs/{run_id}"), github(f"actions/artifacts/{artifact_id}")
    if (run["event"] != "workflow_dispatch" or run["head_branch"] != "main" or run["run_attempt"] != 1
            or run["path"] != workflow or run["status"] != "completed"
            or artifact["expired"] or artifact["workflow_run"]["id"] != run_id
            or artifact["workflow_run"]["head_sha"] != run["head_sha"]
            or artifact.get("digest", "")[:7] != "sha256:"):
        raise ValueError("GitHub artifact/run binding mismatch")
    archive = directory / f"artifact-{artifact_id}.zip"
    with archive.open("wb") as stream:
        subprocess.run(["gh", "api", f"repos/siamese-lang/terraformers-platform/actions/artifacts/{artifact_id}/zip"], stdout=stream, check=True)
    if "sha256:" + sha(archive.read_bytes()) != artifact["digest"]:
        raise ValueError("artifact archive digest mismatch")
    with zipfile.ZipFile(archive) as zipped:
        result = {}
        for name in names:
            member = zipped.getinfo(name)
            if member.file_size > 2_000_000:
                raise ValueError("bounded evidence JSON too large")
            result[name] = json.loads(zipped.read(member))
    return result, {"runId": run_id, "artifactId": artifact_id, "digest": artifact["digest"],
                    "sourceSha": run["head_sha"], "conclusion": run["conclusion"]}


def require_clean_receipt(receipt, binding, request, source):
    """Clean re-embedding must precede the first readiness dispatch at this live source."""
    expected = {"ingestion_mode": rag.PT8A_CLEAN_MODE, "reviewed_source_sha": source,
                "live_approval_comment_id": request["liveApprovalCommentId"]}
    if (binding.get("conclusion") != "success" or binding.get("sourceSha") != source
            or any(receipt.get(key) != value for key, value in expected.items())):
        raise ValueError("MODEL_PROVENANCE_UNPROVEN: completed approved clean-v4 receipt required before readiness")


def next_case(ledger, case_id, review):
    if tuple(row["caseId"] for row in ledger) != CASES:
        raise ValueError("case ledger order mismatch")
    index = CASES.index(case_id)
    if any(row["status"] != "NOT_RUN" for row in ledger[index:]):
        raise ValueError("case already attempted/accepted or frozen order violated")
    if any(row["status"] != "PASS" for row in ledger[:index]):
        raise ValueError("prior case is NOT_PASS or unreviewed; later cases remain NOT_RUN")
    if review.get("decision") != "ACCEPTED" or review.get("material_defect") != "false" or review.get("false_trusted_success") != "0":
        raise ValueError("independent semantic/technical/trust acceptance required")


def reviewed_prior(review, binding, run_id, artifact_id, digest, case_id):
    required = {"reviewed_source_sha": binding["sourceSha"], "candidate_identity": IDENTITY,
                "procedure_sha256": binding["procedureSha256"], "run_id": str(run_id),
                "artifact_id": str(artifact_id), "artifact_digest": digest, "case_id": case_id}
    if any(review.get(key) != value for key, value in required.items()):
        raise ValueError("independent review source/procedure/case/artifact binding mismatch")
    dimensions = ("readiness",) if case_id == "readiness-only" else SCORING
    if any(review.get("dimension_" + name) != "PASS" for name in dimensions):
        raise ValueError("independent per-dimension acceptance missing; no next case")


def mark_upload(record, response):
    if response["transportExitCode"] != 0 or response["httpStatus"] >= 500 or 200 <= response["httpStatus"] < 300 and response["httpStatus"] != 201:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        raise ValueError("ambiguous upload acceptance; never resubmit")
    if response["httpStatus"] != 201:
        record["status"] = "PRE_ACCEPTANCE_REJECTED"
        raise ValueError("pre-acceptance rejection; operator review required, no automatic retry")
    accepted = response["json"]
    if (not isinstance(accepted, dict) or not re.fullmatch(r"[A-Za-z0-9-]{1,128}", str(accepted.get("analysisJobId", "")))
            or type(accepted.get("projectId")) is not int or accepted["projectId"] <= 0
            or type(accepted.get("sourceFileId")) is not int or accepted["sourceFileId"] <= 0
            or not isinstance(accepted.get("createdAt"), str) or accepted.get("binaryPersisted") is not True):
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        raise ValueError("accepted job/object identity unavailable; never resubmit")
    try:
        if datetime.fromisoformat(accepted["createdAt"].replace("Z", "+00:00")).tzinfo is None:
            raise ValueError("acceptance timestamp needs timezone")
    except ValueError:
        record["status"] = "INDETERMINATE_ACCEPTANCE"
        raise ValueError("accepted timestamp unavailable; never resubmit") from None
    record.update(status="ACCEPTED", consumed=True, jobId=accepted["analysisJobId"], projectId=accepted["projectId"],
                  sourceFileId=accepted.get("sourceFileId"), acceptedAt=accepted.get("createdAt"))


def sanitized_job(job):
    names = ("id", "projectId", "sourceFileId", "resultFileId", "sourceBucket", "sourceKey", "resultObjectKey",
             "correlationId", "status", "analysisMode", "provider", "detectedComponents", "detectedRelationships",
             "quality", "timing", "failureReason", "warnings", "analysisSummary", "createdAt", "updatedAt")
    return {key: job.get(key) for key in names}


def retrieval_evidence(job_id, since, expected_documents):
    raw = transport.collect_logs(job_id, since)
    events, stages = [], []
    expected = {doc["documentId"]: doc for doc in expected_documents}
    pattern = r"reference retrieval outcome=success mode=REQUIRED embeddingProvider=vertex index=(\S+).*?hitCount=(\d+) documentIds=\[([^\]]*)\].*?elapsedMs=(\d+)"
    for line in raw["lines"]:
        match = re.search(pattern, line)
        if match:
            index, count, ids, elapsed = match.groups()
            ids = [value.strip() for value in ids.split(",") if value.strip()]
            if index != "terraformers-reference-v4" or len(ids) != int(count) or any(i not in expected for i in ids):
                raise ValueError("correlated retrieval identity mismatch")
            events.append({"index": index, "hitCount": int(count), "elapsedMs": int(elapsed), "documents": [
                {key: expected[i].get(key) for key in ("documentId", "authority", "documentType", "resourceTypes", "sourcePath", "sourceCommit")} for i in ids]})
        match = re.search(r"analysis stage outcome=(\w+) stage=(\w+).*?elapsedMs=(\d+)", line)
        if match:
            stages.append(dict(zip(("outcome", "stage", "elapsedMs"), (match[1], match[2], int(match[3])))))
    official_hits = sum(doc["authority"] == "PROVIDER_DOCUMENTATION"
                        and doc["documentType"] in ("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE")
                        and doc["sourceCommit"] == "f7a3b98da589ab1d52756b0dcee0dbf2de83d635"
                        for event in events for doc in event["documents"])
    return {"jobId": job_id, "events": events, "officialHitCount": official_hits, "stages": stages,
            "generatedClosureTrace": "NOT_EXPOSED_BY_CURRENT_RUNTIME", "rawVisionFacts": "NOT_EXPOSED_BY_CURRENT_RUNTIME"}


def validate_draft(hcl, private, pod):
    # Never publish credentials, vector data or raw HTTP/log bodies. Fail closed on known secret forms.
    if re.search(r"-----BEGIN .*PRIVATE KEY|AKIA[A-Z0-9]{16}|ya29\.[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|(?i:password|secret|secret_key|access_key|access_token|token)\s*=\s*\"(?!\$\{|REPLACE|TODO|<)[^\"]+\"", hcl):
        raise ValueError("unsafe HCL evidence; retain object identity/hash, stop for sanitized independent inspection")
    tf = private / "main.tf"; tf.write_text(hcl)
    namespace = ["kubectl", "-n", "terraformers-target", "exec", "-i", pod, "--"]
    subprocess.run(namespace + ["sh", "-c", "mkdir -p /tmp/pt8a-draft; cat > /tmp/pt8a-draft/main.tf"], input=hcl.encode(), check=True)
    init = subprocess.run(namespace + ["terraform", "-chdir=/tmp/pt8a-draft", "init", "-backend=false", "-input=false", "-no-color", "-plugin-dir=/opt/terraform-plugins"], capture_output=True, text=True)
    validate = None if init.returncode else subprocess.run(namespace + ["terraform", "-chdir=/tmp/pt8a-draft", "validate", "-no-color"], capture_output=True, text=True)
    return {"terraformVersion": "1.8.5", "providerVersion": "5.100.0", "initValidateExitCode": init.returncode if validate is None else validate.returncode,
            "init": {"exitCode": init.returncode, "output": (init.stdout + init.stderr)[:16000]},
            "validate": None if validate is None else {"exitCode": validate.returncode, "output": (validate.stdout + validate.stderr)[:16000]},
            "hclSha256": sha(hcl.encode()), "AWSPlanApply": False}


def observe(client, fixture, directory, record, documents, pod, clock=time.monotonic, wait=time.sleep):
    record.update(status="SUBMISSION_STARTED", uploadAttempts=1)
    write(directory / "attempt.json", record)
    response = client.request("POST", "/api/upload", fixture.parent, "upload", fixture=fixture,
                              project_name="pt8a-" + os.environ["GITHUB_RUN_ID"])
    mark_upload(record, response)
    write(directory / "accepted.json", record)
    started, terminal = clock(), None
    while clock() - started < 540:
        polled = client.request("GET", "/api/analysis/jobs/" + record["jobId"], fixture.parent, "poll-" + str(time.time_ns()))
        job = polled.get("json")
        if polled["httpStatus"] == 200 and isinstance(job, dict):
            if job.get("id") != record["jobId"] or job.get("projectId") != record["projectId"]:
                raise ValueError("owner-scoped job readback identity mismatch")
            if job.get("status") in ("SUCCEEDED", "FAILED"):
                terminal = job; break
        elif polled["httpStatus"] in (401, 403, 404):
            raise ValueError("accepted job inaccessible; no new owner or resubmission")
        wait(5)
    if terminal is None:
        record.update(status="NOT_PASS", observation="TERMINAL_NOT_OBSERVED", censoredObservationMs=round((clock()-started)*1000))
        return
    write(directory / "job.json", sanitized_job(terminal))
    record.update(terminalState=terminal["status"], terminalAt=(terminal.get("timing") or {}).get("terminalAt"),
                  acceptedToTerminalMs=(terminal.get("timing") or {}).get("acceptedToTerminalMs"),
                  deadlineClassification="OPERATING_SERVICE_480000_PLUS_CONDITIONAL_DELTA_REVIEW_REQUIRED")
    quality = terminal.get("quality") or {}
    retrieved = retrieval_evidence(record["jobId"], record["acceptedAt"], documents)
    write(directory / "retrieval.json", retrieved)
    project = client.request("GET", f"/api/projects/{record['projectId']}", fixture.parent, "project")
    if (project["httpStatus"] != 200 or not isinstance(project["json"], dict)
            or project["json"].get("projectId") != record["projectId"]
            or project["json"].get("latestAnalysisJobId") != record["jobId"]):
        raise ValueError("persisted project readback mismatch")
    write(directory / "presentation.json", {key: project["json"].get(key) for key in
          ("projectId", "analysisStatus", "latestAnalysisJobId", "quality", "analysisTiming", "sourceBinaryPersisted", "latestResultObjectKey")})
    if terminal["status"] != "SUCCEEDED":
        record["status"] = "NOT_PASS"; return
    draft = client.request("GET", f"/api/projects/{record['projectId']}/terraform/main.tf", fixture.parent, "draft")
    if (draft["httpStatus"] != 200 or not isinstance(draft["json"], dict)
            or draft["json"].get("latestAnalysisJobId") != record["jobId"]
            or draft["json"].get("latestResultObjectKey") != terminal.get("resultObjectKey")):
        raise ValueError("persisted project/result readback mismatch")
    hcl = draft["json"].get("content")
    if not isinstance(hcl, str) or not hcl.strip():
        raise ValueError("missing persisted Terraform draft")
    write(directory / "draft-identity.json", {"hclSha256": sha(hcl.encode()), "resultObjectKey": terminal.get("resultObjectKey")})
    cli = validate_draft(hcl, fixture.parent, pod)
    write(directory / "cli.json", cli)
    (directory / "main.tf").write_text(hcl)
    record["status"] = "REVIEW_PENDING" if (cli["initValidateExitCode"] == 0 and retrieved["officialHitCount"] > 0
        and quality.get("contractVersion") == "evidence-quality-v1" and quality.get("technicalStatus") == "PASS"
        and quality.get("knowledgeStatus") == "COMPLETE" and project["json"].get("quality") == quality
        and project["json"].get("analysisStatus") == terminal["status"]) else "NOT_PASS"


def seal(output):
    files = [{"path": p.relative_to(output).as_posix(), "sha256": sha(p.read_bytes()), "sizeBytes": p.stat().st_size}
             for p in sorted(output.rglob("*")) if p.is_file() and p.name != "inventory.json"]
    write(output / "inventory.json", {"files": files, "embeddingVectors": False, "credentials": False, "officialImageBytes": False})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("verify", "preflight", "run", "inventory"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--request-file", type=Path)
    args = parser.parse_args()
    frozen_inputs()
    if args.action == "verify":
        print("Frozen official identity/order: PASS; no input acquisition or inference")
        return
    if args.action == "inventory":
        seal(args.output); return
    request = json.loads(args.request_file.read_text())
    source, image = os.environ["GITHUB_SHA"], os.environ["BACKEND_IMAGE"]
    request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
    ensure_latest_dispatch(request, source, int(os.environ["GITHUB_RUN_ID"]))
    if args.action == "preflight":
        return
    output = args.output; output.mkdir(parents=True, exist_ok=False)
    write(output / "release.json", json.loads((Path(os.environ["RUNNER_TEMP"]) / "pt8a-release.json").read_text()))
    ledger = [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in CASES]
    try:
        with tempfile.TemporaryDirectory(prefix="pt8a-private-") as temporary:
            private = Path(temporary)
            receipt, binding = bound_artifact(request["provenanceRunId"], request["provenanceArtifactId"],
                ["receipt.json"], private, ".github/workflows/gcp-target-corpus-ingestion.yml")
            require_clean_receipt(receipt["receipt.json"], binding, request, source)
            manifest, schema, documents, checksum = rag.load_corpus(args.corpus)
            if checksum != CORPUS_CHECKSUM:
                raise ValueError("rebuilt expected corpus checksum differs; do not tune authority")
            readiness = rag.verify_exact_v4(rag.JsonHttpClient("http://127.0.0.1:19200"), manifest, schema,
                documents, checksum, {**binding, "githubArtifactBindingVerified": True, "receipt": receipt["receipt.json"]})
            write(output / "readiness.json", readiness)
            if readiness["classification"] != "EXACT_REUSABLE_COMPLETED_V4":
                raise ValueError("readiness does not authorize case A; no automatic ingestion")
            write(output / "binding.json", {"sourceSha": source, "image": image, "candidateIdentity": IDENTITY,
                "procedureSha256": sha((ROOT / PROCEDURE).read_bytes()), "mode": request["mode"], "caseId": request.get("caseId"),
                "liveApprovalCommentId": request["liveApprovalCommentId"], "runId": int(os.environ["GITHUB_RUN_ID"])})
            if request["mode"] == "case":
                prior, prior_binding = bound_artifact(request["priorRunId"], request["priorArtifactId"],
                    ["ledger.json", "binding.json", "readiness.json"], private, ".github/workflows/gcp-target-runtime-dependencies.yml")
                ledger = prior["ledger.json"]
                prior_identity = prior["binding.json"]
                if any(prior_identity.get(k) != v for k, v in {"sourceSha": source, "image": image,
                        "candidateIdentity": IDENTITY, "liveApprovalCommentId": request["liveApprovalCommentId"],
                        "procedureSha256": sha((ROOT / PROCEDURE).read_bytes())}.items()):
                    raise ValueError("prior case/readiness source identity mismatch")
                review = authority_comment(request["priorReviewCommentId"], "[PRODUCT_TRUST_REVIEW:v1]")
                index = CASES.index(request["caseId"])
                reviewed_prior(review, prior_identity, request["priorRunId"], request["priorArtifactId"], prior_binding["digest"],
                               CASES[index-1] if index else "readiness-only")
                if index > 0:
                    previous = ledger[index-1]
                    if previous["status"] != "REVIEW_PENDING" or prior_identity.get("caseId") != previous["caseId"] or prior_identity["mode"] != "case":
                        raise ValueError("prior first observation/review order mismatch")
                    previous["status"] = "PASS"
                elif prior_identity["mode"] != "readiness" or prior.get("readiness.json", {}).get("correlatedBackendRetrieval") is not True:
                    raise ValueError("accepted deployed-backend retrieval readiness missing")
                next_case(ledger, request["caseId"], review)
                record = ledger[index]
                case = next(c for c in frozen_inputs() if c["caseId"] == request["caseId"])
                fixture = acquire(case, private)
                write(output / "input-identity.json", case)
            else:
                record = {"caseId": "readiness-only", "status": "NOT_RUN", "consumed": False}
                fixture = ROOT / "evaluation/terraformers-realistic-v1/fixtures/pt1-05-private-web-fleet.png"
                # Keep raw transport bodies in the private temporary directory, never the artifact.
                private_fixture = private / "input.png"; private_fixture.write_bytes(fixture.read_bytes()); fixture = private_fixture
            if transport.current_main() != source:
                raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT before inference")
            subprocess.run(["bash", "scripts/smoke/ephemeral-jwks-fixture.sh", "prepare"], check=True)
            client = transport.CurlClient(Path(os.environ["RUNNER_TEMP"]) / "case-c-access.token")
            try:
                observe(client, fixture, output / "observation", record, documents, "terraformers-pt8a-validation")
            finally:
                client.close()
            if request["mode"] == "readiness":
                readiness["correlatedBackendRetrieval"] = record["status"] == "REVIEW_PENDING"
                write(output / "readiness.json", readiness)
                write(output / "readiness-job.json", record)
            if record["status"] == "NOT_PASS":
                raise ValueError("material technical/product failure; preserve later cases NOT_RUN")
    except Exception as error:
        write(output / "error.json", {"class": type(error).__name__, "reason": str(error)[:300]})
        raise
    finally:
        write(output / "ledger.json", ledger)
        seal(output)


if __name__ == "__main__":
    main()
