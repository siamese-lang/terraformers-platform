# Case B Durable Processing — Bounded Implementation Plan

## Status

**ACTIVE PLAN — B1/B2 COMPLETE, B3 SPECIFICATION NEXT**

This plan implements
[ADR-007](../../architecture/decisions/ADR-007-durable-analysis-job-processing.md)
in bounded stages.

No stage authorizes the next stage automatically.

Each production stage requires:

1. current-main verification;
2. bounded Codex implementation specification;
3. implementation and tests;
4. independent ChatGPT diff/evidence review;
5. explicit user merge approval;
6. source-of-truth update before progressing where the stage changes the accepted contract.

The final target is not “use MariaDB as RabbitMQ.” It is:

> persist accepted work durably in MariaDB, acquire work with a time-bounded fenced lease, retry only
> approved transient failures, keep result finalization idempotent, and retain durable accountability
> for cross-resource residue.

## Non-goals for all stages

Unless a later evidence gate explicitly reopens them, do not add:

- RabbitMQ;
- Transactional Outbox;
- Kafka;
- Redis;
- a second worker service;
- generic distributed locks;
- generic workflow/orchestration framework;
- OpenTelemetry/Grafana solely for this case;
- WebSocket/SSE for result display;
- changes to Case A AI quality policy;
- Case C scaling/HPA changes.

Keep the current REST result retrieval and frontend polling model.

---

# B1 — Durable processing schema and fenced state machine

## Status

**COMPLETE**

Implementation evidence:
[Case B B1 Durable State and Fencing Evidence](../../evaluation/case-b-b1-durable-state-fencing.md)

PR #97 merged as `237c97f35184312cac3af56aee438da830f4ff8a`.
The additive durable-state migration, lease/fencing repository primitives, strict temporal
invariants, stale-generation rejection, and real MariaDB initial/reclaim contention checks passed.

Runtime dispatch/restart behavior remains intentionally unchanged until B2.

## Goal

Add only the persistent state and repository transitions required to represent durable eligibility,
lease ownership, fencing, bounded attempts, and cleanup accountability.

Do **not** add a poller, automatic retry, or change runtime scheduling yet.

## Expected persistent contract

Names may be refined during implementation, but the schema must represent:

- `attempt_count`;
- `next_attempt_at`;
- `lease_expires_at`;
- claim/fencing token or monotonically increasing generation;
- intended/result object bucket/key or equivalent durable object reference before/around write;
- cleanup state sufficient to distinguish no cleanup required / cleanup pending / cleanup completed
  or failed in a recoverable way.

Use additive Flyway migration(s). Existing applied migrations remain immutable.

## Repository/state transitions

Add transactionally guarded primitives for at least:

- claim eligible job;
- verify current lease/fencing ownership;
- renew/expire/reclaim as required by the chosen bounded contract;
- fenced success;
- fenced retry scheduling;
- fenced terminal failure;
- cleanup accountability transitions.

Existing terminal jobs must not become claimable.

## Required tests

- MariaDB concurrent claim still has one owner;
- stale fencing token cannot finalize;
- lease expiry permits reclaim by a newer fencing generation;
- terminal job cannot be reclaimed;
- schema validation and existing backend regressions remain green.

## Stop boundary

B1 ends with **state representation and transition correctness only**.

Do not add polling, restart recovery, or retry execution in B1.

---

# B2 — Durable dispatcher and restart recovery

## Status

**COMPLETE**

Implementation evidence:
[Case B B2 Durable Dispatcher and Restart Recovery Evidence](../../evaluation/case-b-b2-durable-dispatch-recovery.md)

PR #99 head `b7ef9ec77d74d936a28068d64386d2331aa05b46` merged as
`0f18e437af3aaf3c16c3ed075e8963c32cdab240`. Periodic MariaDB eligibility discovery now recovers
lost immediate handoffs and expired ownership, while the bounded executor remains a local
concurrency mechanism. Terraform Static Verification run `36445520873` and Backend Local
Verification run `36445520887` succeeded. Provider failure remains single-attempt and terminal for
the current valid owner; B2 does not enable retry, which remains the B3 boundary.

## Goal

Replace “afterCommit memory handoff is the only path to execution” with MariaDB-backed durable
eligibility.

The existing executor remains the bounded local execution pool.

## Required behavior

- API commit creates durable eligible work;
- a dispatcher/poller finds eligible jobs;
- durable eligibility discovery precedes bounded executor submission;
- the submitted runnable must begin and successfully acquire a fenced lease claim before provider
  execution;
- process restart does not mark recoverable PENDING/RUNNING ownership loss terminally FAILED;
- expired ownership is reclaimable;
- executor rejection does not lose the durable job;
- polling/claim loop is bounded and has explicit lifecycle/shutdown behavior.

The accepted ordering is:

`durable eligibility discovery → bounded executor submission → runnable begins → fenced lease claim → provider execution`

Local executor submission does not constitute durable ownership. Rejection or process loss before
the runnable starts leaves the database job durably recoverable, and claim failure prevents provider
execution.

The old startup restart-to-`FAILED` reconciler must be removed, replaced, or narrowed so it no longer
violates ADR-007.

## Required observability

At minimum:

- eligible/claim/reclaim outcome;
- queue wait;
- lease recovery/reclaim duration;
- queue depth or eligible-job count where practical;
- executor rejection under durable semantics.

