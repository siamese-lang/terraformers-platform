Start **PT-6R2 — Provider terminality / latency-budget closure** from current authoritative main.

This first step is an **audit + decision-gate preparation**. Do not silently choose a numeric timeout or retry policy and do not run live model/cloud actions.

## 0. Mandatory start gate

Expected current remote main after USER-merged PR #253:

`406981a8c629a02503f5660453dcc7921b8d5177`

Read GitHub remote main exactly once at activation and bind that exact SHA as the PT-6R2 `execution_base_sha`.

If remote main differs before binding, report the actual reason. Once bound, any later drift is:

`HUMAN_REQUIRED: MAIN_DRIFT`

Read:

- `AGENTS.md`
- `docs/AI_PROJECT_STATE.md`
- `.agents/programs/product-trust-v1.yml`
- `.agents/state/product-trust-v1.json`
- `docs/plans/active/product-trust-modernization.md`
- current Product Trust v4 evidence-validity audit
- merged PR #253
- independent acceptance review `6032563396`
- USER approval evidence comment `6032637762`
- current PT-6R2 Program contract
- PT-4 waiting/status evidence
- PT-2 final disposition and the original `424846 ms` censor
- historical production-equivalent latency evidence relevant to GenAI stages
- current Google GenAI provider implementation and tests.

## 1. Reconcile the v4 approval without mutating the frozen candidate bytes

USER explicitly approved:

candidate revision: `2`

candidate identity:

`3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757`

PR #253 was USER-merged as:

`406981a8c629a02503f5660453dcc7921b8d5177`

The approval was recorded after merge in PR comment:

`6032637762`

Reconcile this approval in the **same PT-6R2 substantive PR**.

Do not create a state-sync-only PR.

Important: the approved candidate identity binds the exact candidate manifest/truth bytes.

Therefore do **not** edit:

- `evaluation/terraformers-aws-official-v1/candidate-manifest.json`
- `evaluation/terraformers-aws-official-v1/candidate-identity.json`
- `docs/evaluation/product-trust-aws-official-truth-candidate.md`

merely to change `truthFrozen`, approver or approval timestamps.

Use the same immutable-freeze pattern already used for historical PT-1: preserve the approved candidate bytes and record subsequent approval/freeze evidence externally in Program/state/approval evidence.

Record at minimum:

- exact candidate revision and identity;
- USER approval;
- approval comment `6032637762`;
- independent review `6032563396`;
- merged PR #253 / main SHA;
- frozen status for future PT-8A authority;
- model-under-test run count remains zero;
- no live authority implied by truth freeze.

Then activate PT-6R2 on the once-bound current main.

## 2. Establish the actual current provider boundary before changing code

Current repository observations must be independently verified, not assumed from this instruction.

Known starting points to audit:

- Maven dependency is expected to be `com.google.genai:google-genai:1.72.0`.
- `VertexRuntimeConfiguration` is expected to create one shared `Client` with Vertex AI and `apiVersion("v1")`.
- no explicit GenAI request timeout is currently expected.
- no explicit SDK retry options are currently expected.
- production application default:
  - `ANALYSIS_MAX_ATTEMPTS=3`
  - `ANALYSIS_RETRY_DELAY=10s`
- application retry currently treats `AnalysisProviderTimeoutException` as retryable.

Verify all of these from exact bound source.

## 3. Audit exact Google GenAI Java SDK 1.72.0 semantics

Do not rely only on current/latest SDK documentation.

Inspect the exact **1.72.0** artifact/source/Javadoc available through Maven cache or the official tagged source.

Establish with evidence:

1. `HttpOptions.timeout`
   - whether supported;
   - exact unit;
   - whether it is client-level, request-level or both;
   - precedence/merge behavior when both client and request values exist.

2. transport timeout behavior
   - what exception class/cause is produced;
   - whether current `ProviderFailureClassifier.isTimeout()` recognizes it.

3. SDK retries
   - whether requests are retried when `HttpRetryOptions` is absent;
   - default attempt count/statuses only if retry options are explicitly enabled;
   - whether timeout and retries can compound.

4. API/server deadline behavior
   - whether client timeout also sets a server timeout/deadline header in 1.72.0;
   - distinguish client-side bounded termination from any Vertex backend deadline.

5. cancellation/thread behavior
   - what can reasonably be guaranteed when the client timeout expires;
   - do not claim hard thread cancellation if the SDK does not provide it.

Record exact source/tag/class/method evidence.

Do **not** upgrade the SDK unless the audit proves 1.72.0 cannot provide the required bounded contract.

A newer SDK version existing is not sufficient reason to change dependencies.

## 4. Enumerate every external provider call in one real AnalysisJob attempt

Trace exact production source from accepted job to terminal state.

At minimum inspect:

- architecture facts extraction;
- query embedding;
- initial OpenSearch retrieval;
- initial generation;
- truncation compact retry if applicable;
- generated-resource grounding closure retrieval;
- grounding repair generation if applicable;
- any additional embedding call associated with closure retrieval.

Do not assume a fixed count from this instruction. Derive the actual maximum path from source.

Produce a stage/call table containing:

- call type;
- Google model/API involved;
- whether it uses the shared GenAI client;
- current timeout;
- current SDK retry behavior;
- application-level retry behavior;
- maximum invocations inside one AnalysisJob attempt;
- whether a timeout is translated into `AnalysisProviderTimeoutException`;
- resulting durable job behavior.

