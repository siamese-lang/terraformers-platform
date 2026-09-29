# Case B Durable Processing — Bounded Implementation Plan

## Status

**COMPLETE — B1/B2/B3/B4/B5 COMPLETE, CASE B INTEGRATED CLOSURE PASS**

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

## Status

**COMPLETE**

Implementation evidence:
[Case B B3 Selective Bounded Retry Evidence](../../evaluation/case-b-b3-selective-bounded-retry.md)

PR #101 final head `f3aa4e488e1858f84688d7b848a4b1af07538656` merged as
`fb1bc3f7b64a0a0274114f1e29ac9f28f2ae652d`. Terraform Static Verification run `36451986173`
and Backend Local Verification run `36451986210` succeeded, including the backend local smoke and
MariaDB schema/repository jobs.

B3 retries only `AnalysisProviderTimeoutException`, with a fixed `10s` delay. Another timeout retry
is scheduled only while the current `attempt_count < 3`; an approved provider timeout at
`attempt_count >= 3` becomes terminal `FAILED`. MariaDB `next_attempt_at` is the durable wait
mechanism, with no backoff or jitter. B2 expired-lease reclaim remains independent of this B3
retry-scheduling threshold. Reclaim increments `attempt_count`, so it can consume later
timeout-retry headroom. Retry scheduling remains generation/lease fenced, and a stale owner neither
schedules retry nor falls through to overwrite newer state. Semantic provider failures and
storage/finalization/cleanup failures remain terminal. B4 subsequently closed deterministic result
identity and durable cleanup accountability without changing the B3 timeout policy.

## Goal

Add retry only for the approved provider-timeout signal.

The implemented configuration freezes:

- provider-timeout retry scheduling only while current `attempt_count < 3`;
- retry delay at a fixed `10s`;
- no exponential backoff or jitter;
- the existing B2 lease duration at `60s` and polling interval at `2s`.

The values must be documented as configuration, not retroactively adjusted to make tests pass.

## Required behavior

- retryable transient failure schedules a later eligible attempt;
- attempt count is durable;
- successful later attempt reaches SUCCEEDED;
- provider-timeout retry scheduling stops when `attempt_count >= 3`;
- exhaustion reaches explicit FAILED;
- non-retryable failures remain terminal immediately;
- stale worker cannot schedule retry or terminal state after losing fencing ownership.

## Implemented failure classification

The sole automatic retry signal is:

- `AnalysisProviderTimeoutException`, including causal `SocketTimeoutException` or
  `HttpTimeoutException` normalized at the provider boundary.

Explicit provider semantic classifications outrank nested timeout causes and remain terminal.

Do not automatically retry:

- rejected input;
- output truncation under unchanged configuration;
- deterministic response-format/semantic failure without separate evidence;
- deterministic Terraform validation failure;
- authorization/invariant failure.

## Required tests

- PR #94 transient-first-failure fixture succeeds on bounded retry;
- timeout-retry exhaustion terminates when the current attempt count reaches the configured threshold;
- non-retryable failure makes one attempt;
- stale owner cannot mutate retry state.

## Stop boundary

B3 does not change object-storage consistency beyond what is necessary for safe job retry.

---

# B4 — Result idempotency and durable cleanup accountability

## Status

**COMPLETE**

Evidence: [Case B B4 Result Idempotency and Durable Cleanup Accountability](../../evaluation/case-b-b4-result-idempotency-cleanup.md)

PR #103 final head `99334b59334f87406f37fcc16137af344cd27c25` merged as
`90c47cb9c0fcdbe75258ed2ac4613cb1625a5463`; PR #105 supplied the follow-up direct changed-date
identity regression evidence. B4 implements deterministic `{prefix}/{projectId}/{jobId}/main.tf`,
pre-write durable intent, canonical mutation and immediate compensation under the fenced owned row
lock, exact `PENDING` accountability, and bounded cleanup recovery. Accountable cleanup failures are
deferred by advancing `updated_at`. No schema, broker, outbox, cleanup service, or deployment was added.

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

