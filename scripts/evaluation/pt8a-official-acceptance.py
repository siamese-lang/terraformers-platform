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
CORRECTIVE_PROCEDURE = "docs/evaluation/product-trust-pt-8a-corrective-recovery-procedure-v3.md"
V2_SHA256 = "d087508c99a267b36e2c7a2ce70bb69ca9b62d161a95de79ff1fa30e9d3b4823"
CORRECTIVE_MODE = "corrective-readiness"
CORRECTIVE_OPERATION = "pt8a-corrective-readiness"
QUALIFIED_MODE = "qualified-case"
QUALIFIED_OPERATION = "pt8a-qualified-case"
DIAGNOSTIC_MODE = "diagnostic-case"
DIAGNOSTIC_OPERATION = "pt8a-diagnostic-case"
DIAGNOSTIC_PURPOSE = "PT8A_B_TO_E_DIAGNOSTIC_ONLY_ONCE"
DIAGNOSTIC_CASES = CASES[1:]
V3_SHA256 = "29e1b9915448a3d8fedffd2661cd95c7d4e0d314afcd88a16d84c3dca548c268"
CASE_A_RUN, CASE_A_ARTIFACT, CASE_A_REVIEW = 37783345572, 11552729185, 6060977646
CASE_A_SOURCE = "ba8eb15a37c02184d02da9f1731c15a802477ef9"
CASE_A_DIGEST = "sha256:0ec69200a42fb34d6d345d21d14d93270419948cccf5a57c2e3e80636a07a7de"
CORRECTION_BASE = "f08a4e8440e82806383a561a6e8f0e15e1d81b25"
# Executable diagnostic contract, frozen by its canonical JSON hash in separate live authority.
# Repository implementation/CI is not permission to execute it. v2/v3 remain immutable.
DIAGNOSTIC_CONTRACT = {
    "version": "pt8a-b-e-diagnostic-v1", "classification": "DIAGNOSTIC_ONLY",
    "cases": DIAGNOSTIC_CASES, "candidateIdentity": IDENTITY,
    "v2Sha256": V2_SHA256,
    "v3Sha256": V3_SHA256, "correctionMergeSha": CORRECTION_BASE,
    "caseA": {"runId": CASE_A_RUN, "artifactId": CASE_A_ARTIFACT, "digest": CASE_A_DIGEST,
              "sourceSha": CASE_A_SOURCE, "reviewCommentId": CASE_A_REVIEW, "disposition": "REJECTED_NOT_PASS_CONSUMED"},
    "dispatchAttemptsPerCaseAcrossSources": 1, "uploadsPerCase": 1,
    "preflightFailureConsumesDispatch": True, "retryOrResubmission": False,
    "predecessorReview": "ACKNOWLEDGED_DIAGNOSTIC_ONLY_VALID_PRODUCT_OBSERVATION",
    "nonterminalCensorAmbiguousAcceptanceOrInfrastructureFailure": "BLOCK_NEXT_DIAGNOSTIC",
    "acceptancePromotion": False, "admission": "READ_ONLY_RISK_QUALIFIED_RETAINED_V4",
    "vectorContinuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN", "admissionEmbeddingRequests": 0, "indexWrites": 0,
}
# GitHub returns this unevaluated display name when the existing job's `if` is false.
# It identifies a skipped job only; it is never an active operation identity.
SKIPPED_PT8A_JOB_NAME = (
    "inputs.operation == 'pt8a-qualified-case' && format('pt8a-qualified-case/{0}', "
    "fromJSON(inputs.pt8a_request).caseId) || inputs.operation == 'pt8a-corrective-readiness' "
    "&& 'pt8a-corrective-readiness' || 'pt8a-official-acceptance'"
)
SKIPPED_DIAGNOSTIC_JOB_NAME = (
    "inputs.operation == 'pt8a-diagnostic-case' && format('pt8a-diagnostic-case/{0}', "
    "fromJSON(inputs.pt8a_request).caseId) || " + SKIPPED_PT8A_JOB_NAME
)
CHAIN_DECISION = 6058612980
RECOVERY_DECISION = 6057566013
ORIGIN_SOURCE = "70373842629fdd569815780e52dc5f0353b97880"
ORIGIN_IMAGE = "asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:6883a720b330a1ee0e2802fd7b62e377f3c014adc865bfca2c5ab127b68b35c8"
ORIGIN_CLEAN_RUN, ORIGIN_CLEAN_ARTIFACT, ORIGIN_APPROVAL = 37710171426, 11522067032, 6049866256
ORIGIN_CLEAN_DIGEST = "sha256:0e6f556d5a10df8fad65c52dcadd5a2f7db1f9bf67c9c2f2fa3ac05ccb4063ac"
ORIGIN_FAILED_RUN, ORIGIN_FAILED_ARTIFACT = 37744660097, 11534783753
ORIGIN_FAILED_DIGEST = "sha256:94ab23e3ace045d54b9b854a9a41be22447cf674b58c72ccf7ac3ba17976e21d"
ORIGIN_FAILED_JOB = "a93a7dcf-c40b-43f4-b55b-357c914a72f3"
ORIGIN_UUID = "L8KKBHT1Qri3E2VVw7As2g"
ORIGIN_CONTENT = "0bfa3e679a331f9680c9fee682f7f4d27bb2de1929a20df3b859a22036f878bb"
READINESS_FIXTURE = "evaluation/terraformers-realistic-v1/fixtures/pt1-05-private-web-fleet.png"


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


def corrective_live_fields(source, image):
    """Exact future live authority, separate from the repository-only decision and origin approval."""
    return {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
            "purpose": "PT8A_RISK_QUALIFIED_CORRECTIVE_READINESS_ONCE", "reviewed_source_sha": source,
            "backend_image": image, "candidate_identity": IDENTITY,
            "procedure_sha256": sha((ROOT / CORRECTIVE_PROCEDURE).read_bytes()),
            "frozen_v2_procedure_sha256": V2_SHA256, "decision_comment_id": str(RECOVERY_DECISION),
            "original_clean_run_id": str(ORIGIN_CLEAN_RUN), "original_clean_artifact_id": str(ORIGIN_CLEAN_ARTIFACT),
            "original_clean_artifact_digest": ORIGIN_CLEAN_DIGEST, "original_clean_source_sha": ORIGIN_SOURCE,
            "origin_clean_approval_id": str(ORIGIN_APPROVAL), "failed_readiness_run_id": str(ORIGIN_FAILED_RUN),
            "failed_readiness_artifact_id": str(ORIGIN_FAILED_ARTIFACT), "failed_readiness_artifact_digest": ORIGIN_FAILED_DIGEST,
            "failed_analysis_job_id": ORIGIN_FAILED_JOB, "failed_readiness_disposition": "FAILED_ACCEPTED_CONSUMED",
            "corpus_checksum": CORPUS_CHECKSUM, "index_uuid": ORIGIN_UUID,
            "nonvector_content_identity": ORIGIN_CONTENT, "embedding_model": "gemini-embedding-2", "vector_dimension": "1536",
            "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
            "accepted_residual_risk": "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED",
            "once_only": "ONE_DISTINCT_CORRECTIVE_READINESS_ATTEMPT_ACROSS_SOURCES",
            "official_cases": "A_TO_E_NOT_RUN", "readiness_fixture_sha256": sha((ROOT / READINESS_FIXTURE).read_bytes())}


def corrective_request_contract(request, source, image, attempt):
    allowed = {"mode", "liveApprovalCommentId", "provenanceRunId", "provenanceArtifactId"}
    if (set(request) != allowed or request.get("mode") != CORRECTIVE_MODE or str(attempt) != "1"
            or not re.fullmatch(r"[0-9a-f]{40}", source) or not re.fullmatch(
                r"asia-northeast3-docker\.pkg\.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:[0-9a-f]{64}", image)
            or any(type(request[k]) is not int or request[k] <= 0 for k in allowed - {"mode"})
            or (request["provenanceRunId"], request["provenanceArtifactId"]) != (ORIGIN_CLEAN_RUN, ORIGIN_CLEAN_ARTIFACT)):
        raise ValueError("invalid corrective request/rerun/origin; no official case or inference")
    if sha((ROOT / PROCEDURE).read_bytes()) != V2_SHA256:
        raise ValueError("frozen v2 changed; no corrective inference")
    decision = authority_comment(RECOVERY_DECISION, "[PT8A_RISK_QUALIFIED_RECOVERY_DECISION:v1]")
    if (decision.get("decision") != "APPROVED_REPOSITORY_ONLY_IMPLEMENTATION"
            or decision.get("approved_main_sha") != "9ef9d7dadbd4dcc3089c27e9fe58d6a7e2bdf447"
            or decision.get("accepted_residual_risk") != "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED"
            or decision.get("once_only") != "ONE_DISTINCT_CORRECTIVE_READINESS_ATTEMPT_ACROSS_SOURCES"):
        raise ValueError("repository risk-qualified design authority missing or changed")
    approval = authority_comment(request["liveApprovalCommentId"], "[HUMAN_GATE_APPROVAL:v1]")
    if any(approval.get(k) != v for k, v in corrective_live_fields(source, image).items()):
        raise ValueError("separate corrective live/risk authority does not bind exact origin/source/image/procedure/candidate")
    if transport.current_main() != source:
        raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT")

    return approval


