# Case B B2 Durable Dispatcher and Restart Recovery Evidence

## Status and identity

**B2 COMPLETE — B3 SPECIFICATION NEXT**

- Case: **Case B — Durable Asynchronous AnalysisJob Processing**
- Stage: **B2 — Durable dispatcher and restart recovery**
- ADR: [ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md)
- Implementation plan:
  [Case B Durable Processing — Bounded Implementation Plan](../plans/active/case-b-durable-processing-implementation.md)
- B1 evidence:
  [Case B B1 Durable State and Fencing Evidence](case-b-b1-durable-state-fencing.md)
- PR #99 head: `b7ef9ec77d74d936a28068d64386d2331aa05b46`
- Merge commit: `0f18e437af3aaf3c16c3ed075e8963c32cdab240`

## 1. Accepted runtime flow

```text
API transaction commits durable PENDING AnalysisJob
→ optional after-commit dispatcher nudge
→ periodic MariaDB eligibility scan remains recovery source
→ bounded local executor submission
→ runnable starts
→ claimEligible()
→ RUNNING + attempt/generation + lease
→ heartbeat renews same ownership generation
→ provider/result execution
→ generation-fenced terminal finalization
```

The after-commit submission is a latency optimization, not the durable guarantee. Periodic database
scanning recovers accepted work when that immediate handoff is lost. A claim occurs only after the
executor runnable actually starts, and provider execution cannot occur without a successful durable
claim.

## 2. Dispatcher behavior

B2 adds:

- periodic discovery of durably eligible jobs;
- bounded batch scanning;
- local duplicate-submission suppression;
- reuse of the existing bounded executor;
- executor-rejection handling that preserves durable eligibility;
- explicit scheduler lifecycle and configuration.

The frozen initial B2 operational configuration is:

| Setting | Initial value |
| --- | --- |
| dispatch poll interval | `2s` |
| dispatch batch size | `4` |
| lease duration | `60s` |
| lease renewal interval | `20s` |

These values are initial operational configuration, **not SLOs**.

## 3. Restart and recovery behavior

- Accepted PENDING work survives loss of the immediate in-memory handoff.
- Restart itself does not convert recoverable work to FAILED.
- A RUNNING job with an active lease cannot be stolen.
- A RUNNING job with an expired lease is reclaimable.
- Reclaim increments both attempt count and claim generation.
- Legacy `RUNNING + NULL lease` is recoverable through the dispatcher/runner compatibility path.

MariaDB remains the durable work and ownership source. The executor is only the bounded local
concurrency mechanism.

## 4. Heartbeat and fencing

- Heartbeat renews the lease under the same ownership generation.
- Heartbeat increments neither attempt count nor claim generation.
- Stale or lost ownership prevents terminal finalization.
- Failure finalization is generation/lease fenced.
- Successful relational finalization uses the current owned transaction.

## 5. Failure semantics

B2 intentionally does **not** retry a provider failure. Such a failure:

- is attempted once;
- becomes terminally FAILED only through the current valid owner;
- does not schedule `next_attempt_at`;
- does not invoke the B3 retry policy.

## 6. Validation evidence

The final accepted PR-head CI was:

- Terraform Static Verification, run `36445520873`: **SUCCESS**
- Backend Local Verification, run `36445520887`: **SUCCESS**
  - Backend local smoke baseline: **SUCCESS**
  - MariaDB schema and repository validation: **SUCCESS**

The MariaDB validation reached:

```text
starting production profile with Flyway and ddl-auto=validate
...
canonical repository smoke queries passed
```

The substantive B2 tests and evidence covered:

- recovery after restart of an accepted-but-not-started job;
- refusal to steal a claimed RUNNING job before lease expiry;
- reclaim of the same job after expiry with a newer generation;
- runtime recovery of legacy `RUNNING + NULL lease`;
- application startup without terminally failing recoverable jobs;
- suppression of local duplicate submissions;
- preservation of durable work after executor rejection;
- persisted lease advancement by heartbeat without attempt/generation changes;
- rejection of relational success metadata from a stale generation;
- publication of FAILED transition/progress only by the current owner;
- one attempt and no retry after transient provider failure;
- continued green B1 MariaDB single-owner initial-claim/reclaim behavior.

## 7. B2 acceptance result

| B2 gate | Result |
| --- | --- |
| durable accepted work survives lost immediate handoff | **PASS** |
| periodic durable scan | **PASS** |
| claim occurs only from started runnable | **PASS** |
| executor rejection does not lose durable work | **PASS** |
| active lease cannot be stolen | **PASS** |
| heartbeat lease extension | **PASS** |
| heartbeat does not alter attempt/generation | **PASS** |
| process-loss expired lease reclaim | **PASS** |
| reclaim generation increments | **PASS** |
| legacy NULL-lease recovery | **PASS** |
| restart does not force FAILED | **PASS** |
| stale generation terminal mutation rejected | **PASS** |
| owned FAILED progress semantics preserved | **PASS** |
| provider retry remains disabled | **PASS** |
| backend regression | **PASS** |
| MariaDB regression/concurrency | **PASS** |

## 8. Explicit residual work

The following work remains intentionally outside B2 and is not a B2 defect.

### B3

- selective retry classification;
- maximum attempts;
- retry delay/backoff;
- retry exhaustion.

### B4

- deterministic result-object identity across attempts;
- stale-attempt object collision handling;
- durable cleanup execution/recovery;
- complete cross-resource idempotency/accountability.

### B5

- integrated Case B failure matrix and final before/after portfolio evidence.

> Immediate next single task: prepare the bounded B3 implementation specification.
