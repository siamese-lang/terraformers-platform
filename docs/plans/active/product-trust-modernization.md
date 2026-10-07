# Product Trust Modernization

## Status

**PROGRAM v3 USER-MERGED / PT-6 COMPLETE / PT-6R1 LOCAL DETERMINISTIC PASS /
HUMAN_REQUIRED: MERGE_CHECKPOINT (independent acceptance and USER merge pending)**

[Review 6031040940](https://github.com/siamese-lang/terraformers-platform/pull/251#issuecomment-6031040940)
accepted v3 amendment head `360fc827e2b6146f0961442b53e3eb172a8aa4e8`; USER merged PR #251 as
`00872520036da38fd1809a9abf61f56d2344fcb5` at `2026-10-07T04:46:24Z`. Explicit USER approval
activates exactly [PT-6R1](../../../.agents/work-packages/product-trust-pt-6r1-first-user-identity-concurrency-v1.yml)
on that once-bound base. No rebase, refresh, rebind or separate activation/state-sync PR.

[Current evidence](../../evaluation/product-trust-pt-6r1-first-user-identity-concurrency.md) preserves
the first instrumentation failure and separately records USER authorization for exactly one extra
before execution. Actual MariaDB reproduced an immediate IDENTITY save uniqueness failure followed
by a recovery query on a poisoned rollback-only session. The narrow correction isolates INSERT and
recovery in existing Spring transactions; the original one same-condition after execution passes:
both callers resolve one USER/ACTIVE durable account. Email ownership, distinct users and display
semantics pass real-database controls; PT-5 access and full offline backend regressions pass.
No additional local database measurement, independent acceptance or merge is authorized. Autonomous
instrumentation repair stays 1; human corrective iteration is separately 1. Production INSERT may
commit independently of a later enclosing operation rollback; that bounded tradeoff is documented.

PT-6 remains independently accepted deterministic browser integration from review 6030408825,
PR #250 accepted head `0b8f7ef0eefc472a0438a3d0eeedee0f1c919c7a`, USER merge
`f344552fe58db26438a4f0b2c9abb1806e6a99d9`. All failed observations, original base/counters and
bounded no-production-IdP/model-quality/generalization claims are preserved. PT-3..PT-5 accepted
production corrections remain limited to their deterministic evidence.

PT-2 remains permanently `PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION`:
03–10 NOT_RUN, null aggregate rates, original 424846 ms censor and zero-product recovery failures.
No reopening/recovery/rerun/dispatch or artifact reinterpretation. PT-1 revision 3 frozen identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014` remains unchanged.

Mandatory order remains **PT-6R1 -> PT-6R2 -> PT-7 -> PT-8A -> PT-8B -> PT-9**. No PT-6R2 activation
or future base prediction. The [cross-phase audit](../../evaluation/product-trust-v3-cross-phase-integrity-audit.md)
and all portfolio/live/security/merge/destructive gates remain in force. Runtime stays retained,
A7-8 remains deferred and GitHub alone holds transient PR/branch lifecycle. CI does not grant acceptance.

## Why this reassessment exists

The completed modernization work established substantial backend, RAG, delivery and observability
mechanics. The following historical reassessment findings preceded PT-3 through PT-6; the current
resolved/unresolved split is in the v3 audit:

1. canonical and holdout positive evaluation images are repository-owned synthetic fixtures, so they
   are useful regression inputs but do not prove realistic-image generalization;
2. runtime quality is explicitly conditional on extracted facts, so a wrong initial image
   interpretation can remain internally consistent downstream;
3. backend quality state can be UNKNOWN or DEGRADED while the current frontend primarily presents
   the terminal AnalysisJob state as "analysis complete";
4. provider-side latency has shown large stochastic tails, while the UI exposes only coarse
   PENDING/RUNNING/SUCCEEDED/FAILED state and a non-SLO "1-3 minutes" message;
5. application authorization is owner-aware, but the representative GCP runtime uses a deterministic
   JWKS test identity rather than proving a permanent production IdP;
6. repository CI exists, but the protected-main required-check boundary and container-build
   shift-left behavior still merit repository-only review.

These are product-trust questions. They are not reasons to reopen every completed infrastructure
decision.

## Evidence policy

### Retain as direct mechanism evidence

Retain unless new contradictory evidence appears:

- Case B durable AnalysisJob ownership, lease/fencing, bounded retry and cleanup accountability;
- provider schema and broad-v4 corpus coverage measurements;
- real Terraform CLI executable validation;
- project owner/non-owner authorization semantics already demonstrated by deterministic/runtime tests;
- WIF, immutable image digest and source-revision delivery evidence;
- real provider latency observations and stage telemetry;
- durable quality persistence and API representation.

### Downgrade to controlled-regression evidence

The following remain useful but may not support realistic-user claims by themselves:

- `terraformers-eval-v1` synthetic positive fixtures;
- `terraformers-eval-holdout-v1` synthetic holdout fixtures;
- canonical N=3 and holdout scores derived only from those fixtures;
- generated-resource evidence coverage percentages measured only on those fixtures;
- one positive A7-7 live request using the synthetic canonical fixture.

Historical numbers are never changed to preserve a narrative. If realistic evidence contradicts
them, the realistic evidence becomes authoritative for the broader product claim.

## Product-trust success criteria

The final product claim requires evidence for five user guarantees:

1. **Input fidelity** — realistic architecture inputs are interpreted with bounded, reviewable
   component/relationship fidelity; false trusted success is explicitly measured.
2. **Result trust** — processing success, executable correctness, knowledge/evidence support and
   uncertainty are not collapsed into one user-visible "success" state.
3. **Waiting experience** — accepted-to-terminal latency is measured and the user receives truthful
   stage/status information without fabricated percent-complete estimates.
4. **Access safety** — authenticated ownership, private/public visibility and mutation boundaries are
   fail-closed for the supported user flow.
5. **Operational credibility** — the source-to-image-to-runtime path and relevant CI/CD gates are
   reproducible without claiming unrelated production-hardening work.

## Program sequence

### PT-1 — Realistic-input benchmark design and freeze

**Status: COMPLETE.** The approved revision-3 bytes and truth are frozen at the binding above.

Repository-only until the truth set is frozen.

Build a small but diverse realistic dataset before observing live results. Target approximately ten
inputs rather than a large benchmark:

- several architecture positives derived from realistic AWS/reference architecture forms or
  independently recreated diagrams with recorded provenance;
- different layout/density/icon/text conditions;
- ambiguous/incomplete controls;
- non-architecture controls.

Positive labels must be human-reviewable components, relationships and expected resource intent.
The agent must not fabricate labels from the model-under-test. Original third-party image licensing
must be respected; when redistribution is unsuitable, preserve provenance and create a
repository-owned faithful test diagram rather than copying copyrighted artwork.

**Human gate:** `REALISTIC_DATASET_TRUTH_FREEZE`.

### PT-2 — Current-system realistic live baseline

**Status: PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION; further execution prohibited.**

Use the frozen PT-1 dataset against the current production-equivalent path exactly once under a
reviewed live checkpoint.

Measure at minimum:

- input classification;
- required/forbidden components and relationships;
- resource intent;
- evidence/grounding status;
- Terraform executable validity;
- user-facing terminal/quality semantics;
- accepted-to-terminal latency and stage latency;
- **false trusted success**: a result that would look acceptably successful to a user while failing
  frozen realistic truth.

Do not tune prompts, retrieval, models or scoring before this baseline. Preserve natural failures.

**Human gate:** `LIVE_REALISTIC_BASELINE`.

### PT-3 — Baseline-driven AI trust correction

**Status: COMPLETE within the independently accepted deterministic omission correction.**

Only evidence-backed defects from PT-2 may create implementation Work Packages.

Predeclared candidate tracks are:

- input-fidelity/human-confirmation boundary;
- generated-evidence closure versus semantic-repair gating;
- bounded classification/fact-extraction correction using the existing model/provider architecture.

No second judge model, model voting, LangGraph, new vector database or new model family is adopted by
default. If PT-2 requires a product/architecture choice such as mandatory human confirmation, Codex
must write a decision brief and stop at `HUMAN_REQUIRED`.

The accepted PT-3 correction used proportional deterministic regression, not new model inference.
Final realistic acceptance of the corrected path remains mandatory in separately gated PT-8A;
old synthetic scores do not replace that acceptance.

### PT-4 — Trustworthy user-facing status and waiting experience

**Status: COMPLETE within the independently accepted deterministic presentation/timing correction.**

Correct the already observed user-facing contract gap:

- expose terminal technical/evidence quality semantics without inventing numeric confidence;
- do not present UNKNOWN/DEGRADED evidence as indistinguishable from fully supported completion;
- measure accepted-to-terminal latency;
- expose truthful durable stages/status (for example analysis, evidence retrieval, generation,
  validation/repair) when production state can support them;
- avoid fake percentage progress and unsupported latency promises;
- preserve page-leave/reload durability.

Reuse Case B durable job state and existing telemetry where possible.

### PT-5 — Access-safety and authentication-boundary audit

**Status: COMPLETE within independently accepted deterministic access audit/route correction.**

Audit the supported API/user flow as an IDOR/ownership boundary:

- owner/non-owner/anonymous reads and mutations;
- private/public artifacts;
- analysis-job lookup and result linkage;
- upload and object access;
- comments and compatibility endpoints;
- explicit public-route allowlist versus default-open security configuration;
- CORS/token-expiry/upload-abuse/cost-abuse boundaries where repository evidence makes them relevant.

A new production IdP is **not** a default requirement. The representative runtime's deterministic
JWKS fixture must not be mislabeled as a permanent production IdP. Selecting or migrating to a new
IdP is a separate human decision if the final product claim requires it.

### PT-6 — Browser-level realistic user flow

**Status: COMPLETE for deterministic browser integration, review 6030408825 / USER-merged PR #250.**

After relevant corrections, validate the actual user journey rather than only backend HTTP calls:

`authenticate -> upload realistic image -> request accepted -> progress survives navigation/reload
-> terminal result -> quality/trust state -> Terraform result`.

Also verify a second identity cannot read or mutate a private project.

Use a browser E2E tool only because this product-level contract now requires browser-observable
behavior; do not expand it into broad cross-browser/UI testing.

### PT-6R1 — First-user identity concurrency closure

Begin with deterministic reproduction/audit against **real MariaDB concurrency**. Inspect actual
lookup/create/flush/commit/recovery semantics before selecting any implementation. If reproduced,
correct the atomicity boundary with the smallest production change; do not preselect upsert versus
conflict-and-reread. Concurrent first requests for one provider/subject must converge on exactly one
durable user and the same identity, without 5xx/constraint leakage or privilege escalation. Distinct
identities remain distinct and the full PT-5 authorization matrix stays intact. No browser/model/GCP
work is required merely for this defect. Product/architecture/security decisions retain human gates.

### PT-6R2 — Provider terminality / latency-budget closure

After independent PT-6R1 acceptance and USER merge, audit the actual Google GenAI request/deadline
capability and provider/retry/job boundaries. Ensure one accepted job cannot remain provider-bound
indefinitely. The original PT-2 `424846 ms` censor stays untouched; its upstream cause is unknown.
Do not invent a timeout or silently choose retry tradeoffs. If a numeric provider/job latency budget
or retry policy requires a product choice, stop at
**HUMAN_REQUIRED: PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION**, with measured evidence and options.
After required approval, freeze a bounded terminal contract and validate timeout/hang injection.
Preserve PT-4 truthful waiting/status; do not claim Gemini is inherently fast.

### PT-7 — CI/CD trust gates and operational handoff

After independent PT-6R2 acceptance and USER merge, audit first. Existing Backend Local Verification
normally tests/packages Maven but skips the production Docker build (`RUN_DOCKER_BUILD=false`).
Determine the smallest shift-left correction for relevant backend/Dockerfile PRs, preferably in the
existing automatic workflow. Verify Terraform 1.8.5 / AWS provider 5.100.0 through the **real backend
Dockerfile**, not a duplicate bespoke pin verifier. Inspect actual protected-main/ruleset required
checks and reconcile only demonstrated gaps; security/ruleset writes retain their human gate.
No workflow breadth for appearance, no automatic GCP deployment on merge.

Produce one concise handoff: **source SHA -> immutable image -> digest -> rollout -> evidence ->
teardown**. Live/model/cost actions remain separately approved.

### PT-8A — Final realistic AI/RAG trust acceptance

This is **NEW final-system acceptance**, not PT-2 recovery. It is required because approved audit of
broad-v4 run `37419983898` found substantial positive false-green/semantic failures; PR #237 changed
production generated-resource closure and PR #238 aligned the evaluator with production orchestration.
No complete final live AI/broad-v4 remeasurement established realistic acceptance after those
corrections. Partial PT-2 does not close this gap.

**Human gate immediately before execution: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE.**

Bind final post-PT-7 reviewed source/runtime, broad `terraformers-reference-v4`,
`gemini-embedding-2 / 1536`, current final production generation/grounding closure and real Terraform
CLI validator. Use frozen revision 3 / identity above. Submit each of ten frozen inputs through the
authenticated production AnalysisJob path **exactly once** in frozen order. No correctness
resubmission, replacement sample or rerun-until-green. Preserve natural failures; score only after
inference against frozen human truth, never tune truth from output.

For each of six positives, inspect required components, directed relationships, resource
intent/cardinality, forbidden interpretations, generated-resource official-evidence closure and real
Terraform executable validity. Both ambiguous and both non-architecture controls must not produce
trusted Terraform success. Acceptance requires **false trusted success = 0**, every accepted job
following PT-6R2's independently accepted bounded terminal contract, and persisted/user-visible trust
agreeing with observed semantic/technical results. Aggregate percentages are secondary; any positive
semantic, evidence, executable or false-trusted defect prevents PASS. Classify and stop for a bounded
corrective Work Package; only an actual correction may justify the minimum declared post-change
regression for the affected mechanism. No universal user-export generalization claim.

### PT-8B — Final integrated release proof

Eligible only after **independent PT-8A acceptance** and authorized merge. Bind exact reviewed main
SHA -> immutable backend image -> source-SHA tag -> digest -> deployed `BUILD_SOURCE_REVISION` ->
exact broad-v4 corpus/index identity. Compose accepted PT-8A semantic and PT-6 browser/access evidence
where explicit source-equivalence permits. Do not rerun ten-case inference for ceremony if
AI-relevant source/runtime identity is unchanged; rerun only an affected boundary when later code
actually invalidates prior evidence. Do not add public ingress to make proof more live.

**Human gate: FINAL_PRODUCT_LIVE_PROOF** (and any separately required live/model/cost gate).
Final claims are bounded to the reviewed dataset and representative runtime, not universal AWS
image generalization or deployment-correct IAM. Independent integrated acceptance is required.

### PT-9 — Destructive teardown and final closure

Blocked until **independent PT-8B acceptance**. Preserve the explicit USER `DESTRUCTIVE_TEARDOWN`
checkpoint. Return retained runtime/bootstrap resources to the reviewed final baseline; verify
residual absence, reconcile A7-8, and independently review final portfolio closure with bounded claims.
No destructive execution is authorized by this amendment.

## Explicit non-blocking portfolio residuals

- Hosted production Cognito/IdP lifecycle is not established; deterministic identity is not hosted IdP proof.
- Per-user/model-cost abuse quota is not established.
- Twelve AWS provider schema resources have no official resource documentation and remain knowledge gaps.
- PT-1 human-reviewed reference-derived recreations are not a statistical sample of all real user exports.

These USER-approved boundaries constrain final claims, are never promoted to PASS, and do not waive
PT-8A per-case evidence/semantic/technical acceptance. Earlier missing PT-6 in-memory events remain
an explicit evidence limitation rather than invented reconstructed history.

## Explicitly out of this program

Do not reopen these merely for portfolio breadth:

- Kubernetes/GKE versus Cloud Run/VM platform reselection;
- microservice decomposition;
- capacity curves, HPA, multi-zone HA or MariaDB HA;
- zero-downtime multi-replica rollout optimization;
- rollback/canary redesign unless the product-trust work exposes a concrete release failure;
- Langfuse/OpenTelemetry/Grafana adoption without a newly demonstrated observability gap;
- RabbitMQ/Kafka/Redis/another database;
- production IdP replacement by default;
- public ingress, new vector DB, evaluator LLM or model voting;
- unrelated hardening.

The existing Kubernetes runtime remains a retained representative deployment substrate. Its use does
not imply that Kubernetes is required for every version of this product.

## Autonomous execution boundary

Once the user explicitly approves `product-trust-v1`, Codex Cloud may choose and execute the next
eligible repository Work Package from the program DAG without requiring a repeated "next task"
instruction.

This autonomy does not include:

- inventing a new phase or architecture;
- weakening acceptance criteria;
- merging production PRs unless separately authorized;
- live model/GCP/cost action without the phase's human gate;
- IAM/security-boundary expansion;
- destructive cleanup;
- rerun-until-green behavior.

At any such boundary the durable state becomes `HUMAN_REQUIRED`.
