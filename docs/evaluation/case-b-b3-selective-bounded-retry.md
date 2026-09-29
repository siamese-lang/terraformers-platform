# Case B B3 Selective Bounded Retry Evidence

## Status and identity

**B3 COMPLETE — B4 SPECIFICATION NEXT**

- Case: **Case B — Durable Asynchronous AnalysisJob Processing**
- Stage: **B3 — Selective bounded retry**
- ADR: [ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md)
- Implementation plan:
  [Case B Durable Processing — Bounded Implementation Plan](../plans/active/case-b-durable-processing-implementation.md)
- B2 evidence:
  [Case B B2 Durable Dispatcher and Restart Recovery Evidence](case-b-b2-durable-dispatch-recovery.md)
- PR #101 final head: `f3aa4e488e1858f84688d7b848a4b1af07538656`
- Merge commit: `fb1bc3f7b64a0a0274114f1e29ac9f28f2ae652d`

## 1. Frozen retry contract

The accepted B3 retry configuration is:

```text
max attempts = 3
retry delay = 10s
retry shape = fixed delay
backoff = none
jitter = none
```

Production bindings are:

```text
terraformers.analysis.max-attempts = 3
ANALYSIS_MAX_ATTEMPTS = 3

terraformers.analysis.retry-delay = 10s
ANALYSIS_RETRY_DELAY = 10s
```

The existing B2 configuration remains:

```text
dispatch poll = 2s
dispatch batch size = 4
lease duration = 60s
lease renewal = 20s
```

These values are initial operational configuration, not SLOs.

## 2. Attempt-budget semantics

B3 reuses the durable `attempt_count`; there is no separate retry counter. `attempt_count`
increments on every successful claim or reclaim:

```text
current attempt_count < 3
→ an approved retryable failure may schedule another attempt

current attempt_count >= 3
→ retryable failure is exhausted and becomes terminal FAILED
```

B2 process-loss reclaims therefore conservatively consume the same total attempt budget.
`max-attempts=3` does **not** mean three retries. It means **at most three claimed execution
attempts**. A typical timeout-only path is:

```text
attempt 1 timeout
→ durable retry

attempt 2 timeout
→ durable retry

attempt 3 timeout
→ terminal FAILED
```

## 3. Selective retry classification

The only accepted B3 automatic retry signal is `AnalysisProviderTimeoutException`. A causal
standard network timeout is normalized to that signal at the **analysis-provider boundary**;
currently the recognized causes are `SocketTimeoutException` and `HttpTimeoutException`.

Arbitrary timeout-looking exceptions are not retryable. No class-name or message-substring
heuristic authorizes retry.

The explicit `AnalysisProviderFailureException` semantic classifications remain authoritative:

```text
OUTPUT_TRUNCATED
INPUT_REJECTED
RESPONSE_FORMAT
```

They are terminal in B3. Even when such an exception contains a nested network-timeout cause, the
explicit semantic classification wins and is not reclassified as a retryable timeout. This
precedence was explicitly protected by the final PR #101 correction.

## 4. Side-effect boundary

B3 automatic retry is intentionally limited to a provider timeout before the completed
result/draft write boundary:

```text
analysisProvider.analyze(...)
→ provider timeout classification
→ eligible for bounded durable retry
```

A later failure is not retried merely because it contains a timeout-like cause. The following are
terminal/non-retryable in B3:

- result-object storage failure;
- Terraform validation failure;
- relational result finalization failure;
- compensation/cleanup failure;
- authorization/invariant/configuration failure;
- generic unknown `RuntimeException`.

A result-storage exception caused by `SocketTimeoutException` remains the original storage failure;
it is not normalized to `AnalysisProviderTimeoutException`. This boundary intentionally leaves
cross-resource idempotency and accountability to B4.

## 5. Durable retry transition

The accepted transition is:

```text
owned RUNNING attempt
→ approved provider timeout
→ scheduleRetryOwned()
→ PENDING
→ next_attempt_at = now + 10s
→ lease cleared
→ periodic MariaDB scanner ignores job until due
→ due job offered to bounded executor
→ runnable starts
→ claimEligible()
→ attempt_count + 1
→ claim_generation + 1
→ provider execution
```