Also distinguish OpenSearch/GCS calls from GenAI calls. Do not expand this phase into unrelated storage/network hardening unless one of those calls can independently violate the exact PT-6R2 accepted-job terminality contract.

## 5. Prove the present defect mechanically

Answer:

**Can one accepted AnalysisJob remain RUNNING indefinitely or materially unbounded while a Google provider request never returns?**

Inspect:

- synchronous provider call structure;
- AnalysisJob worker thread;
- lease heartbeat scheduling;
- lease renewal while provider thread is blocked;
- retry scheduling;
- max-attempt handling;
- durable failure transition.

Do not use a live Gemini request to prove this.

Use source evidence and, only if needed, a deterministic test seam/fault fixture that does not yet select the final numeric timeout.

If heartbeat can continue renewing while the provider worker remains blocked, record that explicitly.

## 6. Audit existing historical latency evidence

Do not make new model calls.

Collect the existing repository evidence needed for a human timeout decision.

At minimum preserve and distinguish:

- PT-2 case-02 censor: `424846 ms`
  - do not reinterpret it as successful provider duration;
  - do not claim exact upstream root cause if not established.

- production-equivalent broad-v4 historical stage timings, including any observed long-tail fact extraction/generation durations.

For every usable historical latency value record:

- run/artifact;
- stage;
- observed duration;
- whether completed or censored;
- whether it used the same model/current path;
- whether later source/model changes limit comparability.

Do not fabricate percentiles from tiny samples.

## 7. Calculate candidate terminal-bound options, but do not choose one

The Program explicitly reserves the numeric budget/retry tradeoff for USER decision.

Produce **2–4 materially different bounded options**.

For each option calculate the worst-case upper bound from:

- per-request timeout(s);
- maximum sequential GenAI requests inside one attempt;
- truncation/repair behavior;
- SDK retries if enabled;
- application `maxAttempts`;
- retry delay;
- known non-provider bounded work where relevant.

Do not present a fake precise SLO.

The options should make tradeoffs clear, for example:

- more tolerant of historical long-tail provider latency but slower failure;
- tighter user-visible terminality with greater risk of timing out legitimate slow calls;
- fewer application retries versus current `maxAttempts=3`;
- one common GenAI timeout versus distinct embedding/fact/generation budgets if the source and SDK cleanly support that distinction.

Do not preselect specific numbers merely because they are round.

Ground proposed values in historical evidence and actual call topology.

## 8. Retry policy must be explicit

Audit two separate retry layers:

1. Google GenAI SDK transport retry;
2. Terraformers durable AnalysisJob retry.

Avoid accidental multiplicative retries.

If the current SDK has no automatic retry without `HttpRetryOptions`, record that.

If enabling SDK retries is considered, compare it explicitly with keeping retries at the durable job layer.

Do not silently begin retrying rate limits, generic 5xx/provider errors or content/format failures unless that policy is explicitly selected.

The current supported retry taxonomy must remain clear.

## 9. Stop at the required product decision

If a numeric request/job timeout or retry-count choice is necessary—as expected—STOP at:

`HUMAN_REQUIRED: PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION`

Do not implement the selected timeout before USER approval.

Prepare one concise decision brief containing:

- confirmed root mechanism;
- exact SDK capability;
- actual production call graph;
- present unbounded/bounded behavior;
- historical latency evidence;
- candidate options;
- worst-case terminal bounds;
- retry semantics;
- recommended option with technical rationale;
- residual risks.

The recommendation is allowed.

The decision itself is not.

## 10. Same PT-6R2 PR / branch

This is one PT-6R2 Work Package.

Create one branch/Work Package and one PR for PT-6R2 if repository protocol requires durable review material.

Do not create a separate state-sync PR or a separate “decision-only final” PR.

Before USER budget decision, the PR may contain only:

- PT-6R2 Work Package;
- v4 approval/freeze reconciliation outside candidate bytes;
- audit/decision evidence;
- necessary Program/state/plan updates showing the decision gate.

Do not touch production timeout/retry code before approval.

After USER selects the policy, continue on the **same PR** with the bounded implementation and deterministic timeout/hang validation.

## 11. Prohibited now

Do not:

- call Vertex/Gemini;
- mutate GCP;
- query or ingest OpenSearch;
- deploy/publish images;
- run final AWS official acceptance inputs;
- change frozen AWS candidate/truth bytes;
- change generation/retrieval semantics;
- upgrade SDK speculatively;
- add another workflow;
- add an evaluator/model judge;
- choose a latency SLO;
- rerun PT-2;
- rerun PT-6R1 MariaDB evidence;
- start PT-7.

## 12. Validation for this audit step

Proportional repository-only validation only:

- exact SDK/source evidence binding;
- Program/state/WP consistency;
- truth-freeze approval reconciliation;
- frozen candidate bytes unchanged;
- PT-6R2 base binding;
- call graph consistency;
- historical evidence references;
- option-bound arithmetic;
- YAML/JSON parse;
- `git diff --check`.

Normal automatic PR CI may run.

Do not use `[skip ci]`.

## 13. Return

Return:

- bound PT-6R2 execution base SHA;
- branch / PR if opened;
- changed files;
- candidate truth-freeze reconciliation evidence;
- exact Google GenAI 1.72.0 timeout/retry findings;
- actual production provider-call graph;
- current terminality defect/mechanism;
- historical latency table;
- candidate budget/retry options and calculated upper bounds;
- recommended option;
- exact unresolved USER decision;
- confirmation of zero live model/cloud/OpenSearch actions.

Stop at:

`HUMAN_REQUIRED: PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION`