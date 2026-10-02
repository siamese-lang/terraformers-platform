# Case C C2 — Repeated Correctness Stability Gate Decision

## Status

**PROPOSED / USER APPROVAL REQUIRED**

This decision is D_DECISION work. It does not authorize live execution, image publication,
backend rollout, or the capacity baseline.

## Operating scenario

The Case C capacity baseline must measure load-induced saturation of one fixed production-
representative workload. Before that experiment can be meaningful, the exact same workload must
execute repeatedly at concurrency 1 without intermittent application/executable-Terraform
correctness failures.

The prior live-validation run `36956086874` passed once, but capacity run `36957682821` then
completed two requests and failed the third request at concurrency 1 on an executable-Terraform
correctness failure. A single successful live analysis is therefore not a sufficient correctness
prerequisite.

C1 has since closed the executable Terraform envelope, request-specific AWS Provider 5.100.0 schema
evidence, bounded CLI diagnostics, and the two-call generation recovery budget in PR #182, merged as
`40237e593530086b4649bfc8bc5499848c4b7165`.

## Problem and impact

If capacity measurement begins after only one successful request, a stochastic correctness failure
can terminate the run before any valid saturation evidence exists. That mixes two different
questions:

1. does the exact integrated path remain correct under repeated low-load execution?
2. where does the runtime saturate as concurrency increases?

C2 must answer the first question before C3/C5 can answer the second.

## Alternatives

### A. Keep one existing backend live-validation run

Rejected. This is the exact prerequisite that already failed to protect the previous capacity run.

### B. Run the existing single live-validation operation five separate times

Technically valid and requires no workflow change, but rejected as the primary design because it
duplicates protected-dispatch overhead, fragments one acceptance gate across five workflow runs, and
makes run identity/evidence aggregation unnecessarily manual.

### C. Extend the existing backend live-validation job with one fixed C2 repeat profile of five sequential requests

**Selected.**

Reuse the existing authenticated JWT/JWKS setup, fixture, integrated API path, AnalysisJob polling,
and generated-Terraform read. Do not create another workflow or parallel harness. One protected
dispatch runs exactly five sequential requests in the same runtime context and emits bounded,
machine-readable result metadata.

### D. Run 10+ repetitions or a soak test

Rejected for C2. There is no measured failure probability from which a statistical reliability
claim can be derived. More repetitions would increase Vertex calls, elapsed time, and exposure to
external provider variance while turning a prerequisite gate into an unfrozen soak test.

### E. Reuse the capacity harness at concurrency 1

Rejected. C3 already records that the current capacity saturation model conflicts with executor
queueing semantics. C2 must not depend on the invalid capacity contract it is intended to unblock.

## Selected repeat count

**Exactly 5 sequential integrated analyses.**

Rationale:

- the observed prior failure occurred on request 3;
- five repetitions cross that observed boundary and add two additional successful executions;
- the gate remains a bounded regression prerequisite rather than a statistical reliability claim;
- the count is selected before live execution and must not be reduced or increased based on results.

No automatic rerun is allowed to obtain a passing sample. A rerun after a failed/inconclusive gate
requires a new explicit checkpoint based on the classified evidence.

## Fixed workload and concurrency

- fixture: `evaluation/terraformers-eval-v1/fixtures/case-01-vpc-three-tier.webp`
- fixture SHA-256: `a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8`
- concurrency: exactly 1
- request model: sequential; request N+1 starts only after request N reaches its accepted terminal
  state and result checks complete
- analysis path: authenticated `POST /api/upload` → durable AnalysisJob → Vertex analysis →
  Vertex embedding → REQUIRED OpenSearch retrieval → GCS-backed result → generated Terraform read
- no capacity/performance threshold is evaluated in C2

## Runtime identity prerequisite

The gate must run against the accepted C1 code, not the historical live backend image.

Before the gate:

1. publish one immutable GCP backend image from the then-current exact `main` using the existing
   `GCP Backend Image Publish` workflow;
2. roll that exact digest into the existing single target runtime using the existing
   `backend-revision-rollout` operation;
