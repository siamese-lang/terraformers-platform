Resume the interrupted **PT-6R2 — Provider terminality / latency-budget closure** work on the existing branch and **PR #254**.

Do not restart the phase, create a new PR, re-run the completed audit, or rebind the execution base.

## 0. Recover the existing interrupted state

Authoritative PT-6R2 execution base remains:

`406981a8c629a02503f5660453dcc7921b8d5177`

Existing audit-only PR:

`#254`

Expected last pushed audit head:

`888255c979914d03e7794a26c2ed1a0f49d5f18d`

Current remote `main` was verified to remain exactly the bound execution base before this resume instruction.

Recover:

- `AGENTS.md`
- startup skill / repository protocol
- `.agents/programs/product-trust-v1.yml`
- `.agents/state/product-trust-v1.json`
- `.agents/work-packages/product-trust-pt-6r2-provider-terminality-v1.yml`
- `docs/evaluation/product-trust-pt-6r2-provider-terminality.md`
- `docs/evidence/product-trust-pt-6r2/audit.json`
- `docs/evidence/product-trust-pt-6r2/latency.json`
- existing PR #254 and its current remote head.

If the interrupted workspace contains legitimate uncommitted work, inspect and reconcile it with the current PR branch before editing. Do not blindly discard it.

Do not rebase, refresh, merge main, or change the once-bound execution base.

If remote main has changed after the bound Work Package began, STOP:

`HUMAN_REQUIRED: MAIN_DRIFT`

## 1. USER decision — Option D is approved

The unresolved `PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION` is now resolved by explicit USER decision.

Select **Option D**.

Approved policy:

- architecture-facts GenAI request timeout: **370 seconds**
- generation request timeout: **220 seconds**
- grounding-repair request timeout: **220 seconds**
- embedding request timeout: **10 seconds**
- Google GenAI SDK attempts: **1**
- SDK retryable HTTP statuses: **empty / none**
- durable AnalysisJob attempts: **1**
- original accepted-age job cutoff: **8 minutes**
- MAX_TOKENS compact-generation fallback: **preserved**
- no new durable retry for rate limits, generic provider errors, content failures or response-format failures.

Record the USER decision durably in the existing PT-6R2 evidence/state/Work Package.

This is not an 8-minute latency SLO.

The contract is:

`original acceptedAt cutoff = 8 minutes`

plus a **bounded terminal-transition delay Δ** that must be established by the implementation and deterministic validation.

Do not claim arbitrary Java worker-thread hard cancellation.

## 2. Preserve the already accepted audit findings

Do not redo the SDK investigation unless implementation exposes a contradiction.

The existing audit established, among other things:

- Google GenAI Java SDK remains pinned at `1.72.0`;
- an SDK upgrade is not required for this correction;
- current production has no finite GenAI request timeout;
- SDK defaults currently install hidden retry behavior;
- one maximal AnalysisJob attempt can contain up to six logical GenAI calls;
- plain SDK call timeout may surface as:

`GenAiIOException -> InterruptedIOException("timeout")`

and the current classifier misses that form;
- independent heartbeat renewal allows a blocked provider worker to remain RUNNING indefinitely;
- current lease reclaim SQL does not make `maxAttempts` an absolute process-loss attempt limit.

Treat these as audit inputs, not work to repeat.

## 3. Implement the approved GenAI request budgets

Use the exact audited Google GenAI **1.72.0** APIs.

Prefer the smallest coherent configuration.

Requirements:

### SDK retry policy

Explicitly disable hidden SDK retries for the production GenAI client:

- attempts = 1;
- retryable HTTP status list = empty;
- do not rely on SDK defaults.

Do not enable another retry layer.

### Per-call timeout budgets

Apply request-level budgets so the distinct approved values are preserved:

- facts extraction: 370 s;
- normal generation: 220 s;
- compact MAX_TOKENS generation fallback: 220 s;
- grounding repair generation: 220 s;
- query embedding: 10 s;
- closure embedding: 10 s.

Do not collapse them into one 370-second client timeout.

Expose the budgets through bounded typed runtime configuration rather than scattered magic numbers.

Production defaults must implement Option D.

Do not change model IDs, embedding dimensions, prompts, retrieval semantics or output-token policy.

## 4. Correct timeout classification

Update the provider timeout classification only as required by the audited real SDK timeout shape.

The chain:

`GenAiIOException -> InterruptedIOException("timeout")`

must be classified as an `AnalysisProviderTimeoutException` / provider timeout.

Do **not** classify every `InterruptedIOException` as a timeout.

Add regression coverage proving that:

- the actual timeout-shaped `InterruptedIOException("timeout")` is recognized;
- ordinary interruption/non-timeout `InterruptedIOException` is not silently converted to a provider timeout;
- existing `SocketTimeoutException` / `HttpTimeoutException` behavior remains intact.

## 5. Enforce the 8-minute original accepted-age cutoff

Request timeouts alone do not satisfy PT-6R2.

Implement a durable job-age terminal boundary anchored to the **original persisted acceptance/creation time**.

The deadline must never reset on:

- claim;
- lease renewal;
- retry/reclaim;
- worker restart.

With Option D there is one durable attempt, but the deadline mechanism must still be based on original accepted age.

The implementation must cover both:

- queued/PENDING work that has exceeded the cutoff;
- RUNNING work whose provider thread remains blocked while heartbeat renewal remains healthy.

Use the existing durable dispatcher/state/repository architecture where possible. Do not add another distributed job system or new technology.

### Required fencing behavior

Once the durable job is terminalized by the accepted-age cutoff:

