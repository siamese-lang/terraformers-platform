# Case B B4 — Result Idempotency and Durable Cleanup Accountability Evidence

## Scope and bounded objective

B4 closes the cross-resource safety boundary left after B3:

```text
retry/reclaim may cause repeated execution
+
external object storage is not transactionally atomic with MariaDB
```

The merged implementation establishes one deterministic result-object identity per logical
`AnalysisJob`, durable object intent before external result persistence, fenced canonical object
mutation, serialized immediate compensation, durable accountability when compensation fails, and
bounded in-process cleanup recovery. It does not establish exactly-once provider execution. The
wider Case B target remains **effectively-once logical finalization under durable ownership/fencing,
not exactly-once external provider invocation**.

## Deterministic result identity

The canonical result key is:

```text
{normalized-result-prefix}/{projectId}/{analysisJobId}/main.tf
```

It contains no wall-clock date, current time, retry number, `attempt_count`, `claim_generation`, or
lease timestamp. B4 removed the pre-B4 `Instant.now()` date-derived key behavior, so retry or reclaim
of one logical job resolves to the same logical object.

## Durable intent freezing

B4 actively uses the B1 fields `result_object_intent_bucket`, `result_object_intent_key`, and
`result_cleanup_status` under this contract:

```text
no existing intent
→ current fenced owner may record canonical bucket/key
→ cleanup status PENDING

same existing intent
→ reuse permitted

different bucket/key
→ rejected
```

Once recorded, durable intent wins over later configuration changes. **B4 reused the
durable-accountability state introduced in B1; no additional Flyway migration was necessary.**

## Cleanup-state meanings

- `NOT_REQUIRED`: no pending external cleanup is required. This includes a successfully finalized,
  retained result.
- `PENDING`: the canonical object may exist or may have become externally persistent while
  successful relational finalization is not durably established. The exact bucket/key remains
  accountable; this state does not prove that the object definitely exists.
- `COMPLETED`: removal, or the idempotent removal path, completed successfully. The job may remain
  `FAILED`; recovery neither changes it to success nor retries provider execution.

## Merged execution and fencing order

```text
fenced lease claim
→ provider analysis
→ Terraform validation
→ deterministic ObjectReference resolution
→ durable fenced intent PENDING commit
→ owned finalization transaction
→ lockOwned(current generation + active lease)
→ canonical object write
→ relational ProjectFile/result finalization
→ SUCCEEDED + cleanup NOT_REQUIRED
→ lease clear
```

Provider/AI work remains outside the pessimistic database row-lock transaction. The final canonical
object mutation is inside the owned finalization row-lock window; this is B4's central fencing
boundary.

The verified stale-owner invariant is:

```text
newer generation has reclaimed
→ old generation lockOwned() fails
→ old generation performs zero canonical result-object writes
→ old generation cannot finalize over newer ownership
```

A stale worker may already have invoked an external provider: lease and process-loss timing can
still duplicate that invocation. The narrower claim is that **stale ownership cannot make canonical
result mutation/finalization terminally visible through the B4 owned-finalization path**.

## Immediate compensation and rollback bookkeeping

Before B4, an external write could precede fenced finalization and compensation could run after the
finalization transaction released ownership serialization. B4 instead runs both canonical write and
immediate compensation inside the owned row-lock transaction:

```text
current owner holds row lock
→ canonical write may succeed
→ relational finalization fails
→ immediate ObjectRemover compensation runs before the ownership lock is released
→ finalization transaction rolls back
```

This ordering prevents late old-owner compensation from deleting a newer winner's canonical object
after reclaim. The external deletion is still a non-transactional external side effect.

Rollback also undoes database cleanup mutations made inside finalization. Therefore, after successful
external compensation a separate generation/reference-fenced bookkeeping transaction records
`PENDING → COMPLETED` for the exact durable reference. It does not require an active lease, but the
matching generation and exact bucket/key prevent an older generation from changing a newer
accountability record.

## Compensation failure and ambiguous writes

The measured pre-B4 failure was:

```text
object write
→ relational finalization failure
→ compensation failure
→ FAILED
→ silent/untracked object residue
```

B4 inverts that result:

```text
object write or ambiguous persisted-then-thrown write
→ relational finalization/storage failure
→ compensation failure
→ FAILED
→ result file/object success fields remain absent
→ exact intent bucket/key retained
→ cleanup status remains PENDING
```

The residue is durably attributable rather than silent. Tests also deterministically exercise an
`ObjectWriter` that persists the canonical object and then throws before returning an
`ObjectWriteResult`. Because intent is committed first, compensation does not need a successful
write result: successful compensation removes the residue and records `COMPLETED`; failed
compensation retains exact `PENDING` accountability.

## Bounded cleanup recovery

```text
FAILED
+ cleanup PENDING
+ complete intent bucket/key
→ periodic bounded discovery
→ existing bounded executor
→ pessimistic cleanup-row lock
→ ObjectRemover.remove(exact reference)
→ COMPLETED on success
```

