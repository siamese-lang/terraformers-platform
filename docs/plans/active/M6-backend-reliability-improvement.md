# M6 — Backend Reliability Improvement

## Status

**ACTIVE — STATE TRANSITION / RECOVERY FIRST**

M5 measured the current `AnalysisJob` lifecycle and confirmed three reliability gaps. M6 changes
only those evidenced behaviors and reuses the exact M5 scenarios for validation.

## M5 evidence gate

Confirmed gaps:

1. **Restart / stranded state**
   - PR #78, merge `c58b2902556d30f4e85ff84b249a8dccfd83e207`
   - persisted `PENDING` and `RUNNING` remain unchanged after a fresh application context starts
     against the same database.
2. **Duplicate same-job delivery**
   - PR #79, merge `8e4c27c5b0c179ba7187e1e92af2698a7bb4b316`
   - the same job id can transition `SUCCEEDED → RUNNING → SUCCEEDED` and execute provider/storage
     and generated-file registration twice.
3. **Object write / DB finalization partial success**
   - PR #80, merge `18503b656de090b6fc26b317908189b7090ecfc3`
   - a persisted result object can remain after generated-file registration fails; the job becomes
     `FAILED` with no committed `resultFileId` or `resultObjectKey`.

Controlled behavior:

- executor `RejectedExecutionException` is already caught and the job is marked `FAILED`; no M6
  change is justified for this path unless later evidence contradicts it.

## Current runtime boundary

The canonical backend Kubernetes Deployment currently has:

- `replicas: 1`;
- rolling update `maxUnavailable: 1`;
- rolling update `maxSurge: 0`.

M6 restart reconciliation may therefore target the **current single-replica runtime contract**. It
must not be described as a general multi-replica recovery design. If the deployment model later
changes, that requires a new evidence gate.

## Improvement invariants

M6 must establish these minimal invariants:

1. **Atomic claim** — only a persisted `PENDING` job can enter `RUNNING`, and exactly one caller
   can win that claim.
2. **Terminal immutability** — `SUCCEEDED` and `FAILED` jobs cannot re-enter execution.
3. **Restart closure** — after the current single-replica process restarts, persisted non-terminal
   jobs from the previous process are not left indefinitely stranded.
4. **Partial-success accountability** — if result object persistence succeeds but relational
   finalization fails, the persistent residue is either removed or durably recorded for deterministic
   cleanup; it must not remain untracked.
5. Existing successful single-execution behavior and controlled executor rejection must not regress.

## Non-goals

M6 does **not** introduce these unless a later M6 subtask proves the minimal changes below cannot
satisfy the measured invariants:

- RabbitMQ;
- Transactional Outbox;
- Kafka;
- Redis;
- distributed lock service;
- generic retry loop;
- a second worker service;
- multi-replica exactly-once claims;
- new GitHub Actions workflow.

No GCP live runtime activation is expected for M6 unless local/MariaDB evidence proves insufficient.

## M6-1 — Atomic PENDING claim and terminal-state guard

**Status: TODO**

Replace the current load-and-unconditionally-set-`RUNNING` transition with an atomic database claim
whose success condition is:

`id = :jobId AND status = PENDING`

Required behavior:

- one caller changes `PENDING → RUNNING`;
- a duplicate caller receives a non-claimed result and does not invoke provider/storage work;
- `SUCCEEDED` and `FAILED` jobs remain terminal;
- concurrent duplicate claim is deterministic at the DB transition, not protected only by an
  in-memory lock;
- existing success/failure metrics remain coherent.

Prefer one repository-owned compare-and-set update or equivalent DB transition over a new lock
technology.

Validation:

- invert the M5-2 baseline so duplicate delivery executes orchestration exactly once;
- add/retain a bounded concurrent-claim test only if needed to prove the compare-and-set contract;
- full Backend Local + MariaDB verification.

## M6-2 — Single-replica restart reconciliation

**Status: TODO**

On application startup, classify persisted `PENDING` and `RUNNING` jobs left by the prior process
as interrupted and move them to a safe terminal `FAILED` state.

Why failure rather than automatic resume:

- M5 proves the in-memory execution handoff is not durable;
- a `RUNNING` job may already have invoked provider/object side effects before the process died;
- the current system has no evidence that replaying such a job is safe;
- manual/new analysis retry already creates a new job identity.

Requirements:

- reconciliation is explicit and repository-owned;
- use a dedicated safe failure reason that does not expose internal details;
- apply only to startup of the current single-replica runtime contract;
- do not resubmit old jobs automatically;
- do not change already-terminal jobs;
- publish/record a bounded reconciliation count if current observability can do so without creating
  a new telemetry subsystem.

Validation:

- invert M5-1: after restart, prior `PENDING` and `RUNNING` are terminal `FAILED`;
- newly created jobs after startup still execute normally;
- no new workflow or live environment.

## M6-3 — Partial-success residue control

**Status: TODO — DECISION AFTER CAPABILITY INSPECTION**

M5-3 proves that object persistence can succeed before relational finalization fails.

Before implementation, inspect current object-provider capabilities and choose the **smallest
provider-neutral control** that satisfies invariant 4. Eligible solution families are limited to:

- compensating removal of the just-written result object when DB finalization fails; or
- durable recording of the persisted residue/reference so cleanup is deterministic.

Decision criteria:

- must work for the repository's real persistent object implementations, not only a test stub;
- must preserve the existing result-object key contract;
- must not require a broker/outbox solely to coordinate one object and one DB transaction;
- must have deterministic M5-3 revalidation.

If a portable compensation operation can be added cleanly to existing storage boundaries, prefer
that over a new persistence subsystem. If it cannot, document why and choose the smallest durable
residue ledger.

## M6-4 — Same-scenario before/after closure

**Status: TODO**

Re-run the M5 evidence scenarios after M6 changes.

Expected closure table:

| Scenario | M5 before | M6 required after |
| --- | --- | --- |
| Restarted `PENDING`/`RUNNING` | stranded non-terminal | terminal/reconciled |
| Duplicate delivery after success | re-executes | no second claim/execution |
| Object write then DB failure | untracked persisted residue | removed or durably recorded |
| Executor rejection | controlled `FAILED` | unchanged controlled behavior |

## Exit condition

M6 is complete when:

1. only one caller can claim a `PENDING` job for execution;
2. terminal jobs cannot re-enter execution;
3. current single-replica restart leaves no indefinitely stranded M5 non-terminal jobs;
4. M5 partial-success residue is removed or durably accountable;
5. the same M5 scenarios demonstrate the change rather than new unrelated tests;
6. normal successful job behavior and executor rejection do not regress;
7. no broker/outbox/distributed system is added without new evidence.

## Immediate next single task

Implement M6-1 as the smallest atomic repository/state-service claim and invert the existing M5-2
duplicate-delivery baseline. Do not begin restart reconciliation or partial-success cleanup in the
same code change.
