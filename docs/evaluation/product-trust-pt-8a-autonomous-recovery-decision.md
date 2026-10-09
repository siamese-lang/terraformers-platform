# PT-8A autonomous recovery decision and resume checkpoint

Status: **REPOSITORY PREPARATION COMPLETE / POLICY AND WORK PACKAGE APPROVAL REQUIRED**.
This is a decision proposal, not an executable measurement procedure, campaign approval or
Product Trust acceptance. The USER's 2026-10-09 request authorizes investigation, contract/PR
preparation and existing deterministic validation while absent. It explicitly preserves actual
architecture, policy, merge, live/cost and security gates.

[AGENTS.md — Case Decision Gate](../../AGENTS.md#case-decision-gate) explicitly requires:
“위 판단 방향을 사용자가 승인하기 전에는 대표 case 구현을 시작하지 않는다.”
The approved Program and active Work Package also require one case per dispatch followed by
independent review, with the historical repair budget already consumed (1/1). A new automated
progression policy and three-iteration budget therefore need an explicit decision; preparation
does not change those existing requirements.

Preparation is bound to remote main `c789d433f13da744e14c6a6f31db2d4fd3cf1d74`.
No refresh/rebase or replacement of historical execution bases is permitted.
[One proposed integrated Work Package](../../.agents/work-packages/product-trust-pt-8a-autonomous-recovery-v1.yml)
keeps implementation/live authority false. Continue that same PR after an explicit decision if
main remains unchanged; a separate contract merge is not presumed necessary. Do not create another
activation/state-sync PR. Request attachment SHA-256:
`40ac59f730f866b61116b6fd15d5d872b0effaef8fb27304b0099db166471074`.

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
| One existing protected job with serial case steps and per-case artifacts | **Recommended for USER decision.** One start/environment approval, existing WIF/runtime/observer/CLI, no child dispatch API or Actions write needed. Fixed order with durable checkpoint/resume and separate final independent review. |
| SDK/job retries or blind quota increase | Not selected. Violates current Policy D without a new decision and cannot fix an unproven cause. |

This is genuine GitHub Actions campaign automation, not a substitute test system. Repository
implementation/CI is authorized only after the policy/Work Package decision. The future one-time
live campaign authority must bind exact source/image/budget before the protected start. Existing
environment approval remains mandatory. A resume needing a new protected run can require another
actual environment approval; the design must stop there rather than guarantee unavailable access.

## Proposed recovery contract and validation

Retain strict v2/v3 and diagnostic modes, their original artifacts and all once-only guards. Add
one separately approved campaign/mode to the existing observer and runtime job only. A campaign
has one explicit immutable backend candidate, control/procedure source, frozen image/truth set,
model/region and corpus admission. Automated technical progression retains original observations;
it cannot sign independent semantic reviews or generate REVIEWED_PASS. Final independent review
must annotate all ten frozen dimensions for every case. A complete evidence campaign with product
defects is not Product Trust acceptance; missing HCL/dimensions remain unmet, never invented PASS.

Per-case artifact checkpoints reuse existing accepted/job/presentation/retrieval/draft/CLI/inventory
JSON. Each captures campaign/case/candidate and original run/artifact identity, technical state,
independent review state and remaining **approved** attempt budget. GitHub owns transient lifecycle;
do not put active PR/branch state in the Program JSON. Before any POST, a prior started or ambiguous
step without sufficient durable evidence blocks replay. On resume, read all prior case artifacts,
continue the earliest unresolved case, and never repeat an independently reviewed success from
the same candidate. An accepted nonterminal job may only be observed read-only under its bound
owner/job; do not replace censoring or missing acceptance with a fresh upload.

Semantic defects cannot receive technical-recovery retries. A separately authorized technical
recovery needs a confirmed allowlisted cause and an explicit per-case/total budget; retain every
failed attempt, never best-of-N selection. The original A/B budgets stay consumed regardless of
any new campaign. Candidate changes invalidate cross-candidate PASS reuse and require a newly
approved measurement scope. Original B repair failure is not proof that all calls should retry.

Preserve Policy D: facts/generation+compact+repair/embedding 370/220/10s, SDK attempts 1 and status
retry list empty, durable attempts 1, original accepted-age cutoff 8m, single initial MAX_TOKENS
compact fallback, one closure/repair and finalization/deadline/cleanup/post-commit fences.
Read-only corpus admission reuses original clean run 37710171426/artifact 11522067032 and exact
5395 / 1536 / UUID L8KKBHT1Qri3E2VVw7As2g, without embedding/index writes. The accepted
VECTOR_WRITE_CONTINUITY_UNPROVEN residual remains explicit. Normal serving retrieval embeddings
are model calls and need live budget; admission's embeddingRequests=0 does not erase them.

The existing 35-minute job timeout is not enough to promise five sequential 540s observation
windows plus setup/admission/artifact work. A reviewed total workflow wallclock budget is required;
do not silently alter provider/job deadlines. SDK/job retry changes remain separately gated.
Per-case recovery limits, total uploads, model/embedding call and cost ceilings, actual model region
and workflow budget are **unset**, not defaults. The proposed three repository-only corrective
iterations need explicit approval for this new unit; old auto-repair 1/1 stays unchanged.

Control-only changes also must not cause ceremonial backend publication. Recommend, for decision,
separating control source from backend build source only when all backend Docker build inputs and
runtime identity are verified unchanged, and live approval explicitly binds both sources/image.
Current strict modes still require their existing same-source binding. If typed production
telemetry or a supported product correction changes backend build inputs, one new publication/
rollout becomes necessary at the actual release/live checkpoint. No source-equivalence waiver,
predicted merge SHA/digest or release is implemented in this preparation.

After decision, extend only existing tests for actual contracts: all-source duplicate/ambiguous
uploads, partial artifact resume, same-candidate reviewed-success reuse, candidate drift, negative
semantic evidence, bounded cause-authorized recovery, read-only admission and unforgeable final
review. No separate test framework or tests solely restating new names. Use proportional existing
backend tests if production telemetry changes, existing observer/RAG tests if runner changes, then
normal PR CI once per justified tree. Preserve natural failures; no unchanged rerun-until-green.

## Completed preparation and exact resume point

Existing offline tests ran once: ProviderFailureClassifierTest **6**, VertexGenerationStageTest
**19**, VertexArchitectureFactsExtractorTest **12**, VertexFinalClientInjectionTest **1**,
TerminalQualityAssessmentMapperTest **2** — **40 PASS / 0 failures / 0 errors / 0 skipped**.
Command: `mvn -o -s /workspace/.cloud-setup/maven-settings.xml -Dtest=ProviderFailureClassifierTest,VertexGenerationStageTest,VertexArchitectureFactsExtractorTest,VertexFinalClientInjectionTest,TerminalQualityAssessmentMapperTest test`
in backend with the cached JDK 17/Maven 3.9.9. This validates current mechanisms, not live quota,
historical HTTP status or improved model quality. No new tests, production or executable workflow
changes were made. Metadata/scope/frozen checks and normal PR CI are recorded in the PR.

Stop at **HUMAN_REQUIRED: PT8A_AUTONOMOUS_RECOVERY_POLICY_AND_WORK_PACKAGE_APPROVAL**.
Resume the same PR from this bound base after one consolidated USER decision covering:

1. The distinct campaign and technical-progress/final-review policy, plus at most three bounded
   repository-only corrections for the new unit, without any original counter reset.
2. Whether to obtain the missing original read-only provider evidence first or authorize narrow
   typed telemetry; no retry/product fix is justified by current evidence alone.
3. Fixed candidate/control-source binding and explicit cause/attempt/call/cost/wallclock budgets
   before live execution; unchanged Policy D unless separately decided otherwise.

Then implement/validate the chosen scope in that PR, obtain independent acceptance and USER merge,
and bind the actual final candidate to the separate live/model/cost approval. Do not require a
contract-only merge first unless review establishes a real prerequisite. No self-merge, SDK policy
change, new token, cloud/model operation, official fetch/upload, ingestion, rollout or teardown
occurred. The full A–E goal is **NOT COMPLETE**; this checkpoint is not a successful benchmark.
