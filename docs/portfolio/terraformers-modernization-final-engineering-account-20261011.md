# Terraformers modernization — portfolio-oriented final engineering account (2026-10-11)

> **Decision: engineering-case evidence sufficient; Product Trust full acceptance not established; retained GCP environment pending approved teardown.** This is the **2026 current** account. Do not replace the historical [AWS-era interview guide](final-evidence-and-interview-guide.md) or pretend the old AWS and new GCP runtime were operating simultaneously.

## 1. What this project is — three distinct periods

| Period | Scope and honest attribution | Evidence boundary |
|---|---|---|
| **2024 original 5-person Terraformers team project** | AWS Cloud School team implementation of an architecture-image → Terraform draft/project-management web service; user reported personal work primarily on backend upload/result persistence and DB/object storage integration, plus infra/CI/CD participation. Team award is applicant-reported and requires a separate award certificate if used. | Do **not** attribute the entire original team implementation to the later individual modernization. Editing generated Terraform already existed in this original product. |
| **Earlier AWS modernization** | Separate historic effort covering Spring domain/runtime, AWS Bedrock/AOSS, Cognito, EKS, GitOps, AWS OIDC, CI/delivery, CloudWatch observability and resolved incidents. The AWS runtime was **subsequently torn down**. | Historical [AWS-era guide](final-evidence-and-interview-guide.md), [last live AWS evidence](last-verified-live-evidence.md), [AWS teardown](../lifecycle/aws-runtime-teardown-closure.md). Do not present as the current deployment. |
| **Current 2026 GCP modernization** | Preserve logical provider-neutral ports and existing domain, select one live GCP target with GKE/Vertex/OpenSearch/MariaDB/GCS, evidence-grounded AI/RAG diagnosis, durable async Job handling, immutable build/delivery, real Firebase-hosted UI/owner boundaries and bounded failure testing. | Latest GitHub code `5dd67b046ed802089b1a55b7bf07343b7a6316e6` and [final live observation](terraformers-gcp-final-live-evidence-20261011.md). Work conducted with AI coding/execution assistance and human decision, merge, test and cost checkpoints; describe personal design/review/verification contributions truthfully. |

**Product definition:** authenticated user uploads an architecture image → backend extracts topology facts and retrieves official/provider references → model produces an AWS Terraform **draft** → validation and quality metadata are persisted → the user reviews the result. This is **not** automatic Terraform plan/apply, workload deployment, or guaranteed architecture translation.

## 2. Current logical/physical architecture

```text
User Browser (React static frontend) — HTTPS / otogi.cloud
  └─ Firebase Authentication (signed ID Token)
       └─ GCE Ingress /api (private backend Service)
            └─ Spring Boot Backend on GKE (1 replica, actual tested shape)
                 ├─ Project/identity/ownership and AnalysisJob state → MariaDB 11.4 on GCE VM + PD
                 ├─ Source image / result Terraform draft → Google Cloud Storage
                 ├─ Vertex AI architecture facts + embeddings + grounded generation
                 ├─ OpenSearch v4 reference corpus / nearest-document retrieval
                 └─ Stage logs, quality/Job timing, Actuator/Prometheus
  Delivery: GitHub → immutable Artifact Registry image digest → GKE Pod
```

Core services are designed around provider-neutral identity, storage, provider and retrieval interfaces. AWS-specific adapters and historical delivery paths are kept as reference, not claimed as live GCP services. The GCP runtime deliberately retains a **dedicated MariaDB VM** rather than claiming managed Cloud SQL/HA. Cloud SQL/MySQL compatibility with existing MariaDB migrations was not assumed; database resource contention inside the measured GKE worker was avoided. The practical target prioritizes explainable trade-offs and affordable representative deployment, not multi-replica production HA.

**Key contracts:**

