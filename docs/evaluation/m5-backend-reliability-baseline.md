# M5 Backend Reliability Baseline Evidence

## Status

**COMPLETE**

This document accumulates M5 measurement results. M5 records current behavior only; reliability
fixes are deferred to M6 after classification.

## Fixed code boundary

The baseline focuses on the current `AnalysisJob` lifecycle:

- `AnalysisJobService` persists `PENDING` and schedules through the in-process executor after
  transaction commit;
- `AnalysisJobRunner` transitions through `AnalysisJobStateService`;
- `markRunning`, `markSucceeded`, and `markFailed` use separate `REQUIRES_NEW` transactions;
- provider execution and result-object storage occur before final relational success registration;
- the current entity has no `@Version` field and `markRunning` has no source-status predicate.

No M5 measurement changes production behavior.

## M5-1 — Restart / stranded-state baseline

**Classification: `CONFIRMED_RELIABILITY_GAP`**

Evidence:

- PR #78
- merge commit: `c58b2902556d30f4e85ff84b249a8dccfd83e207`
- test:
  `AnalysisJobRestartBaselineTest.persistedPendingAndRunningJobsRemainStrandedAcrossApplicationRestart`
- Backend Local Verification run: `36394053570` — **SUCCESS**
- MariaDB schema/repository validation in the same workflow — **SUCCESS**

### Reproduction

The deterministic test:

1. starts the real Spring Boot application against an isolated H2 database;
2. persists one `PENDING` and one `RUNNING` analysis job;
3. closes the application context, removing the in-process executor/runtime;
4. starts a fresh application context against the same database without recreating the schema;
5. reads both persisted jobs after startup.

Observed state after restart:

- `PENDING → PENDING`
- `RUNNING → RUNNING`

No startup component discovered, resumed, failed, or otherwise reconciled either job.

### Interpretation boundary

The test does not claim how frequently a production process interruption occurs. It proves the
conditional recovery behavior: **if** a committed job is left `PENDING` or `RUNNING` when the
process/executor task disappears, the current application startup leaves that state unchanged.

This violates the M5 recovery invariant because an accepted job can remain indefinitely
non-terminal without an active execution path.

No recovery implementation was added.

## M5-2 — Duplicate same-job delivery baseline

**Classification: `CONFIRMED_RELIABILITY_GAP`**

Evidence test:

`AnalysisJobDuplicateExecutionBaselineTest.duplicateDeliveryReexecutesSucceededJobWithoutStateGuard`

### Reproduction

The test uses the real JPA repository and proxied `AnalysisJobStateService` transaction boundary,
while replacing orchestration side effects with deterministic mocks so invocation counts are
observable.

For one persisted job id:

1. first delivery starts from `PENDING`, enters `RUNNING`, executes the provider/store
   orchestration path, and reaches `SUCCEEDED`;
2. the exact same job id is delivered to `AnalysisJobRunner` again;
3. `markRunning` accepts the existing `SUCCEEDED` row and changes it back to `RUNNING`;
4. the provider/store orchestration path is invoked a second time;
5. generated-Terraform registration is attempted a second time;
6. the job ends `SUCCEEDED` again.

Expected invocation evidence, enforced by the test:

- status observed before `markRunning`: `[PENDING, SUCCEEDED]`;
- `executeProviderAndStoreDraft(...)`: 2 calls;
- `registerGeneratedTerraform(...)`: 2 calls.

### Interpretation boundary

A sequential duplicate delivery is sufficient to prove the state-transition/idempotency gap; a
concurrent overlap is not required to establish that a terminal job can be re-executed. M5 therefore
does not add a more timing-sensitive concurrency harness merely to reproduce the same missing guard.

The mocked side-effect boundary proves duplicate execution/registration attempts. It does not yet
claim the exact object/DB residue produced by a mid-flight failure; that is measured separately in
M5-3.

No lock, compare-and-set transition, idempotency key, or deduplication behavior was added.

## M5-3 — Object write / DB finalization partial-success baseline

**Classification: `CONFIRMED_RELIABILITY_GAP`**

Evidence:

- PR #80
- merge commit: `18503b656de090b6fc26b317908189b7090ecfc3`
- final successful Backend Local Verification run: `36395373207`
- test:
  `AnalysisJobPartialSuccessBaselineTest.successfulObjectWriteRemainsWhenRelationalFinalizationFails`

### Reproduction

The deterministic test uses the real JPA repository/state-service transaction boundaries and real
`AnalysisJobOrchestrator`/Terraform validation with:

- a deterministic persistent `ObjectWriter` fixture;
- a forced exception at `ProjectArtifactService.registerGeneratedTerraform(...)` after object write.

Observed result:

1. provider analysis and Terraform validation succeed;
2. result-object write succeeds and remains observable;
3. generated-file relational registration fails;
4. the runner records the job as `FAILED`;
5. persisted `resultFileId` remains null;
6. persisted `resultObjectKey` remains null;
7. the already-written result object remains present because there is no compensation path.

`AnalysisResultStorage` derives the object key from the job id, so repeat execution of the same job
targets the same deterministic key rather than an arbitrary new path. M5 does not use that property
as a substitute for residue cleanup/accountability.

### Interpretation boundary

This proves a cross-resource partial-success window: object persistence can commit before the
relational success record, leaving persistent data that the job row does not reference. M5 does not
claim a broker/outbox is required; it classifies the consistency gap and leaves the minimal control
decision to M6.

No compensation/delete behavior was added.

## M5-4 — Executor pressure / rejection classification

**Classification: `CONTROLLED_CURRENT_BEHAVIOR`**

Existing deterministic coverage already proves:

- executor `RejectedExecutionException` is caught;
- rejection is observed through `AnalysisObservability.jobRejected()`;
- the persisted job is marked `FAILED`.

Because the failure mode is already terminal and observable, M5 does not add a separate saturation
workflow or load harness merely to reproduce the same rejection contract.

## Current classification table

| Candidate | Classification | Evidence |
| --- | --- | --- |
| Restart leaves persisted `PENDING`/`RUNNING` | **CONFIRMED_RELIABILITY_GAP** | PR #78 / restart baseline test |
| Duplicate delivery of same job id | **CONFIRMED_RELIABILITY_GAP** | duplicate-execution baseline test |
| Object write succeeds, DB finalization fails | **CONFIRMED_RELIABILITY_GAP** | PR #80 / partial-success baseline test |
| Executor rejection | **CONTROLLED_CURRENT_BEHAVIOR** | existing `AnalysisJobServiceTest` |

## M5 closure decision

M5 exit criteria are **MET**.

Confirmed reliability gaps:

1. application restart can strand committed `PENDING`/`RUNNING` jobs indefinitely;
2. duplicate delivery can re-execute an already-`SUCCEEDED` job and repeat side-effect attempts;
3. object persistence can succeed while relational finalization fails, leaving untracked residue.

Controlled current behavior:

- executor rejection is converted to an observable terminal `FAILED` state.

M5 does not select RabbitMQ, Transactional Outbox, distributed locks, or retry infrastructure.
The evidence instead supports a smaller M6 sequence:

1. atomic `PENDING → RUNNING` claim / terminal-state guard;
2. current single-replica restart reconciliation to a safe terminal state;
3. provider-neutral partial-success residue control after capability inspection.

The M6 source-of-truth plan is
[`M6 — Backend Reliability Improvement`](../plans/active/M6-backend-reliability-improvement.md).
