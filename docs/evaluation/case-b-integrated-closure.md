# Case B Integrated Durable-Processing Closure

## 1. Scope and experiment identity

| Field | Value |
|---|---|
| Case | Case B — Durable Asynchronous AnalysisJob Processing |
| Stage | B5 integrated closure |
| Execution SHA | `164fe2f5a83c9d9eba44eca351db604368940598` |
| Fresh authoritative run | GitHub Actions `36555800770` |
| Trigger / ref | manual `workflow_dispatch` on `main` |
| Run conclusion | `SUCCESS` |

This is a documentation-only closure assessment. It measures the already accepted implementation;
it does not change production behavior, tests, configuration, or verification infrastructure.

Architecture under test:

```text
MariaDB durable AnalysisJob source of truth
→ durable eligibility
→ bounded local executor
→ lease/fencing
→ timeout-only bounded retry
→ deterministic result identity
→ durable result intent
→ fenced finalization
→ durable cleanup accountability/recovery
```

## 2. Preserved before-state

The following is **preserved historical before-state evidence**, not behavior regenerated during B5.
The M5 baseline and Case B measurement-readiness evidence established that:

- accepted `PENDING` / `RUNNING` work could be stranded or forced to failure after process loss;
- duplicate delivery could re-enter terminal execution;
- transient provider failure had no durable bounded retry;
- object persistence could precede failed relational finalization; and
- failed cleanup could leave object residue without durable accountability.

Sources: [M5 Backend Reliability Baseline](m5-backend-reliability-baseline.md) and
[Case B Measurement Readiness Evidence](case-b-measurement-readiness.md). B5 does not reintroduce
these broken behaviors merely to regenerate a before measurement.

## 3. Accepted ADR-007 architecture

[ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md) selected MariaDB-backed
durable eligibility, time-bounded lease ownership, generation fencing, and selective bounded retry.
The existing executor remains a bounded local concurrency pool, not a durable delivery source.
RabbitMQ without an outbox was rejected for the current atomic-acceptance requirement; Transactional
Outbox plus RabbitMQ remains a valid deferred alternative if measured scale, fan-out, or service
boundaries later justify its operational and consistency cost.

## 4. B1–B4 implemented mechanisms

- **B1 — durable state and fencing:** durable attempt, eligibility, lease, generation, and cleanup
  fields; atomic single-winner claim/reclaim transitions; stale-generation rejection.
- **B2 — durable dispatch and recovery:** eligible MariaDB scanning feeds the bounded executor;
  accepted work survives lost local handoff, and expired leases can be reclaimed.
- **B3 — selective bounded retry:** only the approved provider-timeout signal schedules another
  attempt, using fixed future eligibility and fenced state transitions.
- **B4 — result and cleanup safety:** deterministic canonical result identity, durable pre-write
  intent, fenced relational finalization, compensation, and recoverable cleanup accountability.

Detailed evidence remains in the existing
[B1](case-b-b1-durable-state-fencing.md),
[B2](case-b-b2-durable-dispatch-recovery.md),
[B3](case-b-b3-selective-bounded-retry.md), and
[B4](case-b-b4-result-idempotency-cleanup.md) documents.

## 5. Frozen B5 runtime/retry contract

| Setting | Frozen value | Interpretation |
|---|---:|---|
| dispatch poll interval | `2s` | configured contract |
| dispatch batch size | `4` | configured bounded scan size |
| lease duration | `60s` | configured contract |
| lease renewal interval | `20s` | configured contract |
| max attempts | `3` | timeout retry-scheduling threshold |
| retry delay | `10s` | fixed configured delay |
| retry shape | fixed delay | no backoff or jitter |

`max-attempts=3` decides whether an approved provider timeout may schedule another retry. It is not
a generic B2 reclaim limit. Expired-lease reclaim increments the shared attempt counter but is not
made ineligible solely because that counter has reached three.

## 6. Fresh execution provenance

GitHub Actions run `36555800770` was a manual `workflow_dispatch` run on `main` at the exact
execution SHA. It completed successfully with both required jobs:

- **Backend local smoke baseline — SUCCESS.** The job ran
  `bash scripts/checks/backend-local-verification.sh`, including Flyway migration uniqueness,
  `mvn -q -e clean test`, and `mvn -q -DskipTests package`. Its log reports Maven clean tests,
  packaging without rerunning tests, and local verification completion.