def complete_dispatch_history(workflow):
    """All main dispatch sources, bounded pagination; no head_sha filter or history reset."""
    rows = []
    for page in range(1, 11):
        response = github(f"actions/workflows/{workflow}/runs?event=workflow_dispatch&branch=main&per_page=100&page={page}")
        runs = response["workflow_runs"]
        rows.extend(runs)
        if len(runs) < 100:
            if len(rows) != response["total_count"] or len({r["id"] for r in rows}) != len(rows):
                raise ValueError("corrective history incomplete or changed; no inference")
            return rows
    raise ValueError("corrective history inspection bound exhausted; no inference")


def pt8a_history_job(jobs):
    if jobs["total_count"] != len(jobs["jobs"]) or jobs["total_count"] > 100:
        raise ValueError("PT8A job history incomplete; no inference")
    names = {CORRECTIVE_OPERATION, "pt8a-official-acceptance", SKIPPED_PT8A_JOB_NAME, SKIPPED_DIAGNOSTIC_JOB_NAME}
    names.update(QUALIFIED_OPERATION + "/" + case for case in CASES)
    names.update(DIAGNOSTIC_OPERATION + "/" + case for case in DIAGNOSTIC_CASES)
    related = [j for j in jobs["jobs"] if j["name"] in names]
    if len(related) != 1:
        raise ValueError("prior dispatch operation unproven; no inference")
    job = related[0]
    if job["name"] in (SKIPPED_PT8A_JOB_NAME, SKIPPED_DIAGNOSTIC_JOB_NAME) and (
            job.get("status") != "completed" or job.get("conclusion") != "skipped"):
        raise ValueError("unevaluated PT8A job name does not prove skipped execution; no inference")
    return job


def corrective_history(source, current_run_id):
    rows = complete_dispatch_history("gcp-target-runtime-dependencies.yml")
    current = [r for r in rows if r["id"] == current_run_id]
    if (len(current) != 1 or current[0]["head_sha"] != source or current[0]["run_attempt"] != 1
            or not any(r["id"] == ORIGIN_FAILED_RUN and r["head_sha"] == ORIGIN_SOURCE for r in rows)):
        raise ValueError("current dispatch or original consumed history binding missing")
    for run in rows:
        if run["id"] == current_run_id or run["id"] <= ORIGIN_FAILED_RUN:
            continue
        job = pt8a_history_job(github(f"actions/runs/{run['id']}/jobs?per_page=100"))
        if job["conclusion"] != "skipped":
            # Any post-origin corrective dispatch consumes the capability even before upload.
            # A later ordinary observation also prevents using this as a result-driven retry.
            raise ValueError("prior corrective/PT8A dispatch across sources already exists; no retry")
    writes = complete_dispatch_history("gcp-target-corpus-ingestion.yml")
    if not any(r["id"] == ORIGIN_CLEAN_RUN and r["head_sha"] == ORIGIN_SOURCE for r in writes):
        raise ValueError("origin clean ingestion history unavailable")
    later = [r for r in writes if r["id"] > ORIGIN_CLEAN_RUN]
    if later:
        raise ValueError("known later ingestion dispatch requires independent writer-history review; no reuse")
    return {"allSourcesInspected": True, "runtimeDispatchesInspected": len(rows),
            "ingestionDispatchesInspected": len(writes), "knownLaterIngestionDispatches": 0,
            "completeAllWriterAuditAvailable": False, "vectorWriteContinuity": "UNPROVEN",
            "unobservedVectorOnlyWritesCannotBeExcluded": True}


def corrective_origins(private):
    clean, binding = bound_artifact(ORIGIN_CLEAN_RUN, ORIGIN_CLEAN_ARTIFACT,
        ["receipt.json", "clean-run-binding.json", "contract.json"], private, ".github/workflows/gcp-target-corpus-ingestion.yml")
    expected = {"runId": ORIGIN_CLEAN_RUN, "artifactId": ORIGIN_CLEAN_ARTIFACT, "digest": ORIGIN_CLEAN_DIGEST,
                "sourceSha": ORIGIN_SOURCE, "conclusion": "success"}
    if any(binding.get(k) != v for k, v in expected.items()):
        raise ValueError("original clean artifact identity mismatch; never rebind receipt")
    receipt = clean["receipt.json"]
    required = {"reviewed_source_sha": ORIGIN_SOURCE, "live_approval_comment_id": ORIGIN_APPROVAL,
        "ingestion_mode": rag.PT8A_CLEAN_MODE, "outcome": "ingested", "document_count": 5395,
        "embedded_this_run": 5395, "skipped_existing": 0, "corpus_version": "terraformers-reference-v4",
        "index_name": "terraformers-reference-v4", "embedding_model_id": "gemini-embedding-2", "vector_dimension": 1536,
        "index_uuid": ORIGIN_UUID, "index_uuid_before": ORIGIN_UUID, "index_uuid_after": ORIGIN_UUID,
        "checksum": CORPUS_CHECKSUM, "non_vector_content_identity": ORIGIN_CONTENT,
        "pre_content_identity": ORIGIN_CONTENT, "post_content_identity": ORIGIN_CONTENT,
        "provider_source": rag.PT8A_PROVIDER_SOURCE, "project_decision_source": CORPUS_SOURCE,
        "candidate_identity": IDENTITY, "procedure_sha256": V2_SHA256}
    original_approval = authority_comment(ORIGIN_APPROVAL, "[HUMAN_GATE_APPROVAL:v1]")
    approval_fields = {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
        "reviewed_source_sha": ORIGIN_SOURCE, "backend_image": ORIGIN_IMAGE, "candidate_identity": IDENTITY,
        "procedure_sha256": V2_SHA256, "purpose": "PT8A_CLEAN_V4_REEMBED_ALL_5395", "corpus_checksum": CORPUS_CHECKSUM,
        "provider_source": rag.PT8A_PROVIDER_SOURCE, "project_decision_source": CORPUS_SOURCE,
        "embedding_model": "gemini-embedding-2", "vector_dimension": "1536"}
    cb = clean["clean-run-binding.json"]
    if (any(receipt.get(k) != v for k, v in required.items())
            or any(original_approval.get(k) != v for k, v in approval_fields.items())
            or any(cb.get(k) != v for k, v in {"sourceSha": ORIGIN_SOURCE, "runId": str(ORIGIN_CLEAN_RUN),
                "runAttempt": "1", "mode": rag.PT8A_CLEAN_MODE, "status": "success", "receiptAvailable": True,
                "liveApprovalCommentId": str(ORIGIN_APPROVAL), "procedureSha256": V2_SHA256}.items())
            or any(clean["contract.json"].get(k) != v for k, v in {"sha256": CORPUS_CHECKSUM,
                "documentCount": 5395, "chunkCount": 5395, "corpusVersion": "terraformers-reference-v4"}.items())):
        raise ValueError("original clean receipt/source/approval mismatch; no relabel or reuse")
    failed, failed_binding = bound_artifact(ORIGIN_FAILED_RUN, ORIGIN_FAILED_ARTIFACT,
        ["inventory.json", "binding.json", "ledger.json", "observation/accepted.json", "observation/job.json"],
        private, ".github/workflows/gcp-target-runtime-dependencies.yml", verify_inventory=True)
    accepted, job, fb = failed["observation/accepted.json"], failed["observation/job.json"], failed["binding.json"]
    if (any(failed_binding.get(k) != v for k, v in {"runId": ORIGIN_FAILED_RUN,
            "artifactId": ORIGIN_FAILED_ARTIFACT, "digest": ORIGIN_FAILED_DIGEST,
            "sourceSha": ORIGIN_SOURCE, "conclusion": "failure"}.items())
            or any(fb.get(k) != v for k, v in {"sourceSha": ORIGIN_SOURCE, "image": ORIGIN_IMAGE,
                "candidateIdentity": IDENTITY, "procedureSha256": V2_SHA256, "mode": "readiness",
                "liveApprovalCommentId": ORIGIN_APPROVAL, "runId": ORIGIN_FAILED_RUN}.items())
            or accepted.get("jobId") != ORIGIN_FAILED_JOB or accepted.get("status") != "ACCEPTED"
            or accepted.get("consumed") is not True or accepted.get("uploadAttempts") != 1
            or type(accepted.get("projectId")) is not int or accepted["projectId"] <= 0
            or type(accepted.get("sourceFileId")) is not int or accepted["sourceFileId"] <= 0
            or job.get("id") != ORIGIN_FAILED_JOB or job.get("status") != "FAILED"
            or job.get("resultFileId") is not None or job.get("resultObjectKey") is not None
            or job.get("quality", {}).get("reasons") != ["PROVIDER_OUTPUT_TRUNCATED"]
            or job.get("quality", {}).get("technicalStatus") != "FAIL"
            or job.get("projectId") != accepted.get("projectId") or job.get("sourceFileId") != accepted.get("sourceFileId")
            or failed["ledger.json"] != [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in CASES]):
        raise ValueError("original FAILED/ACCEPTED/consumed evidence missing or altered")
    return receipt, binding, failed_binding


