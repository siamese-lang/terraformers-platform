# Case C C2 — Repeated Correctness Stability Gate Evidence

## Status

**FIRST LIVE GATE FAILED / TYPED DIAGNOSTIC IMPLEMENTATION STATIC / RERUN NOT EXECUTED**

This document records the implementation/readiness and first-live-execution boundary for C2. It
does not claim that C-ARCH-05 is resolved. Run `36985305485` executed and failed on attempt 1.
Only a later separately approved five-request gate that passes 5/5 can satisfy the live acceptance.

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

## First live execution — failed, no rerun authorized

Run `36985305485` executed against:

- source SHA `8d72c91767c25705d460551babfc4fc079ce7d80`;
- backend digest `sha256:221e11378f2e73557c1fc0933ea09c629c5120c41360917f5bedb9d3b213768c`;
- fixture SHA-256 `a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8`;
- backend restart count `0`;
- concurrency `1`.

The gate stopped on **attempt 1** with terminal state `FAILED` and classification
`CORRECTNESS_FAILURE`. No later attempt ran, and `automatic_whole_gate_rerun=false` was retained.
The placeholder JWKS cleanup and sanitized artifact upload both succeeded.

The uploaded artifact also exposed an implementation defect in the C2 evidence contract: for a
failed AnalysisJob, `attempts.jsonl` did not retain the already-known project ID, AnalysisJob ID, or
bounded observability failure category. Consequently the run proves that the integrated request
failed, but does not preserve enough sanitized evidence to identify the repository-owned root cause
after the workflow ended.

This maps to the existing `C-ARCH-05` work rather than creating a new architecture issue. The Work
Package's single bounded corrective iteration is used to add those missing failed-attempt identity
fields and one allowlisted safe failure category only. Acceptance, repeat count, workload,
concurrency, retry policy, runtime sizing, backend source, and live execution policy are unchanged.

**No rerun is authorized by this repair.** A later retry still requires a new explicit user live
checkpoint after the repair is merged and independently accepted.

### Recovered failure class

A read-only backend log query after run `36985305485` recovered the job-correlated failure:

- AnalysisJob: `6a6eec5d-9e21-4fa8-907a-0dcd7521a24f`
- source revision: `8d72c91767c25705d460551babfc4fc079ce7d80`
- stage: `analysis_execution`
- error class: `GeneratedTerraformContractViolation`
- existing observability category: `other`
- claim generation: `1`

This excludes the C2 `EXTERNAL_VARIANCE_INCONCLUSIVE` path. The request was rejected by the
repository-owned C1 generated-Terraform contract after generation. The current backend observability
taxonomy does not preserve which of the contract's three safe subtypes fired (module block,
non-AWS/provider-envelope resource, or request-schema-envelope resource), and the durable failure
reason is intentionally replaced by the generic user-facing analysis failure reason.

PR #187 selected a closed typed diagnostic contract for this error class, and the
subsequent static implementation maps the three existing fail-closed branches to:

- `generated_terraform_contract_module`;
- `generated_terraform_contract_provider`;
- `generated_terraform_contract_request_schema`.

The failed run `36985305485` predates that typed telemetry, so its specific subtype remains
unknown and must not be retroactively inferred. A later C2 rerun can preserve one of the three
categories without parsing exception messages or uploading raw HCL/log content.