- **MariaDB schema and repository validation — SUCCESS.** The job used
  `MariaDB 11.4.13-MariaDB-ubu2404`, started the production profile with Flyway and
  `ddl-auto=validate`, passed application health and schema validation, and ran the canonical
  repository smoke queries. The existing script runs
  `mvn -q -Dtest=MariaDbRepositorySmokeTest test` against that real MariaDB service.

The authoritative run executed the full `mvn clean test` backend suite on the exact B5 SHA. The
named B5 tests are members of that suite, and their current source assertions define the row-specific
state/count invariants. The focused B5 command was **not** separately executed successfully; a prior
local attempt was blocked before test execution by GitHub/Maven HTTP 403 and contributes no PASS
evidence. Quiet Maven output does not provide invented per-method log lines.

Thus B5-01–B5-09 and B5-11/B5-12 use current source assertions plus the fresh full-suite PASS.
B5-10 uses the current `MariaDbRepositorySmokeTest` assertions plus its fresh real-MariaDB PASS.

## 7. Twelve-row B5 matrix

| ID | Scenario and current direct source | Assertion-supported observation | Fresh execution | Result |
|---|---|---|---|---|
| B5-01 | Accepted DB commit then process loss — `AnalysisJobRestartBaselineTest.acceptedButNotStartedJobIsRecoveredByDurableScanAfterRestart()` | accepted `1`; terminal `1`; stranded `0`; `SUCCEEDED`; `attempt_count=1`; `claim_generation=1` | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-02 | Process loss before execution start — same fixture as B5-01 | Original runnable does not execute; fresh durable scan recovers the one accepted job. One fixture covers both boundaries. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-03 | Process loss after claim — `AnalysisJobRestartBaselineTest.claimedRunningJobIsNotStolenBeforeLeaseExpiryAndIsReclaimedAfterExpiry()` | Before expiry: `RUNNING`, attempt `1`, generation `1`, no early submission/reclaim. After expiry: same job reclaimed, attempt `2`, generation `2`, `SUCCEEDED`. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-04 | Duplicate delivery — `AnalysisJobDuplicateExecutionBaselineTest.duplicateDeliveryDoesNotReexecuteSucceededJobAfterAtomicClaim()` | Deliveries `2`; initial claims `1`; second delivery not claimed; provider executions `1`; result registrations `1`; duplicate logical execution `0`; duplicate persistent logical side effect `0`. This is not exactly-once delivery. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-05 | Transient provider timeout — `AnalysisJobRunnerTest.providerTimeoutSchedulesDurableRetryBeforeExhaustion()` | Provider invocation `1`; `scheduleRetryOwned(..., +10s)` invoked; retry outcome scheduled; no terminal failure emitted for that attempt. B5-06 supplies DB-backed lifecycle evidence. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-06 | Retry then success — `AnalysisJobRestartBaselineTest.durableRetryWaitsUntilDueThenSucceedsOnSecondClaim()` | Attempt 1 times out; retry due `+10s`; no execution before due; attempt 2 succeeds; attempt/generation `2`; provider invocations `2`; terminal `SUCCEEDED`. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-07 | Retry exhaustion — `AnalysisJobRestartBaselineTest.durableRetryExhaustionFailsThirdAttemptAndNeverExecutesFourth()` and `AnalysisJobRunnerTest.exhaustedProviderTimeoutIsFencedFailed()` | Timeout-driven attempts/provider invocations `3`; terminal `FAILED`; `next_attempt_at=null`; lease null; fourth timeout-scheduled execution `0`. This does not prohibit a later B2 expired-lease reclaim solely because `attempt_count=3`. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-08 | Object write then DB finalization failure — `AnalysisJobPartialSuccessBaselineTest.successfulObjectWriteIsCompensatedWhenRelationalFinalizationFails()` | Object write `1`; relational finalization fails; job `FAILED`; published result fields null; exact intent retained; cleanup `COMPLETED`; residue `0`. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-09 | Cleanup failure/recovery — `failedCompensationLeavesDurablyAccountedObjectResidue()`, `persistedThenThrownWriteRemainsDurablyAccountedWhenCleanupFails()`, `recoveryDeletesDurablyAccountedResidueWithoutRetryingJob()`, and `AnalysisResultCleanupDispatcherTest.failedRecoveryIsDeferredAndCanSucceedOnLaterSubmission()` | Failure: job `FAILED`, exact intent retained, cleanup `PENDING`, residue accountable. Recovery: object removed, cleanup `COMPLETED`, job remains `FAILED`, provider job not retried. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-10 | Concurrent claim — `MariaDbRepositorySmokeTest` | Two independent transactions and a `CyclicBarrier`; `containsExactlyInAnyOrder(0, 1)` for initial claim and expired-lease reclaim. Each case has winner `1`, loser `1`. | Real MariaDB 11.4.13 smoke, run `36555800770` | **PASS — fresh real MariaDB** |
| B5-11 | Stale-worker fencing — `AnalysisJobStateServiceTest.staleGenerationCannotCreateRelationalMetadataOrSucceedAfterReclaim()`, `AnalysisJobRepositoryTest.staleGenerationIsFencedAfterExpiredLeaseReclaim()`, `AnalysisJobRunnerTest.staleOwnerCannotScheduleRetryOrMarkTerminalFailure()`, and `failedHeartbeatBlocksStaleSuccessAndFailureFinalization()` | Stale successful finalization `0`; stale retry scheduling rejected; stale terminal failure rejected; current generation remains authoritative. A stale execution may already have invoked an external provider. | Full `mvn clean test`, run `36555800770` | **PASS** |
| B5-12 | Normal success/readback — `AnalysisJobControllerIntegrationTest.createAnalysisJobReturnsSucceededStateAndResultArtifact()` | POST returns `PENDING`; later GET is `SUCCEEDED`; `resultFileId`, `resultObjectKey`, and `resultPreview` are present; retrieval regression `0`. | Full `mvn clean test`, run `36555800770` | **PASS** |

