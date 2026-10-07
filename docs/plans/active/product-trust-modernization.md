# Product Trust Modernization

## Status

**Approved Product Trust v4 / PT-6R2 COMPLETE / PT-7 repository implementation prepared**

[PR #254 independent review 6037528315](https://github.com/siamese-lang/terraformers-platform/pull/254#issuecomment-6037528315)
accepted head `987ec944c84ac74f99f2c2f3ae38b0bc16499f8a`; USER merged it as
**`6228d69b1da604e816b3e0a826563d79b8bcd844`**. Explicit USER authorizes PT-7 on that once-read
main. [PT-7 Work Package](../../../.agents/work-packages/product-trust-pt-7-ci-delivery-trust-v1.yml)
freezes the narrow scope and deterministic procedure before implementation. No refresh/rebase/rebind.

[PT-7 handoff/evidence](../../evaluation/product-trust-pt-7-ci-delivery-handoff.md) audits two gaps:
backend PR checks skipped the real production Dockerfile, and protected main required only Terraform.
Existing Backend Local Verification now separates backend and production-image scope, runs existing
Maven/package/MariaDB when needed, and always creates `backend-required-verification`. Real unchanged
Dockerfile builds only for Dockerfile/pom/src/main changes and must embed the exact PR head revision.
This CI-only PR selects backend regression, not Docker. Normal exact-head CI is GitHub-authoritative
and is not acceptance or new PT-6R1 concurrency evidence. Local deterministic validation is recorded
separately; no live runtime/tooling is started.

After successful exact-head CI, stop at **HUMAN_REQUIRED: PT7_REQUIRED_CHECK_RULESET_CHANGE**.
The observed ruleset still requires only `terraform-static-verification`; proposed addition is only
`backend-required-verification`. Ruleset mutation requires explicit USER approval; all other settings
are preserved. PT-7 independent acceptance and USER merge remain pending. No PT-8A execution.

PT-6R2 original base `406981a8c629a02503f5660453dcc7921b8d5177`, all failed/successful evidence,
auto repairs 1 and human correction 1 are unchanged. Accepted Option D is original 480000-ms age
plus conditional Δ=P+B+S+T (5s injected model for P≤2s, B/S/T≤1s), not a production DB SLO,
outage guarantee or hard thread cancellation. It is not deployed/reconfigured by PT-7.
PT-6R1 code, before/after evidence and counters also remain exact.

V4 AWS revision 2 identity **`3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757`**
remains externally frozen; immutable candidate bytes, model count 0 and official execution unauthorized
are unchanged. Historical PT-1 identity `04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`
is controlled reference-derived regression. PT-2 is permanently final INCOMPLETE: 03–10 NOT_RUN,
four aggregate rates null, original 424846-ms censor and zero-product recovery failures unchanged.
No further PT-2 dispatch. Retain the single representative runtime; A7-8 deferred and teardown
unauthorized. Remaining **PT-7 -> PT-8A -> PT-8B -> PT-9** preserves every readiness/live/model/cost/
security/merge/destructive gate. GitHub owns transient PR lifecycle; no state-sync PR.

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

**SYNTHETIC_OR_LOCAL_ONLY_EVIDENCE_CANNOT_CLOSE_FINAL_PRODUCT_TRUST**.

| Authority | Evidence | Permitted claim |
| --- | --- | --- |
| Controlled regression | Mockito/unit, H2/local fixtures, repository-authored architecture images, canonical/holdout synthetic sets, historical reference-derived PT-1 set, local browser/stub-provider journeys | Regression and integration under declared controls; never independently final AI/RAG trust. |
| Mechanism | Real MariaDB concurrency, real Terraform CLI, provider schema/broad corpus measurements, real Dockerfile/CI image reproducibility, ownership/durability and WIF/source binding | That mechanism only; not image interpretation, semantic usability or deployed final AI/RAG acceptance. |
| Final AI/RAG | External frozen AWS image -> reviewed source -> immutable real-Dockerfile image/digest -> deployed retained representative GCP -> exact broad-v4 OpenSearch -> real retrieval/configured model -> authenticated persisted AnalysisJob -> persisted result/quality -> real CLI draft validation -> post-inference frozen truth scoring | Independently accepted bounded five-official-positive sample/runtime claim only, with every link present. |

No unit/local evaluator/synthetic/repository image/browser-fixture PASS, green CI, or consistent docs
can mark Product Trust or modernization complete. Final trust remains incomplete without the full
independently accepted deployed external-input chain. Keep historical numbers, failures and
censors; reclassify authority without rewriting evidence. Existing PT-1 ambiguous/non-architecture
controls remain controlled regression only; do not invent new synthetic final controls.

### Reviewable Terraform draft

Formal principle: **REVIEWABLE_IAC_DRAFT**. Unknown account IDs, domains/zone/certificate/role ARNs,
callbacks/federated IdP settings, secrets, existing networks and externally owned resources may be
declared variables, coherent references/data boundaries or clearly editable placeholder/TODO inputs.
An unavailable personalized value alone is not a failure; fabricated identifiers are not rewarded.
This never excuses missing core service intent, wrong directed relations/material cardinality,
unrelated architecture, missing required wiring/authorization, invalid provider arguments,
undeclared references or invalid HCL. Equivalent valid grounded mechanisms are acceptable; one icon
need not imply one newly created resource. A TODO must not erase a required relationship.

Use real `backend/Dockerfile`-pinned Terraform **1.8.5** and AWS provider **5.100.0**: initialization
and `terraform validate` must pass. No real AWS plan/apply/deployment is part of acceptance;
validation alone proves neither semantic fidelity nor effective deployed IAM. Preserve the existing
repair prompt's variable/reference behavior. Current prompt/evaluator gaps are audited for future
bounded Work Packages, with no prompt/scorer changes here.

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

**COMPLETE** under review 6031698220 and USER-merged PR #252. The real MariaDB before-state
established immediate IDENTITY save uniqueness failure and rollback-only poisoned recovery.
Selected REQUIRES_NEW INSERT/recovery correction and one same-condition after-state are accepted.
Distinct identities, email/display semantics and PT-5 access invariants remain intact. The first
instrumentation failure and separately authorized additional before-state remain evidence, not
retry-until-green. This amendment records completion only; no production change or remeasurement.

### PT-6R2 — Provider terminality / latency-budget closure

**COMPLETE under independent review 6037528315 / USER-merged PR #254.** Accepted head
987ec944 and merge 6228d69b are bound in PT-7 authority. Original audit/probe, failures, one exhausted
automatic repair, one human test correction, focused 11/0/0/0 and full 576/0/0/4 are preserved.
Automatic CI run 37617690039 passed backend and MariaDB; it is ordinary regression, not PT-6R1
before/after measurement. [Original brief](../../evaluation/product-trust-pt-6r2-provider-terminality.md)
remains the immutable prepared snapshot; current acceptance is recorded in this substantive PT-7 PR.
Accepted Δ is conditional on operating scheduler/DB bounds, never a production outage/SLO claim.

### PT-7 — CI/CD trust gates and operational handoff

**Repository implementation prepared; exact-head normal CI and independent acceptance pending.**
The [Work Package](../../../.agents/work-packages/product-trust-pt-7-ci-delivery-trust-v1.yml) binds
6228d69b once. Existing workflow removes its PR paths filter, classifies backend/image scopes,
conditionally reuses both existing regression jobs and always creates the fail-closed
`backend-required-verification`. Production-image scope is exactly backend Dockerfile/pom/src/main.
The real Dockerfile is sole Terraform/provider pin/schema/positive/negative authority; no new workflow
or duplicate verifier. Non-backend/test-only/image-input and gate failure cases are deterministic tests.

[One handoff document](../../evaluation/product-trust-pt-7-ci-delivery-handoff.md) records actual
read-only ruleset settings and unchanged manual source→SHA tag→digest→retained rollout→deployed
source/v4 identity→later exact serving/retrieval readiness. No live action/readiness claim.
Successful normal CI leads to **PT7_REQUIRED_CHECK_RULESET_CHANGE**, proposing only one added check
while preserving Terraform and every other setting. Explicit USER approval is still required for
ruleset mutation, then independent phase acceptance and USER merge. PT-8A remains unauthorized.

### PT-8A — Final external official AI/RAG deployed acceptance

NEW final-system acceptance, never PT-2 recovery. Final truth must be USER-frozen on the exact
[official candidate](../../evaluation/product-trust-aws-official-truth-candidate.md) identity before
model-under-test use. Historical PT-1 recreations are not the final substitute. The prior broad-v4
run `37419983898`, PR #237 closure correction, PR #238 orchestration alignment and PT-2 partial
subset do not establish accepted final external-input performance.

**Mandatory order before any official evaluation image is consumed:**

1. Exact post-PT-7 reviewed main -> production backend Dockerfile -> immutable source-SHA tag ->
   remote sha256 digest -> deployed backend `BUILD_SOURCE_REVISION == reviewed main`.
2. Prove actual serving `INDEX_NAME=CORPUS_VERSION=terraformers-reference-v4`,
   `gemini-embedding-2`, 1536 dimensions and immutable corpus/checksum/model/content equivalence.
   Required universe: schema 1526, official/selected 1514, gaps 0, provider chunks 5387,
   project decisions 8, total 5395. Counts/config names alone do not establish exact index identity.
3. Distinguish exact reusable complete v4, partial, stale/mixed model space, wrong model/dimension
   and missing index. Reuse exact proven v4; do not ceremonially re-embed/re-ingest 5395 documents.
   Unprovable equivalence fails readiness before cases. Existing reviewed build/embed/ingest only
   under the appropriate separate live/model/cost authority. Never add case labels or truth to RAG.
4. Correlated deployed-backend retrieval returns official reference evidence from the intended
   exact index. This is serving readiness, not a semantic case PASS.
5. Separate explicit **FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE** approval immediately
   before once-only official-input execution. Earlier cloud/model actions also need their authority.
6. Fetch raw frozen external image URLs; exact SHA/media/dimensions mismatch stops before upload.

Submit A–E in frozen order through authenticated production upload/object persistence -> durable
AnalysisJob -> image/facts -> embedding/live v4 retrieval -> configured-model generation/closure ->
real CLI draft validation -> persisted result/quality -> API readback, **once per case**. No correctness
resubmission, observed-result replacement or rerun-until-green. Preserve natural failures. Freeze the
procedure before outcomes; score only afterward against unchanged frozen truth.

Individually score classification, core components, directed relationships, material containment/
cardinality, forbidden/invented interpretations, resource intent, unknown-input handling,
generated-resource official evidence closure, real CLI draft validity and persisted/user-visible trust.
Use deterministic comparisons where meaningful and explicit human semantic-alias review; no exact
text/naming requirement or model judge. **FALSE_TRUSTED_SUCCESS = 0**. A materially wrong trusted
architecture is NOT_PASS even if HCL validates; a valid input-variable draft is not a failure solely
for missing account values. No averages hide a material positive defect. PT-6R2's accepted bounded
terminal contract must hold for every accepted job. Claim only this five-official-positive
sample/runtime, not universal AWS/user-export generalization. Existing synthetic negatives cannot
replace external positive proof.

### PT-8B — Integrated evidence composition / release equivalence

Only after independent PT-8A acceptance and authorized merge. Verify the **already bound** PT-8A
source/image/deployed revision/exact corpus identity; PT-8B is not first identity discovery. Compose
semantic/RAG evidence with accepted browser/access/durability evidence only under explicit source-
equivalence. Later code invalidation permits only the minimum required boundary regression under
its authority, not ceremonial repeats of all official model cases. No new ingress/IdP/runtime.

**Human gate: FINAL_PRODUCT_LIVE_PROOF**, plus required live/model/cost gates. Independent integrated
acceptance is required; no universal generalization, fully parameterized apply or deployment-correct
IAM claim follows.

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

Automatic Goal progression stops after exact-head normal CI at `PT7_REQUIRED_CHECK_RULESET_CHANGE`.
The observed required check remains Terraform-only; no ruleset mutation is authorized. Explicit USER
ruleset authority, independent PT-7 acceptance and USER merge are required before later phase
readiness, and separate live/model/cost approval precedes official inputs. CI is not acceptance.
Original execution bases, historical failures/counters and frozen bytes remain exact. No state-sync
PR, predicted future base, manual workflow rerun, new runtime or live operation is authorized here.

This autonomy does not include:

- inventing a new phase or architecture;
- weakening acceptance criteria;
- merging production PRs unless separately authorized;
- live model/GCP/cost action without the phase's human gate;
- IAM/security-boundary expansion;
- destructive cleanup;
- rerun-until-green behavior.

At any such boundary the durable state becomes `HUMAN_REQUIRED`.
