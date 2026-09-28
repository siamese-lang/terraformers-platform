# Case B B1 Durable State and Fencing Evidence

## Status

**B1 COMPLETE — B2 NOT STARTED**

This document records the first bounded production stage under ADR-007.

- Case: **Case B — Durable Asynchronous AnalysisJob Processing**
- Stage: **B1 — Durable processing schema and fenced state machine**
- ADR: [ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md)
- Implementation plan:
  [Case B Durable Processing — Bounded Implementation Plan](../plans/active/case-b-durable-processing-implementation.md)
- PR #97 head: `20a53602e0419da6cc5779bf6cfdf829c73107af`
- Merge commit: `237c97f35184312cac3af56aee438da830f4ff8a`
- Backend Local Verification run `36434929346`: **SUCCESS**
  - Backend local smoke baseline: **SUCCESS**
  - MariaDB schema and repository validation: **SUCCESS**
- Terraform Static Verification run `36434929303`: **SUCCESS**

B1 adds durable state representation and database-enforced ownership primitives only. It does not
change the current runtime delivery/restart behavior.

## 1. Persistent state added

Flyway migration `V20260928_006__add_analysis_job_durable_processing_state.sql` extends
`analysis_jobs` with:

- `attempt_count INT NOT NULL DEFAULT 0`;
- `next_attempt_at TIMESTAMP(6) NULL`;
- `lease_expires_at TIMESTAMP(6) NULL`;
- `claim_generation BIGINT NOT NULL DEFAULT 0`;
- `result_object_intent_bucket VARCHAR(255) NULL`;
- `result_object_intent_key VARCHAR(1024) NULL`;
- `result_cleanup_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED'`.

The cleanup status is represented by:

- `NOT_REQUIRED`;
- `PENDING`;
- `COMPLETED`.

Existing rows remain compatible through zero/null/default values.

## 2. Durable ownership semantics

### Initial claim

An eligible PENDING job can be claimed only when:

- `next_attempt_at IS NULL OR next_attempt_at <= now`; and
- requested `lease_expires_at > now`.

Successful claim atomically:

- changes status to RUNNING;
- increments `attempt_count`;
- increments `claim_generation`;
- sets the requested lease expiry;
- clears `next_attempt_at`.

Invalid or duplicate claims mutate no durable counters/state.

### Expired-lease reclaim

A RUNNING job is reclaimable only when its lease is present and expired.

Successful reclaim increments both:

- `attempt_count`;
- `claim_generation`.

The new claim generation fences the previous owner.

### Active ownership

An active lease cannot be stolen.

Lease renewal requires:

- RUNNING status;
- matching claim generation;
- current lease still active;
- new expiry strictly later than the current lease.

Renewal to the same, earlier, or already-expired time is rejected at the database transition.

## 3. Fencing semantics

`claim_generation` is the durable fencing generation.

Measured repository behavior now supports the invariant:

> after generation N+1 reclaims an expired job, ownership-sensitive mutations from generation N are
> rejected by conditional database state transitions.

B1 verifies stale-generation rejection for result-object intent and retry-state mutation.

The repository also retains a pessimistic `lockOwned(...)` primitive. Its contract is explicitly
transaction-local: it may only protect a mutation performed in the same transaction. B1 does not
expose the lock result as a reusable boolean authorization check.

Full result finalization remains a B4 concern.

## 4. Future retry-state representation

B1 can represent a retry without executing one.

An owned RUNNING job may be transitioned back to PENDING only when:

- the current generation still owns an unexpired lease; and
- `next_attempt_at > now`.

The transition:

- clears the lease;
- preserves attempt count and claim generation;
- records the future eligibility time.

No timer, poller, backoff policy, provider retry, or retry execution is active in B1.

## 5. Fenced terminal failure

B1 adds a generation/lease-guarded terminal failure transition.

Only the current unexpired owner can transition RUNNING to FAILED through the new primitive.

The existing production runtime is not yet wired to use this primitive; wiring belongs to later
bounded stages.

## 6. Result-object accountability state

B1 adds persistence primitives for future B4 use:

- record intended object bucket/key for the current fenced owner;
- set cleanup accountability to PENDING;
- resolve the exact recorded object to COMPLETED.

No object write/delete path is changed in B1.

The existing successful-result `result_object_key` contract is not overloaded.

## 7. Real MariaDB concurrency evidence

The MariaDB repository smoke test now validates both durable ownership cases with two independent
transactions and bounded synchronization.

### Initial claim

Starting state:

`PENDING / attempt=0 / generation=0`

Two concurrent claimers produce exactly one winner.

Final durable state:

`RUNNING / attempt=1 / generation=1`

### Expired reclaim

Starting state:

`RUNNING / attempt=1 / generation=1 / expired lease`

Two concurrent reclaimers produce exactly one winner.

Final durable state:

`RUNNING / attempt=2 / generation=2`

This preserves the single-current-owner invariant on the actual MariaDB validation path rather than
relying only on H2 behavior.

## 8. Runtime behavior intentionally unchanged

B1 does **not** introduce:

- MariaDB polling;
- a scheduled dispatcher;
- automatic restart reclaim;
- provider retry;
- retry backoff;
- runtime lease TTL configuration;
- RabbitMQ;
- Transactional Outbox;
- DLQ;
- result-finalization rewrite;
- object cleanup execution;
- frontend changes;
- Kubernetes/Terraform runtime changes.

The existing after-commit in-memory executor and restart-to-FAILED reconciler remain active.

Therefore the PR #94 accepted-but-not-started and claimed-RUNNING restart before-state is still
expected to fail terminally after restart at this checkpoint.

That is not a B1 regression. B2 is the bounded stage that changes durable delivery and restart
recovery.

## 9. B1 acceptance result

| B1 gate | Result |
| --- | --- |
| additive Flyway migration | **PASS** |
| existing-row compatibility | **PASS** |
| deterministic attempt/generation semantics | **PASS** |
| valid initial lease claim | **PASS** |
| invalid/past lease rejection | **PASS** |
| expired lease reclaim | **PASS** |
| active lease cannot be stolen | **PASS** |
| strict lease extension | **PASS** |
| future retry-state representation | **PASS** |
| stale-generation fenced mutation | **PASS** |
| concurrent initial claim on MariaDB | **PASS** |
| concurrent reclaim on MariaDB | **PASS** |
| result-object accountability representation | **PASS** |
| current runtime behavior preserved | **PASS** |
| backend + MariaDB regression | **PASS** |

B1 is therefore complete.

## 10. Residual work

The remaining ADR-007 stages are intentionally separate:

- **B2:** durable dispatcher and restart recovery;
- **B3:** selective bounded retry;
- **B4:** deterministic result identity, fenced finalization, and cleanup execution/accountability;
- **B5:** integrated Case B before/after closure experiment.

## 11. Immediate next task

Prepare the bounded **B2 implementation specification**.

B2 must make MariaDB durable eligibility the execution source and convert process-loss ownership into
lease-based reclaim while keeping provider retry disabled.

Do not begin B3 retry behavior as part of B2.
