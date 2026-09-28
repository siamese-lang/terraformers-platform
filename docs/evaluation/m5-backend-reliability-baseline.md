# M5 Backend Reliability Baseline Evidence

## Status

**ACTIVE**

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

## Existing executor-rejection evidence

Existing deterministic coverage already proves:

- executor `RejectedExecutionException` is caught;
- rejection is observed through `AnalysisObservability.jobRejected()`;
- the persisted job is marked `FAILED`.

M5 will reuse this evidence unless a later baseline shows a distinct executor-pressure state gap.

## Current classification table

| Candidate | Classification | Evidence |
| --- | --- | --- |
| Restart leaves persisted `PENDING`/`RUNNING` | **CONFIRMED_RELIABILITY_GAP** | PR #78 / restart baseline test |
| Duplicate delivery of same job id | **CONFIRMED_RELIABILITY_GAP** | duplicate-execution baseline test |
| Object write succeeds, DB finalization fails | PENDING MEASUREMENT | M5-3 |
| Executor rejection | **CONTROLLED_CURRENT_BEHAVIOR** | existing `AnalysisJobServiceTest` |

## Immediate next measurement

M5-3: force a deterministic failure after Terraform object write succeeds but before generated-file
and job-success state commit, then record the remaining object and DB/job state. Do not add
compensation or outbox behavior.
