# M6 Backend Reliability Improvement Closure

## Status

**REOPENED — IMPLEMENTATION EVIDENCE RETAINED, FINAL CLOSURE WITHDRAWN**

M6 applies only the three reliability controls justified by M5 and reuses the same M5 scenarios as
before/after evidence.

This document was originally written as final closure in PR #85. That closure decision is withdrawn:
the implementation evidence remains valid, but M6 must pass an integrated case-depth review before
the project advances. The reopened case is tracked in
[`backend-reliability-analysis-job-lifecycle-case.md`](backend-reliability-analysis-job-lifecycle-case.md).

## Before state

M5 confirmed:

| Scenario | M5 behavior |
| --- | --- |
| Process restart with persisted `PENDING` / `RUNNING` | both remain stranded and non-terminal |
| Duplicate delivery of one job id after success | `SUCCEEDED → RUNNING → SUCCEEDED`; provider/storage and generated-file registration run twice |
| Result object write followed by relational finalization failure | job becomes `FAILED`, DB result references stay null, persisted object remains |
| Executor rejection | already controlled: rejection is observed and job becomes `FAILED` |

M5 evidence:
[`m5-backend-reliability-baseline.md`](m5-backend-reliability-baseline.md).

## M6-1 — Atomic pending-job claim

PR #82 merged as:

`d36a36148353027118f0c5eb1c86543fffa00dad`

Validation workflow:

- Backend Local Verification run `36399415255` — **SUCCESS**
- H2/local backend job — **SUCCESS**
- MariaDB schema/repository validation — **SUCCESS**

Change:

- repository compare-and-set updates only `PENDING → RUNNING`;
- exactly one claim can update a given pending row;
- existing non-`PENDING` rows produce an empty claim;
- `AnalysisJobRunner` returns without provider/storage work when no claim is won.

Same M5-2 scenario after the change:

- first delivery executes normally;
- second delivery of the same `SUCCEEDED` job id is skipped;
- provider/store orchestration: **2 → 1** calls;
- generated Terraform registration: **2 → 1** calls;
- final state remains `SUCCEEDED`.

This closes the terminal-state re-entry gap without an in-memory lock, distributed lock, queue, or
new service.

## M6-2 — Single-replica restart reconciliation

PR #83 merged as:

`3385114b365859aaa8d688621f33176d411d3ab6`

Validation workflow:

- Backend Local Verification run `36399836413` — **SUCCESS**
- H2/local backend job — **SUCCESS**
- MariaDB schema/repository validation — **SUCCESS**

Change:

- an `ApplicationRunner` executes before runtime readiness;
- persisted `PENDING` and `RUNNING` left by the previous process are moved to `FAILED`;
- a dedicated safe failure reason asks the user to start a new analysis;
- already-terminal `SUCCEEDED` and `FAILED` rows remain unchanged;
- old jobs are **not** replayed automatically.

Same M5-1 restart scenario after the change:

- `PENDING → FAILED`;
- `RUNNING → FAILED`;
- existing `SUCCEEDED → SUCCEEDED`;
- existing `FAILED → FAILED`.

### Runtime boundary

The canonical backend Deployment currently has:

- `replicas: 1`;
- `maxUnavailable: 1`;
- `maxSurge: 0`.

Therefore this reconciliation closes the measured current-runtime gap. It is not claimed as a
general multi-replica recovery algorithm. A future replica/topology change requires a new evidence
gate.

## M6-3 — Rollback-safe result-object compensation

PR #84 merged as:

`bb25229da188f690bc88f9abce5248911db3514f`

Validation workflow:

- Backend Local Verification run `36400693033` — **SUCCESS**
- H2/local backend job — **SUCCESS**
- MariaDB schema/repository validation — **SUCCESS**

Capability inspection found that all current object-writer modes can support the required
provider-neutral compensation boundary:

- filesystem — delete object bytes and sidecar metadata;
- S3 — `DeleteObject`;
- metadata-only — explicit no-op because no bytes were persisted.

Implementation:

- adds provider-neutral `ObjectRemover`;
- classifies failures raised **inside** the relational success-finalization transaction body as
  `AnalysisResultFinalizationException`;
- flushes the relational state inside that body so ordinary SQL/finalization failures surface
  before transaction return;
- only this rollback-safe failure class compensates the immediately preceding result-object write;
- the original failure still terminates the job as `FAILED`.

Same M5-3 scenario after the change:

1. result-object write succeeds;
2. generated-file relational registration is forced to fail;
3. relational success transaction rolls back;
4. the just-written object is removed;
5. job becomes `FAILED`;
6. `resultFileId` and `resultObjectKey` remain null.

### Compensation boundary

M6 deliberately does **not** delete objects for every exception escaping transaction commit. A
commit-phase error can have an ambiguous outcome; deleting in that case could remove an object that
a committed DB row references. Only failures proven inside the rollback-safe finalization body are
compensated.

That unmeasured commit-ambiguity case is not the M5 failure that M6 was tasked to fix and is not
silently claimed as solved.

## Before / after closure

| Scenario | M5 before | M6 after | Result |
| --- | --- | --- | --- |
| Restarted `PENDING` / `RUNNING` | indefinitely non-terminal | terminal `FAILED` before readiness | **CONTROLLED** |
| Duplicate terminal job delivery | re-executes side effects | second claim fails; no second execution | **CONTROLLED** |
| Object write then pre-commit DB finalization failure | persistent untracked residue | written object compensated | **CONTROLLED** |
| Executor rejection | terminal `FAILED` | unchanged | **NO REGRESSION** |

## Architecture decision result

M5/M6 evidence did **not** justify:

- RabbitMQ;
- Transactional Outbox;
- Kafka;
- Redis;
- distributed locks;
- generic retry loops;
- a second worker service.

The measured current-runtime problems were controlled with database state transitions, startup
reconciliation, and a provider-neutral storage compensation port.

## Previous exit decision — superseded

PR #85 recorded the following criteria as met, but that final decision is **superseded** pending the integrated case review:

1. only one caller can claim a `PENDING` job;
2. terminal jobs do not re-enter execution;
3. current single-replica restart does not leave M5 non-terminal states indefinitely stranded;
4. the measured object/DB partial-success residue is compensated;
5. all three M5 failure scenarios were directly inverted rather than replaced by unrelated tests;
6. normal Backend Local and MariaDB regression validation remain green;
7. executor rejection remains controlled;
8. no broker/outbox/distributed system was added.

Do not treat this historical closure statement as permission to advance. M6 remains active until the MariaDB restart-query gap is directly closed and the compensation-delete failure trade-off is explicitly decided.
