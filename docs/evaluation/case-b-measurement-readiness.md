# Case B Measurement Readiness Evidence

## Status

**READY FOR ARCHITECTURE DECISION — PRODUCTION IMPLEMENTATION NOT YET AUTHORIZED**

This document records the Case B measurement-readiness checkpoint after PR #94.

It is evidence for the next Case Decision Gate. It does not select or authorize a durable-processing
architecture.

- Representative case: **Case B — Durable Asynchronous AnalysisJob Processing**
- Base before measurement enablement: `f349128736ca1155b2d7bc5bbb9bc6cce1f1db22`
- PR #94 head: `b7540aa4dcbdc8b855494a884100804999acd7aa`
- Merge commit: `49866a24b88d5e91054981a218ab22a59b42622c`
- Backend Local Verification: run `36427488357` — **SUCCESS**
- Terraform Static Verification: run `36427488283` — **SUCCESS**; Terraform implementation scope was skipped by scope gating.

## 1. Operating contract

For an accepted analysis request, application-process restart by itself must not require a new user
submission and must not be sufficient reason to mark the request terminally failed.

The final design must provide bounded durable semantics for reclaim/resume/retry or an explicitly
justified terminal failure.

PR #94 intentionally does **not** implement this target. It improves the ability to measure the
current before-state.

## 2. Measurement-enablement added by PR #94

PR #94 added only bounded measurement/test support:

- claim outcome metric:
  `terraformers.analysis.claims{outcome=claimed|not_claimed}`;
- successful-claim queue-wait timer:
  `terraformers.analysis.queue.wait`;
- `analysisJobId` MDC correlation before claim so claimed and not-claimed delivery paths are
  attributable to the logical job;
- deterministic accepted-but-not-started restart evidence;
- deterministic claimed-RUNNING restart evidence;
- deterministic transient-failure/no-retry evidence;
- deterministic compensation-cleanup-failure evidence;
- real MariaDB concurrent-claim validation using independent transactions and bounded waits.

No schema change, durable queue, broker, retry loop, lease, DLQ, Outbox, or new cloud/runtime
architecture was introduced.

## 3. Failure-matrix evidence

| # | Scenario | Current evidence after PR #94 | Classification for decision |
| --- | --- | --- | --- |
| 1 | request DB commit then process loss | real `AnalysisJobService.create()` commits a PENDING job while a capturing executor prevents execution; restart reconciliation marks the accepted job FAILED | **MEASURED GAP** — accepted work is durable in DB but delivery/recovery is not |
| 2 | crash before dispatch/start | same accepted-but-not-started fixture proves committed work can exist without execution and is failed on restart | **MEASURED GAP** |
| 3 | crash after claim | real `claimPending()` transitions PENDING→RUNNING; application recreation then reconciles it to FAILED | **MEASURED GAP** — ownership loss is not reclaimed |
| 4 | duplicate delivery | duplicate runner invocation produces one claimed and one not-claimed outcome; provider/storage/finalization side effects execute once | **CONTROLLED PRIMITIVE** — atomic claim is reusable |
| 5 | transient downstream failure | deterministic first invocation timeout would succeed on a second call, but current runner invokes only once and marks the job FAILED | **MEASURED GAP / CURRENT NO-RETRY SEMANTICS** |
| 6 | retry then success | current architecture has no retry mechanism | **FINAL-DESIGN VALIDATION REQUIRED**; not a missing current-baseline fact |
| 7 | retry exhaustion | current architecture has no retry mechanism | **FINAL-DESIGN VALIDATION REQUIRED** |
| 8 | object write then relational finalization failure | existing compensation removes the written object when failure is proven inside the rollback-safe finalization body | **CONTROLLED PRIMITIVE** |
| 9 | cleanup/compensation failure | forced remover failure leaves the object residue while the job is FAILED and relational result references remain null; stage failure is observable but no durable residue-accountability record exists | **MEASURED GAP** |
| 10 | concurrent workers claim same job | MariaDB 11.4 test starts two independent transactions against one PENDING row; results are exactly one `1` and one `0`, final state RUNNING | **CONTROLLED PRIMITIVE** — conditional claim is reusable |

## 4. Current observability coverage

The repository can now distinguish or measure:

- logical claim success versus no-claim/duplicate delivery;
- accepted/created timestamp to successful claim queue wait;
- analysis execution duration;
- result-finalization duration/failure;
- compensation duration/success/failure;
- job success/failure;
- executor rejection;
- bounded failure categories;
- job-correlated logs across claim, start, skip, execution, finalization, and compensation paths.

Important remaining timing/capacity measurements are architecture-dependent:

- durable enqueue/publication latency;
- retry attempt count;
- retry delay;
- restart-to-reclaim/recovery duration;
- durable queue depth;
- active durable consumers;
- accepted-to-terminal duration under the selected recovery semantics.

These should be implemented only after the Case Decision Gate identifies the delivery model and the
minimum state needed to measure it.

## 5. Measurement Readiness Gate

| Gate | Result | Evidence |
| --- | --- | --- |
| operating scenario fixed | **YES** | Case B operating contract is explicit |
| failure/limitation reproducible | **YES** | restart windows, no-retry behavior, cleanup failure, duplicate delivery and concurrent claim have deterministic fixtures |
| user/system impact explicit | **YES** | accepted-work loss semantics, duplicate cost, residue, manual resubmission/cleanup are identified |
| before-state measurable | **YES for architecture decision** | correctness boundaries and claim/queue-wait/stage evidence are available |
| metric can judge proposed improvement | **YES for decision** | current failure invariants plus claim/queue/stage signals exist; selected architecture may add mechanism-specific retry/recovery metrics |
| minimum missing measurements/tests added | **YES** | PR #94 closed the identified pre-decision gaps without preselecting an architecture |
| acceptance thresholds frozen before implementation | **NOT YET APPLICABLE TO IMPLEMENTATION** | hard correctness gates are fixed; numeric queue/recovery/throughput thresholds require the selected design's comparable before experiment |
| at least two realistic alternatives | **YES** | current executor containment, MariaDB durable queue, RabbitMQ, Outbox + RabbitMQ |

Therefore Case B is **ready to enter the Architecture Decision Gate**, but production implementation
remains gated.

## 6. Decision inputs that must be preserved

The next decision must compare at least:

1. current in-process executor + reconciliation;
2. MariaDB-backed durable queue / claim / lease / bounded retry;
3. RabbitMQ-backed durable delivery;
4. Transactional Outbox + RabbitMQ where DB-commit/publication atomicity is required.

The comparison must explicitly account for the measured facts:

- DB persistence currently outlives in-memory delivery;
- process ownership loss currently becomes FAILED rather than reclaimed;
- duplicate terminal execution is already controlled by atomic claim;
- real MariaDB concurrent claim admits exactly one winner;
- no retry currently exists;
- rollback-safe object compensation works;
- cleanup failure can leave an object residue with no durable accountability record.

No candidate is preferred merely for portfolio technology breadth.

## 7. Exit from this checkpoint

This measurement-readiness checkpoint is complete when this evidence is merged as repository source
of truth.

The next single task is:

> **Case B Architecture Decision Gate** — compare the four durable-processing alternatives against
> the measured current behavior and operating contract, select a proposed direction, define the
> minimum before/after acceptance experiment, and present the decision to the user before any
> production implementation.