3. verify the deployed image digest and embedded `BUILD_SOURCE_REVISION` exactly match the approved
   source.

These are live/cost/runtime actions and require a separate explicit user checkpoint after the C2
contract is implemented and merged.

C4 remains responsible for converging repository declarative desired state to the accepted live
digest. C2 may roll the accepted candidate into the existing target runtime, but it does not claim
that C-ARCH-06 is resolved.

## C2 gate acceptance

PASS requires all five attempts, with no skipped attempt, to satisfy:

- authenticated upload returns HTTP 201;
- source bytes are persisted through the configured GCS path;
- one exact AnalysisJob ID and project ID are captured;
- AnalysisJob reaches `SUCCEEDED`;
- `failureReason == null`;
- provider/result identity fields are present;
- generated Terraform is readable through the backend and non-empty;
- deployed backend image remains the approved immutable digest;
- embedded backend source SHA remains the approved exact source;
- backend remains one ready/available replica;
- no unexpected backend pod restart/runtime identity drift occurs during the gate;
- the workflow records all five attempt identities in bounded machine-readable evidence.

The gate does not pass on 4/5, timeout, missing evidence, or an automatic retry of the whole gate.

## Failure semantics

- **CORRECTNESS_FAILURE**: an application, schema-envelope, Terraform validation, result-integrity,
  authentication-boundary, or other repository-owned correctness failure occurs. Stop immediately.
  Do not start capacity work.
- **EXTERNAL_VARIANCE_INCONCLUSIVE**: evidence identifies an external provider/network condition such
  as rate limiting. The gate does not pass, but C2 does not relabel that event as a repository
  correctness defect. Stop and require a new user checkpoint before any rerun.
- **RUNTIME_IDENTITY_DRIFT**: image/source/restart identity changes unexpectedly. Stop immediately
  as HUMAN_REQUIRED.
- **PASS**: all five sequential attempts satisfy the frozen acceptance.

No new failure class receives an automatic model regeneration or whole-gate retry.

## Implementation decision

Modify the existing
`.github/workflows/gcp-target-runtime-dependencies.yml` only enough to add a fixed
`backend-live-validation` C2 repeat profile.

The existing one-request profile must remain compatible.

The C2 profile must:

- require an explicit profile/confirmation distinct from the one-request checkpoint;
- hard-code/validate repeat count 5 rather than accepting arbitrary user counts;
- reuse one JWT/JWKS setup and one backend port-forward;
- generate unique project names per iteration;
- execute requests sequentially;
- stop on the first non-accepted result;
- record attempt number, project ID, AnalysisJob ID, terminal state, provider, result object key,
  approved backend image, and approved source SHA;
- upload only sanitized machine-readable evidence; no JWT/private key/raw generated HCL;
- restore the placeholder JWKS in the existing `always()` cleanup.

Do not create a new GitHub Actions workflow or a new standalone validation script unless the existing
job proves impossible to extend within these boundaries.

## Validation before live execution

Static implementation acceptance requires:

- workflow YAML parses;
- the existing single-request live-validation profile remains encoded;
- the C2 profile can execute only exactly five sequential attempts;
- exact main SHA and immutable image boundary remain fail-closed;
- sanitized artifact contract contains no credential/HCL payloads;
- existing Case C architecture ledger check passes;
- no production/backend/IaC/model/retrieval/runtime-sizing change occurs.

Static PASS does not resolve C-ARCH-05. Only one separately approved live C2 gate can do that.

## Residual risks

- Five successful requests do not establish a statistical production reliability percentage.
- Vertex/network variance remains an external residual risk and can make the gate inconclusive.
- C2 tests only the exact capacity workload; broader AI/RAG quality remains governed by Case A.
- Repository desired-state/image convergence remains C4.
- Executor-aware saturation semantics remain C3.

## Decision checkpoint

User approval of this decision authorizes implementation of the bounded C2 gate contract only.
It does **not** authorize image publication, backend rollout, live C2 execution, capacity baseline
execution, C3 implementation, or any runtime tuning.
