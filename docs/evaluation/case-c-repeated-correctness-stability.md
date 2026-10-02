# Case C C2 — Repeated Correctness Stability Gate Evidence

## Status

**STATIC IMPLEMENTATION / LIVE GATE NOT EXECUTED**

This document records the implementation/readiness boundary for C2. It does not claim that
C-ARCH-05 is resolved. Only one separately approved live five-request gate can satisfy the live
acceptance.

## Execution identity

- Decision PR: #184
- Decision merge: `b6fb62c457d08b73a256f1d014e67b8fcdb07cf4`
- Work Package: `.agents/work-packages/case-c-repeated-correctness-stability-v1.yml`
- Bound implementation base: `b6fb62c457d08b73a256f1d014e67b8fcdb07cf4`
- Capacity baseline: `SUSPENDED_BY_ARCHITECTURE_AUDIT`
- Current architecture phase: C2

## Frozen gate contract

- fixture: `evaluation/terraformers-eval-v1/fixtures/case-01-vpc-three-tier.webp`
- fixture SHA-256: `a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8`
- concurrency: exactly `1`
- repeat count: exactly `5`
- submission: sequential; request N+1 begins only after request N reaches terminal acceptance and
  its result contract has passed
- pass rule: `5/5`
- automatic whole-gate rerun: forbidden

The count is a bounded regression gate chosen before execution. It is not a statistical reliability
percentage or soak-test claim.

## Reused runtime path

C2 extends the existing manual
`.github/workflows/gcp-target-runtime-dependencies.yml` workflow instead of adding another workflow
or standalone harness.

The historical one-request operation remains:

- `backend-live-validation`
- confirmation `RUN_REVIEWED_CASE_C_BACKEND_LIVE_VALIDATION_1`

The C2 operation is:

- `backend-live-validation-c2-stability`
- confirmation `RUN_REVIEWED_CASE_C_C2_STABILITY_GATE_5`

Both reuse the existing ephemeral JWT/JWKS fixture and integrated backend path. The C2 operation
uses one JWT/JWKS setup and one backend port-forward for all five sequential attempts.

## Runtime identity boundary

The C2 operation additionally requires:

- exact workflow/main SHA;
- exact immutable Artifact Registry backend digest;
- exact `backend_source_sha`;
- `backend_source_sha == expected_sha`;
- deployed backend `BUILD_SOURCE_REVISION == backend_source_sha`;
- exactly one backend Pod;
- stable backend Pod UID and container restart count through all five attempts;
- exactly one ready and one available backend replica throughout the gate.

Any image/source/Pod/restart/replica drift stops the gate as `RUNTIME_IDENTITY_DRIFT`.

## Per-attempt correctness boundary

Each accepted attempt requires:

- authenticated upload HTTP 201;
- GCS source persistence fields;
- one exact AnalysisJob ID and project ID;
- AnalysisJob terminal state `SUCCEEDED`;
- `failureReason == null`;
- provider/result identity fields;
- generated Terraform read HTTP 200;
- generated Terraform content non-empty;
- latest AnalysisJob/result identity matches the terminal job;
- runtime identity still unchanged after the attempt.

The first nonaccepted attempt stops the gate. No later attempt is substituted for it.

## Failure semantics

- `CORRECTNESS_FAILURE`: repository-owned/application/schema/Terraform/authentication/result
  correctness failure, or a failure without evidence proving an external cause.
- `EXTERNAL_VARIANCE_INCONCLUSIVE`: a job-correlated provider rate-limit or
  `AnalysisProviderTimeoutException` is observed. The gate does not pass and no capacity work is
  authorized.
- `RUNTIME_IDENTITY_DRIFT`: immutable image/source/Pod/restart/replica boundary changes.
- `PASS`: only five accepted sequential attempts under one frozen runtime identity.

A failed/inconclusive live gate requires a new user checkpoint before any rerun.

## Sanitized evidence contract

For the C2 operation only, the existing workflow uploads
`case-c-c2-stability-<run-id>` containing:

- `runtime-identity.json`
- `attempts.jsonl`
- `summary.json`

The artifact contains attempt/project/job/result identity and classification metadata only. JWTs,
private keys, response bodies, and raw generated Terraform are kept outside the artifact path.

The existing `always()` cleanup restores the placeholder JWKS before the artifact upload.

## Live sequence — not authorized by this implementation

After this implementation is independently accepted and merged, a separate explicit checkpoint is
still required for:

1. publish one immutable image from the exact then-current main SHA using
   `GCP Backend Image Publish`;
2. roll out that exact digest using the existing `backend-revision-rollout` operation;
3. execute exactly one protected `backend-live-validation-c2-stability` run.

This implementation performs none of those live/cost/runtime actions.

C4 remains responsible for repository declarative image convergence. C3 remains responsible for
repairing executor-aware capacity saturation semantics. Capacity baseline execution remains
prohibited.
