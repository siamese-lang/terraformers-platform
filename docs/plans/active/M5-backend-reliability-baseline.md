# M5 — Backend Reliability Baseline

## Status

**COMPLETE — M5 BASELINE CLASSIFIED / M6 GATE OPEN**

M4 is complete. M5 measures the current `AnalysisJob` lifecycle before selecting any reliability
mechanism.

## Objective

Classify current backend reliability behavior with reproducible evidence:

`candidate failure → reproduction → resulting state → invariant assessment → problem/non-problem classification`

M5 does not implement a reliability fix. Confirmed problems move to M6.

## Current lifecycle facts

Current production code establishes the following baseline:

- `AnalysisJobService.create(...)` persists a `PENDING` row and schedules execution only after the
  surrounding transaction commits.
- Scheduling uses the in-process `analysisJobExecutor`.
- The executor is a `ThreadPoolTaskExecutor` with core size 2, max size 4, queue capacity 50, and
  `AbortPolicy`.
- executor rejection is already caught and mapped to `FAILED`; an existing deterministic test
  covers this behavior.
- `AnalysisJobRunner` moves the job to `RUNNING` in a `REQUIRES_NEW` transaction, executes the
  provider and result-object write, then marks success in another `REQUIRES_NEW` transaction.
- `AnalysisJobStateService.markRunning(...)` currently loads by id and writes `RUNNING` without a
  status precondition, row lock, version field, or compare-and-set transition.
- result object bytes are written before the generated Terraform file/job success state is committed
  to the relational database.
- the repository has no status query/reconciliation component for stale `PENDING` or `RUNNING`
  jobs and no startup recovery hook visible in the current code.

These are observations, not M5 defect conclusions.

## Non-goals

Do not introduce or select any of the following during M5:

- RabbitMQ or another durable queue;
- Transactional Outbox;
- Kafka;
- Redis;
- scheduled retry infrastructure;
- automatic duplicate suppression;
- distributed locks;
- optimistic/pessimistic locking changes;
- new cloud service;
- new GitHub Actions workflow.

Do not treat a candidate as a bug merely because a common architecture would solve it.

## Baseline invariants to test

M5 uses these as assessment questions, not assumed implementation requirements:

1. An accepted job should have an explainable terminal or recoverable state after process
   interruption.
2. One persisted job id should not silently produce conflicting terminal artifacts from concurrent
   executions.
3. A terminal job/result relationship should be explainable when storage write or DB registration
   fails partway through the success path.
4. Executor pressure should not leave an accepted job indefinitely `PENDING`.
5. Existing successful single-execution behavior must remain the reference baseline.

If evidence shows an invariant is not required by the current product contract, record that
explicitly rather than inventing a fix.

## M5-1 — Restart / stranded-state baseline

**Status: COMPLETE — PR #78 / merge `c58b2902556d30f4e85ff84b249a8dccfd83e207`**

Current behavior is confirmed: persisted `PENDING` and `RUNNING` jobs remain unchanged after a fresh
application context starts against the same database with no surviving executor task.

Required evidence:

- reproduce persisted `PENDING` without a corresponding active executor task;
- reproduce persisted `RUNNING` with the execution process gone;
- restart/recreate the application lifecycle using existing local/backend integration test
  infrastructure where practical;
- observe whether either state is discovered, resumed, failed, or remains unchanged;
- record DB state before and after the restart boundary.

Do not add a recovery implementation in this task.

## M5-2 — Same-job duplicate execution baseline

**Status: COMPLETE — DETERMINISTIC DUPLICATE-DELIVERY BASELINE**

The baseline uses the stronger, simpler condition of duplicate delivery after terminal success rather
than a timing-sensitive concurrent harness. One persisted job is executed to `SUCCEEDED`, then the
same job id is delivered to the runner again.

The current state service accepts `SUCCEEDED → RUNNING`, and the deterministic test records two
provider/store orchestration invocations and two generated-Terraform registration attempts before
the job reaches `SUCCEEDED` again. Because duplicate side-effect execution is already proven,
concurrent overlap is unnecessary to establish the missing state/idempotency guard.

No locks, state guards, idempotency keys, or deduplication were added.

## M5-3 — Object/DB partial-success baseline

**Status: COMPLETE — PR #80 / merge `18503b656de090b6fc26b317908189b7090ecfc3`**

The deterministic baseline forced generated-file relational registration to fail after a successful
persistent result-object write.

Observed behavior:

- the object write succeeds and remains present;
- the job ends `FAILED`;
- relational `resultFileId` remains null;
- relational `resultObjectKey` remains null;
- no compensation removes the already-written object.

The result key is deterministic from the job id, but the persisted job row does not account for the
residue after finalization failure. Classification:
`CONFIRMED_RELIABILITY_GAP`.

No compensation/deletion/outbox behavior was added.

## M5-4 — Executor pressure classification

**Status: COMPLETE — CONTROLLED CURRENT BEHAVIOR**

Reuse the existing rejection behavior first.

Current evidence already shows:

- `RejectedExecutionException` is caught;
- rejection increments observability;
- the persisted job is marked `FAILED`.

The existing deterministic rejection coverage is sufficient for the observed failure contract:
executor rejection is caught, observed, and the persisted job is terminal `FAILED`. M5 therefore
does not add a saturation workflow or another load harness.

## M5-5 — Baseline classification and M6 gate

**Status: COMPLETE**

Classify each candidate as one of:

- `CONFIRMED_RELIABILITY_GAP`;
- `CONTROLLED_CURRENT_BEHAVIOR`;
- `NOT_REPRODUCED`;
- `OUT_OF_SCOPE / PRODUCT_CONTRACT_UNCLEAR`.

Final classifications are recorded in
[`m5-backend-reliability-baseline.md`](../../evaluation/m5-backend-reliability-baseline.md).

Confirmed gaps:

- restart-stranded `PENDING`/`RUNNING`;
- duplicate same-job execution through a terminal state;
- persistent object residue after relational finalization failure.

Controlled behavior:

- executor rejection terminates the job as `FAILED`.

M5 selects no broker/outbox/lock/retry architecture. The evidence opens M6 only for the minimal
state-transition, current-runtime restart reconciliation, and partial-success residue controls.

## Validation discipline

- Prefer existing backend tests, MariaDB integration support, and local deterministic harnesses.
- Add focused tests only when they reproduce a lifecycle behavior that existing coverage cannot.
- Do not create milestone-specific GitHub Actions workflows.
- Do not rerun completed M1/M2/M4 live evidence unless the M5 scenario actually depends on it.
- M5 should require no GCP node activation unless local/runtime evidence proves insufficient.

## Exit condition

M5 is complete when:

1. restart/stranded-state behavior is reproducibly classified;
2. duplicate same-job execution behavior is reproducibly classified;
3. object-write/DB-finalization failure behavior is reproducibly classified;
4. executor rejection/pressure is classified using existing evidence or one bounded measurement;
5. confirmed failures are separated from controlled/non-problem behavior;
6. M6 has explicit invariants and evidence-backed problem statements without a preselected
   architecture.

## Immediate next single task

Begin M6-1 from
[`M6 — Backend Reliability Improvement`](M6-backend-reliability-improvement.md): replace the
unconditional `RUNNING` transition with an atomic `PENDING` claim and invert the M5-2 duplicate
delivery baseline. Do not combine restart reconciliation or partial-success cleanup into M6-1.
