# PT-8A autonomous recovery decision and resume checkpoint

Status: **REPOSITORY IMPLEMENTATION / INDEPENDENT ACCEPTANCE PENDING**.
USER approved `APPROVE_PT8A_A_TO_E_AUTONOMOUS_RECOVERY_REPOSITORY_IMPLEMENTATION_ON_PR265`
after [preparation review 6079812459](https://github.com/siamese-lang/terraformers-platform/pull/265#issuecomment-6079812459).
This authorizes same-PR implementation and at most three bounded repository corrective iterations,
not live execution or Product Trust acceptance. Numeric live budgets are not prerequisites to
writing/testing repository code; they must be bound before actual inference.

Execution remains bound to `c789d433f13da744e14c6a6f31db2d4fd3cf1d74`; no rebase/base refresh.
[The integrated Work Package](../../.agents/work-packages/product-trust-pt-8a-autonomous-recovery-v1.yml)
records this authority separately from the original consumed 1/1 repair budget. The original USER
request attachment SHA-256 remains `40ac59f730f866b61116b6fd15d5d872b0effaef8fb27304b0099db166471074`.

## Verified observations and actual causal boundary

| Observation | Immutable evidence | Disposition and limits |
| --- | --- | --- |
| Original A | [run 37783345572](https://github.com/siamese-lang/terraformers-platform/actions/runs/37783345572), artifact 11552729185, [review 6060977646](https://github.com/siamese-lang/terraformers-platform/pull/262#issuecomment-6060977646) | REJECTED / NOT_PASS / consumed; material semantic defects despite CLI success. Source ba8eb15a37c02184d02da9f1731c15a802477ef9. |
| Product correction | PR #263 merged as f08a4e8440e82806383a561a6e8f0e15e1d81b25; PR #264 [review 6063773526](https://github.com/siamese-lang/terraformers-platform/pull/264#issuecomment-6063773526), merged as current base | Repository correction accepted; live semantic improvement has not been established. Original acceptance guards remain in force. |
| Original diagnostic B | [run 37911522774](https://github.com/siamese-lang/terraformers-platform/actions/runs/37911522774), attempt 1, artifact 11606947643, [review 6078438584](https://github.com/siamese-lang/terraformers-platform/pull/264#issuecomment-6078438584) | Technical failure / NOT_PASS / consumed; terminal FAILED. No final result object/HCL/CLI, so semantic dimensions are NOT_OBSERVED, not semantic FAIL or PASS. |
| Original diagnostic C–E | Original B artifact ledger and independent review, corroborated by complete 55-run main runtime-dispatch history with no later dispatch | NOT_RUN. No source change or new recovery proposal replenishes A/B original budgets. |

B source is the preparation base. Deployed image in the original release readback:
`asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:49a3fd8ba65750d11f0254309f75ffc395bed501697061d6060645cca62f6d0e`.
This is an as-observed binding, not a new live Deployment inspection. Existing [approval 6078152582](https://github.com/siamese-lang/terraformers-platform/pull/264#issuecomment-6078152582)
authorizes only the original B–E diagnostic contract; it is not Recovery Campaign authority.

Authenticated GitHub run/artifact metadata and the downloaded original ZIP agree on B source,
attempt, run and artifact. Archive SHA-256 is
`289519752b3e3f0fc07d4f12dd1717fa306845d59a6a6aa5e93f559e00443ba7`;
all **20** inventory members match their original SHA-256 and byte sizes. They were inspected,
not republished or edited. Job `09e115ca-2963-4681-abf4-062e5decd14d` was accepted once at
2026-10-09 **18:31:16 KST** (09:31:16.532891847Z); durable terminal FAILED occurred at
18:31:57 KST (09:31:57.483080Z), accepted-to-terminal **40,950 ms**.

| Stage | Original evidence |
| --- | --- |
| facts | RECEIVED / STOP; output 327, total 1786 tokens |
| initial_generation | RECEIVED / STOP; output 3214, thinking 563, total 15333 tokens |
| initial retrieval / closure retrieval | Success; 10 and 9 hits, 1700 and 777 ms; 19 official-document hits in total |
| closure | Success reported, 9 hits; this is retrieval success, not proof of semantic evidence sufficiency |
| repair | Failure / finishReason UNAVAILABLE; token counts unavailable |
| persisted result | technical FAIL / knowledge UNKNOWN / quality UNKNOWN / PROVIDER_RATE_LIMITED; no result object |
| real CLI | NOT_RUN / BACKEND_FAILED; no draft to validate |

The earliest observed failure is the single repair provider request after closure retrieval.
Facts/initial generation did not truncate. No evidence supports increasing facts tokens, changing
prompts, raising all generation limits, weakening evidence quality or regenerating the corpus.
The elapsed time and recorded category do not demonstrate a Policy D deadline failure.

`VertexGenerationStage.completedResponse()` catches the original exception, maps it using
`ProviderFailureClassifier`, and logs the **mapped** class plus literal `UNAVAILABLE`.
`UNAVAILABLE` means no completed response in this log; **it does not prove HTTP 503**.
The classifier detects numeric 429 via `statusCode/getStatusCode/code`, or a cause class containing
`throttl`. `TerminalQualityAssessmentMapper` faithfully maps RATE_LIMITED to the persisted reason.
Google GenAI 1.72.0's cached `ApiException.code()` is a valid numeric accessor; ClientException and
ServerException inherit it. Current tests already distinguish 429, throttling and non-429 503.
No demonstrated classifier bug was found. A rate-limit category alone does not identify quota
exhaustion, shared capacity, request concurrency, endpoint provenance or transient recoverability.

Neither the artifact nor downloaded Actions logs contains the original SDK exception/status,
quota details, upstream request ID or repair input-token count. Generation model
`gemini-3.8-flash` is present in the original release identity; actual request project/location,
concurrent external requests and quota/usage at the failure are not captured. `global` is a code
default, not independent proof of the actual request location. Repair's configured 16384 output
tokens / LOW thinking / 220s budget are known; its actual input/output/thinking usage is unknown.
Successful initial request usage must not be relabeled as repair usage.

Read-only evidence still needed: the original correlated provider exception's typed HTTP code and
origin, request project/model/location, any structured quota/capacity signal, and time-correlated
usage/concurrency. The existing GKE log-read contract is `get pods/log`; alternatively a narrowly
scoped Cloud Logging read uses `logging.logEntries.list`, with quota/monitoring read only if needed
to distinguish the observed cause. No IAM addition is requested or performed. Codex has no gcloud/
kubectl connection and its injected GCP credential is an empty object. Do not add a long-lived
credential or dispatch an existing live mutation/evaluation operation merely to inspect logs.

Operator's existing approved read path can use the original backend job filter, without inference:

```text
resource.type="k8s_container"
resource.labels.cluster_name="terraformers-target"
resource.labels.namespace_name="terraformers-target"
resource.labels.container_name="backend"
timestamp >= "2026-10-09T09:30:00Z"
timestamp <= "2026-10-09T09:33:00Z"
"09e115ca-2963-4681-abf4-062e5decd14d"
```

Return only typed code/class/origin/model/location and bounded quota metadata, not prompts, images,
raw provider payloads, credentials or tokens. If those facts were never logged, historical forensic
recovery remains impossible; safe typed telemetry on a future separately approved observation is
an alternative, not permission to replay B.

## GitHub execution authority and automation alternatives

Current GitHub credentials successfully read runs, artifacts, logs, reviews, environment and
ruleset. Repository `permissions.admin=true` does **not** prove installation-token Actions write.
Reading `/actions/permissions` and `/actions/permissions/workflow` returned integration 403;
the latter advertises `administration=read`. This is a denied settings-inspection API, not proof
that workflow dispatch lacks Actions write. The App installation endpoint requires a separate
App authentication form and did not establish the installed App permission set. No settings,
tokens, bypass actors or IAM grants were changed; no mutating permission probe was attempted.

The actual B job's setup log declares **Actions read / Contents read / Metadata read**.
Future child dispatch would require job-scoped `actions: write` for
`POST /repos/siamese-lang/terraformers-platform/actions/workflows/gcp-target-runtime-dependencies.yml/dispatches`.
Current caller Actions write is unproven. Do not ask for a PAT or administration permission solely
to inspect it. Explicit job permissions are preferable if this API is genuinely necessary.

The existing runtime workflow also owns whole-run concurrency group
`gcp-target-terraform-state` with cancellation disabled. A parent that holds it while dispatching
and waiting for the same workflow can deadlock its child. Required environment
`gcp-target-apply` currently names USER `siamese-lang` as reviewer; prevent_self_review=false is
not authority to forge a user review. Ruleset 24074505 remains active with both required checks
terraform-static-verification and backend-required-verification / integration 15368, PR rule,
deletion and non-fast-forward protections. Leave these settings intact.

| Alternative | Decision proposal and reason |
| --- | --- |
| Keep original manual diagnostic sequence | Safe existing lane; cannot establish new fixed-candidate A–E evidence or satisfy autonomous Recovery progression. Retain unchanged as historical authority. |
| Parent/child workflow dispatcher | Reject as default: shared lock deadlock, separate per-child protected approvals and unnecessary Actions write/controller state. No new framework/PAT/permission workaround. |
| One existing protected job with serial case steps and per-case artifacts | **Selected under USER repository implementation approval.** One start/environment approval, existing WIF/runtime/observer/CLI, no child dispatch API or Actions write needed. Fixed order with durable checkpoint/resume and separate final independent review. |
| SDK/job retries or blind quota increase | Not selected. Violates current Policy D without a new decision and cannot fix an unproven cause. |

This is genuine GitHub Actions campaign automation, not a substitute test system. Repository
implementation/CI is now USER-authorized; live execution remains separately gated. The future one-time
live campaign authority must bind exact source/image/budget before the protected start. Existing
environment approval remains mandatory. A resume needing a new protected run can require another
actual environment approval; the design must stop there rather than guarantee unavailable access.

## Implemented separate Recovery contract

The existing observer and `gcp-target-runtime-dependencies.yml` add **recovery-campaign /
pt8a-recovery-campaign** only. Strict v2/v3/diagnostic authority, predecessor review, consumption
and frozen bytes remain unchanged. A separately authenticated USER live comment must bind the
actual merged source, same-source immutable backend image, campaign ID, executable contract hash,
this procedure's final file hash, frozen candidate/v2/v3, original A/B and clean-v4 provenance,
accepted vector-continuity risk and numeric live bounds. Repository approval and the original
B–E approval do not satisfy that comment. No predicted merge SHA or image digest is recorded.

One existing `gcp-target-apply` protected job executes A→E. Preflight runs before WIF/cloud access
and uploads an immutable start checkpoint. Each case has a separate artifact upload after the
actual JWKS cleanup receipt; the next case requires the preceding observation/cleanup and artifact
step to succeed. A shared YAML shell anchor keeps five case steps from duplicating implementation.
No child dispatch, PAT, new workflow, controller service, verifier or permission change is needed.
The original modes retain 35 minutes/1800s validation-pod sleep; Recovery uses its explicitly
approved workflow limit, capped by GitHub's hosted-job maximum. It does not change Policy D.

Requests require explicit `liveBounds`: `maxUploads` (at most five), `maxModelCalls`,
`maxDispatches`, `wallclockMinutes`, `modelLocation`. Before first upload of a case, the runner
reserves the production path's upper bound of six calls: facts 1 + initial/compact ≤2 + repair ≤1
+ retrieval query embeddings ≤2. Final documentation lookups do not embed. This is conservative
budget accounting, not observed/billed usage or a newly chosen cost ceiling. The runtime's model
project/location is independently read back. Missing/wrong bounds fail before cloud/inference.
The actual job start determines remaining approved wallclock; no new case/drain starts with less
than its existing 540-second observation window. Actual cost/call/attempt values remain USER live
decisions, and a short total budget can leave an honest incomplete campaign.

Resume scans complete main dispatch history across sources, authenticated run/artifact archive
digests and every inventory member. Unknown/missing/rerun/concurrent history, a changed candidate,
stale resume run, duplicate/out-of-order observations, later ingestion or missing checkpoint blocks
submission. Original A/B remain consumed in their separate campaigns. A completed product
observation from this same Recovery candidate is reused without another fetch/upload/model call;
its negative CLI/quality/HCL outcome remains intact and is never converted to semantic PASS.
A selected case step without durable evidence fails closed even if it may have failed before POST;
preflight/partial failure is not an excuse to reset original once-only guards.

An accepted nonterminal checkpoint permits **one read-only drain** of that same owner/job, never
a POST. The existing JWT/JWKS fixture restricts this operation's owner to the first Recovery run
admitted from authenticated same-candidate artifacts; arbitrary overrides and PT-2 owner changes
remain forbidden. Original acceptance identity and latency censoring stay bound; a later terminal
readback supplements the old artifact and never removes/replaces its censor. Another drain,
ambiguous acceptance, incomplete provenance or failed cleanup blocks further work. No new accepted
job overlaps an unresolved predecessor.

A complete SUCCEEDED observation can progress even when quality is UNKNOWN/DEGRADED, CLI fails,
or no reviewable HCL exists. All original job/presentation/retrieval/draft/CLI evidence is retained
for final review. Explicit input rejection and generated Terraform-validation failure remain product observations;
unknown failed-job causes block classification. Terminal provider failures stop as `TECHNICAL_FAILURE`; an unconfirmed 429 cause
never authorizes an automatic recovery upload. There is **no automatic resubmission, SDK retry,
quota change, best-of-N selection or new product generation fix**. The original B rate-limit root
cause remains unproven. Future technical re-submission needs supported cause evidence and separate
authority rather than inventing a current recovery policy.

The only production change adds numeric `upstreamHttpStatus` from typed exception accessors to
existing facts/generation/repair failure logs, with null for missing evidence. The existing
classifier, request/model/prompt/retrieval choices and timeouts are unchanged. No exception
message, response body, prompt, image, quota guess or secret is logged. The observer retains that
field without treating UNAVAILABLE/throttling/message text as an HTTP code. Historical evidence
is not retrospectively filled from this new telemetry.

Before/after each observation, admission reuses the existing authenticated clean-v4 receipt and
read-only exact mapping/5395 IDs/non-vector bodies/index UUID/finite 1536-dimensional vectors.
No admission embedding/index write occurs. `VECTOR_WRITE_CONTINUITY_UNPROVEN` remains explicit;
normal serving retrieval embeddings are separately budgeted model calls. Unknown write history
stops reuse. The approved corpus is never rebuilt/re-embedded to follow backend source changes.

Campaign completion is only `EVIDENCE_COLLECTED_AWAITING_INDEPENDENT_REVIEW`. The final artifact
keeps all five individual states, original A/B references, uploads used and all ten required
independent dimensions. No automatic REVIEWED_PASS, fabricated quality rates or Product Trust
closure is produced; rates stay null. Semantic/product defects require independent assessment
and any later candidate must receive its own evidence, without cross-candidate PASS promotion.

## Validation, corrections and exact resume point

Initial observer run: **59 tests / 1 legacy assertion failure**, preserved in PR evidence. The
assertion required the old artifact step's exact `always()` string; the new mode needs that upload
to run always for non-Recovery operations. Corrective iteration **1/3** aligned only this expected
condition and completed the same-scope checkpoint tests. Historical repair 1/1 is unchanged.
Observer final suite: **62 PASS** (existing 50 plus 12 Recovery regressions), including the actual runner
and observer executing five sequential deterministic drafts, real Terraform-command construction,
negative UNKNOWN/DEGRADED/CLI outcomes, checkpoint reuse, duplicate/partial/candidate/source/censor
fail-closed, one GET-only drain, missing live authority/budgets, owner restriction and cleanup gates.
These use deterministic external transport/process boundaries; they are not live product samples.

Backend focused tests **41 PASS**. Full offline package **BUILD SUCCESS: 588 total / 584 PASS /
4 MariaDB-prerequisite SKIP / 0 failures / 0 errors**, executed once. Existing RAG suites **55 PASS**.
YAML/JSON, workflow/smoke Bash syntax, repository policy, Case C ledger, exact scope/state preservation,
original frozen hashes and diff checks PASS. Normal exact-head PR CI results are recorded in the PR. No manual CI rerun or cloud/model operation is
permitted. The source/image release and exact live budgets are future gates, not current code
prerequisites. Narrow telemetry changes the backend build inputs, so any future publication/rollout
must use the actual independently accepted merge source and separate explicit release authority.

Stop at **PT8A RECOVERY IMPLEMENTATION COMPLETE / INDEPENDENT ACCEPTANCE PENDING**, with gate
**HUMAN_REQUIRED: PT8A_RECOVERY_IMPLEMENTATION_ACCEPTANCE_AND_USER_MERGE**. Continue on the same PR
only for independent feedback within the approved scope/remaining correction limit while main is
unchanged. After independent acceptance and USER merge, require separately bound live/model/cost
approval and the existing environment gate; do not activate cases from repository approval alone.
No live dispatch, model call, official image fetch/upload, publication, rollout, corpus write,
reembedding, IAM change, teardown or self-merge occurred. The full A–E goal is **NOT COMPLETE**.