## Status

**COMPLETE — INTEGRATED CLOSURE PASS**

B1/B2/B3/B4/B5 are complete and Case B is portfolio-closed. The integrated evidence and exact
execution provenance are recorded in
[Case B Integrated Durable-Processing Closure](../../evaluation/case-b-integrated-closure.md).

## Goal

Validate the completed design against the same failure matrix and produce portfolio-grade
before/after evidence.

This stage is validation/closure, not a new architecture stage. It validates the already-selected
ADR-007 design without changing its semantics before measurement:

```text
MariaDB AnalysisJob durable source of truth
+ durable eligibility
+ bounded local executor
+ lease/fencing
+ timeout-only bounded retry
+ deterministic canonical result identity
+ durable object intent
+ fenced finalization
+ durable cleanup accountability/recovery
```

The executor remains a bounded local concurrency pool; it is not the durable work source.

## Frozen runtime and retry contract

B5 uses, rather than tunes, this existing contract:

```text
dispatch poll interval = 2s
dispatch batch size = 4
lease duration = 60s
lease renewal interval = 20s
max attempts = 3
retry delay = 10s
retry shape = fixed delay
```

`max-attempts=3` is the B3 threshold for deciding whether an approved provider timeout may schedule
another retry. It is not a generic claim limit and does not cap B2 expired-lease reclaim. Reclaim
increments the shared attempt counter and can therefore reduce later timeout-retry headroom, but a
job is not made ineligible for reclaim solely because that counter has reached three.

## Required deterministic matrix

The portfolio measurement contract defines the core ten rows. This active B5 plan is the
stage-specific superset and adds stale-worker fencing plus normal success/readback as explicit
closure regressions. All twelve rows remain required:

1. **B5-01** accepted DB commit then process loss;
2. **B5-02** process loss before execution start;
3. **B5-03** process loss after claim;
4. **B5-04** duplicate delivery;
5. **B5-05** transient provider timeout;
6. **B5-06** retry then success;
7. **B5-07** retry exhaustion;
8. **B5-08** object write then DB finalization failure;
9. **B5-09** cleanup failure and recovery;
10. **B5-10** concurrent claim;
11. **B5-11** stale-worker fencing;
12. **B5-12** normal success/readback regression.

“Integrated closure” means that the selected design is validated across this full matrix. It does
not require one giant integration method or twelve artificially unique fixtures.

## Evidence classification

B5 uses these classifications consistently:

- **DIRECT EXISTING EVIDENCE** — an existing deterministic assertion already demonstrates the row's
  required invariant.
- **REUSE WITH B5 EXECUTION** — rerun the named smallest relevant test and record its observed state,
  count, or timing in the closure artifact.
- **MISSING DIRECT EVIDENCE** — only a required invariant that no current test/assertion can directly
  demonstrate after inspection; none is presently identified.
- **NOT APPLICABLE** — a measurement or behavior does not apply to a particular scenario; absence
  from one monolithic test is not a gap.

## Scenario-to-existing-evidence map