def risk_qualified_admission(client, manifest, schema, documents, checksum, receipt, binding, authority):
    if (not authority or authority.get("purpose") not in ("PT8A_RISK_QUALIFIED_CORRECTIVE_READINESS_ONCE", DIAGNOSTIC_PURPOSE)
            or authority.get("vector_write_continuity") != "VECTOR_WRITE_CONTINUITY_UNPROVEN"
            or authority.get("accepted_residual_risk") != "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED"):
        raise ValueError("explicit corrective live risk authority missing")
    if (checksum != CORPUS_CHECKSUM or receipt.get("reviewed_source_sha") != ORIGIN_SOURCE
            or binding.get("sourceSha") != ORIGIN_SOURCE or binding.get("digest") != ORIGIN_CLEAN_DIGEST):
        raise ValueError("risk-qualified origin/checksum mismatch")
    snapshot = rag.verify_exact_v4(client, manifest, schema, documents, checksum,
        {**binding, "githubArtifactBindingVerified": True, "receipt": receipt}, inspect_vectors=True)
    if (snapshot["classification"] != "EXACT_REUSABLE_COMPLETED_V4" or snapshot.get("indexUuid") != ORIGIN_UUID
            or snapshot.get("liveContentIdentity") != ORIGIN_CONTENT or snapshot.get("vectorDocumentsChecked") != 5395):
        raise ValueError("risk-qualified admission failed: classification=" + snapshot["classification"]
            + " reason=" + snapshot.get("reason", "unavailable")
            + " uuidMatchesOrigin=" + str(snapshot.get("indexUuid") == ORIGIN_UUID)
            + " contentMatchesOrigin=" + str(snapshot.get("liveContentIdentity") == ORIGIN_CONTENT)
            + " vectorsChecked=" + str(snapshot.get("vectorDocumentsChecked")))
    return {**snapshot, "classification": "RISK_QUALIFIED_RETAINED_V4",
            "reason": "exact_read_only_content_shape_and_authenticated_origin_with_USER_accepted_residual_risk",
            "vectorWriteContinuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN", "cryptographicVectorContinuityProven": False,
            "originSourceSha": ORIGIN_SOURCE, "originApprovalCommentId": ORIGIN_APPROVAL,
            "originArtifactDigest": ORIGIN_CLEAN_DIGEST, "indexWrites": 0}


def corrective_release(release, source, image):
    expected_runtime = (source + "|terraformers-reference-v4|terraformers-reference-v4|gemini-embedding-2|1536|1536"
        "|https://identity.example.test/case-c|http://terraformers-jwks:8080/jwks.json|vertex|vertex|REQUIRED"
        "|gemini-3.8-flash|case-c-runtime-client|gcs|gcs|http://terraformers-opensearch:9200|5.100.0")
    if any(release.get(k) != v for k, v in {"sourceSha": source, "image": image,
            "sourceTagDigest": image.split("@", 1)[1], "readyReplicas": 1,
            "deployedRuntimeIdentity": expected_runtime, "noRuntimeConfigurationMutation": True}.items()):
        raise ValueError("corrective deployed source/image/model/index/runtime identity mismatch")


def qualified_live_fields(request, source, image, readiness_digest):
    """One separately approved A-E campaign; its readiness authority cannot authorize cases."""
    return {**corrective_live_fields(source, image),
        "purpose": "PT8A_V3_QUALIFIED_OFFICIAL_A_TO_E_SEQUENTIAL_CAMPAIGN",
        "case_chain_decision_id": str(CHAIN_DECISION), "official_cases": "A_TO_E_SEQUENTIAL_INDEPENDENT_REVIEW_EACH",
        "once_only": "ONE_DISPATCH_ATTEMPT_UPLOAD_PER_CASE_ACROSS_SOURCES_NO_RETRY",
        "corrective_readiness_run_id": str(request["readinessRunId"]),
        "corrective_readiness_artifact_id": str(request["readinessArtifactId"]),
        "corrective_readiness_artifact_digest": readiness_digest,
        "corrective_readiness_review_id": str(request["readinessReviewCommentId"]),
        "corrective_readiness_live_approval_id": str(request["readinessLiveApprovalCommentId"])}


def qualified_request_contract(request, source, image, attempt):
    allowed = {"mode", "caseId", "liveApprovalCommentId", "provenanceRunId", "provenanceArtifactId",
        "readinessRunId", "readinessArtifactId", "readinessReviewCommentId", "readinessLiveApprovalCommentId",
        "priorRunId", "priorArtifactId", "priorReviewCommentId"}
    if (set(request) != allowed or request.get("mode") != QUALIFIED_MODE or request.get("caseId") not in CASES
            or any(type(request[k]) is not int or request[k] <= 0 for k in allowed - {"mode", "caseId"})):
        raise ValueError("invalid qualified case request; no case or inference")
    decision = authority_comment(CHAIN_DECISION, "[PT8A_V3_QUALIFIED_OFFICIAL_CASE_CHAIN_DECISION:v1]")
    expected = {"decision": "APPROVED_REPOSITORY_ONLY_IMPLEMENTATION",
        "reviewed_execution_base_sha": "c9caeafd900b8310eae8ead467b20f8e358599db", "candidate_identity": IDENTITY,
        "frozen_v2_sha256": V2_SHA256, "strict_v2_mode": "UNCHANGED", "case_dispatch_budget": "ONE_EACH_NO_RETRY",
        "independent_review_each_stage": "REQUIRED", "approved_action_scope": "REPOSITORY_ONLY"}
    if any(decision.get(k) != v for k, v in expected.items()):
        raise ValueError("repository qualified-case design authority missing or changed")
    readiness_authority = corrective_request_contract({"mode": CORRECTIVE_MODE,
        "liveApprovalCommentId": request["readinessLiveApprovalCommentId"],
        "provenanceRunId": request["provenanceRunId"], "provenanceArtifactId": request["provenanceArtifactId"]},
        source, image, attempt)
    campaign = authority_comment(request["liveApprovalCommentId"], "[HUMAN_GATE_APPROVAL:v1]")
    digest = campaign.get("corrective_readiness_artifact_digest", "")
    if (not re.fullmatch(r"sha256:[0-9a-f]{64}", digest)
            or any(campaign.get(k) != v for k, v in qualified_live_fields(request, source, image, digest).items())):
        raise ValueError("separate A-E campaign live/risk authority does not bind qualified readiness/source/image/procedure")
    return campaign, readiness_authority


def qualified_history(request, source, current_run_id):
    """The immutable consumed failure/all-NOT_RUN ledger anchors the global case capability."""
    rows = complete_dispatch_history("gcp-target-runtime-dependencies.yml")
    current = [r for r in rows if r["id"] == current_run_id]
    if (len(current) != 1 or current[0]["head_sha"] != source or current[0]["run_attempt"] != 1
            or not any(r["id"] == ORIGIN_FAILED_RUN and r["head_sha"] == ORIGIN_SOURCE for r in rows)):
        raise ValueError("qualified current/origin history binding missing")
    index = CASES.index(request["caseId"])
    predecessors = {}
    for run in rows:
        if run["id"] < ORIGIN_FAILED_RUN or run["id"] == ORIGIN_FAILED_RUN:
            continue
        job = pt8a_history_job(github(f"actions/runs/{run['id']}/jobs?per_page=100"))
        if run["id"] == current_run_id:
            if job["name"] != QUALIFIED_OPERATION + "/" + request["caseId"] or job["conclusion"] == "skipped":
                raise ValueError("current qualified dispatch case identity mismatch")
            continue
        if job["conclusion"] == "skipped":
            continue
        name = job["name"]
        case = "readiness-only" if name == CORRECTIVE_OPERATION else name.removeprefix(QUALIFIED_OPERATION + "/")
        if case != "readiness-only" and (case not in CASES or CASES.index(case) >= index):
            raise ValueError("case already dispatched/unknown/out of order across sources; no retry")
        if (case in predecessors or run["status"] != "completed" or run["conclusion"] != "success"
                or run["run_attempt"] != 1 or run["head_sha"] != source or job["conclusion"] != "success"):
            raise ValueError("qualified predecessor failed/duplicate/incomplete/rerun/source mismatch")
        artifacts = github(f"actions/runs/{run['id']}/artifacts?per_page=100")
        matches = [a for a in artifacts["artifacts"] if a["name"] == f"pt8a-official-{run['id']}"]
        if artifacts["total_count"] != len(artifacts["artifacts"]) or artifacts["total_count"] > 100 or len(matches) != 1:
            raise ValueError("qualified predecessor artifact history incomplete")
        predecessors[case] = {"runId": run["id"], "artifactId": matches[0]["id"], "digest": matches[0]["digest"]}
    expected = ["readiness-only", *CASES[:index]]
    ordered = [predecessors[c] for c in expected if c in predecessors]
    if (set(predecessors) != set(expected) or any(a["runId"] >= b["runId"] for a, b in zip(ordered, ordered[1:]))
            or ordered[-1]["runId"] >= current_run_id
            or (ordered[0]["runId"], ordered[0]["artifactId"]) != (request["readinessRunId"], request["readinessArtifactId"])
            or (ordered[-1]["runId"], ordered[-1]["artifactId"]) != (request["priorRunId"], request["priorArtifactId"])):
        raise ValueError("missing/stale/nonsequential qualified predecessor history")
    writes = complete_dispatch_history("gcp-target-corpus-ingestion.yml")
    if (not any(r["id"] == ORIGIN_CLEAN_RUN and r["head_sha"] == ORIGIN_SOURCE for r in writes)
            or any(r["id"] > ORIGIN_CLEAN_RUN for r in writes)):
        raise ValueError("known ingestion history missing or later writer requires review")
    return {"allSourcesInspected": True, "predecessors": predecessors, "knownLaterIngestionDispatches": 0,
        "completeAllWriterAuditAvailable": False, "vectorWriteContinuity": "UNPROVEN",
        "unobservedVectorOnlyWritesCannotBeExcluded": True}


