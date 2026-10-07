# Product Trust Modernization

## Status

**PROGRAM APPROVED — PT-1 COMPLETE / PT-2 FINAL INCOMPLETE EVIDENCE ACCEPTED / PT-3 COMPLETE / PT-4 COMPLETE / PT-5 COMPLETE / PT-6 INCOMPLETE BROWSER EVIDENCE / HUMAN_REQUIRED: BOUNDED_REPAIR_LIMIT_AND_BROWSER_MEASUREMENT_BLOCKER**

PT-1 revision 3 remains USER-frozen at
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`.
All 26 pinned files, truth/provenance, original bases and repair history remain unchanged.

[Independent result review 6028079078](https://github.com/siamese-lang/terraformers-platform/pull/246#issuecomment-6028079078)
and USER's decision closed PT-2 as [final INCOMPLETE evidence](../../evaluation/product-trust-pt-2-final-disposition.md).
It did not establish ten-case realistic generalization. Cases 03–10 remain NOT_RUN, neither pass nor
fail, with no aggregate realistic classification/hallucination/semantic-success/executable-validity
rate. Case 02 remains censored at `424846 ms`; the two original accepted jobs are preserved.
Runs `37500000739` and `37509368210` remain zero-product-observation measurement failures.
No further PT-2 dispatch/rerun/exception/harness recovery is authorized. PT-2 base is unchanged.

[PT-3](../../evaluation/product-trust-pt-3-origin-authorization.md) is COMPLETE within its bounded
omission-detection correction. [Independent review 6028631570](https://github.com/siamese-lang/terraformers-platform/pull/247#issuecomment-6028631570)
accepted exact head `f127e71a5d36c9e8565c0e3e72260e67a5e71865`, then USER merged PR #247 as
`5f623d820f5bf379636f933f5773f46177b5caf3` on `2026-10-07T01:05:47Z`.
Technical PASS remains separate from semantic DEGRADED/reason; the omission check does not prove
effective IAM authorization, deployment success or realistic generalization. No live proof followed.
The one Program dependency amendment remains unchanged.

[PT-4](../../evaluation/product-trust-pt-4-trust-status-waiting.md) is COMPLETE within deterministic
processing/trust separation and durable timing. [Independent review 6029174275](https://github.com/siamese-lang/terraformers-platform/pull/248#issuecomment-6029174275)
accepted head `68110fc0429d88b3211c5811b1c470770bc4c3f7`; USER merged PR #248 as
`0c4fa0e9616881e281aa027a18a9d741ff614699` on `2026-10-07T01:52:07Z`.
Its original base, validation evidence and bounded residuals are retained; no live proof is implied.

[PT-5](../../evaluation/product-trust-pt-5-access-safety.md) is COMPLETE after
[independent review 6029459989](https://github.com/siamese-lang/terraformers-platform/pull/249#issuecomment-6029459989)
accepted head `bc13af0cb889b803a8d83352ef869896c97348f8` and USER merged PR #249 at
`2026-10-07T02:16:09Z` as `37e006be4d5995019704ea8a4009e1a59da039b2`.
Its original base and owner/admin/public policy, evidence and residuals remain preserved.

The approved Goal/DAG and explicit USER bounded local browser instruction activate
[PT-6](../../../.agents/work-packages/product-trust-pt-6-browser-journey-v1.yml) on that exact main.
[Partial evidence](../../evaluation/product-trust-pt-6-browser-journey.md) proves real browser/JWT
login, one realistic binary upload and persisted PENDING across navigation/reload. The original
local stub job then FAILED because the real Terraform validator CLI/provider prerequisites are
absent. Terminal draft/trust/HCL and second-identity browser journey remain incomplete.
Earlier unsuccessful observations are preserved; no product code or live dependency changed.

GitHub owns transient PR lifecycle. This substantive PR records PT-5 accepted completion and
partial PT-6 without another state/activation/normalization PR. The one bounded measurement-driver
correction is spent; stop for independent review and explicit additional bounded correction
before another journey. PT-7 remains blocked until complete PT-6 acceptance and USER merge;
CI success alone is not acceptance. Live/model/GCP, architecture/security expansion, cost and
teardown remain gated. Runtime is retained and A7-8 remains deferred.

## Why this reassessment exists

The completed modernization work established substantial backend, RAG, delivery and observability
mechanics, but the latest review exposed product-level evidence gaps:

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

Revalidate against the same frozen realistic dataset. The target is not preservation of old
synthetic scores.

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

**Status: AUDITED / BOUNDED ROUTE CORRECTION; independent review and USER merge pending.**

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

After relevant corrections, validate the actual user journey rather than only backend HTTP calls:

`authenticate -> upload realistic image -> request accepted -> progress survives navigation/reload
-> terminal result -> quality/trust state -> Terraform result`.

Also verify a second identity cannot read or mutate a private project.

Use a browser E2E tool only because this product-level contract now requires browser-observable
behavior; do not expand it into broad cross-browser/UI testing.

### PT-7 — CI/CD trust gates and operational handoff

Repository-only unless an existing workflow check requires otherwise.

Review and correct only demonstrated delivery gaps, including:

- stable protected-main required checks for relevant backend/frontend/infra changes;
- container-build verification before immutable publication when justified;
- a concise live-session runbook connecting source SHA, publication, rollout, evidence and teardown.

Do not convert the cost-gated representative GCP environment into automatic deploy-on-merge.

### PT-8 — Final integrated product proof

Build/publish/deploy the final reviewed backend/runtime revision once. Compose the final claim from
that live runtime proof plus PT-6 browser evidence. Re-run browser E2E only if relevant user-facing
behavior changed after PT-6. Do **not** add a public GCP frontend workload or ingress merely to make
this phase "fully live".

Run the bounded final product proof:

- realistic-image fidelity;
- representative repeated cases for stochastic stability;
- false-trusted-success result;
- evidence and Terraform executable validity;
- user-visible trust state;
- accepted-to-terminal latency/progress;
- ownership/access behavior;
- browser user flow;
- exact source/image/runtime identity.

Natural failures remain evidence. Do not rerun until green.

**Human gate:** `FINAL_PRODUCT_LIVE_PROOF`.

### PT-9 — Teardown and final portfolio closure

Only after PT-8 acceptance:

- protected runtime teardown;
- final bootstrap cleanup;
- residual-resource/cost check;
- reconcile A7-8 and product-trust closure documents;
- produce the final 2-3 engineering cases and explicit residual risks.

Destructive cleanup always requires a human checkpoint.

## Explicitly out of this program

Do not reopen these merely for portfolio breadth:

- Kubernetes/GKE versus Cloud Run/VM platform reselection;
- microservice decomposition;
- capacity curves, HPA, multi-zone HA or MariaDB HA;
- zero-downtime multi-replica rollout optimization;
- rollback/canary redesign unless the product-trust work exposes a concrete release failure;
- Langfuse/OpenTelemetry/Grafana adoption without a newly demonstrated observability gap;
- RabbitMQ/Kafka/Redis/another database;
- production IdP replacement by default.

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