| ID | Scenario | Existing direct evidence | B5 execution action | Missing evidence | Acceptance observation |
|---|---|---|---|---|---|
| B5-01 | accepted DB commit then process loss | **DIRECT EXISTING EVIDENCE:** `AnalysisJobRestartBaselineTest.acceptedButNotStartedJobIsRecoveredByDurableScanAfterRestart()` commits the accepted job, drops the original application context before its captured runnable executes, starts a fresh context on the same DB, scans durable eligibility, and reaches `SUCCEEDED`. | **REUSE WITH B5 EXECUTION:** rerun the existing restart fixture and record accepted `1`, terminal `1`, stranded `0`. | None. One deterministic fixture directly covers B5-01 and B5-02 at the same accepted-but-not-started boundary. | The same accepted job survives process loss and reaches terminal success without resubmission. |
| B5-02 | process loss before execution start | **DIRECT EXISTING EVIDENCE:** the same `acceptedButNotStartedJobIsRecoveredByDurableScanAfterRestart()` fixture proves that local submission/runnable non-execution is not the durability boundary. | **REUSE WITH B5 EXECUTION:** reuse the B5-01 run; do not invent a duplicate test merely to produce a second method name. | None; this row overlaps B5-01 in the current fixture at the accepted-but-not-started timing. | Fresh-process durable scan discovers and executes the committed work. |
| B5-03 | process loss after claim | **DIRECT EXISTING EVIDENCE:** `AnalysisJobRestartBaselineTest.claimedRunningJobIsNotStolenBeforeLeaseExpiryAndIsReclaimedAfterExpiry()`. | **REUSE WITH B5 EXECUTION:** record current `RUNNING` lease, no early steal, post-expiry reclaim, generation increment, and terminal state for the same job. | None. | Current lease is respected; after expiry a newer generation reclaims and the same job succeeds. |
| B5-04 | duplicate delivery | **DIRECT EXISTING EVIDENCE:** `AnalysisJobDuplicateExecutionBaselineTest.duplicateDeliveryDoesNotReexecuteSucceededJobAfterAtomicClaim()`. | **REUSE WITH B5 EXECUTION:** record two deliveries, one logical provider execution, and one persistent result registration/finalization. | None. | Terminal redelivery is allowed, but duplicate logical execution and duplicate persistent logical side effect are both `0`; this is not exactly-once delivery. |
| B5-05 | transient provider timeout | **DIRECT EXISTING EVIDENCE:** `AnalysisJobRunnerTest.providerTimeoutSchedulesDurableRetryBeforeExhaustion()` plus the B3 evidence directly prove timeout classification and the fenced retry transition. | **REUSE WITH B5 EXECUTION:** record one approved timeout, durable `PENDING`, `next_attempt_at`, scheduled-retry signal, and no immediate terminal/catch-all retry. B5-06 supplies the stronger end-to-end completion evidence. | None. | Approved provider timeout schedules durable future eligibility; unrelated failures remain outside the retry class. |
| B5-06 | retry then success | **DIRECT EXISTING EVIDENCE:** `AnalysisJobRestartBaselineTest.durableRetryWaitsUntilDueThenSucceedsOnSecondClaim()`. | **REUSE WITH B5 EXECUTION:** record no execution before due time, fixed 10s contract delay, second claim, success, `attempt_count=2`, `claim_generation=2`, and provider invocations `2`. | None. | Timeout on attempt 1 becomes a due durable retry and attempt 2 succeeds. |
| B5-07 | retry exhaustion | **DIRECT EXISTING EVIDENCE:** `AnalysisJobRestartBaselineTest.durableRetryExhaustionFailsThirdAttemptAndNeverExecutesFourth()`, supported by `AnalysisJobRunnerTest.exhaustedProviderTimeoutIsFencedFailed()`. | **REUSE WITH B5 EXECUTION:** record timeout-driven attempts 1–3, terminal `FAILED`, cleared retry eligibility, and no fourth timeout-scheduled execution. | None. | Timeout retry scheduling is bounded and exhaustion is terminal. This does not assert that B2 lease reclaim can never occur after attempt three. |
| B5-08 | object write then DB finalization failure | **DIRECT EXISTING EVIDENCE:** `AnalysisJobPartialSuccessBaselineTest.successfulObjectWriteIsCompensatedWhenRelationalFinalizationFails()`. | **REUSE WITH B5 EXECUTION:** record write, relational failure, `FAILED`, absent published result fields, retained exact intent, successful compensation, `COMPLETED`, and zero residue. | None. | Cross-resource partial success is compensated and leaves neither a published result nor silent residue. |
| B5-09 | cleanup failure and recovery | **DIRECT EXISTING EVIDENCE:** `AnalysisJobPartialSuccessBaselineTest.failedCompensationLeavesDurablyAccountedObjectResidue()`, `.persistedThenThrownWriteRemainsDurablyAccountedWhenCleanupFails()`, and `.recoveryDeletesDurablyAccountedResidueWithoutRetryingJob()`; `AnalysisResultCleanupDispatcherTest.failedRecoveryIsDeferredAndCanSucceedOnLaterSubmission()`. | **REUSE WITH B5 EXECUTION:** record `FAILED` + exact intent + cleanup `PENDING`, later durable discovery/removal, cleanup `COMPLETED`, unchanged job failure, and no provider re-execution. | None. | Cleanup failure remains accountable and recoverable. Accountable object-removal failures can be deferred so oldest failed work does not indefinitely pin later bounded candidates; this is not a universal fairness theorem. |
| B5-10 | concurrent claim | **DIRECT EXISTING EVIDENCE:** the real-MariaDB `MariaDbRepositorySmokeTest` concurrent paths call `assertConcurrentDurableClaim(...)` for initial claim and expired-lease reclaim, using two independent transactions and a `CyclicBarrier`; each produces results `0` and `1`. B1 records both as PASS. | **REUSE WITH B5 EXECUTION:** rerun the existing MariaDB repository smoke test, not only H2/unit mocks, and record the one-winner state for initial claim and reclaim. | None. | Two contenders produce exactly one current ownership winner per generation. |
| B5-11 | stale-worker fencing | **DIRECT EXISTING EVIDENCE:** `AnalysisJobStateServiceTest.staleGenerationCannotCreateRelationalMetadataOrSucceedAfterReclaim()`, `AnalysisJobRepositoryTest.staleGenerationIsFencedAfterExpiredLeaseReclaim()`, `AnalysisJobRunnerTest.staleOwnerCannotScheduleRetryOrMarkTerminalFailure()`, and `.failedHeartbeatBlocksStaleSuccessAndFailureFinalization()`. | **REUSE WITH B5 EXECUTION:** record generation N loss, generation N+1 reclaim, rejection of stale canonical/finalization/retry/failure mutation, and stale successful finalizations `0`. | None. | A stale generation cannot publish relational success or overwrite the current owner. A stale worker may already have invoked an external provider. |
| B5-12 | normal success/readback regression | **DIRECT EXISTING EVIDENCE:** `AnalysisJobControllerIntegrationTest.createAnalysisJobReturnsSucceededStateAndResultArtifact()` exercises accepted API creation followed by result retrieval. | **REUSE WITH B5 EXECUTION:** record `SUCCEEDED`, present `resultFileId`, present `resultObjectKey`, and present/readable `resultPreview`; retain the existing API semantics consumed by frontend polling. | None. Browser E2E is **NOT APPLICABLE** to the accepted B5 API/readback contract. | Normal result retrieval regression count is `0`; no B5-only browser test is required. |