def qualified_predecessor(request, source, image, campaign, history, private):
    """Authenticate the complete bounded predecessor chain, reusing v2 review/ledger primitives."""
    index = CASES.index(request["caseId"])
    reference = {"runId": request["priorRunId"], "artifactId": request["priorArtifactId"],
                 "reviewCommentId": request["priorReviewCommentId"]}
    successor_ledger = None
    for case in reversed(["readiness-only", *CASES[:index]]):
        expected_ref = history["predecessors"][case]
        if any(reference.get(k) != expected_ref[k] for k in ("runId", "artifactId")):
            raise ValueError("qualified predecessor link disagrees with all-source history")
        readiness = case == "readiness-only"
        names = ["inventory.json", "binding.json", "ledger.json", "readiness.json", "post-admission.json",
            "origin-bindings.json", "release.json", "observation/accepted.json", "observation/job.json",
            "observation/cli.json", "observation/retrieval.json", "observation/presentation.json", "observation/draft-identity.json"]
        names += ["readiness-job.json"] if readiness else ["qualified-chain.json", "input-identity.json"]
        evidence, bound = bound_artifact(reference["runId"], reference["artifactId"], names, private,
            ".github/workflows/gcp-target-runtime-dependencies.yml", verify_inventory=True)
        if (bound != {**expected_ref, "sourceSha": source, "conclusion": "success"}
                or any(evidence["binding.json"].get(k) != v for k, v in {"sourceSha": source, "image": image,
                    "candidateIdentity": IDENTITY, "procedureSha256": sha((ROOT / CORRECTIVE_PROCEDURE).read_bytes()),
                    "runId": reference["runId"], "mode": CORRECTIVE_MODE if readiness else QUALIFIED_MODE,
                    "caseId": None if readiness else case,
                    "liveApprovalCommentId": request["readinessLiveApprovalCommentId"] if readiness else request["liveApprovalCommentId"]}.items())):
            raise ValueError("qualified predecessor artifact/source/image/procedure/campaign mismatch")
        if readiness and (reference != {"runId": request["readinessRunId"], "artifactId": request["readinessArtifactId"],
                "reviewCommentId": request["readinessReviewCommentId"]}
                or bound["digest"] != campaign["corrective_readiness_artifact_digest"]):
            raise ValueError("qualified campaign readiness digest/review mismatch")
        if any(e.get("path") == "error.json" for e in evidence["inventory.json"]["files"]):
            raise ValueError("qualified predecessor has preserved failure evidence")
        corrective_release(evidence["release.json"], source, image)
        for snapshot in (evidence["readiness.json"], evidence["post-admission.json"]):
            required = {"classification": "RISK_QUALIFIED_RETAINED_V4", "indexUuid": ORIGIN_UUID,
                "liveContentIdentity": ORIGIN_CONTENT, "vectorDocumentsChecked": 5395, "vectorDimension": 1536,
                "originSourceSha": ORIGIN_SOURCE, "originApprovalCommentId": ORIGIN_APPROVAL,
                "originArtifactDigest": ORIGIN_CLEAN_DIGEST, "vectorWriteContinuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
                "cryptographicVectorContinuityProven": False, "embeddingRequests": 0, "indexWrites": 0}
            if any(snapshot.get(k) != v for k, v in required.items()):
                raise ValueError("qualified predecessor admission risk/content/UUID/vector evidence mismatch")
        if evidence["readiness.json"].get("correlatedBackendRetrieval") is not True:
            raise ValueError("qualified predecessor correlated backend retrieval missing")
        origins = evidence["origin-bindings.json"]
        for key, values in {"clean": {"runId": ORIGIN_CLEAN_RUN, "artifactId": ORIGIN_CLEAN_ARTIFACT,
                "digest": ORIGIN_CLEAN_DIGEST, "sourceSha": ORIGIN_SOURCE, "conclusion": "success"},
                "failed": {"runId": ORIGIN_FAILED_RUN, "artifactId": ORIGIN_FAILED_ARTIFACT,
                "digest": ORIGIN_FAILED_DIGEST, "sourceSha": ORIGIN_SOURCE, "conclusion": "failure"}}.items():
            if origins.get(key) != values:
                raise ValueError("qualified predecessor relabeled/missing immutable origins")
        accepted, job, presentation = (evidence[n] for n in
            ("observation/accepted.json", "observation/job.json", "observation/presentation.json"))
        quality = job.get("quality") or {}
        cli, retrieval = evidence["observation/cli.json"], evidence["observation/retrieval.json"]
        if (accepted.get("status") != "ACCEPTED" or accepted.get("consumed") is not True or accepted.get("uploadAttempts") != 1
                or not accepted.get("jobId") or accepted["jobId"] != job.get("id") or job.get("status") != "SUCCEEDED"
                or job.get("projectId") != accepted.get("projectId") or job.get("sourceFileId") != accepted.get("sourceFileId")
                or not job.get("resultObjectKey") or not job.get("resultFileId")
                or any(quality.get(k) != v for k, v in {"contractVersion": "evidence-quality-v1", "technicalStatus": "PASS", "knowledgeStatus": "COMPLETE"}.items())
                or any(presentation.get(k) != v for k, v in {"projectId": job.get("projectId"), "analysisStatus": "SUCCEEDED",
                    "latestAnalysisJobId": job["id"], "quality": quality, "latestResultObjectKey": job.get("resultObjectKey")}.items())
                or any(cli.get(k) != v for k, v in {"terraformVersion": "1.8.5", "providerVersion": "5.100.0", "initValidateExitCode": 0, "AWSPlanApply": False}.items())
                or retrieval.get("jobId") != job["id"] or not isinstance(retrieval.get("officialHitCount"), int) or retrieval["officialHitCount"] <= 0):
            raise ValueError("qualified predecessor accepted/terminal/CLI/retrieval/persisted trust evidence invalid")
        hcl = evidence["observation/draft-identity.json"]
        hcl_entries = [e for e in evidence["inventory.json"]["files"] if e.get("path") == "observation/main.tf"]
        if (len(hcl_entries) != 1 or hcl_entries[0]["sha256"] != hcl.get("hclSha256")
                or cli.get("hclSha256") != hcl.get("hclSha256") or hcl.get("resultObjectKey") != job.get("resultObjectKey")):
            raise ValueError("qualified predecessor draft/CLI/inventory identity mismatch")
        previous = evidence["ledger.json"]
        if tuple(r["caseId"] for r in previous) != CASES:
            raise ValueError("qualified predecessor case ledger order mismatch")
        if readiness:
            record = evidence["readiness-job.json"]
            if previous != [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in CASES]:
                raise ValueError("qualified readiness official ledger not all NOT_RUN")
        else:
            position = CASES.index(case)
            if [r["status"] for r in previous] != ["PASS"] * position + ["REVIEW_PENDING"] + ["NOT_RUN"] * (4 - position):
                raise ValueError("qualified case ledger failed/unreviewed/nonsequential")
            if evidence["input-identity.json"] != next(c for c in frozen_inputs() if c["caseId"] == case):
                raise ValueError("qualified predecessor frozen input identity mismatch")
            record = previous[position]
        if (record.get("status") != "REVIEW_PENDING" or record.get("terminalState") != "SUCCEEDED"
                or record.get("jobId") != job["id"] or record.get("consumed") is not True or record.get("uploadAttempts") != 1
                or "censoredObservationMs" in record or "observation" in record):
            raise ValueError("qualified predecessor failed/censored/ambiguous/unreviewed observation")
        review = authority_comment(reference["reviewCommentId"], "[PRODUCT_TRUST_REVIEW:v1]")
        reviewed_prior(review, evidence["binding.json"], reference["runId"], reference["artifactId"], bound["digest"], case)
        if (review.get("decision") != "ACCEPTED" or review.get("material_defect") != "false" or review.get("false_trusted_success") != "0"
                or any(review.get(k) != v for k, v in {"admission_class": "RISK_QUALIFIED_RETAINED_V4",
                    "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
                    "accepted_residual_risk": "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED",
                    "origin_clean_artifact_digest": ORIGIN_CLEAN_DIGEST, "origin_clean_source_sha": ORIGIN_SOURCE,
                    "failed_readiness_artifact_digest": ORIGIN_FAILED_DIGEST, "original_failure_consumed": "true"}.items())):
            raise ValueError("qualified predecessor independent PASS/risk review missing")
        if successor_ledger is None:
            successor_ledger = copy_ledger = json.loads(json.dumps(previous))
            if not readiness:
                copy_ledger[CASES.index(case)]["status"] = "PASS"
            next_case(copy_ledger, request["caseId"], review)
        elif not readiness:
            original = json.loads(json.dumps(previous[:CASES.index(case) + 1]))
            original[-1]["status"] = "PASS"
            if successor_ledger[:len(original)] != original:
                raise ValueError("qualified ledger rewrote an earlier individual observation")
        if not readiness:
            chain = evidence["qualified-chain.json"]
            if any(chain.get(k) != v for k, v in {"campaignApprovalCommentId": request["liveApprovalCommentId"],
                    "readinessRunId": request["readinessRunId"], "readinessArtifactId": request["readinessArtifactId"],
                    "readinessReviewCommentId": request["readinessReviewCommentId"]}.items()):
                raise ValueError("qualified campaign/readiness chain link mismatch")
            reference = chain["prior"]
    return successor_ledger