Use bounded labels only.

## Required before/after tests

Reuse PR #94 fixtures:

- accepted-but-not-started restart now recovers same job;
- claimed-RUNNING process loss becomes reclaimable after lease expiry;
- duplicate delivery remains one logical execution;
- concurrent claim remains one owner.

## Stop boundary

B2 ends when restart durability works **without retrying provider failures yet**.

Do not add transient retry policy in B2.

---

# B3 — Selective bounded retry

## Goal

Add retry only for approved transient failures.

Before implementation, freeze configuration values for:

- maximum attempts;
- retry delay/backoff shape;
- lease duration as it interacts with retry;
- polling interval where relevant.

The values must be documented as configuration, not retroactively adjusted to make tests pass.

## Required behavior

- retryable transient failure schedules a later eligible attempt;
- attempt count is durable;
- successful later attempt reaches SUCCEEDED;
- maximum attempts are bounded;
- exhaustion reaches explicit FAILED;
- non-retryable failures remain terminal immediately;
- stale worker cannot schedule retry or terminal state after losing fencing ownership.

## Initial failure classification

Retry candidates may include:

- provider/network timeout;
- explicit transient upstream failure.

Do not automatically retry:

- rejected input;
- output truncation under unchanged configuration;
- deterministic response-format/semantic failure without separate evidence;
- deterministic Terraform validation failure;
- authorization/invariant failure.

## Required tests

- PR #94 transient-first-failure fixture succeeds on bounded retry;
- retry exhaustion terminates exactly at configured bound;
- non-retryable failure makes one attempt;
- stale owner cannot mutate retry state.

## Stop boundary

B3 does not change object-storage consistency beyond what is necessary for safe job retry.

---

# B4 — Result idempotency and durable cleanup accountability

## Goal

Make result persistence safe under reclaim/retry and remove silent cross-resource residue.

## Required behavior

### Deterministic result identity

One logical AnalysisJob must resolve to one deterministic logical result-object identity across
attempts.

Do not derive retry identity from mutable attempt wall-clock time.

### Fenced finalization

Only the current lease/fencing owner may make result success/failure terminally visible.

A stale worker may have invoked an external provider or even written a draft, but it must not be
able to replace a newer finalized relational result.

### Durable object accountability

Before or at the point where object existence can become externally persistent, the DB must retain
enough information to identify the intended object.

If DB finalization fails:

- immediate compensation should still be attempted where safe;
- if compensation fails, cleanup remains durably pending/identifiable;
- retry/recovery must not silently abandon the residue.

## Required tests

- same job retried across a changed wall-clock date does not create a new logical result identity;
- stale worker cannot finalize over a newer owner;
- DB finalization failure + successful compensation leaves no residue;
- compensation failure leaves durable object reference + cleanup state;
- cleanup recovery clears the residue/accountability state;
- normal result readback and frontend-visible project result semantics do not regress.

## Stop boundary

Do not add a separate cleanup service, broker, or outbox unless this stage demonstrates the
AnalysisJob-owned recovery mechanism is insufficient.

---

# B5 — Integrated Case B closure experiment

## Goal

Validate the completed design against the same failure matrix and produce portfolio-grade
before/after evidence.

This stage is validation/closure, not a new architecture stage.

## Required deterministic matrix

1. accepted DB commit then process loss;
2. process loss before execution start;
3. process loss after claim;
4. duplicate delivery;
5. transient failure;
6. retry then success;
7. retry exhaustion;
8. object write then DB finalization failure;
9. cleanup failure and recovery;
10. concurrent claim;
11. stale-worker fencing;
12. normal success/readback regression.

## Required measurements

Record, where applicable:

- accepted job count;
- terminal job count;
- stranded job count;
- duplicate logical finalization count;
- attempt count;
- retry exhaustion;
- queue wait;
- restart-to-reclaim duration;
- retry delay;
- accepted-to-terminal duration;
- eligible/queue depth;
- cleanup pending/recovered count.

## Hard acceptance

- stranded accepted jobs = 0;
- terminal duplicate re-execution/finalization = 0;
- stale-worker successful finalization = 0;
- unbounded retry = 0;
- exhausted retry left non-terminal = 0;
- silent/unaccounted object residue = 0;
- normal result retrieval regression = 0.

## Performance interpretation

B5 records correctness and bounded recovery behavior.

Do not claim production-scale throughput from B5.

Case C later measures whether MariaDB polling, executor concurrency, database load, or external AI
latency is the first runtime capacity bottleneck.

## Closure artifact

Produce a Case B before/after evidence document that can answer:

- what failed before;
- why the process-memory handoff was insufficient;
- why MariaDB durable ownership was selected over RabbitMQ/Outbox;
- how lease/fencing/retry changed failure behavior;
- how object consistency was handled;
- what complexity and remaining limits were accepted.

---

# Stage dependency summary

`B1 durable state/fencing → B2 dispatcher/recovery → B3 bounded retry → B4 result/cleanup safety → B5 integrated closure`

No stage may be merged merely because a later stage needs it. Each must independently satisfy its
bounded correctness purpose.

## Current immediate next task

Prepare the bounded **B3 implementation specification**.

Provider retry remains disabled pending B3, and result-idempotency/cleanup execution remains
deferred to B4.