- Durable MariaDB-backed `AnalysisJob` acceptance and scan; local bounded worker pool; atomic claim with generation/lease; late-owner fencing; deterministic result reference, pre-write intent, fenced DB finalization and compensating GCS cleanup. Current Policy D forces `maxAttempts=1`; historical B5 `maxAttempts=3` fixture must **not** be described as current runtime retry policy.
- Actual Backend `BUILD_SOURCE_REVISION` and image digest can be read back after deployment. WIF separates image publishing from privileged infrastructure apply. No blanket claim of automatic safe production rollout from every merge.
- Firebase JWT verifier/identity mapping, ACTIVE account checks and project owner access; explicit **default-deny** paid-analysis UID allowlist for both upload and direct-Job POST. No separate distributed billing quota/instant token revocation claim.
- RAG corpus v4 has **5,395 chunks** (5,387 official/provider + 8 project decisions) spanning **1,514 AWS resource kinds** in the cited frozen inventory. Corpus size/document availability is **not** semantic correctness, model utilization or guaranteed relevance.
- Terraform CLI `init/validate` and provider-schema support are technical gates; they do not prove that an image was understood or that a generated infrastructure plan would deploy.

## 3. Representative engineering story A — image facts and grounded draft quality

**Scenario / issue:** Real architecture diagrams include directed relationships and intent that official provider documentation alone cannot resolve. Historical PT-8A image A exposed missing API Gateway → Cognito authorization wiring in generated HCL even when the earlier fact-extraction pipeline had identified directed facts. A constrained initial retrieval filter and conflation of icon **presence** with resource **CREATE intent** contributed to unreliable downstream decisions.

**Mechanism and decision:** Trace facts → initial query → retrieval evidence → initial/compact generation → repair → Terraform validation rather than attributing every error to the vision model. [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274) supplied structured facts to initial/compact generation and removed the overrestrictive first semantic retrieval type filter. [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277) clarified icon/presence versus create intent, directed wiring, aliased-provider consistency and acceptance of syntactically valid Repair output. Provider knowledge constrains valid implementation shapes, **not** the ground truth of an ambiguous diagram.

**Evidence:** On the original pinned image A, a later revision contained the previously missing authorizer wiring. In a separate qualified current-image cohort, A/B/N1/N2/N3 yielded **5/5 SUCCEEDED** and final executable Terraform CLI PASS. N2 Repair corrected an initial **22-resource** draft to a **30-resource** CLI-valid draft; this is one concrete Repair observation, not a generalized success probability. Pinned cases and SHA-256: [qualified disposition](../evaluation/pt8a-qualified-five-case-disposition.md). Older v3 canonical N=3/holdout tests provide distinct retrieval generalization evidence; their results cannot be assigned to the new v4 facts-flow correction.

**Residual:** All five `qualityStatus=UNKNOWN`; A contains known domain/authorization deployment gaps, and N3 contains a CloudFront custom-domain/certificate consistency concern whose true values are **not inferable from the image**. No semantic 5/5 score, end-to-end AWS apply, 100% correct fact extraction or complete official PT-8A A–E acceptance is asserted. The original frozen negative/not-run evidence remains preserved.

## 4. Representative engineering story B — durable asynchronous analysis, ownership and result integrity

**Scenario / issue:** A long-running provider call outlives an HTTP request; Pod restarts, duplicate delivery, timeout and partial GCS/DB success introduce stranded Job, stale-worker and orphan-object risks. An application-level in-memory queue alone cannot own the durable truth.

**Architecture decision:** MariaDB is the authoritative Job/claim/result-intent store. Atomic claim and generation fencing prevent duplicate durable finalization. A separate bounded executor handles work while lease/age checks restrict late commits. External GCS writing is outside long-lived relational locks; compensation and durable cleanup status account for uncertain object/DB outcomes. [ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md) records the rejected RabbitMQ-without-outbox and deferred transactional outbox trade-off.