Inspection therefore identifies no genuinely missing direct B5 invariant and proposes no new test,
fixture, instrumentation, verifier, script, harness, workflow, runtime, or architecture. If execution
reveals that a named assertion no longer demonstrates its row, the second-choice response is only
the smallest missing assertion or one narrowly scoped integration fixture; such a change requires a
separately bounded task rather than automatic expansion of B5.

## Required measurements and provenance

B5 is a bounded deterministic correctness experiment. Fixture counts such as `accepted=1`,
`terminal=1`, and `stranded=0` are legitimate for a defined one-job scenario, but are not production
fleet metrics.

| Measurement | Existing source | How B5 records it | Need new instrumentation? |
|---|---|---|---|
| accepted job count | Persisted fixture/API response and `AnalysisJob` DB state | Count the scenario's committed accepted jobs. | No — DB/assertion derived. |
| terminal job count | Final `AnalysisJob.status` | Count `SUCCEEDED` or `FAILED` at the scenario boundary. | No — DB/assertion derived. |
| stranded job count | Accepted IDs minus terminal IDs after the defined recovery sequence | Record `0` only when every accepted fixture reaches the expected terminal state. | No — DB/assertion derived. |
| duplicate delivery count | Explicit duplicate runner deliveries in the duplicate baseline | Record deliveries separately from logical executions. | No — fixture/assertion derived. |
| duplicate logical execution count | Provider/orchestrator invocation counter | Record invocations beyond the one intended logical execution; expected duplicate count `0`. | No — explicit counter. |
| duplicate persistent side-effect count | Result registration/finalization verification and durable result fields | Record persistent logical side effects beyond one; expected duplicate count `0`. | No — assertion/DB derived. |
| attempt count | `attempt_count`, claim results, and provider invocation counter | Record per retry/reclaim scenario, including `2` for retry-success and `3` for timeout exhaustion. | No — DB/assertion derived. |
| retry exhausted count | Terminal status plus `terraformers.analysis.retries{outcome=exhausted}` where used | Record exhausted fixtures and verify terminal `FAILED`. | No. |
| unaccounted object count | Exact intent, cleanup status, in-memory object fixture/removal assertions, and absent/present result fields | Count residue that has neither canonical success nor durable intent/cleanup accountability; expected `0`. | No — state/assertion derived. |
| cleanup pending/recovered count | `result_cleanup_status`, object-removal fixture state, and `terraformers.analysis.cleanup` | Record transition from accountable `PENDING` to `COMPLETED` and object absence. | No. |
| accepted/enqueued → first claim / queue wait | `terraformers.analysis.queue.wait`, job timestamps, and controlled clocks | Record timer observation when exposed, otherwise exact controlled-clock/fixture delta with provenance. | No. |
| restart → reclaim/recovery | `terraformers.analysis.recovery.delay`, lease timestamps, and controlled clocks | Record the observed test delta and the point at which reclaim becomes eligible. | No. |
| retry delay | `next_attempt_at` and controlled-clock eligibility assertions | Record configured fixed `10s`, no execution at `T+9s`, and eligibility at `T+10s`. | No. |
| accepted → terminal | `created_at`/`updated_at` and controlled scenario boundaries | Record the deterministic fixture delta where meaningful, clearly labeled as test observation. | No. |
| processing duration | `terraformers.analysis.duration` and `terraformers.analysis.stage.duration` | Record timer values/counts supported by the selected run; do not infer an SLO. | No. |
| eligible work | Exact fixture DB query/count or `terraformers.analysis.dispatch.scan.candidates` | Prefer exact DB fixture count when needed; otherwise label the signal **bounded eligible-scan candidate count**. | No. |
| active local work / saturation | Existing executor and rejection observations where applicable | **NOT APPLICABLE** to B5 correctness PASS/FAIL; preserve any incidental observation for Case C. | No. |

