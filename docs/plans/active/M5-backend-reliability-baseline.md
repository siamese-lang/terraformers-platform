# M5 — Backend Reliability Baseline

## Status

**ACTIVE — BASELINE MEASUREMENT ONLY**

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

**Status: TODO**

Measure what happens to persisted `PENDING` and `RUNNING` jobs when no in-process executor task is
available after an application restart/process loss.

Required evidence:

- reproduce persisted `PENDING` without a corresponding active executor task;
- reproduce persisted `RUNNING` with the execution process gone;
- restart/recreate the application lifecycle using existing local/backend integration test
  infrastructure where practical;
- observe whether either state is discovered, resumed, failed, or remains unchanged;
- record DB state before and after the restart boundary.

Do not add a recovery implementation in this task.

## M5-2 — Same-job duplicate execution baseline

**Status: TODO**

Measure whether the same persisted job id can execute concurrently more than once.

Required evidence:

- invoke the current runner/state-transition path concurrently against one job id;
- observe provider invocation count;
- observe result-object writes and generated Terraform file registration;
- record final job state and which result reference wins;
- distinguish harmless repeat invocation from conflicting duplicate side effects.

Do not add locks, state guards, idempotency keys, or deduplication in M5.

## M5-3 — Object/DB partial-success baseline

**Status: TODO**

Measure the boundary where the Terraform object is written successfully but relational result
registration or final job-state persistence fails.

Required evidence:

- preserve successful object-write evidence;
- force a deterministic DB/result-registration failure after that write;
- observe job terminal state;
- observe whether a result object remains without a committed corresponding result-file/job
  reference;
- record whether repeating the job overwrites the same deterministic object key or creates a new
  side effect.

Do not add compensation/deletion/outbox behavior in M5.

## M5-4 — Executor pressure classification

**Status: TODO**

Reuse the existing rejection behavior first.

Current evidence already shows:

- `RejectedExecutionException` is caught;
- rejection increments observability;
- the persisted job is marked `FAILED`.

Only add a bounded saturation integration measurement if the existing deterministic coverage cannot
answer whether real executor pressure leaves jobs stranded. Do not create a separate load workflow.

## M5-5 — Baseline classification and M6 gate

**Status: TODO**

Classify each candidate as one of:

- `CONFIRMED_RELIABILITY_GAP`;
- `CONTROLLED_CURRENT_BEHAVIOR`;
- `NOT_REPRODUCED`;
- `OUT_OF_SCOPE / PRODUCT_CONTRACT_UNCLEAR`.

For every confirmed gap record:

- exact reproduction;
- state transition sequence;
- persisted DB/object result;
- user-visible consequence;
- invariant violated;
- smallest solution families eligible for M6 consideration.

Do not select a solution in M5.

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

Run M5-1 using the smallest existing backend integration boundary: prove the current persisted
`PENDING`/`RUNNING` behavior across a simulated process-restart boundary without implementing
recovery.