There is no thread sleep, delayed executor task, second scheduler, broker, Spring Retry, or
Resilience4j. MariaDB remains the durable retry scheduler through `next_attempt_at`.

## 6. Fencing behavior

Every retry decision remains ownership-fenced. If `scheduleRetryOwned(...)` returns false because
the generation or lease is no longer current:

- do not schedule retry;
- do not fall through to terminal `markFailedOwned`;
- do not overwrite newer ownership;
- record the ownership-lost retry outcome.

Stale workers remain unable to schedule retry, fail, or succeed the job.

## 7. B3 observability

B3 adds bounded retry-outcome telemetry:

```text
terraformers.analysis.retries{outcome=scheduled}
terraformers.analysis.retries{outcome=exhausted}
terraformers.analysis.retries{outcome=ownership_lost}
```

Structured retry-scheduling logs include bounded operational fields such as `attempt`,
`generation`, and `nextAttemptAt`. Job IDs are not metric labels.

## 8. Deterministic evidence

### Retry then success

```text
PENDING
→ claim attempt=1 generation=1
→ provider timeout
→ PENDING
→ next_attempt_at = T+10s
→ lease NULL
→ not eligible at T+9s
→ eligible at T+10s
→ claim attempt=2 generation=2
→ provider succeeds
→ SUCCEEDED
```

Provider execution count: exactly `2`.

### Exhaustion

```text
attempt 1 timeout → retry
attempt 2 timeout → retry
attempt 3 timeout → FAILED
```

Advancing beyond another retry interval produces no fourth provider execution. The terminal state
is:

```text
status = FAILED
attempt_count = 3
next_attempt_at = NULL
lease_expires_at = NULL
```

### Semantic failure

A representative `OUTPUT_TRUNCATED` follows:

```text
one attempt
→ terminal FAILED
→ no scheduleRetryOwned()
```

### Stale owner

```text
provider timeout
→ retry scheduling fencing rejects owner
→ no markFailedOwned fall-through
→ newer state not overwritten
```

### Provider-boundary normalization

```text
provider RuntimeException
  └─ SocketTimeoutException
→ AnalysisProviderTimeoutException
```

### Side-effect boundary

```text
provider succeeds
→ Terraform validation succeeds
→ result storage throws exception containing SocketTimeoutException
→ original storage exception preserved
→ not normalized to provider timeout
```

### Semantic precedence regression

```text
AnalysisProviderFailureException(
    OUTPUT_TRUNCATED,
    SocketTimeoutException
)
→ same AnalysisProviderFailureException
→ terminal semantic failure
```

## 9. Final validation evidence

### Terraform

- Run `36451986173`: **SUCCESS**

### Backend

- Run `36451986210`: **SUCCESS**
- Backend local smoke baseline: **SUCCESS**
- MariaDB schema and repository validation: **SUCCESS**

MariaDB production-profile validation reached:

```text
starting production profile with Flyway and ddl-auto=validate
...
canonical repository smoke queries passed
```

## 10. B3 acceptance result

| Requirement | Result |
| --- | --- |
| retryable provider timeout schedules durable later attempt | **PASS** |
| retry waits until `next_attempt_at` | **PASS** |
| retry succeeds on later claim | **PASS** |
| attempt/generation increment on next claim | **PASS** |
| total claimed attempts bounded at 3 | **PASS** |
| exhaustion reaches FAILED | **PASS** |
| no fourth execution | **PASS** |
| explicit semantic provider failure remains terminal | **PASS** |
| nested timeout cannot override semantic classification | **PASS** |
| storage/finalization boundary not automatically retried | **PASS** |
| stale owner cannot schedule retry | **PASS** |
| retry schedule remains generation/lease fenced | **PASS** |
| B2 restart/reclaim behavior preserved | **PASS** |
| MariaDB regression/concurrency preserved | **PASS** |
| no B4 result/cleanup behavior introduced | **PASS** |

## 11. Explicit residual work

B4 deterministic result identity, durable object accountability, and cleanup execution remain
unresolved. B5 integrated Case B closure also remains pending, so Case B is not portfolio-closed.

> Immediate next single task: prepare the bounded **B4 result idempotency and durable cleanup
> accountability implementation specification**.