### Existing Micrometer signals

The implementation already exposes the signals needed to interpret B5 without adding a metric:

```text
terraformers.analysis.duration
terraformers.analysis.claims
terraformers.analysis.dispatch
terraformers.analysis.lease.renewals
terraformers.analysis.retries
terraformers.analysis.cleanup
terraformers.analysis.cleanup.scan.candidates
terraformers.analysis.dispatch.scan.candidates
terraformers.analysis.recovery.delay
terraformers.analysis.queue.wait
terraformers.analysis.stage.duration
terraformers.analysis.stage.failures
```

`terraformers.analysis.dispatch.scan.candidates` records the bounded candidate count returned by a
scan constrained by `dispatch batch size = 4`. It is **not** an exact global DB queue-depth metric.
B5 may use it only as a bounded eligible-scan candidate count, or derive the exact count for its
small deterministic fixture directly from DB state. Exact production-wide queue depth is unnecessary
for B5 correctness closure, so B5 adds no such metric.

### Timing interpretation

B5 must label each time value as one of:

1. **deterministic configured contract time**, such as the fixed retry delay of `10s`, lease duration
   of `60s`, or poll interval of `2s`;
2. **observed test timing**, from a controlled clock, timestamps, or an existing Micrometer timer;
3. **production performance SLO**, of which B5 defines none.