- status must remain terminal FAILED;
- lease/next-attempt ownership must no longer allow success;
- a provider thread that later returns must not restore SUCCEEDED;
- late result-object intent must be rejected;
- late result write/finalization must be rejected or safely compensated through the existing ownership/cleanup boundary;
- heartbeat renewal after deadline must not resurrect/extend the job;
- a stale/process-loss reclaim must not create an attempt beyond the approved durable-attempt limit.

Do not merely set an in-memory cancellation flag.

The database state is the authority.

## 6. Make durable attempt=1 real, not cosmetic

Current audit found that process-loss lease reclaim can increment attempts independently of the configured application retry branch.

Option D requires one durable attempt in reality.

Audit and minimally correct claim/reclaim eligibility so a job that has already consumed its single approved attempt cannot be repeatedly reclaimed after lease expiry.

An exhausted/over-age job must reach an explicit terminal disposition rather than disappearing forever from eligibility while remaining PENDING/RUNNING.

Do not introduce a second retry path.

## 7. Bound and prove Δ proportionally

The USER approved an **8-minute accepted-age cutoff**, not an exact 480000 ms hard thread kill.

Establish the smallest defensible bounded Δ from the actual scheduling/state-transition mechanism.

Prefer reuse of the existing dispatch/scheduling cadence over a new scheduler when that provides a clean bounded mechanism.

Deterministically verify at minimum:

- an already over-age PENDING job terminalizes without starting provider work;
- a RUNNING job with a simulated never-returning provider can be durably failed after cutoff while its worker is still blocked;
- after releasing that blocked provider, late completion cannot overwrite FAILED;
- healthy heartbeat renewal cannot defeat the cutoff;
- no second durable attempt occurs;
- cutoff is anchored to original accepted time;
- a job just inside the cutoff is not prematurely failed;
- terminal reason/quality remains truthful and consistent with PT-4;
- timeout metrics/status do not claim normal provider completion.

If a DB transaction or existing result-finalization lock makes the claimed bounded Δ impossible to establish without a materially broader architecture change, **do not silently redesign finalization**.

STOP and report:

`HUMAN_REQUIRED: PT6R2_TERMINAL_ENFORCEMENT_SCOPE_DECISION`

with the exact blocker.

Do not weaken the approved cutoff merely to make tests green.

## 8. Scope of the terminal guarantee

PT-6R2 primarily closes the demonstrated defect:

`accepted job remains provider-bound RUNNING indefinitely`

Do not expand into broad GCS/OpenSearch/MariaDB/network timeout hardening merely because unrelated external operations could theoretically stall.

However, the accepted-age cutoff/fencing must remain authoritative enough that a late provider result cannot produce false success after terminalization.

Document any residual non-provider terminality boundary explicitly rather than claiming universal process/network hard deadlines.

## 9. Preserve existing product behavior

Must remain unchanged except for the approved terminality correction:

- MAX_TOKENS compact fallback;
- one generated-resource closure/repair cycle;
- retrieval semantics;
- provider/model selection;
- PT-4 truthful user waiting/status semantics;
- PT-3/PT-5/PT-6/PT-6R1 behavior;
- frozen AWS official revision-2 truth;
- PT-2 `424846 ms` historical censor;
- existing historical evidence/counters.

Do not call the model.

Do not use live GCP/OpenSearch.

Do not rerun PT-2.

Do not rerun MariaDB PT-6R1 measurements.

## 10. Testing

Use deterministic local tests/fakes only for the timeout/hang mechanics.

No sleeping for hundreds of real seconds.

Use controllable clock, latch/future, fake client/test seam and/or scheduler invocation so the 370/220/10-second budgets and eight-minute cutoff can be tested immediately.

Add the minimum durable regression coverage necessary.

At minimum validate:

- exact configured request budgets;
- SDK attempt/retry policy;
- timeout exception classification;
- facts timeout;
- generation timeout;
- embedding timeout;
- cutoff for PENDING;
- cutoff for RUNNING blocked provider;
- late-success fencing;
- one-attempt enforcement;
- existing non-timeout provider failures remain non-retryable;
- MAX_TOKENS compact fallback is preserved.

Run focused tests first.

Then run the existing proportional backend regression suite required by this Work Package.

Do not create a new workflow/evaluator/test framework.

## 11. PT-6R2 evidence

Update the existing PT-6R2 evidence rather than replacing the audit.

Preserve the original audit/decision evidence and append the USER-selected policy and implementation evidence.

Record:

- selected Option D;
- exact approved values;
- exact USER decision authority;
- changed production files;
- deterministic test cases/results;
- established Δ or bounded scheduler/state transition statement;
- timeout classification;
- SDK retry policy;
- durable attempts=1;
- late-result fencing result;
- residual claim boundaries;
- zero live model/cloud/OpenSearch actions.

Do not rewrite historical audit findings to make them look as if D had been selected originally.

## 12. Same PR only

Continue on **PR #254**.

Do not create PR #255 for this phase.

Do not create a separate state-sync PR.

Update the existing PR title/body from audit-only wording once implementation/evidence is complete.

Do not merge.

Do not self-accept.

Do not start PT-7.

## 13. Completion check

Before returning:

- confirm remote main still equals the once-bound execution base;
- report final PR #254 head SHA;
- report exact changed files;
- report selected Option D configuration;
- report exact terminality implementation;
- report deterministic tests and backend regression results;
- report bounded Δ and its assumptions;
- report residual risks;
- confirm frozen AWS candidate bytes remain unchanged;
- confirm zero model calls;
- confirm zero GCP/OpenSearch/live actions;
- run `git diff --check`;
- ensure normal PR required CI is allowed to run; do not use `[skip ci]`.

Then stop for independent review.

Expected final state:

`PT-6R2 IMPLEMENTATION COMPLETE / INDEPENDENT ACCEPTANCE PENDING`

Do not mark PT-6R2 independently accepted yourself.
