# Case C C2 — Repeated Correctness Stability Gate Evidence

## Status

**THIRD LIVE GATE FAILED AT 1/5 / ACTUAL TERRAFORM VALIDATION VARIANCE / FURTHER RERUN NOT AUTHORIZED**

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

## Second live execution — failed, C1 contract reopened

Run `37003640318` executed against:

- source SHA `61accc9ee50bba57ae5fa9dff0074fdc01e970e4`;
- backend digest `sha256:201eece790fd4a9913ee54fe122f4b72efdf70152d93037f60c05da20ae51235`;
- the same frozen fixture and concurrency `1`.

The gate again stopped on **attempt 1**, with `0/5` accepted and
`CORRECTNESS_FAILURE`.

The correlated backend trace proved the terminal category was
`generated_terraform_contract_request_schema` after:

1. REQUIRED retrieval succeeded with eight references;
2. the first generation triggered the policy-only `sensitive_credential` regeneration path;
3. the regenerated Terraform was rejected because one or more generated AWS resource types were
   outside the pre-generation request schema envelope.

The backend typed diagnostic implementation therefore worked. The sanitized C2 artifact recorded
`failure_category=unclassified` for a separate workflow-parser defect: it selected an earlier
informational category associated with the same AnalysisJob rather than the terminal
`Analysis job failed outcome=failed exceptionCategory=...` line.

This live evidence invalidates the earlier C1 assumption that request-specific schema context should
also be a hard generated-resource allowlist. The C1 contract is reopened under
[Case C C1 Correction — Simplified Terraform Draft Validation Decision](../plans/active/case-c-simplified-terraform-validation-decision.md).

No additional C2 rerun is authorized until that correction is implemented, merged, independently
accepted, published as a new immutable image, and rolled out under a separate live checkpoint.

## Third live execution — attempt 1 passed, attempt 2 Terraform validation failed

Run `37016993776` executed after the revised C1 contract was merged, published, and rolled out.

Frozen runtime identity:

- main/source SHA: `b420291fa1534184d2a260883df718cc96511505`;
- backend image:
  `sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`;
- fixture SHA-256:
  `a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8`;
- concurrency: `1`;
- repeat count: `5`;
- initial backend restart count: `0`.

Attempt 1 passed the complete integrated contract:

- project ID: `14`;
- AnalysisJob: `65817f11-7e19-4b86-b0aa-a247692d0f54`;
- terminal status: `SUCCEEDED`;
- provider: `vertex:gemini-3.8-flash`;
- generated Terraform result persisted and was readable/non-empty.

Attempt 2 then failed:

- project ID: `15`;
- AnalysisJob: `06ca4f00-f3f2-40fd-bcc1-815452a32593`;
- terminal status: `FAILED`;
- classification: `CORRECTNESS_FAILURE`;
- retained terminal failure category: `terraform_validate_configuration`.

The gate therefore stopped with `1/5` accepted and
`automatic_whole_gate_rerun=false`.

This is materially different from the prior C1 policy failures. The revised parser preserved the
terminal Terraform category correctly, and no request-schema contract category, external-provider
classification, or runtime-identity-drift classification fired. The remaining observed instability
is that the same frozen image/fixture/model path can produce a Terraform draft that passes on one
attempt and fails `terraform validate` on the next.

The current safe-diagnostic contract deliberately retains the bounded category but not raw generated
HCL or unbounded Terraform CLI diagnostics. Because the failed draft is not persisted before
validation succeeds, the exact invalid construct from attempt 2 cannot be reconstructed from the
retained artifact after the run.

No fourth C2 run is authorized by this evidence. Another live gate requires a new user checkpoint;
rerunning without first improving root-cause evidence would risk repeating a failure that remains
only category-level diagnosable.
