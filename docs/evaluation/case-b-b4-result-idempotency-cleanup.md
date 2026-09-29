# Case B B4 — Result Idempotency and Durable Cleanup Accountability Evidence

## Scope and accepted contract

B4 closes the boundary where retry/reclaim may repeat execution while external object storage is not
transactionally atomic with MariaDB. The merged behavior establishes deterministic result identity,
durable pre-write intent, fenced canonical mutation, serialized immediate compensation, durable
accountability after failed compensation, and bounded in-process cleanup recovery. The Case B target
is effectively-once logical finalization under durable ownership/fencing, not exactly-once provider
invocation.

## Deterministic identity and intent

The canonical key is:

```text
{normalized-result-prefix}/{projectId}/{analysisJobId}/main.tf
```

It contains no wall-clock date/time, attempt count, generation, retry number, or lease timestamp. The
pre-B4 `Instant.now()` date path is gone. A retry/reclaim of one job resolves to one object identity.
PR #105 directly tests the same job without pre-populated intent while `attempt_count` and
`claim_generation` change from 1 to 2 and timing crosses from
`2026-09-29T23:59:50Z`/`23:59:59Z` to `2026-09-30T00:00:10Z`/`00:01:00Z`; the reference remains
`result-bucket/custom-prefix/104/{same-job-id}/main.tf`.

B4 uses B1's `result_object_intent_bucket`, `result_object_intent_key`, and
`result_cleanup_status`:

```text
no intent → current fenced owner records canonical bucket/key → PENDING
same intent → reuse permitted
different intent → rejected
```

Recorded intent wins over later configuration. B4 reused B1 durable-accountability state; no new
Flyway migration was required.

## Cleanup-state semantics

- `NOT_REQUIRED`: no pending cleanup, including a successfully finalized retained result.
- `PENDING`: the exact object may exist while relational success is not durably established; it does
  not prove existence, but preserves accountability.
- `COMPLETED`: idempotent removal completed. The job may remain `FAILED`; cleanup neither converts it
  to success nor retries provider execution.

## Execution and stale-owner fencing

```text
fenced claim → provider analysis → Terraform validation
→ deterministic reference → durable intent PENDING commit
→ owned finalization transaction → lockOwned(current generation + active lease)
→ canonical write → relational finalization
→ SUCCEEDED + NOT_REQUIRED → lease clear
```

Provider work remains outside the pessimistic row-lock transaction; canonical mutation is inside the
owned finalization lock. After a newer generation reclaims, the older generation fails `lockOwned()`,
performs zero canonical writes, and cannot finalize over newer ownership. This does not prevent a
stale worker from having invoked the external provider.

## Compensation and durable bookkeeping

Canonical write and immediate compensation run in the same owned row-lock window. If relational
finalization fails, `ObjectRemover` runs before ownership serialization is released, preventing late
old-owner compensation from deleting a newer winner's canonical object. External deletion remains a
non-transactional side effect.

Because finalization rollback also rolls back in-transaction cleanup mutations, successful external
compensation is followed by a separate generation/reference-fenced `PENDING → COMPLETED` transition.
It needs no active lease but requires the matching generation and exact reference.

For a normal write or a persist-then-throw ambiguous write, compensation failure produces `FAILED`,
leaves result success fields absent, retains the exact intent, and leaves cleanup `PENDING`. Thus
residue is attributable rather than silent. Intent is committed before write, so compensation does
not depend on receiving an `ObjectWriteResult`.

## Bounded cleanup recovery and liveness

```text
FAILED + PENDING + complete intent
→ periodic bounded discovery → existing bounded executor
→ pessimistic cleanup-row lock → idempotent remove(exact reference)
→ COMPLETED
```

No broker, outbox, separate service, or deployment was added. Rejection or process loss leaves
`PENDING` discoverable; local duplicate suppression is only an efficiency mechanism.