def diagnostic_contract_sha256():
    return sha(json.dumps(DIAGNOSTIC_CONTRACT, sort_keys=True, separators=(",", ":")).encode())


def diagnostic_live_fields(source, image):
    """A new exact live/cost authority; old readiness/A-E approvals cannot authorize diagnostics."""
    return {"gate": "FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE", "decision": "APPROVED",
        "purpose": DIAGNOSTIC_PURPOSE, "reviewed_source_sha": source, "backend_image": image,
        "candidate_identity": IDENTITY, "diagnostic_contract_sha256": diagnostic_contract_sha256(),
        "frozen_v2_procedure_sha256": V2_SHA256, "frozen_v3_procedure_sha256": V3_SHA256,
        "product_correction_merge_sha": CORRECTION_BASE, "official_cases": "B_TO_E_DIAGNOSTIC_ONLY_SEQUENTIAL",
        "once_only": "ONE_DISPATCH_ATTEMPT_UPLOAD_PER_CASE_ACROSS_SOURCES_NO_RETRY",
        "original_case_a_run_id": str(CASE_A_RUN), "original_case_a_artifact_id": str(CASE_A_ARTIFACT),
        "original_case_a_artifact_digest": CASE_A_DIGEST, "original_case_a_review_id": str(CASE_A_REVIEW),
        "original_case_a_source_sha": CASE_A_SOURCE, "original_case_a_disposition": "REJECTED_NOT_PASS_CONSUMED",
        "original_clean_run_id": str(ORIGIN_CLEAN_RUN), "original_clean_artifact_id": str(ORIGIN_CLEAN_ARTIFACT),
        "original_clean_artifact_digest": ORIGIN_CLEAN_DIGEST, "original_clean_source_sha": ORIGIN_SOURCE,
        "origin_clean_approval_id": str(ORIGIN_APPROVAL), "corpus_checksum": CORPUS_CHECKSUM,
        "index_uuid": ORIGIN_UUID, "nonvector_content_identity": ORIGIN_CONTENT,
        "embedding_model": "gemini-embedding-2", "vector_dimension": "1536",
        "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN",
        "accepted_residual_risk": "INTERVENING_VECTOR_ONLY_WRITES_CANNOT_BE_EXCLUDED"}


def diagnostic_request_contract(request, source, image, attempt):
    allowed = {"mode", "caseId", "liveApprovalCommentId", "provenanceRunId", "provenanceArtifactId",
               "priorRunId", "priorArtifactId", "priorReviewCommentId"}
    if (set(request) != allowed or request.get("mode") != DIAGNOSTIC_MODE or request.get("caseId") not in DIAGNOSTIC_CASES
            or str(attempt) != "1" or not re.fullmatch(r"[0-9a-f]{40}", source)
            or not re.fullmatch(r"asia-northeast3-docker\.pkg\.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:[0-9a-f]{64}", image)
            or any(type(request[k]) is not int or request[k] <= 0 for k in allowed - {"mode", "caseId"})
            or (request["provenanceRunId"], request["provenanceArtifactId"]) != (ORIGIN_CLEAN_RUN, ORIGIN_CLEAN_ARTIFACT)
            or sha((ROOT / PROCEDURE).read_bytes()) != V2_SHA256
            or sha((ROOT / CORRECTIVE_PROCEDURE).read_bytes()) != V3_SHA256):
        raise ValueError("invalid diagnostic request/source/image/frozen procedure; no inference")
    authority = authority_comment(request["liveApprovalCommentId"], "[HUMAN_GATE_APPROVAL:v1]")
    if any(authority.get(k) != v for k, v in diagnostic_live_fields(source, image).items()):
        raise ValueError("new B-E diagnostic live/risk authority missing or mismatched")
    if github(f"compare/{CORRECTION_BASE}...{source}").get("status") not in ("ahead", "identical"):
        raise ValueError("diagnostic source does not contain merged product correction")
    if transport.current_main() != source:
        raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT")
    return authority


def diagnostic_origin(private):
    evidence, bound = bound_artifact(CASE_A_RUN, CASE_A_ARTIFACT,
        ["inventory.json", "binding.json", "ledger.json", "observation/accepted.json", "observation/job.json"],
        private, ".github/workflows/gcp-target-runtime-dependencies.yml", verify_inventory=True)
    binding = evidence["binding.json"]
    review = authority_comment(CASE_A_REVIEW, "[PRODUCT_TRUST_REVIEW:v1]")
    expected = {"decision": "REJECTED", "reviewed_source_sha": CASE_A_SOURCE, "candidate_identity": IDENTITY,
                "procedure_sha256": V3_SHA256, "case_id": CASES[0], "run_id": str(CASE_A_RUN),
                "artifact_id": str(CASE_A_ARTIFACT), "artifact_digest": CASE_A_DIGEST}
    rows, accepted, job = evidence["ledger.json"], evidence["observation/accepted.json"], evidence["observation/job.json"]
    if (bound != {"runId": CASE_A_RUN, "artifactId": CASE_A_ARTIFACT, "digest": CASE_A_DIGEST,
            "sourceSha": CASE_A_SOURCE, "conclusion": "failure"}
            or any(review.get(k) != v for k, v in expected.items())
            or any(binding.get(k) != v for k, v in {"sourceSha": CASE_A_SOURCE, "runId": CASE_A_RUN,
                "candidateIdentity": IDENTITY, "procedureSha256": V3_SHA256, "mode": QUALIFIED_MODE, "caseId": CASES[0]}.items())
            or tuple(r["caseId"] for r in rows) != CASES or rows[0].get("status") != "NOT_PASS"
            or rows[0].get("consumed") is not True or rows[0].get("uploadAttempts") != 1
            or accepted.get("status") != "ACCEPTED" or accepted.get("consumed") is not True
            or accepted.get("uploadAttempts") != 1 or accepted.get("jobId") != rows[0].get("jobId")
            or job.get("id") != accepted.get("jobId") or job.get("status") != rows[0].get("terminalState")
            or rows[1:] != [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in DIAGNOSTIC_CASES]):
        raise ValueError("original rejected/consumed Case A or B-E NOT_RUN evidence changed")
    return rows, bound