There is no separate cleanup service, broker, outbox, or deployment. The existing application
scheduler and bounded executor are reused, and removal is required to be idempotent. Executor
rejection or process loss leaves durable `PENDING` work discoverable by a later scan.

Cleanup dispatch also suppresses duplicate local in-process submissions, analogously to execution
dispatch. On submission rejection the local marker is removed while durable cleanup stays
`PENDING`, allowing a later scan to resubmit. This is only an efficiency mechanism; MariaDB remains
the durable source of cleanup accountability.

### Bounded-batch liveness correction

Initial B4 discovery ordered a bounded batch by `updated_at ASC, id ASC`. A permanently failing
oldest set could repeatedly consume every batch. The merged correction handles an accountable
removal failure as follows:

```text
remove fails
→ cleanup transaction rolls back
→ exact ObjectReference carried outward
→ separate REQUIRES_NEW defer/touch transition
→ FAILED remains unchanged
→ PENDING remains unchanged
→ bucket/key unchanged
→ updated_at advances
```

This rotates that candidate behind older untouched pending work. Precisely, **accountable
object-removal failures are durably deferred so bounded cleanup discovery does not indefinitely pin
later rows behind the same oldest failed candidate set**. This is not a generic global fairness
claim for arbitrary database or infrastructure failures before an accountable reference is obtained.

## Observability boundary

B4 adds a cleanup-scan candidate signal, cleanup outcome metrics, `CLEANUP_RECOVERY` stage evidence,
and retains compensation telemetry. All new metric dimensions use fixed bounded labels. These are
bounded diagnostic signals, not a complete production observability or SLO system.

## Acceptance evidence

| Scenario | Expected B4 result | Evidence status |
|---|---|---|
| same job across different wall-clock dates | same canonical object identity | PASS — deterministic storage tests |
| existing durable intent + configuration change | recorded intent reused | PASS — `AnalysisResultStorageTest` |
| different replacement intent | rejected | PASS — repository and MariaDB smoke tests |
| stale generation success attempt | zero canonical write / no stale finalization | PASS — `AnalysisJobStateServiceTest` |
| current owner normal finalization | `SUCCEEDED` + canonical key + `NOT_REQUIRED` | PASS — `AnalysisJobStateServiceTest` |
| relational finalization failure + compensation success | `FAILED`, no residue, `COMPLETED` | PASS — partial-success baseline test |
| compensation failure | `FAILED` + exact intent + `PENDING` residue accountability | PASS — partial-success baseline test |
| persist-then-throw ambiguous write + cleanup success | no residue, `COMPLETED` | PASS — partial-success baseline test |
| persist-then-throw + cleanup failure | exact `PENDING` accountability retained | PASS — partial-success baseline test |
| later cleanup recovery | residue removed, `COMPLETED`, job remains `FAILED` | PASS — partial-success baseline test |
| cleanup recovery removal failure | remains `PENDING` | PASS — cleanup dispatcher test |
| oldest cleanup repeatedly failing | candidate deferred so later bounded work can advance | PASS — repository/dispatcher tests |
| executor cleanup submission rejection | durable `PENDING` retained for later scan | PASS — cleanup dispatcher test |
| B3 provider-timeout policy | unchanged | PASS — backend regression suite |
| MariaDB schema/repository validation | existing schema valid | PASS — accepted MariaDB CI subjob |
| normal result readback semantics | regression suite remains green | PASS — accepted backend CI suite |

## Accepted CI and revision evidence

```text
PR #103 final head
99334b59334f87406f37fcc16137af344cd27c25

merge commit
90c47cb9c0fcdbe75258ed2ac4613cb1625a5463

Terraform Static Verification
run 36541905544
SUCCESS

Backend Local Verification
run 36541905514
SUCCESS

Backend local smoke baseline
SUCCESS

MariaDB schema and repository validation
SUCCESS
```

The backend path reported six unique existing versioned Flyway migrations, Maven clean tests, and
successful local verification. B4 added no migration;
`V20260928_006__add_analysis_job_durable_processing_state.sql` remains the schema source for its
durable accountability state. No test count is inferred from the accepted log.

## Review-discovered corrections retained in the accepted head

Independent review corrected test telemetry registry leakage, MariaDB smoke modifying-query
transaction boundaries, the cleanup bounded-batch starvation risk, the ambiguous-write plus
cleanup-failure evidence gap, compensation telemetry ordering after compensation moved inside
finalization, and `TIMESTAMP(6)` versus nanosecond fixture precision. The starvation correction was
a production liveness correction; the others primarily strengthened test/evidence correctness.

## Limitations and non-claims

B4 does **not** prove:

- exactly-once provider invocation or zero duplicate provider calls under all process-loss timing;
- production-scale cleanup throughput or a cleanup latency SLO;
- distributed multi-service cleanup orchestration;
- broker/outbox necessity or rejection for all future workloads; or
- full Case B portfolio closure.

Cleanup currently reuses the B2 poll cadence and executor and has no dedicated cleanup backoff/jitter
contract. B5 remains necessary to validate the complete integrated failure matrix.