Discovery orders its bounded batch by `updated_at ASC, id ASC`. For an accountable removal failure,
the cleanup transaction rolls back and a separate `REQUIRES_NEW` transition preserves `FAILED`,
`PENDING`, and exact intent while advancing `updated_at`. **Accountable object-removal failures are
durably deferred so the same oldest failed candidate set does not indefinitely pin later bounded
cleanup work.** This is not a generic fairness theorem for failures before an accountable
`ObjectReference` can be obtained.

## Observability boundary

B4 adds cleanup-scan candidate and cleanup-outcome signals plus `CLEANUP_RECOVERY` stage evidence,
while retaining compensation telemetry. Labels are fixed and bounded. This is not a complete
production observability/SLO system.

## Acceptance evidence

| Scenario | Expected result | Evidence status |
|---|---|---|
| same job across changed UTC wall-clock date and mutable retry/reclaim state | same canonical object identity | PASS — `AnalysisResultStorageTest.sameLogicalJobKeepsCanonicalIdentityWhenRetryTimingCrossesUtcDateBoundary`, PR #105 |
| recorded intent + configuration change | recorded intent reused | PASS — `AnalysisResultStorageTest` |
| different replacement intent | rejected | PASS — repository/MariaDB tests |
| stale generation success | zero canonical write and no stale finalization | PASS — `AnalysisJobStateServiceTest` |
| current owner success | `SUCCEEDED`, canonical key, `NOT_REQUIRED` | PASS — `AnalysisJobStateServiceTest` |
| relational failure + compensation success | `FAILED`, no residue, `COMPLETED` | PASS — partial-success test |
| compensation or ambiguous-write cleanup failure | exact intent retained with `PENDING` | PASS — partial-success tests |
| later cleanup recovery | residue removed, `COMPLETED`, job remains `FAILED` | PASS — partial-success test |
| failed oldest cleanup candidate is deferred so later bounded work can advance | candidate retains `FAILED` + `PENDING` + exact intent; `updated_at` advances; next bounded discovery can select another candidate | PASS — `AnalysisJobRepositoryTest.touchingFailedOldestCleanupRotatesLaterPendingWorkIntoBoundedBatch` and `AnalysisResultCleanupDispatcherTest.failedRecoveryIsDeferredAndCanSucceedOnLaterSubmission` |
| executor rejection | durable `PENDING` available for later scan | PASS — cleanup dispatcher test |
| B3 timeout policy and normal readback | unchanged | PASS — backend regression suite |
| MariaDB schema/repository | existing schema valid | PASS — accepted CI |

## Implementation evidence — PR #103

```text
final head  99334b59334f87406f37fcc16137af344cd27c25
merge       90c47cb9c0fcdbe75258ed2ac4613cb1625a5463
Terraform Static Verification  36541905544  SUCCESS
Backend Local Verification     36541905514  SUCCESS
Backend local smoke baseline                SUCCESS
MariaDB schema and repository validation    SUCCESS
```

Six existing versioned Flyway migrations were verified and Maven clean tests completed. B4 added no
migration; `V20260928_006__add_analysis_job_durable_processing_state.sql` remains its schema source.

## Follow-up direct acceptance evidence — PR #105

```text
final head  35455b29b1423bae08d26638437ef2d4d66885a4
merge       035b950975ef90c292856bd720ed23a729e12971
Terraform Static Verification  36548193231  SUCCESS
Backend Local Verification     36548193274  SUCCESS
Backend local smoke baseline                SUCCESS
MariaDB schema and repository validation    SUCCESS
```

PR #105 added no production behavior. It supplied the missing direct regression evidence for the
already-implemented deterministic identity contract.

## Limitations

B4 does not prove exactly-once provider invocation, zero duplicate calls under every process-loss
timing, production-scale cleanup throughput, a cleanup latency SLO, distributed multi-service cleanup
orchestration, universal broker/outbox rejection, or full Case B closure. Cleanup reuses the B2 poll
cadence/executor without a dedicated cleanup backoff/jitter contract. B5 must validate the integrated
failure matrix.
