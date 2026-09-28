# Backend Reliability Case — AnalysisJob Durable Lifecycle

## Status

**ACTIVE CASE REVIEW — NOT YET PORTFOLIO-CLOSED**

This document groups M5 and M6 into one engineering case. It is not a list of three unrelated bugs
and it is not a final portfolio narrative.

## Case thesis

The backend accepted asynchronous analysis work into a relational `AnalysisJob` row, but execution
ownership lived in an in-process executor while result finalization crossed both MariaDB and object
storage.

That split produced one common reliability problem:

> the system did not have one coherent durable lifecycle for claiming work, surviving process loss,
> and keeping DB/object side effects accountable.

Three different symptoms exposed that same boundary.

## User / system impact

The issue is operationally meaningful even in the current single-replica runtime:

1. **Process interruption**
   - an accepted job could remain `PENDING` or `RUNNING` indefinitely after the process that owned
     its in-memory task disappeared;
   - the user could keep seeing a non-terminal analysis with no executor left to complete it.

2. **Duplicate delivery**
   - the same persisted job id could re-enter `RUNNING` even after `SUCCEEDED`;
   - provider/model work, object storage, and generated-file registration could be attempted again;
   - this creates duplicate cost/side-effect risk and makes the job state an unreliable execution
     ownership boundary.

3. **Cross-resource partial success**
   - Terraform object persistence could succeed before relational result registration failed;
   - the job then ended `FAILED` with no committed result reference while the external object still
     existed;
   - this creates orphaned data and makes cleanup/accounting ambiguous.

These are not three independent feature bugs. They are manifestations of one asynchronous lifecycle
whose durable state transitions and side-effect boundaries were incomplete.

## M5 before-state evidence

### Restart / stranded state

PR #78 / merge `c58b2902556d30f4e85ff84b249a8dccfd83e207`

Before:

- persisted `PENDING → PENDING` after application recreation;
- persisted `RUNNING → RUNNING`;
- no startup discovery, resume, or terminal reconciliation.

### Duplicate same-job execution

PR #79 / merge `8e4c27c5b0c179ba7187e1e92af2698a7bb4b316`

Before:

- first delivery: `PENDING → RUNNING → SUCCEEDED`;
- second delivery of the same id: `SUCCEEDED → RUNNING → SUCCEEDED`;
- provider/store orchestration attempted twice;
- generated-Terraform registration attempted twice.

### Object / DB partial success

PR #80 / merge `18503b656de090b6fc26b317908189b7090ecfc3`

Before:

- result object write succeeds;
- relational generated-file registration fails;
- job becomes `FAILED`;
- `resultFileId=null`, `resultObjectKey=null`;
- persistent object remains.

Executor rejection was separately classified as controlled because rejection already transitions the
job to observable terminal `FAILED`.

## Root mechanism

The common mechanism is a boundary mismatch rather than a single exception:

- **execution ownership** was implicit in an in-memory executor task;
- **job state transition** loaded a row and changed it without an atomic source-state claim;
- **restart semantics** had no durable reconciliation rule for work whose in-memory owner vanished;
- **result finalization** crossed object storage and relational persistence without one transaction
  spanning both resources.

The database already contained the durable job identity, so M6 first asks how far the existing
database/state machine and storage abstractions can solve the measured failures before introducing
new infrastructure.

## Alternatives considered

### Durable broker / worker architecture

Examples: RabbitMQ, Kafka, or a separate durable worker.

Why not selected now:

- M5 did not show that a second messaging system was required to prevent terminal-job re-execution;
- current canonical runtime is a single backend replica;
- adding broker delivery semantics, consumer ownership, retries, and operational surface would be a
  much larger change than the measured first problems require.

This remains a future option if later load/failure evidence proves the in-process handoff itself must
be durably queued rather than safely failed/retried by creating a new job.

### Transactional Outbox

Why not selected now:

- an outbox could make DB-to-message publication durable, but M5's immediate gaps were atomic job
  claiming, restart closure, and DB/object partial success;
- it would not by itself make object storage and the relational result record one atomic transaction.

### Distributed lock

Why not selected now:

- the existing MariaDB row can express execution ownership with a conditional update;
- a new lock service would duplicate a constraint the authoritative job state can enforce directly.

### Chosen minimal direction

Use the existing durable boundaries:

1. atomic DB compare-and-set claim for execution ownership;
2. explicit restart reconciliation for the current single-replica process model;
3. provider-neutral compensation for result objects when relational success finalization fails.

