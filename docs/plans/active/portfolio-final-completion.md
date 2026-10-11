# Terraformers — Portfolio Completion & Single Current State Plan

> **CURRENT DECISION / 2026-10-11** — Base: `main` `6e4486988b97ad1abdaa09a15db18b6ea30fc838` (PR #278 merged). This is the **single current completion roadmap**. `AGENTS.md` and actual `main` code remain higher authority. Historical milestone/AWS plans are not automatic work queues.
>
> **Status:** Goal/state reconciliation **PROPOSED FOR INDEPENDENT REVIEW / USER MERGE**. No GitHub PR/CI or future work is pre-approved by this document. Source code, cloud, security/IAM, provider calls and destructive operations are unchanged.

## 1. Goal — not a new product or AWS→GCP migration

Modernize the **existing 2024 five-person Terraformers team project** while preserving its reusable product/domain contracts. Target a cloud-provider-neutral application and **one** GCP representative runtime. Improvement axes remain: (1) backend reliability, (2) evidence-led AI/RAG evaluation and targeted correction, (3) actual failure-diagnosis observability, (4) cloud portability. The primary portfolio deliverable is **2–3 strong engineering cases**, not a list of tools, merged PRs, or a permanently public website. Every case needs scenario → before state → impact → root mechanism → alternatives/trade-offs → chosen correction → comparable after state → residual risk. Clearly distinguish the 2024 team contribution from subsequent modernization work.

The later Product Trust extension asks whether a real user-facing architecture-image → Terraform **reviewable draft** service is trustworthy, without treating valid HCL as semantic correctness. New public ingress, new hosted IdP, permanent frontend deployment, new evaluator framework, model swaps, and extra RAG tuning are **not** default portfolio requirements.

## 2. Frozen evidence and truthful disposition

| Workstream | Current defensible result | What is NOT established |
|---|---|---|
| Case A — RAG | Portfolio-closed historical retrieval-grounding claim; corrected selector/context and canonical N=3/holdout PASS; broad v4 serving and later facts→initial-generation fixes observed separately | General semantic correctness, unlimited diagram coverage, all RAG evidence used by model |
| Case B — durable AnalysisJob | Portfolio-closed; MariaDB durable eligibility, lease/fencing, selective retry, outcome/cleanup recovery, B5 bounded scenarios | Exactly-once across all conditions, fleet SLO |
| Case C — GCP runtime & delivery | Portfolio-closed with residuals; authenticated GKE/Vertex/OpenSearch/MariaDB/GCS integrated evidence, pod-replacement durability, immutable source→digest release evidence | Capacity curve, HA, fault-free deploy, public product |
| Product Trust PT-8A | **USER-approved qualified closure on 2026-10-11**; preserve original official A–E failure/NOT_RUN/consumption; separate later A,B,N1,N2,N3 observations: five terminal-success + CLI PASS; quality UNKNOWN; partial semantic caveats including N3 domain/CloudFront internal consistency and unresolved diagram inputs | Original official A–E full Acceptance PASS; five semantic PASS; full Product Trust PASS; hosted login |
| PT-6 browser | Chromium real UI/Backend and signed JWT fixture verified using local H2/filesystem + **stub** analysis | Same real GCP Vertex run through browser |
| PR #278 | Existing React-to-private-GKE development-preview **code path** merged | Public GCP frontend, hosted user login, completed real GCP browser session |

Retain old PT-2 incomplete and all consumed PT-8A evidence unchanged. **Qualified closure is a USER scope/risk disposition, not an original PT-8A PASS.** The legacy program PT-8B dependency on independent full PT-8A acceptance is **NOT SATISFIED**. No implied PT-8B authorization, no automatic phase advance.

## 3. Next bounded work: deliberate execution order

### S0 — Repository state reconciliation (**this PR; docs/state only**)

- Record the qualified PT-8A USER decision and distinguish it from the original acceptance.
- Set one current checkpoint across program/state/AI_PROJECT_STATE/active plan; label old AWS and early snapshot plans HISTORICAL.
- Preserve actual live-runtime state `ACTIVE_RETAINED` as *last recorded*, not independently remeasured.
- Fix navigation to this plan. No relaxation of original gates, source/runtime edits or rewritten failed evidence.
- **Exit:** no contradictory *authoritative* current labels; program/state parse; docs only; independent review then USER merge.

### S1 — One decision: minimum final integrated proof (not a service redesign)

**Immediate next task after S0 merge:** a bounded **read-only proof-readiness decision** against the exact deployed GCP source/image, ownership of already created A/B/N1–N3 Jobs, retained v4 and JWKS placeholder, using existing runbook and PR #278. Decide whether any **existing** persisted result can be viewed from the real React UI under a safely authorized fixture identity. If not, record why and decide whether one fresh upload is worth its paid model/API call. No actual fixture activation or model request in S1.

- Evidence: deployed source revision vs reviewed PR #277, runtime readiness (read-only), existing frontend dev proxy path, identity/owner match, safe JWKS readback, explicit no-model plan.
- Stop: source drift, missing ownership, unavailable runtime or invalid auth preconditions. Do not silently replace results or create a public endpoint.
- **Exit:** either (a) a minimal bounded live browser proof specification with precise identity/cost/cleanup gates, or (b) a justified **not-required** decision because existing portfolio A/B/C claims already suffice and the integrated service claim is explicitly excluded.

### S2 — Only if S1 warrants it: one real browser / GCP integration observation

Separate USER decision and authorization are required for temporary JWKS mutation, protected auth context and any Vertex call. Prefer **read-only existing result navigation**; if ownership or proof goal makes that impossible, approve at most one new upload with an explicit call/budget boundary. Use existing React dev preview, GKE Backend, MariaDB/GCS, OpenSearch, Vertex and result/quality readback. Retain ordinary failures; never rerun for green.

- Minimum proof: authenticated `/generate` or `/projects` session → correct user-owned Job/Project → progress/terminal/HCL and quality rendered from Backend; reload persistence; unauthenticated/other-user denial where testable; identical source/Job IDs; cleanup JWKS and token evidence.
- State **exactly** what was exercised. Fixture login is not hosted login; completed backend Job readback is not proof of a new browser upload or model invocation.
- **Exit:** independent review of evidence + residuals; no automatic Hosted IdP/Ingress/production frontend project.

### S3 — Product Trust closure decision and portfolio materials

Keep **two separate outcomes**: `PORTFOLIO_CASES_A_B_C_EVIDENCE_SUFFICIENT` vs `FULL_PRODUCT_TRUST_ACCEPTANCE_NOT_ESTABLISHED` until further evidence/explicit gate amendment. The original PT-8B `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` stays locked. A new D_DECISION/USER-approved amendment is required **if** the program is to proceed on qualified PT-8A evidence; no silent equivalence. Compose only source-equivalent A/B/C/PT-6/PT-8A evidence. Produce diagrams, before/after and interview narratives with source links, team-vs-individual attribution, realistic-image limitations, and privacy-safe screenshots.

- **Exit:** precise claims supported by cited run IDs, artifacts, actual versions, measured before/after and known residual risk. No invented reliability/performance or production login claim.

### S4 — PT-9 / resource and cost closure (separate destructive checkpoint)

Retained GCP target is **not authorized** for teardown by S0–S3. When proof is finished or further runtime is not needed, inspect resources and costs, submit exact source/resource-bound teardown plan, and obtain explicit USER destructive approval. Verify post-teardown residual absence/soft-delete boundaries. Do not keep a costly runtime alive solely for documentation.

## 4. Stop rules and plan discipline

- One approved task at a time; do not cascade S1–S4.
- `main`, `AGENTS.md`, this plan, selected Work Package and actual code/evidence are authoritative. Never follow stale historical AWS/old Current checkpoints as live directions.
- PR/CI green ≠ acceptance; no self-merge; no unrelated auth/infra changes.
- PT-8A qualified closure is final **as a scoped observation**; original A–E/10-dimension full PASS remains NOT_MET/NOT_ESTABLISHED without rewriting or re-testing.
- If the stated portfolio objective is already sufficient, choose closure over technology additions. Any broader publicly accessible product goal must be separately proposed with scenario, alternatives, cost/security decisions and USER approval.