Thus `retry delay contract = fixed 10s` and the controlled due-time assertion do not claim
`production p95 retry latency = 10s`. Queue wait, recovery latency, accepted-to-terminal time, and
processing time must be recorded and interpreted where supported, but no arbitrary numeric PASS/FAIL
threshold is introduced without a frozen before baseline.

## Hard acceptance

Correctness invariants, rather than performance thresholds, decide B5:

| Hard gate | Matrix proof |
|---|---|
| stranded accepted jobs = `0` | B5-01, B5-02, B5-03, and terminal-state confirmation in B5-06/B5-07 |
| terminal duplicate re-execution/finalization = `0` | B5-04 |
| duplicate logical side effect = `0` | B5-04, with B5-08/B5-09 confirming accountable cross-resource behavior |
| stale-worker successful finalization = `0` | B5-11 |
| unbounded retry = `0` | B5-05, B5-06, and B5-07 |
| retry exhaustion left non-terminal = `0` | B5-07 |
| silent/unaccounted object residue = `0` | B5-08 and B5-09 |
| normal result retrieval regression = `0` | B5-12 |

Immediate cleanup failure passes only when exact residue remains durably identifiable and follows a
bounded later cleanup path. A failed hard gate keeps Case B open.

## Performance interpretation

B5 records and interprets correctness and bounded recovery timing. It does not invent numeric
performance thresholds and is not a load test. Production-scale throughput and capacity bottlenecks
belong to Case C, which later measures whether MariaDB polling, executor concurrency, database load,
or external AI latency is the first runtime capacity bottleneck.

## Executed validation shape

1. **Reuse applied:** the fresh full backend suite covered the named deterministic tests, and the
   existing real-MariaDB contention smoke path supplied the required database evidence; current
   source assertions define the row-specific state, counter, and controlled-time observations.
2. **Second choice — exception:** only if execution proves a required invariant lacks a direct
   assertion, propose the smallest missing assertion or one narrowly scoped integration fixture in a
   separately bounded change.
3. **Prohibited default:** do not create `CaseBClosureVerifier`, `B5VerificationWorkflow`,
   `B5EvidenceGenerator`, a generic chaos framework, a new broker fixture, or a distributed worker
   service merely to aggregate evidence.

Multiple existing deterministic tests may form the integrated closure evidence. B5 must not change
retry, lease, polling, batch, result, cleanup, or ownership semantics before measuring them.

## Closure artifact and decision

The completed B5 execution is recorded in:

```text
docs/evaluation/case-b-integrated-closure.md
```

It contains:

1. before-state;
2. accepted architecture and why;
3. B1–B4 implemented mechanisms;
4. the 12-row B5 matrix with commands and results;
5. measurement table with provenance and observed values;
6. hard acceptance results;
7. before/after interpretation under the same scenarios;
8. complexity and trade-offs;
9. residual limitations;
10. an explicit Case B closure decision.

Every applicable matrix row has direct evidence, every hard gate passes, measurements are reported
without overstated SLO/scale claims, and residual risks are explicit. The successful commands are
supporting evidence rather than the closure decision by themselves.

## Residual limitations

The passing B5 does **not** prove:

- exactly-once external provider invocation;
- zero duplicate provider calls under every process-loss timing;
- production-scale throughput;
- a queue-wait or recovery-latency SLO;
- multi-service distributed durability;
- universal rejection of RabbitMQ or Transactional Outbox;
- generic cleanup fairness.

MariaDB remains the durable source of truth and the executor remains a bounded local concurrency
pool. Reopening the broker/outbox decision requires new measured scale, fan-out, service-boundary, or
failure evidence; B5 neither introduces nor universally rejects those alternatives.

---

# Stage dependency summary

`B1 durable state/fencing → B2 dispatcher/recovery → B3 bounded retry → B4 result/cleanup safety → B5 integrated closure`

No stage may be merged merely because a later stage needs it. Each must independently satisfy its
bounded correctness purpose.

## Current immediate next task

Case B is complete. The next candidate is a separate user-approved **Case A decision/checkpoint**.
Do not start Case A implementation or Case C automatically; this closure authorizes neither.