def diagnostic_history(request, source, current_run_id):
    rows = complete_dispatch_history("gcp-target-runtime-dependencies.yml")
    current = [r for r in rows if r["id"] == current_run_id]
    if (len(current) != 1 or current[0]["head_sha"] != source or current[0]["run_attempt"] != 1
            or not any(r["id"] == CASE_A_RUN and r["head_sha"] == CASE_A_SOURCE for r in rows)):
        raise ValueError("diagnostic current/original Case A history missing")
    index, predecessors = DIAGNOSTIC_CASES.index(request["caseId"]), {}
    for run in rows:
        if run["id"] <= ORIGIN_FAILED_RUN:
            continue
        job = pt8a_history_job(github(f"actions/runs/{run['id']}/jobs?per_page=100"))
        if run["id"] == current_run_id:
            if job["name"] != DIAGNOSTIC_OPERATION + "/" + request["caseId"] or job["conclusion"] == "skipped":
                raise ValueError("current diagnostic operation/case mismatch")
            continue
        if job["conclusion"] == "skipped":
            if job["name"].startswith((DIAGNOSTIC_OPERATION + "/", QUALIFIED_OPERATION + "/")):
                raise ValueError("selected PT8A dispatch was skipped; no retry or diagnostic bypass")
            continue
        if job["name"] == QUALIFIED_OPERATION + "/" + CASES[0] and run["id"] == CASE_A_RUN:
            continue
        if job["name"] == CORRECTIVE_OPERATION and run["id"] < CASE_A_RUN:
            continue
        case = job["name"].removeprefix(DIAGNOSTIC_OPERATION + "/")
        if case not in DIAGNOSTIC_CASES or DIAGNOSTIC_CASES.index(case) >= index:
            raise ValueError("diagnostic duplicate/out-of-order/other PT8A attempt across sources; no retry")
        if (case in predecessors or run["status"] != "completed" or run["run_attempt"] != 1
                or run["conclusion"] not in ("success", "failure") or job["conclusion"] != run["conclusion"]):
            raise ValueError("diagnostic predecessor incomplete/ambiguous/rerun")
        artifacts = github(f"actions/runs/{run['id']}/artifacts?per_page=100")
        matches = [a for a in artifacts["artifacts"] if a["name"] == f"pt8a-diagnostic-{run['id']}"]
        if artifacts["total_count"] != len(artifacts["artifacts"]) or artifacts["total_count"] > 100 or len(matches) != 1:
            raise ValueError("diagnostic artifact history incomplete")
        predecessors[case] = {"runId": run["id"], "artifactId": matches[0]["id"], "digest": matches[0]["digest"],
                              "sourceSha": run["head_sha"], "conclusion": run["conclusion"]}
    expected = DIAGNOSTIC_CASES[:index]
    ordered = [predecessors[c]["runId"] for c in expected if c in predecessors]
    prior = predecessors[expected[-1]] if expected else {"runId": CASE_A_RUN, "artifactId": CASE_A_ARTIFACT}
    if (set(predecessors) != set(expected) or ordered != sorted(ordered) or any(i <= CASE_A_RUN or i >= current_run_id for i in ordered)
            or (request["priorRunId"], request["priorArtifactId"]) != (prior["runId"], prior["artifactId"])):
        raise ValueError("missing/stale/nonsequential diagnostic predecessor")
    writes = complete_dispatch_history("gcp-target-corpus-ingestion.yml")
    if (not any(r["id"] == ORIGIN_CLEAN_RUN and r["head_sha"] == ORIGIN_SOURCE for r in writes)
            or any(r["id"] > ORIGIN_CLEAN_RUN for r in writes)):
        raise ValueError("known ingestion history missing/later write; no diagnostic reuse")
    return {"allSourcesInspected": True, "predecessors": predecessors, "knownLaterIngestionDispatches": 0,
            "completeAllWriterAuditAvailable": False, "vectorWriteContinuity": "UNPROVEN"}


def diagnostic_predecessor(request, history, private):
    origin, _ = diagnostic_origin(private)
    reference = {"runId": request["priorRunId"], "artifactId": request["priorArtifactId"],
                 "reviewCommentId": request["priorReviewCommentId"]}
    ledger = None
    for case in reversed(DIAGNOSTIC_CASES[:DIAGNOSTIC_CASES.index(request["caseId"])]):
        expected = history["predecessors"][case]
        if any(reference.get(k) != expected[k] for k in ("runId", "artifactId")):
            raise ValueError("diagnostic predecessor link/history mismatch")
        data, bound = bound_artifact(reference["runId"], reference["artifactId"],
            ["inventory.json", "binding.json", "ledger.json", "diagnostic-chain.json", "diagnostic-disposition.json",
             "release.json", "readiness.json", "post-admission.json", "input-identity.json",
             "observation/accepted.json", "observation/job.json", "observation/presentation.json",
             "observation/retrieval.json", "observation/cli.json", "observation/draft-identity.json"],
            private, ".github/workflows/gcp-target-runtime-dependencies.yml", verify_inventory=True)
        b = data["binding.json"]
        if (bound != expected or any(b.get(k) != v for k, v in {"sourceSha": bound["sourceSha"], "runId": bound["runId"],
                "mode": DIAGNOSTIC_MODE, "caseId": case, "candidateIdentity": IDENTITY,
                "procedureSha256": diagnostic_contract_sha256(),
                "diagnosticContractSha256": diagnostic_contract_sha256(), "classification": "DIAGNOSTIC_ONLY"}.items())):
            raise ValueError("diagnostic predecessor binding mismatch")
        approval = authority_comment(b["liveApprovalCommentId"], "[HUMAN_GATE_APPROVAL:v1]")
        if any(approval.get(k) != v for k, v in diagnostic_live_fields(bound["sourceSha"], b["image"]).items()):
            raise ValueError("diagnostic predecessor original live authority mismatch")
        corrective_release(data["release.json"], bound["sourceSha"], b["image"])
        for snapshot in (data["readiness.json"], data["post-admission.json"]):
            required = {"classification": "RISK_QUALIFIED_RETAINED_V4", "indexUuid": ORIGIN_UUID,
                "liveContentIdentity": ORIGIN_CONTENT, "vectorDocumentsChecked": 5395, "vectorDimension": 1536,
                "originSourceSha": ORIGIN_SOURCE, "originArtifactDigest": ORIGIN_CLEAN_DIGEST,
                "vectorWriteContinuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN", "cryptographicVectorContinuityProven": False,
                "embeddingRequests": 0, "indexWrites": 0}
            if any(snapshot.get(k) != v for k, v in required.items()):
                raise ValueError("diagnostic predecessor read-only admission mismatch")
        rows = data["ledger.json"]
        position = CASES.index(case)
        record, accepted, job = rows[position], data["observation/accepted.json"], data["observation/job.json"]
        if (tuple(r["caseId"] for r in rows) != CASES or rows[0] != origin[0]
                or rows[position+1:] != origin[position+1:] or record.get("status") not in ("NOT_PASS", "REVIEW_PENDING")
                or record.get("consumed") is not True or record.get("uploadAttempts") != 1
                or record.get("terminalState") not in ("FAILED", "SUCCEEDED") or "censoredObservationMs" in record
                or accepted.get("status") != "ACCEPTED" or accepted.get("consumed") is not True or accepted.get("uploadAttempts") != 1
                or record.get("jobId") != accepted.get("jobId") or job.get("id") != accepted.get("jobId")
                or job.get("projectId") != accepted.get("projectId") or job.get("sourceFileId") != accepted.get("sourceFileId")
                or job.get("status") != record.get("terminalState")
                or data["input-identity.json"] != next(c for c in frozen_inputs() if c["caseId"] == case)
                or any(data["diagnostic-disposition.json"].get(k) != v for k, v in {
                    "classification": "DIAGNOSTIC_ONLY", "officialAcceptance": "NOT_ACCEPTANCE", "dispatchConsumed": True,
                    "observedStatus": record["status"], "evidenceValidity": "COMPLETE_AWAITING_INDEPENDENT_CLASSIFICATION"}.items())):
            raise ValueError("diagnostic predecessor incomplete/ambiguous or rewritten observation")
        presentation, retrieved = data["observation/presentation.json"], data["observation/retrieval.json"]
        if (presentation.get("projectId") != accepted.get("projectId")
                or presentation.get("latestAnalysisJobId") != job["id"] or retrieved.get("jobId") != job["id"]):
            raise ValueError("diagnostic owner/result/retrieval identity mismatch")
        cli, draft = data["observation/cli.json"], data["observation/draft-identity.json"]
        hcl = [e for e in data["inventory.json"]["files"] if e["path"] == "observation/main.tf"]
        if draft.get("hclPresent") is True:
            if (len(hcl) != 1 or cli.get("hclSha256") != hcl[0]["sha256"]
                    or draft.get("hclSha256") != hcl[0]["sha256"]
                    or cli.get("terraformVersion") != "1.8.5" or cli.get("providerVersion") != "5.100.0"
                    or type(cli.get("initValidateExitCode")) is not int or cli.get("AWSPlanApply") is not False):
                raise ValueError("diagnostic original CLI/draft identity missing")
        elif (draft.get("hclPresent") is not False or hcl or cli != {"status": "NOT_RUN",
                "reason": "BACKEND_FAILED" if job["status"] == "FAILED" and not job.get("resultObjectKey") else "NO_REVIEWABLE_DRAFT",
                "AWSPlanApply": False}):
            raise ValueError("diagnostic missing draft cannot fabricate CLI validity")
        review = authority_comment(reference["reviewCommentId"], "[PT8A_DIAGNOSTIC_REVIEW:v1]")
        required = {"decision": "ACKNOWLEDGED_DIAGNOSTIC_ONLY", "observation_class": "PRODUCT_OBSERVATION",
            "infrastructure_auth_provenance": "VERIFIED", "official_acceptance": "NOT_ACCEPTANCE", "allow_next_diagnostic": "true",
            "reviewed_source_sha": bound["sourceSha"], "backend_image": b["image"], "candidate_identity": IDENTITY,
            "diagnostic_contract_sha256": diagnostic_contract_sha256(), "run_id": str(bound["runId"]),
            "artifact_id": str(bound["artifactId"]), "artifact_digest": bound["digest"], "case_id": case,
            "observed_status": record["status"], "vector_write_continuity": "VECTOR_WRITE_CONTINUITY_UNPROVEN"}
        if (any(review.get(k) != v for k, v in required.items())
                or any(review.get("dimension_" + d) not in ("PASS", "FAIL", "PARTIAL", "UNKNOWN", "NOT_OBSERVED", "NOT_APPLICABLE") for d in SCORING)):
            raise ValueError("authenticated diagnostic classification/review missing; no next case")
        if ledger is None:
            ledger = rows
        elif ledger[:position+1] != rows[:position+1]:
            raise ValueError("diagnostic ledger rewrote a previous FAIL/UNKNOWN observation")
        reference = data["diagnostic-chain.json"]["prior"]
    if reference != {"runId": CASE_A_RUN, "artifactId": CASE_A_ARTIFACT, "reviewCommentId": CASE_A_REVIEW}:
        raise ValueError("diagnostic chain does not end at original rejected Case A")
    return json.loads(json.dumps(ledger if ledger is not None else origin))


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