The purpose is not to claim "exactly once" across all future distributed deployments. The purpose is
to make the current runtime's state machine truthful and its failure outcomes explicit.

## M6 implementation evidence

### M6-1 — atomic execution claim

PR #82 / merge `d36a36148353027118f0c5eb1c86543fffa00dad`

Implementation:

- conditional DB update claims only `id = :jobId AND status = PENDING`;
- only the caller that changes one row proceeds to provider/storage work;
- terminal/non-pending delivery is skipped;
- the M5 duplicate-delivery scenario was inverted to one orchestration attempt;
- the compare-and-set path is directly exercised against MariaDB in the existing repository smoke.

### M6-2 — restart reconciliation

PR #83 / merge `3385114b365859aaa8d688621f33176d411d3ab6`

Implementation:

- application startup bulk-transitions previous-process `PENDING`/`RUNNING` jobs to `FAILED`;
- already terminal jobs remain terminal;
- jobs are not automatically replayed because a previous `RUNNING` job may already have produced
  external side effects;
- behavior is explicitly scoped to the current canonical Kubernetes contract:
  `replicas: 1`, `maxSurge: 0`.

Validation gap being closed:

- the restart behavior is covered by the application-context restart test;
- the new MariaDB bulk-update query was not directly exercised in the existing MariaDB smoke in PR
  #83;
- the current gap-fix PR adds that direct MariaDB execution before M6 closure.

### M6-3 — partial-success compensation

PR #84 / merge `bb25229da188f690bc88f9abce5248911db3514f`

Implementation:

- final relational success registration is explicitly classified as a finalization boundary;
- when it fails after a persisted result-object write, the runner invokes provider-neutral removal;
- S3 and filesystem persistent writers implement removal;
- metadata-only storage remains a no-op because no object was persisted;
- the M5 partial-success scenario is inverted for successful compensation.

## Supporting observability baseline

PR #86 / merge `46b87be8284e127d8d7fa070d04fb6561a1948a5` does not add a new observability
platform. It captures the signals already emitted by the same M6-3 failure scenario:

- failed-job counter increments;
- bounded failure category remains `other`;
- analysis duration is recorded;
- logs carry `analysisJobId`;
- compensation success is visible in logs;
- configured `trace_id` / `span_id` fields are present but empty.

This evidence is retained because it deepens the same reliability case and shows exactly what an
operator can and cannot infer today. It does not by itself justify advancing to M7 or installing a
tracing backend.

## Residual risk and trade-off

M6 is not yet closed.

The current compensation path is **best effort**. If object deletion itself fails:

- the cleanup failure is logged;
- the analysis job is still marked `FAILED`;
- the object can remain;
- there is currently no durable cleanup ledger/reference that guarantees a later retry.

Therefore the strongest possible claim is not "DB/object consistency is guaranteed." The current
claim is:

> the common pre-commit finalization failure is compensated when the storage provider can delete the
> written object, while cleanup failure remains an explicit residual risk.

M6-4 must decide whether this limitation is acceptable for the current case or whether a small
durable residue-accountability mechanism is necessary. It must not hide this trade-off merely to
close the milestone.

## Why this is a portfolio-quality candidate

The useful case is not "the service restarted, so I changed the status to FAILED."

The case is:

1. identify that asynchronous execution ownership was split between an in-memory executor and a DB
   row;
2. reproduce three failure manifestations at process, state-transition, and cross-resource
   boundaries;
3. reject larger conventional infrastructure because evidence did not require it;
4. strengthen the existing state machine with an atomic claim;
5. define a conservative restart policy rather than unsafe replay;
6. add compensation at the non-transactional object-storage boundary;
7. preserve the unresolved cleanup-failure trade-off instead of claiming exactly-once semantics.

This supports discussion of transaction boundaries, idempotency, failure recovery, object/DB
consistency, state-machine design, and architecture trade-offs from one connected problem.

## Case-depth gate before moving on

Do not move to a new unrelated backend feature merely because M6-1 through M6-3 are implemented.

Before M6 closure, require:

- direct MariaDB validation of restart reconciliation;
- an integrated before/after table for all three M5 failure scenarios;
- an explicit decision on compensation-delete failure;
- source-of-truth synchronization;
- a concise explanation of which larger alternatives were rejected and why.

M7/M8 should preferably deepen this same case by adding failure-correlated telemetry and bounded
failure/load evidence, rather than creating unrelated observability/load demos.