**Mechanism-level evidence:** [Case B B5 integrated closure](../evaluation/case-b-integrated-closure.md) records a twelve-row matrix of accepted/stranded work, duplicate delivery, retry, partial-write compensation, delayed cleanup recovery, stale-generation fencing and normal readback, with full suite workflow `36555800770` and a real MariaDB concurrent-claim smoke test. Some invariants are deterministic fixture assertions; not all were full public-GCP live fault experiments. Old `maxAttempts=3` belongs to that historical contract; Policy D subsequently enforces **one** attempt and an original accepted-age cutoff **up to 8 minutes**, with an explicitly unbounded scheduling/DB commit delta; no hard end-to-end latency guarantee.

**New live evidence:** In T2, one Job `f8c00ef4-fb4e-4cdd-b30a-d1d5f5226559` was executing when one Backend Pod normal deletion was requested. The Vertex facts call succeeded; RAG subsequently raised `BeanCreationNotAllowedException` as Spring shutdown overlapped active execution. The original claim `generation=1 attempt=1` ended `FAILED` after ~13s; `resultFileId=null`, `resultObjectKey=null`. The replacement Pod and public HTTP 200 recovered, and temporary paid access was revoked. **This proves bounded graceful-shutdown failure recording and no false result attachment, not an ungraceful crash/lease expiry/reclaim scenario.** Full observed timeline: [T2 evidence](terraformers-gcp-final-live-evidence-20261011.md).

**Residual:** Shutdown-time lazy Bean initialization is a known defect/operational limitation, not silently repaired. Lease-expiry reacquisition and exact GCS orphan-free cleanup were not live re-established by T2; B5 remains the strongest specific regression evidence. A real GCS-write/DB-finalization forced failure (T3) is deferred because the public runtime lacks an isolated fault-injection seam and production DB sabotage is unjustified.

## 5. Representative engineering story C — one GCP target, delivery and access safety

**Scenario / issue:** A reference-only AI service does not prove that a real authenticated user can securely access their own durable job and artifacts, nor that a Kubernetes Pod replacement preserves results. AWS-specific dependencies/secret/delivery paths must be separated from the reusable application flow.

**Decision and implementation:** Single representative GCP runtime, isolated DB VM with durable PD, private GCS object bytes, Vertex/OpenSearch adapters behind application ports, Firebase hosted authentication and owner authorization, immutable source/digest delivery, short-lived WIF identities and instrumented stage logging. Historical [Case C closure](../evaluation/case-c-portfolio-closure.md) documents real authenticated upload→Job→Vertex/OpenSearch→DB/GCS→result readback and replacement durability (`36889896239`, `36891629279`); it explicitly does **not** prove HA/autoscaling.

**New live T1 evidence:** Two distinct real Firebase accounts A/B were authenticated. B's GET of private Project 29, related Job, generated HCL and diagnostics each returned **HTTP 403**, while B's own authenticated Project list returned 200. The signed-in verified B's upload returned 403 without a new project; direct Job POST returned 403 with explicit `billable analysis is not enabled for this verified account` after `AUTH_CHECK=200`. Current server defaults to an empty paid allowlist. See [T1 evidence](terraformers-gcp-final-live-evidence-20261011.md) and [ADR-009](../architecture/decisions/ADR-009-firebase-id-token-backend-boundary.md). This is concrete server-side user isolation and paid-entry denial, not a formal security penetration audit or cost/quota guarantee.

**Operations:** Source SHA `5dd67b046ed802089b1a55b7bf07343b7a6316e6` was both running in the Backend and exact `main` at test time, against digest `sha256:228c9be092c6e2255617e0f8061b41f4fc0ae63353b86bc43fb75507e82a2ab6`. Project logs include `analysisJobId`, source revision, stage and bounded error category: this made T2's shutdown-time retrieval fault distinguishable from facts-model failure. No cross-zone uptime or permanent 0-cost runtime claim.

## 6. Selected measurements and precise scope