def bound_artifact(run_id, artifact_id, names, directory, workflow, *, verify_inventory=False):
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
        if verify_inventory:
            entries = result["inventory.json"]["files"]
            paths = [e["path"] for e in entries]
            if (len(paths) > 64 or len(set(paths)) != len(paths)
                    or set(zipped.namelist()) != set(paths) | {"inventory.json"}):
                raise ValueError("original artifact inventory/archive membership mismatch")
            for entry in entries:
                name = entry["path"]
                if Path(name).is_absolute() or ".." in Path(name).parts or zipped.getinfo(name).file_size > 2_000_000:
                    raise ValueError("bounded artifact inventory member invalid")
                data = zipped.read(name)
                if entry.get("sha256") != sha(data) or entry.get("sizeBytes") != len(data):
                    raise ValueError("original artifact inventory/member hash mismatch")
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
    events, stages, provider_calls, grounding_stages = [], [], [], []
    expected = {doc["documentId"]: doc for doc in expected_documents}
    pattern = r"reference retrieval outcome=success mode=REQUIRED embeddingProvider=(?:VERTEX|vertex) index=(\S+).*?hitCount=(\d+) documentIds=\[([^\]]*)\].*?elapsedMs=(\d+)"
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
        match = re.search(r"Vertex provider call stage=(facts|initial_generation|repair) "
                          r"(?:compact=(true|false) )?outcome=(received|failure) finishReason=([A-Z_]+) "
                          r"outputTokens=(\d+|null|unknown) thinkingTokens=(\d+|null|unknown) "
                          r"totalTokens=(\d+|null|unknown)", line)
        if match:
            stage, compact, outcome, finish, output, thinking, total = match.groups()
            provider_calls.append({"stage": stage, "compact": None if compact is None else compact == "true",
                                   "outcome": outcome, "finishReason": finish,
                                   "outputTokens": int(output) if output.isdecimal() else None,
                                   "thinkingTokens": int(thinking) if thinking.isdecimal() else None,
                                   "totalTokens": int(total) if total.isdecimal() else None})
        match = re.search(r"Vertex grounding stage=closure outcome=(success|failure) "
                          r"finishReason=NOT_APPLICABLE outputTokens=NOT_APPLICABLE"
                          r"(?: hitCount=(\d+) elapsedMs=(\d+))?", line)
        if match:
            outcome, hits, elapsed = match.groups()
            grounding_stages.append({"stage": "closure", "outcome": outcome,
                                     "hitCount": None if hits is None else int(hits),
                                     "elapsedMs": None if elapsed is None else int(elapsed)})
    official_hits = sum(doc["authority"] == "PROVIDER_DOCUMENTATION"
                        and doc["documentType"] in ("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE")
                        and doc["sourceCommit"] == "f7a3b98da589ab1d52756b0dcee0dbf2de83d635"
                        for event in events for doc in event["documents"])
    return {"jobId": job_id, "events": events, "officialHitCount": official_hits, "stages": stages,
            "providerCalls": provider_calls, "groundingStages": grounding_stages,
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


def observe(client, fixture, directory, record, documents, pod, clock=time.monotonic, wait=time.sleep, *, diagnostic=False):
    record.update(status="SUBMISSION_STARTED", uploadAttempts=1)
    write(directory / "attempt.json", record)
    response = client.request("POST", "/api/upload", fixture.parent, "upload", fixture=fixture,
                              project_name="pt8a-" + os.environ["GITHUB_RUN_ID"])
    mark_upload(record, response)
    write(directory / "accepted.json", record)
    started, terminal, last_job = clock(), None, None
    while clock() - started < 540:
        polled = client.request("GET", "/api/analysis/jobs/" + record["jobId"], fixture.parent, "poll-" + str(time.time_ns()))
        job = polled.get("json")
        if polled["httpStatus"] == 200 and isinstance(job, dict):
            if job.get("id") != record["jobId"] or job.get("projectId") != record["projectId"]:
                raise ValueError("owner-scoped job readback identity mismatch")
            last_job = job
            if job.get("status") in ("SUCCEEDED", "FAILED"):
                terminal = job; break
        elif polled["httpStatus"] in (401, 403, 404):
            raise ValueError("accepted job inaccessible; no new owner or resubmission")
        wait(5)
    if terminal is None:
        record.update(status="NOT_PASS", observation="TERMINAL_NOT_OBSERVED", censoredObservationMs=round((clock()-started)*1000))
        if diagnostic and last_job is not None:
            write(directory / "job.json", sanitized_job(last_job))
            write(directory / "retrieval.json", retrieval_evidence(record["jobId"], record["acceptedAt"], documents))
            write(directory / "cli.json", {"status": "NOT_RUN", "reason": "TERMINAL_NOT_OBSERVED", "AWSPlanApply": False})
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
    if terminal["status"] != "SUCCEEDED" and (not diagnostic or not terminal.get("resultObjectKey")):
        if diagnostic:
            if project["json"].get("latestResultObjectKey"):
                raise ValueError("failed job has unbound persisted result; evidence incomplete")
            write(directory / "draft-identity.json", {"hclPresent": False})
            write(directory / "cli.json", {"status": "NOT_RUN", "reason": "BACKEND_FAILED", "AWSPlanApply": False})
        record["status"] = "NOT_PASS"; return
    draft = client.request("GET", f"/api/projects/{record['projectId']}/terraform/main.tf", fixture.parent, "draft")
    if (draft["httpStatus"] != 200 or not isinstance(draft["json"], dict)
            or draft["json"].get("latestAnalysisJobId") != record["jobId"]
            or draft["json"].get("latestResultObjectKey") != terminal.get("resultObjectKey")):
        raise ValueError("persisted project/result readback mismatch")
    hcl = draft["json"].get("content")
    if not isinstance(hcl, str) or not hcl.strip():
        if diagnostic:
            write(directory / "draft-identity.json", {"hclPresent": False, "resultObjectKey": terminal.get("resultObjectKey")})
            write(directory / "cli.json", {"status": "NOT_RUN", "reason": "NO_REVIEWABLE_DRAFT", "AWSPlanApply": False})
            record["status"] = "NOT_PASS"; return
        raise ValueError("missing persisted Terraform draft")
    write(directory / "draft-identity.json", {"hclSha256": sha(hcl.encode()), "resultObjectKey": terminal.get("resultObjectKey"),
        **({"hclPresent": True} if diagnostic else {})})
    cli = validate_draft(hcl, fixture.parent, pod)
    write(directory / "cli.json", cli)
    (directory / "main.tf").write_text(hcl)
    record["status"] = "REVIEW_PENDING" if (terminal["status"] == "SUCCEEDED" and cli["initValidateExitCode"] == 0 and retrieved["officialHitCount"] > 0
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
    corrective = request.get("mode") == CORRECTIVE_MODE
    qualified = request.get("mode") == QUALIFIED_MODE
    diagnostic = request.get("mode") == DIAGNOSTIC_MODE
    risk_path = corrective or qualified or diagnostic
    if diagnostic:
        if os.environ.get("OPERATION") != DIAGNOSTIC_OPERATION:
            raise ValueError("diagnostic mode requires its separate workflow operation")
        authority = diagnostic_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
        history = diagnostic_history(request, source, int(os.environ["GITHUB_RUN_ID"]))
        if args.action == "preflight":
            with tempfile.TemporaryDirectory(prefix="pt8a-diagnostic-") as directory:
                corrective_origins(Path(directory))
                diagnostic_predecessor(request, history, Path(directory))
    elif qualified:
        if os.environ.get("OPERATION") != QUALIFIED_OPERATION:
            raise ValueError("qualified mode requires its distinct reviewed workflow operation")
        campaign, authority = qualified_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
        history = qualified_history(request, source, int(os.environ["GITHUB_RUN_ID"]))
        if args.action == "preflight":
            with tempfile.TemporaryDirectory(prefix="pt8a-chain-") as directory:
                corrective_origins(Path(directory))
                qualified_predecessor(request, source, image, campaign, history, Path(directory))
    elif corrective:
        if os.environ.get("OPERATION") != CORRECTIVE_OPERATION:
            raise ValueError("corrective mode requires its distinct reviewed workflow operation")
        authority = corrective_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
        history = corrective_history(source, int(os.environ["GITHUB_RUN_ID"]))
        if args.action == "preflight":
            with tempfile.TemporaryDirectory(prefix="pt8a-origin-") as directory:
                corrective_origins(Path(directory))
    else:
        if os.environ.get("OPERATION") in (CORRECTIVE_OPERATION, QUALIFIED_OPERATION, DIAGNOSTIC_OPERATION):
            raise ValueError("qualified/corrective operation cannot execute ordinary readiness or official cases")
        request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
        ensure_latest_dispatch(request, source, int(os.environ["GITHUB_RUN_ID"]))
    if args.action == "preflight":
        return
    output = args.output; output.mkdir(parents=True, exist_ok=False)
    release = json.loads((Path(os.environ["RUNNER_TEMP"]) / "pt8a-release.json").read_text())
    write(output / "release.json", release)
    ledger = None if diagnostic else [{"caseId": c, "status": "NOT_RUN", "uploadAttempts": 0, "consumed": False} for c in CASES]
    if diagnostic:
        write(output / "diagnostic-contract.json", DIAGNOSTIC_CONTRACT)
        write(output / "diagnostic-disposition.json", {"classification": "DIAGNOSTIC_ONLY",
            "officialAcceptance": "NOT_ACCEPTANCE", "evidenceValidity": "INCOMPLETE", "dispatchConsumed": True})
    try:
        with tempfile.TemporaryDirectory(prefix="pt8a-private-") as temporary:
            private = Path(temporary)
            if risk_path:
                if diagnostic:
                    ledger = diagnostic_predecessor(request, history, private)
                    original_ledger = json.loads(json.dumps(ledger))
                corrective_release(release, source, image)
                origin_receipt, binding, failed_binding = corrective_origins(private)
                # Store original identities separately; never issue a new ingestion receipt.
                write(output / "origin-bindings.json", {"clean": binding, "failed": failed_binding,
                    "originApprovalCommentId": ORIGIN_APPROVAL, "originalFailedJobConsumed": True})
                write(output / "known-writer-history.json", history)
                if diagnostic:
                    write(output / "diagnostic-chain.json", {"prior": {"runId": request["priorRunId"],
                        "artifactId": request["priorArtifactId"], "reviewCommentId": request["priorReviewCommentId"]}})
                elif qualified:
                    ledger = qualified_predecessor(request, source, image, campaign, history, private)
                    write(output / "qualified-chain.json", {
                        "campaignApprovalCommentId": request["liveApprovalCommentId"],
                        "readinessRunId": request["readinessRunId"], "readinessArtifactId": request["readinessArtifactId"],
                        "readinessReviewCommentId": request["readinessReviewCommentId"],
                        "prior": {"runId": request["priorRunId"], "artifactId": request["priorArtifactId"],
                            "reviewCommentId": request["priorReviewCommentId"]}})
            else:
                receipt, binding = bound_artifact(request["provenanceRunId"], request["provenanceArtifactId"],
                    ["receipt.json"], private, ".github/workflows/gcp-target-corpus-ingestion.yml")
                require_clean_receipt(receipt["receipt.json"], binding, request, source)
            manifest, schema, documents, checksum = rag.load_corpus(args.corpus)
            if checksum != CORPUS_CHECKSUM:
                raise ValueError("rebuilt expected corpus checksum differs; do not tune authority")
            if risk_path:
                rag.validate_pt8a_clean_corpus(manifest, documents, checksum,
                    json.loads((args.corpus / "coverage-report.json").read_text()))
                readiness = risk_qualified_admission(rag.JsonHttpClient("http://127.0.0.1:19200"),
                    manifest, schema, documents, checksum, origin_receipt, binding, authority)
                readiness["correlatedBackendRetrieval"] = False
            else:
                readiness = rag.verify_exact_v4(rag.JsonHttpClient("http://127.0.0.1:19200"), manifest, schema,
                    documents, checksum, {**binding, "githubArtifactBindingVerified": True, "receipt": receipt["receipt.json"]})
            write(output / "readiness.json", readiness)
            if readiness["classification"] != ("RISK_QUALIFIED_RETAINED_V4" if risk_path else "EXACT_REUSABLE_COMPLETED_V4"):
                raise ValueError("readiness does not authorize case A; no automatic ingestion")
            write(output / "binding.json", {"sourceSha": source, "image": image, "candidateIdentity": IDENTITY,
                "procedureSha256": diagnostic_contract_sha256() if diagnostic else sha((ROOT / (CORRECTIVE_PROCEDURE if risk_path else PROCEDURE)).read_bytes()),
                "mode": request["mode"], "caseId": request.get("caseId"),
                "liveApprovalCommentId": request["liveApprovalCommentId"], "runId": int(os.environ["GITHUB_RUN_ID"]),
                **({"classification": "DIAGNOSTIC_ONLY", "diagnosticContractSha256": diagnostic_contract_sha256(),
                    "frozenV2Sha256": V2_SHA256, "frozenV3Sha256": V3_SHA256} if diagnostic else {})})
            if qualified or diagnostic:
                record = ledger[CASES.index(request["caseId"])]
                if diagnostic:
                    record.update(classification="DIAGNOSTIC_ONLY", dispatchConsumed=True)
                case = next(c for c in frozen_inputs() if c["caseId"] == request["caseId"])
                fixture = acquire(case, private)
                write(output / "input-identity.json", case)
            elif request["mode"] == "case":
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
                fixture = ROOT / READINESS_FIXTURE
                # Keep raw transport bodies in the private temporary directory, never the artifact.
                private_fixture = private / "input.png"; private_fixture.write_bytes(fixture.read_bytes()); fixture = private_fixture
            if transport.current_main() != source:
                raise ValueError("HUMAN_REQUIRED: MAIN_DRIFT before inference")
            if diagnostic:
                authority = diagnostic_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
                history = diagnostic_history(request, source, int(os.environ["GITHUB_RUN_ID"]))
                if diagnostic_predecessor(request, history, private) != original_ledger:
                    raise ValueError("diagnostic predecessor changed before upload")
            elif qualified:
                campaign, authority = qualified_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
                history = qualified_history(request, source, int(os.environ["GITHUB_RUN_ID"]))
                if qualified_predecessor(request, source, image, campaign, history, private) != ledger:
                    raise ValueError("qualified predecessor changed before upload")
            elif corrective:
                corrective_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
                corrective_history(source, int(os.environ["GITHUB_RUN_ID"]))
            subprocess.run(["bash", "scripts/smoke/ephemeral-jwks-fixture.sh", "prepare"], check=True)
            client = transport.CurlClient(Path(os.environ["RUNNER_TEMP"]) / "case-c-access.token")
            try:
                observe(client, fixture, output / "observation", record, documents, "terraformers-pt8a-validation",
                        **({"diagnostic": True} if diagnostic else {}))
            finally:
                client.close()
            if risk_path and (record["status"] == "REVIEW_PENDING" or diagnostic):
                # Recheck after the one job, without any new upload or embedding request.
                post = risk_qualified_admission(rag.JsonHttpClient("http://127.0.0.1:19200"),
                    manifest, schema, documents, checksum, origin_receipt, binding, authority)
                write(output / "post-admission.json", post)
                if diagnostic:
                    write(output / "post-known-writer-history.json", diagnostic_history(request, source, int(os.environ["GITHUB_RUN_ID"])))
                    diagnostic_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
                    complete = (record.get("consumed") is True and record.get("uploadAttempts") == 1
                        and record.get("terminalState") in ("FAILED", "SUCCEEDED") and "censoredObservationMs" not in record)
                    write(output / "diagnostic-disposition.json", {"classification": "DIAGNOSTIC_ONLY",
                        "officialAcceptance": "NOT_ACCEPTANCE", "observedStatus": record["status"], "dispatchConsumed": True,
                        "evidenceValidity": "COMPLETE_AWAITING_INDEPENDENT_CLASSIFICATION" if complete else "INCOMPLETE"})
                elif qualified:
                    write(output / "post-known-writer-history.json", qualified_history(request, source, int(os.environ["GITHUB_RUN_ID"])))
                    qualified_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
                else:
                    write(output / "post-known-writer-history.json", corrective_history(source, int(os.environ["GITHUB_RUN_ID"])))
                    corrective_request_contract(request, source, image, os.environ["GITHUB_RUN_ATTEMPT"])
            if request["mode"] in ("readiness", CORRECTIVE_MODE, QUALIFIED_MODE):
                readiness["correlatedBackendRetrieval"] = record["status"] == "REVIEW_PENDING"
                write(output / "readiness.json", readiness)
                if not qualified:
                    write(output / "readiness-job.json", record)
            if record["status"] == "NOT_PASS":
                raise ValueError("material technical/product failure; preserve later cases NOT_RUN")
    except Exception as error:
        write(output / "error.json", {"class": type(error).__name__, "reason": str(error)[:300],
            **({"classification": "DIAGNOSTIC_ONLY", "officialAcceptance": "NOT_ACCEPTANCE",
                "failureClassification": "INDEPENDENT_EVIDENCE_CLASSIFICATION_REQUIRED"} if diagnostic else {})})
        raise
    finally:
        if ledger is not None:
            write(output / "ledger.json", ledger)
        seal(output)


if __name__ == "__main__":
    main()
