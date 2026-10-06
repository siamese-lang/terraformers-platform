# Product Trust Modernization

## Status

**PROGRAM DEFINED — AWAITING EXPLICIT PROGRAM APPROVAL**

This plan supersedes automatic progression to A7-8 and teardown. It does not invalidate repository
evidence merely because it is old; it reclassifies each claim according to whether it proves an
internal mechanism or a real user-facing product property.

The active question is no longer only whether Terraformers completed Case A/B/C engineering
mechanics. The active question is:

> Can a real user upload a realistic architecture diagram, understand what the system is doing,
> distinguish a technically completed job from a trustworthy result, receive a useful result within
> an explainable waiting experience, and remain protected by correct ownership/authorization?

The program contract is `.agents/programs/product-trust-v1.yml`.
Durable execution state is `.agents/state/product-trust-v1.json`.

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

### PT-0 — Product-trust contract and evidence reset

Repository-only.

- freeze the product-trust success criteria;
- classify historical evidence as retained, downgraded or superseded;
- record that A7-8 and teardown are deferred while the current live runtime is intentionally retained;
- define realistic-input and user-facing success terminology;
- do not modify production behavior.

### PT-1 — Realistic-input benchmark design and freeze

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

Build/publish/deploy the final reviewed revision once and run the bounded final product proof:

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
