# ADR-007: Use MariaDB as the durable source of truth for AnalysisJob execution

## Status

**Accepted for bounded implementation**

This ADR authorizes the Case B implementation direction only. It does not authorize implementing all
stages in one change or automatically progressing between stages.

## Context

Case B requires an accepted analysis request to survive application-process restart without forcing
the user to submit a new job.

The measured current flow is:

`API → analysis_jobs(PENDING) commit → afterCommit callback → in-memory executor → claim → analysis`

PR #94 and
[Case B Measurement Readiness Evidence](../../evaluation/case-b-measurement-readiness.md)
established that:

- the committed MariaDB job outlives the in-memory delivery mechanism;
- an accepted-but-not-started job is currently marked `FAILED` after restart;
- a claimed `RUNNING` job is currently marked `FAILED` after restart rather than reclaimed;
- terminal duplicate execution is already controlled by an atomic conditional claim;
- two real MariaDB transactions contending for one PENDING job admit exactly one winner;
- transient provider failure currently has no retry;
- rollback-safe object compensation works for the measured DB-finalization failure;
- cleanup failure can leave an object residue without a durable accountability record.

The target is not “add a message queue technology.” The target is durable ownership and bounded
recovery for the measured AnalysisJob lifecycle.

## Decision gate alternatives

### 1. Current in-process executor + restart-to-FAILED reconciliation

**Decision: REJECT as final Case B design**

Advantages:

- lowest implementation and operational complexity;
- current executor and atomic claim already work for a live process.

Why rejected:

- the measured durability gap remains;
- DB commit can succeed while execution exists only in process memory;
- process ownership loss converts accepted work into terminal failure;
- no bounded automatic retry exists.

It remains useful as historical before-state and as an execution primitive.

### 2. MariaDB-backed durable job queue / lease / bounded retry

**Decision: ACCEPT**

MariaDB is already the system of record for `AnalysisJob`. The committed job row becomes the
durable work source.

A worker/poller selects eligible work from MariaDB and offers it to the existing bounded executor.
Only after the submitted runnable begins does it atomically acquire a time-bounded lease before
performing provider work.

The design must include:

- durable eligibility for PENDING/retryable work;
- atomic claim;
- lease expiry and reclaim;
- fencing token/generation so a stale worker cannot finalize after another worker reclaims;
- bounded attempt count and retry schedule;
- explicit retryable versus terminal failure classification;
- deterministic result-object identity for one logical job;
- durable accountability for result-object cleanup residue;
- mechanism-specific observability for claim/reclaim/retry/terminal outcomes.

Why accepted:

- directly closes the measured DB-to-memory delivery gap;
- uses the existing MariaDB system of record and proven conditional-claim primitive;
- does not create a second durable system whose publication must be coordinated with the DB;
- preserves cloud portability;
- has lower operational and consistency complexity than broker + outbox for the currently measured
  workload;
- can be validated using the existing deterministic Case B failure matrix.

### 3. RabbitMQ durable delivery without Transactional Outbox

**Decision: REJECT as the final design for the current requirement**

RabbitMQ provides mature acknowledgement, redelivery, routing, and consumer semantics, but adding it
after committing the AnalysisJob introduces a new failure window:

`DB commit succeeds → process fails before broker publish`

That is the same class of “accepted work exists but delivery did not happen” problem Case B is meant
to remove.

Using RabbitMQ alone therefore does not provide an atomic accepted-work contract.

RabbitMQ may be reconsidered if later measured requirements justify an independent broker, such as
higher queue throughput, fan-out, multiple independent consumer classes, or service decomposition.

### 4. Transactional Outbox + RabbitMQ

**Decision: DEFER**

This design can close the DB-commit/publication gap by writing the job/outbox record in one DB
transaction and asynchronously publishing to RabbitMQ.

It is not rejected technically. It is deferred because the current evidence does not require the
additional durable outbox state, publisher/relay, broker operations, redelivery layer, and cleanup
lifecycle.

Reopen this decision if the MariaDB-backed queue becomes a measured bottleneck or if a future
architecture needs independent consumers, event fan-out, or broker-specific delivery semantics.

## Selected architecture

The selected logical flow is:

`API → MariaDB AnalysisJob commit → durable eligibility → bounded executor scheduling → runnable begins → atomic durable lease/fencing claim → provider work → fenced finalization`

MariaDB is the durable source of truth for work ownership.

The existing `ThreadPoolTaskExecutor` remains useful, but its role changes:

- **before:** in-memory delivery is required for the job to execute;
- **after:** the executor only limits local concurrent execution of work that remains durably
  represented in MariaDB.

Executor queue admission is local and non-durable, so it does not constitute durable ownership.
Pre-claiming before a runnable starts could consume a lease for work that is rejected, remains
queued when the process dies, or never starts. Ownership therefore begins only when the runnable
starts and successfully claims; provider work cannot begin before that claim. MariaDB eligibility
scanning makes a lost local scheduling attempt recoverable. A crash after claim may lose the local
task, but lease expiry makes the same logical job eligible for reclaim.

## Durable state contract

The implementation may refine names, but the persistent contract must represent at least:

- attempt count;
- next eligible attempt time;
- lease expiry;
- claim/fencing generation or opaque claim token;
- durable result-object intent/reference needed to identify possible residue;
- cleanup/recovery state where an object may require later removal.

Do not encode worker identity or high-cardinality operational details unless required by a proven
correctness invariant.