| Measurement/observation | Value | Source / claim limit |
|---|---:|---|
| GCP qualified practical images | 5 final Job SUCCEEDED / 5 CLI PASS | [Five-case disposition](../evaluation/pt8a-qualified-five-case-disposition.md); **semantic quality UNKNOWN**, source/cohort qualified |
| N2 final Repair delta | 22 → 30 Terraform resources | Same five-case evidence; one case only |
| Broad v4 corpus inventory | 5,395 chunks, 1,514 AWS resource kinds | [Current completion plan](../plans/active/portfolio-final-completion.md); knowledge breadth not semantic proof |
| B5 runtime/retry fixture | Twelve supported scenarios, full suite plus real MariaDB concurrency | [B5 closure](../evaluation/case-b-integrated-closure.md); some assertions are synthetic failure injection |
| T1 cross-owner protected resources | 4 named endpoints → HTTP 403 | [T1 browser observations](terraformers-gcp-final-live-evidence-20261011.md); two users, one private owner target |
| T1 paid API entry points | Upload 403, direct Job POST 403 with reason | Same evidence; no independent model-billing meter measurement |
| T2 interrupted Job | `FAILED`, ~13s; both result metadata fields null | [T2 logging and API readback](terraformers-gcp-final-live-evidence-20261011.md); **not** lease-expiry proof |
| Post-T2 public service | HTTP 200, temporary UID allowlist override absent | Operator GKE/HTTP readbacks at one timestamp; not HA/SLO proof |

## 7. Skills/decisions worth telling in an interview

1. **Cross-stage fault isolation:** Differentiate observed image topology, model facts, retrieved document, generation prompt, syntactic Terraform validation and runtime quality score. Show which stage actually lost the relationship, the minimal correction and same-image qualified follow-up, retaining contradictory evidence.
2. **Durability vs concurrency:** Explain why `PENDING/RUNNING/SUCCEEDED/FAILED` and generation/lease are DB-backed; why late workers cannot commit; why external object writes require compensation beyond a DB transaction; how tests controlled interleaving and distinguished old retry policy from current one.
3. **Pragmatic cloud choice:** Explain rejecting an unverified Cloud SQL migration, selecting a separate MariaDB VM, GCS object separation, immutable image identity, GKE deployment and why one representative cluster is not HA. Show live 403 owner matrix and intentionally default-deny billable paths.
4. **Incident investigation:** Present exact T2 timeline (Vertex facts `STOP` → shutdown-related Spring Bean exception → FAILED without result), what is proven versus what would require an ungraceful-kill test. A failed run is legitimate engineering evidence when recorded without rerunning until green.
5. **Honest attribution:** Separate original five-person team product/backend participation, earlier AWS modernization, and current individually directed/AI-assisted 2026 modernization. Describe personal design constraints, reviews, approvals, integration/verification and directly authored changes only when corroborated.

## 8. Project completion and lifecycle decision

**USER decision, 2026-10-11:** stop adding features and tests for the portfolio objective; retain the actual negatives/unknowns; record final strong evidence, then produce a separate source-bank-ready project account, then review exact GCP residuals and seek separate approval for teardown. This is **portfolio evidence sufficiency**, **not** universal product correctness or full Product Trust acceptance. The original PT-8B `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` dependency remains **blocked**, and PT-9 teardown is **not automatically authorized** by this documentation decision.

**Known residuals:** PT-8A original A–E full-acceptance gap, UNKNOWN semantic quality, specific A/N3 configuration gaps, provider timeout upstream causal uncertainty, graceful-shutdown Bean creation failure, untested hard-kill lease expiry, untested T3 live GCS compensation, no HA/capacity SLO or automatic AWS infrastructure deployment, short-lived Firebase tokens not checked for remote revocation on every call.

**Stop rule:** no new speculative system, verifier or repeat test. Preserve owner-scoped raw evidence where possible, avoid committing tokens/private architecture inputs. Teardown must first reconcile currently hosted HTTPS/Firebase/Ingress resources with the **historical** 29-delete procedure. See [2026 GCP hosted teardown preflight](../lifecycle/gcp-2026-hosted-teardown-preflight.md).