Accountable cleanup failures in B5-09 can be deferred so later bounded work can advance. This is a
bounded-liveness mechanism, not a universal fairness guarantee.

## 8. Measurements and provenance

| Measurement | Value / observation | Provenance category |
|---|---|---|
| accepted / terminal / stranded | `1 / 1 / 0` for B5-01/B5-02 | controlled source assertion + actual full-suite PASS |
| duplicate delivery / logical duplicate / persistent duplicate | `2 / 0 / 0` for B5-04 | controlled source assertion + actual full-suite PASS |
| retry-success attempts / generations / invocations | `2 / 2 / 2` for B5-06 | test DB/counter assertions + actual full-suite PASS |
| retry exhaustion | three timeout-driven executions, terminal `FAILED`, no fourth scheduled execution | test DB/counter assertions + actual full-suite PASS |
| unaccounted object count | `0` across B5-08/B5-09 | intent/object/state assertions + actual full-suite PASS |
| cleanup pending / recovered | one accountable `PENDING` fixture progresses to `COMPLETED` | controlled source assertion + actual full-suite PASS |
| initial claim contention | winner `1`, loser `1` | real-MariaDB assertion + actual smoke PASS |
| reclaim contention | winner `1`, loser `1` | real-MariaDB assertion + actual smoke PASS |
| retry delay | fixed `10s`; not eligible before due and eligible at due | configured contract + controlled-clock assertion |
| lease duration | `60s` | configured contract |
| poll interval / batch size | `2s / 4` | configured contract |
| restart/reclaim delay | no steal before expiry; reclaim after controlled expiry | controlled-clock assertion; no CI wall-clock claim |
| queue wait | numeric sample not exposed by aggregate quiet-Maven log | instrumentation/assertion available; no performance claim |
| accepted-to-terminal duration | numeric sample not exposed by aggregate quiet-Maven log | no performance claim; B5-09 cleanup update is not mislabeled as terminal time |
| processing duration | numeric sample not exposed by aggregate quiet-Maven log | instrumentation exists and suite passed; no performance claim |
| bounded eligible-scan candidate count | signal exists; no numeric sample reported for closure | `terraformers.analysis.dispatch.scan.candidates` is not global queue depth |

Correctness counts are deterministic fixture observations, not production-population statistics.
Instrumentation/assertions exist and passed in the fresh suite, but the aggregate CI log does not
expose numeric queue-wait, processing-duration, accepted-to-terminal, or percentile samples suitable
for a portfolio performance claim. None of the configured or controlled times is a production SLO.

## 9. Hard acceptance results