### Fencing requirement

Lease expiry alone is insufficient.

Required scenario:

1. Worker A claims generation/token A.
2. A stalls beyond lease expiry.
3. Worker B reclaims with generation/token B.
4. B becomes current owner.
5. A resumes late.

A must not be able to commit success, failure, retry scheduling, or cleanup state using stale token A.

All ownership-sensitive transitions must therefore validate the current fencing value.

## Retry contract

Retry is bounded and selective.

Initial implementation must distinguish:

**Retryable candidates**
- provider/network timeout;
- explicitly classified transient upstream failure;
- other failure classes only when evidence justifies retry.

**Terminal/non-retryable candidates**
- rejected architecture input;
- deterministic response-format/semantic failure unless a separate policy is approved;
- output truncation under unchanged configuration;
- deterministic Terraform validation failure;
- authorization or invariant violation.

The exact maximum attempt count and retry delay are configuration choices to be frozen before the
retry implementation stage and validated with deterministic tests.

Do not add generic catch-all retry.

## Result persistence and idempotency

The current result-storage shape remains valid:

`analysis result → object storage + relational project/result metadata → REST read → frontend polling`

No message broker is required for result delivery to the UI.

However durable re-execution requires stronger result idempotency:

1. one logical job must have a deterministic result-object identity;
2. retry/reclaim must not create an unbounded sequence of object keys;
3. only the current fenced owner may make the job/result terminally visible;
4. stale workers must not overwrite a newer finalized result.

The current result key uses `Instant.now()` for the date path. A bounded implementation must make
same-job retries resolve to the same logical result identity, for example by deriving the date/path
from immutable job creation identity rather than attempt time.

## Cross-resource cleanup contract

Immediate compensation remains useful, but cleanup failure must not become silent residue.

If an object may exist while relational finalization did not complete, MariaDB must retain enough
durable information to answer:

- which object may exist;
- whether cleanup is required;
- whether cleanup succeeded or remains pending;
- whether terminal job state is safe to expose.

A separate message broker, cleanup microservice, or outbox is not required by this ADR.

The minimum design should keep accountability in the AnalysisJob lifecycle unless evidence shows
that a separate component is necessary.

## Before/after acceptance contract

The selected implementation must invert or preserve the existing failure matrix:

1. **Accepted but not started**
   - before: restart → FAILED;
   - after: same job remains recoverable and reaches a valid terminal outcome without user
     resubmission.

2. **Claimed RUNNING process loss**
   - before: restart → FAILED;
   - after: expired lease becomes reclaimable.

3. **Duplicate delivery**
   - no regression: logical execution/finalization side effect remains once.

4. **Transient failure**
   - before: one attempt → FAILED;
   - after: approved transient failure can retry and succeed within the bound.

5. **Retry exhaustion**
   - bounded attempts only;
   - explicit terminal FAILED;
   - no indefinitely non-terminal job.

6. **Concurrent claim**
   - exactly one current owner.

7. **Stale-worker fencing**
   - stale owner cannot finalize after reclaim.

8. **Object write then DB finalization failure**
   - successful immediate compensation or durable cleanup accountability.

9. **Cleanup failure**
   - residue, if any, remains durably attributable and enters a bounded recovery path.

10. **Normal success regression**
    - upload → analysis → Terraform persistence/readback → frontend-visible result remains correct.

Hard Case B acceptance invariants from the Measurement & Acceptance Contract remain unchanged.

## Performance and recovery thresholds

Do not invent arbitrary service-level numbers.

For bounded implementation:

- recovery must occur within the configured lease/poll/retry contract;
- queue-wait, reclaim duration, retry count/delay, accepted-to-terminal duration, and queue depth
  must be measurable;
- concrete numeric configuration is frozen before the implementation stage that depends on it;
- later Case C load testing determines whether MariaDB-backed queue throughput or polling becomes a
  capacity bottleneck.

## Consequences

### Positive

- accepted-work durability is tied to the existing DB transaction boundary;
- no DB→broker publication gap is introduced;
- current atomic-claim work is reusable;
- current executor remains useful for bounded local concurrency;
- cloud-neutral application/storage/provider boundaries remain intact;
- final result retrieval can continue through existing REST API and frontend polling.

### Trade-offs

- MariaDB becomes both business system of record and work-queue substrate;
- polling creates database read/write load that must be measured in Case C;
- lease/retry/fencing state increases lifecycle complexity;
- retry cannot guarantee exactly-once external provider invocation;
- cross-resource object consistency still requires explicit accountability.

The goal is **effectively-once logical finalization**, not a false claim of exactly-once execution.

## Reopen conditions

Reconsider RabbitMQ or Outbox + RabbitMQ if measured evidence shows one or more of:

- MariaDB polling/claim load materially limits required capacity;
- independent consumer services are needed;
- fan-out/routing becomes a real requirement;
- queue operational isolation becomes necessary;
- broker-specific delivery semantics materially simplify a measured problem;
- service decomposition makes DB sharing inappropriate.

## References

- [ADR-004: Gate architectural changes on reproducible evidence](ADR-004-change-gates.md)
- [Case B Measurement Readiness Evidence](../../evaluation/case-b-measurement-readiness.md)
- [Portfolio Case Measurement & Acceptance Contract](../../plans/active/portfolio-case-measurement-contract.md)
- [Case B durable-processing implementation plan](../../plans/active/case-b-durable-processing-implementation.md)
