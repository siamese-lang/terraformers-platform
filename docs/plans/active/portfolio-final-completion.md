# Terraformers — Portfolio Completion & Single Current State Plan

> **S1 READ-ONLY AUDIT RESULT (2026-10-11; repository-only, subject to independent review and USER merge):** [Current evidence prioritization and browser-readiness decision](../../portfolio/current-evidence-prioritization-20261011.md). Latest PR #274/#277 cross-stage facts/intent/repair correction plus actual five-image observations are promoted to lead portfolio candidates; B5 failure injection and Case C pod replacement remain the strongest for different invariants. S1 repository review completed; present-time GCP cluster/owner/JWKS readback **not performed**. Last verified rollout source `6e448698…` differs from PR #278 preview script's default source `435ca32d…`, but the existing `GCP_PREVIEW_BACKEND_SOURCE_SHA` override is available *after fresh readback*. New preview subject does not automatically own existing A/B/N1–N3 Jobs. No S2 authority, model call, JWKS mutation or Product Trust gate waiver.

---


> **CURRENT DECISION / 2026-10-11** — Base: `main` `6e4486988b97ad1abdaa09a15db18b6ea30fc838` (PR #278 merged). This is the **single current completion roadmap**. `AGENTS.md` and actual `main` code remain higher authority. Historical milestone/AWS plans are not automatic work queues.
>
> **Status:** Goal/state reconciliation **PROPOSED FOR INDEPENDENT REVIEW / USER MERGE**. No GitHub PR/CI or future work is pre-approved by this document. Source code, cloud, security/IAM, provider calls and destructive operations are unchanged.

## 1. Goal — not a new product or AWS→GCP migration

Modernize the **existing 2024 five-person Terraformers team project** while preserving its reusable product/domain contracts. Target a cloud-provider-neutral application and **one** GCP representative runtime. Improvement axes remain: (1) backend reliability, (2) evidence-led AI/RAG evaluation and targeted correction, (3) actual failure-diagnosis observability, (4) cloud portability. The portfolio deliverable is a **small set of evidence-backed engineering problem-solving narratives**, not a mandatory count of three or permanently fixed Case A/B/C headings. The earlier A/B/C names were convenient historical workstreams; combine, split or reframe them when a stronger newer mechanism and better evidence warrant it. PR counts, tooling breadth and a permanently public website are not portfolio goals. Every case needs scenario → before state → impact → root mechanism → alternatives/trade-offs → chosen correction → comparable after state → residual risk. Clearly distinguish the 2024 team contribution from subsequent modernization work.

The later Product Trust extension asks whether a real user-facing architecture-image → Terraform **reviewable draft** service is trustworthy, without treating valid HCL as semantic correctness. New public ingress, new hosted IdP, permanent frontend deployment, new evaluator framework, model swaps, and extra RAG tuning are **not** default portfolio requirements.

## 2. Evidence promotion — use the **strongest current** demonstration per claim

The 2026-10-11 portfolio audit is **not** an order to keep the original three Case A/B/C stories unchanged. Raw before-state artifacts and negative observations remain immutable, but **the interpretation, lead demonstration, and grouping must change** when newer source/evidence provides a stronger, honest claim. The independent report [PT-8A qualified five-case observation](../../evaluation/pt8a-qualified-five-case-disposition.md) contains job IDs and exact hashes. That summary is not a raw evidence artifact.

**Important identity split:** recent **test A and test B** reuse the frozen official image bytes (SHA-256 `0a6a5927…` and `d14c4ba6…`), while newer N1/N2/N3 use different official reference images; they are not the historical **portfolio Case A (RAG)** and **portfolio Case B (durable jobs)**. Tests N1/N2/N3 must not be substituted for the frozen official C/D/E acceptance results.

| Current portfolio-worthy technical claim (provisional grouping) | Strongest **current** evidence / change | Historical before-state or supporting evidence | Credible claim boundary |
|---|---|---|---|
| **Image facts & RAG context reaching Terraform generation** | [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274): extracted directed facts previously omitted from initial generation; now passed to initial/fallback generation, while first semantic search is no longer endpoint-type constrained. [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277): icon/resource **presence vs CREATE intent**, relationship wiring, aliased-provider quality and malformed-repair adoption corrected. Later same-image **test A** generates the API Gateway→Cognito authorization connection that earlier PT-8A A had missed. | The earlier Case A N=3 / frozen holdout prove **v3 evidence coverage/generalization and negative controls**, but do not by themselves prove these new v4 cross-stage behaviors. Original official A diagnostics show the missing relationship in generated HCL despite extraction. | Verified propagation and one observed topology improvement. Do **not** declare all relationships, resource intent or semantics correct. Same image alone is not a controlled same-runtime before/after because backend source changed and other conditions may differ. |
| **Latest real reviewable Terraform draft behavior across architectures** | **A, B, N1, N2, N3: 5/5 completed AnalysisJobs and final real Terraform CLI PASS**, varied serverless, VPC fleet, ALB/EC2/RDS, ALB/ECS/Aurora and Route53/CloudFront/S3; **N2 initial malformed/incomplete draft repaired into final CLI-valid draft**. All quality statuses remain UNKNOWN. | Earlier v4/official evaluations exposed Terraform executable and semantic failures; historical fabricated/reference-derived fixtures alone were weaker realism evidence. N2/N3 original invalid multipart media-type submissions remain preserved as a test-client protocol issue. | Strong **five-sample implementation/draft validation observation**, not measured long-run reliability, semantic 5/5 PASS, deployable full AWS infrastructure, or a substitute for the original frozen official A–E criterion. N3 domain issue is unresolved image input + draft internal consistency, **not** a product-specific correction mandate. |
| **Grounding scale & retrieval architecture** | Broad v4 reference universe: 5,395 chunks including 5,387 official/provider and 8 project decisions, 1,514 documented AWS resource kinds. Latest actual generated resource-type documentation and retrieved IDs can be examined by Job. Facts and retrieval have distinct stage evidence. | Original 30-resource curated corpus gap, earlier v3 Case A required-context exclusion, v3 canonical N=3 and holdout, and adaptive evidence-capacity 10/12→12/12 in a bounded controlled evaluation. | Corpus scope and retrieval availability are not proof of exact factual interpretation, selected-document relevance or causal model use; current five-case `knowledgeStatus=COMPLETE` cannot be promoted to semantic PASS. |
| **Durable analysis and failure-diagnosable serving Backend** | Recent five real GCP persisted jobs plus owner-scoped initial/final draft and Terraform CLI diagnostics; historical PT-6R2 provider deadline/terminal fence, Case B durable lease/claim, result-intent/cleanup and actual MariaDB integration demonstrate mechanism. Repeated ~220s provider incidents have diagnostic evidence, though their physical upstream cause is not fully established. | [Case B B5](../../evaluation/case-b-integrated-closure.md) deterministic failure injection and real MariaDB concurrent claim, which remain **stronger than five successful uploads for crash/restart correctness**. | Latest jobs upgrade normal-path realism; they do not replace the deliberate restart/duplicate/partial-write fault injections. The five successes do not prove provider timeout root cause permanently resolved. |
| **Single GCP real runtime, cloud portability and delivery** | Real v4 GCP GKE/Vertex/OpenSearch/MariaDB VM/GCS end-to-end Job flow, source→immutable image→deployed revision capability, persistent quality/diagnostics and actual error classification. [PR #278](https://github.com/siamese-lang/terraformers-platform/pull/278) adds **dev-only** React→private GCP backend preview transport. | [Case C live integrated/rollout/pod-replacement evidence](../../evaluation/case-c-portfolio-closure.md) remains stronger for its **specific pod-replacement durability and immutable release identity claims**. PT-6 Chromium UI exercise used real API but a stub model and local fixture. | GCP React→real model browser journey is **not yet observed**. Do not claim hosted production login, public deployment, source equivalence across historical and latest runs, HA or capacity. |

### Explicit evidence-ranking decision

- **Lead with latest evidence** when it verifies a previously missing *material behavior* (e.g. extracted relationship → actual authorization HCL; real CLI on five practical images; real v4 serving).
- **Retain older evidence as the strongest proof** where recent tests do not exercise the same invariant (e.g. Case B crash/recovery/fencing, Case C pod replacement, Case A canonical/holdout controls and controlled retrieval-capacity comparison).
- Before claiming causality or a before/after effect, bind the original image hash, job/source SHA, stage outputs, model/corpus configuration and semantic review method; label uncontrolled changes. Absence of source-equivalence forbids a combined integrated-release claim.
- Organize the final portfolio by **which engineering decisions can actually be explained**, not by Case A/B/C labels, number of PRs, or whether a milestone is closed. A cross-cutting AI evidence-to-generation flaw may be a more compelling lead than the old retrieval-only story; exact final prioritization belongs to the bounded evidence promotion task below.
- Keep raw artifacts unmodified but permit explicit supersession of outdated **claims, slides and narratives**. Distinguish `technicalStatus=PASS`, `knowledgeStatus=COMPLETE`, `qualityStatus=UNKNOWN`, human semantic review and functional/deployment validity.

### Product Trust disposition

PT-8A is **USER-qualified-closed** on the actual A/B/N1/N2/N3 observational boundary. Original A–E negative/NOT_RUN records and original official acceptance criteria are unchanged. The PT-8B legacy `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` dependency remains **unmet**; the qualified closure does not authorize PT-8B, paid model calls, a new IdP, public ingress or PT-9 teardown.

## 3. Next bounded work: deliberate execution order

### S0 — Repository state reconciliation (**this PR; docs/state only**)

- Record the qualified PT-8A USER decision and distinguish it from the original acceptance.
- Set one current checkpoint across program/state/AI_PROJECT_STATE/active plan; label old AWS and early snapshot plans HISTORICAL.
- Preserve actual live-runtime state `ACTIVE_RETAINED` as *last recorded*, not independently remeasured.
- Fix navigation to this plan. No relaxation of original gates, source/runtime edits or rewritten failed evidence.
- **Exit:** no contradictory *authoritative* current labels; program/state parse; docs only; independent review then USER merge.

### S1 — Current portfolio evidence promotion & optional integrated-proof readiness (read-only)

**Immediate single task after S0 independent review and USER merge:** inspect newest source-bound generation/facts/RAG/Repair artifacts, earlier same-image A/B negatives, v4 corpus receipts and runtime/job provenance. Decide **which concrete engineering claims now supersede or enrich historical Case A/B/C**, which older experiments remain stronger for unique invariants, and which are still unproven. Do not preselect a mandatory number or category of final portfolio pieces. At the same time, read-only inspect the existing GCP React preview readiness and source/identity/ownership of A/B/N1–N3 to determine whether the final portfolio *needs* any additional real UI observation.

- Evidence review: compare **same pinned image A/B** historical vs latest source/initial/final facts, relationship and HCL; N1–N3 for breadth; prior v3 canonical/holdout and B5/C live controls for complementary invariants. Include exact source/image/runtime binding; avoid a fake same-condition causal conclusion.
- Proof readiness: deployed source revision, retained v4 read-only serving, existing owner Jobs, JWT/JWKS placeholder status, frontend dev proxy and auth identity. Reuse existing results if legally/technically feasible; no token handling/model calls/fixture mutation at this stage.
- **Exit:** one **claim → strongest evidence → historical baseline → improvement mechanism → limitation** matrix, an evidence-backed **provisional shortlist ranked by portfolio explanatory strength (not fixed count)**, and either (a) one bounded GCP browser proof specification or (b) explicit not-needed decision. No new production code or automatic S2.

### S2 — Only if S1 warrants it: one real browser / GCP integration observation

Separate USER decision and authorization are required for temporary JWKS mutation, protected auth context and any Vertex call. Prefer **read-only existing result navigation**; if ownership or proof goal makes that impossible, approve at most one new upload with an explicit call/budget boundary. Use existing React dev preview, GKE Backend, MariaDB/GCS, OpenSearch, Vertex and result/quality readback. Retain ordinary failures; never rerun for green.

- Minimum proof: authenticated `/generate` or `/projects` session → correct user-owned Job/Project → progress/terminal/HCL and quality rendered from Backend; reload persistence; unauthenticated/other-user denial where testable; identical source/Job IDs; cleanup JWKS and token evidence.
- State **exactly** what was exercised. Fixture login is not hosted login; completed backend Job readback is not proof of a new browser upload or model invocation.
- **Exit:** independent review of evidence + residuals; no automatic Hosted IdP/Ingress/production frontend project.

### S3 — Product Trust closure decision and portfolio materials

Keep **two separate outcomes**: `PORTFOLIO_EVIDENCE_BACKED_ENGINEERING_CLAIMS_SUFFICIENT` (case grouping flexible, newest evidence ranked) vs `FULL_PRODUCT_TRUST_ACCEPTANCE_NOT_ESTABLISHED` until further evidence/explicit gate amendment. The original PT-8B `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` stays locked. A new D_DECISION/USER-approved amendment is required **if** the program is to proceed on qualified PT-8A evidence; no silent equivalence. Compose source-equivalent **strongest currently defensible evidence**, regardless of old A/B/C labels; preserve stronger older invariant proof where newer data are not comparable. Produce diagrams, before/after and interview narratives with source links, team-vs-individual attribution, realistic-image limitations, and privacy-safe screenshots.

- **Exit:** precise claims supported by cited run IDs, artifacts, actual versions, measured before/after and known residual risk. No invented reliability/performance or production login claim.

### S4 — PT-9 / resource and cost closure (separate destructive checkpoint)

Retained GCP target is **not authorized** for teardown by S0–S3. When proof is finished or further runtime is not needed, inspect resources and costs, submit exact source/resource-bound teardown plan, and obtain explicit USER destructive approval. Verify post-teardown residual absence/soft-delete boundaries. Do not keep a costly runtime alive solely for documentation.

## 4. Stop rules and plan discipline

- One approved task at a time; do not cascade S1–S4.
- `main`, `AGENTS.md`, this plan, selected Work Package and actual code/evidence are authoritative. Never follow stale historical AWS/old Current checkpoints as live directions.
- PR/CI green ≠ acceptance; no self-merge; no unrelated auth/infra changes.
- PT-8A qualified closure is final **as a scoped observation**; original A–E/10-dimension full PASS remains NOT_MET/NOT_ESTABLISHED without rewriting or re-testing.
- If the stated portfolio objective is already sufficient, choose closure over technology additions. Any broader publicly accessible product goal must be separately proposed with scenario, alternatives, cost/security decisions and USER approval.