| Hard gate | Matrix evidence | Result |
|---|---|---|
| stranded accepted jobs = `0` | B5-01, B5-02, B5-03, B5-06, B5-07 | **PASS** |
| terminal duplicate re-execution/finalization = `0` | B5-04 | **PASS** |
| duplicate logical side effect = `0` | B5-04, with B5-08/B5-09 accountable cross-resource evidence | **PASS** |
| stale-worker successful finalization = `0` | B5-11 | **PASS** |
| unbounded retry = `0` | B5-05, B5-06, B5-07 | **PASS** |
| retry exhaustion left non-terminal = `0` | B5-07 | **PASS** |
| silent/unaccounted object residue = `0` | B5-08, B5-09 | **PASS** |
| normal result retrieval regression = `0` | B5-12 | **PASS** |

These gates are bounded deterministic fixture invariants. They are not fleet-wide rates or
production population statistics.

## 10. Before/after interpretation

The preserved before-state and the fresh after-state cover the same failure classes without
recreating obsolete behavior:

- process-local delivery loss changed from stranded/forced-failure work to durable rediscovery and
  lease-expiry reclaim;
- duplicate terminal delivery changed from re-entry to a rejected second claim with no duplicate
  logical execution or persistent finalization;
- provider timeout changed from terminal-only behavior to selective durable retry with a fixed due
  time and terminal exhaustion;
- object-write/DB-finalization partial success changed from residue risk to deterministic intent,
  fenced finalization, compensation, or durable accountability; and
- cleanup failure changed from unaccounted residue to exact `PENDING` intent recoverable without
  provider re-execution.

This comparison supports correctness under the specified deterministic scenarios. It does not turn
historical evidence into a fresh before run and does not establish scale or latency improvement.

## 11. Complexity and trade-offs

The selected design avoids a second durable broker/outbox system and reuses MariaDB transaction and
ownership semantics. Its cost is additional job state, polling, lease renewal, fencing on every
ownership-sensitive mutation, deterministic object identity, and cleanup recovery bookkeeping.
Local executor admission remains non-durable but is safe to lose because eligibility remains in
MariaDB. Polling and the bounded batch can introduce queue delay and database load; B5 records this
as a trade-off rather than claiming capacity. Case C owns capacity and bottleneck validation.

## 12. Residual limitations

Case B closure does **not** prove:

- exactly-once provider invocation;
- zero duplicate provider calls under every crash timing—a stale worker may have invoked an
  external provider before fencing can reject its later state mutation;
- production-scale throughput;
- a queue-wait SLO;
- a recovery-latency SLO;
- multi-service distributed durability;
- universal rejection of RabbitMQ or Transactional Outbox; or
- a generic cleanup fairness theorem.

MariaDB remains the durable source of truth. The executor remains a bounded local concurrency pool.

## 13. Case B closure decision

```text
B5 = PASS
Case B integrated closure = PASS
Case B = portfolio-closed
```

All twelve required rows have direct current assertions, the full backend suite passed freshly on
the exact execution SHA, the concurrent claim/reclaim smoke passed freshly against real MariaDB,
and all hard correctness gates pass. This is a correctness closure, not production-scale validation.

The project sequence remains `Case B → Case A → Case C`. Case A is the next candidate, but it
requires a separate user-approved decision/checkpoint. This closure does not authorize Case A
implementation and does not start Case C.

## 14. Exact validation evidence

Authoritative execution:

```text
Workflow: Backend Local Verification
Run ID: 36555800770
Event: workflow_dispatch
Branch: main
Head SHA: 164fe2f5a83c9d9eba44eca351db604368940598
Overall conclusion: SUCCESS
```

Backend job:

```text
Backend local smoke baseline — SUCCESS
bash scripts/checks/backend-local-verification.sh
[backend] running Maven clean tests
[backend] packaging application without re-running tests
[backend] local verification completed
```

The script's relevant commands are:

```text
bash scripts/checks/flyway-migration-uniqueness.sh
mvn -q -e clean test
mvn -q -DskipTests package
```

MariaDB job:

```text
MariaDB schema and repository validation — SUCCESS
MariaDB 11.4.13-MariaDB-ubu2404
[mariadb] application health check passed
[mariadb] Flyway migration and Hibernate schema validation passed
[mariadb] running canonical repository smoke queries
[mariadb] canonical repository smoke queries passed
```

The existing validation path runs:

```text
bash scripts/checks/mariadb-schema-validation.sh
mvn -q -Dtest=MariaDbRepositorySmokeTest test
```

No failure diagnostic artifact was required because both jobs passed. Documentation validation for
this closure uses `git diff --check`, changed-path inspection, and contradiction/overclaim searches;
no new verifier or workflow was added.
