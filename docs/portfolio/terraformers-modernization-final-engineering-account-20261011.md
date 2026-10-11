# Terraformers — 2026 modernization engineering account (Case A/B/C → Product Trust → GCP live)

> **Lifecycle update (after this historical engineering account):** The recorded `GCP_TEARDOWN_NOT_EXECUTED` is a test-time snapshot; the actual 2026-10-11 UTC teardown and final operator inventory are in the [GCP closure receipt](../lifecycle/gcp-2026-final-teardown-receipt.md). Engineering evidence and negative/UNKNOWN Product Trust boundaries below are unchanged.

> **Scope:** THIS modernization program only. This is an evidence-ranked account of the early Case A/B/C work **and** the later Product Trust/Firebase/T1/T2 work. It is not a team-project history or a description of another modernization effort.
>
> **Disposition:** `PORTFOLIO_EVIDENCE_BACKED_ENGINEERING_CLAIMS_SUFFICIENT`; `FULL_PRODUCT_TRUST_ACCEPTANCE_NOT_ESTABLISHED`; `GCP_TEARDOWN_NOT_EXECUTED`. At final T1/T2 test, `main` and deployed Backend revision were `5dd67b046ed802089b1a55b7bf07343b7a6316e6`. Earlier cases were executed on different, frozen revisions and must not be represented as one same-source benchmark.

## 1. Objective and runtime under consideration

The service accepts a cloud architecture image, extracts observed component/relationship facts, retrieves official AWS provider/schema and applicable project decision documents, generates and validates a **reviewable Terraform draft**, and persists user-owned Job, image and result state. It does not automatically deploy AWS infrastructure or infer unavailable domain, certificate or organizational decisions as ground truth.

This modernization targeted four engineering problems: (1) AI/RAG evidence completeness and fact propagation; (2) durability/fencing/compensation for long-running Backend jobs; (3) observability that identifies **first failure stage** rather than a generic failed flag; (4) one GCP runtime with independently verifiable deployment, ownership and failure behavior.

```text
React + Firebase Authentication + public HTTPS (otogi.cloud)
  → GCE Ingress /api → Spring Boot on GKE (tested with 1 backend replica)
     ├─ Project/User/File/AnalysisJob and claim state → MariaDB 11.4, GCE VM + PD
     ├─ source images and Terraform draft bytes → GCS
     ├─ facts/generation/embedding → Vertex AI
     ├─ reference retrieval → OpenSearch v4
     ├─ provider schema + Terraform CLI validation
     └─ analysisJobId/source-revision stage logs, quality/timing, Actuator/Prometheus
Delivery → WIF / GitHub Actions → immutable Artifact Registry digest → GKE
```

**Critical distinctions:** factual topology vs provider documentation; retrieved evidence vs model use; Terraform CLI PASS vs image-semantic correctness; authenticated identity vs project authorization; GCS object existence vs MariaDB published success; graceful failure recording vs ungraceful crash recovery.

## 2. Early Case A — grounded AI/RAG evidence, not merely a completed response

### Operating failure and reproducible baseline

The initial M3 canonical evaluation (`terraformers-eval-v1`, workflow [36379633596](https://github.com/siamese-lang/terraformers-platform/actions/runs/36379633596)) had **fact extraction 4/6 PASS and 2/6 FAIL**. Later diagnosis classified at least one response as **OUTPUT_TRUNCATED / RESPONSE_TRUNCATED** rather than generic model semantic failure.

A separate problem was **retrieval succeeding but generation lacking required evidence**. [A2 repeated current baseline](../evaluation/case-a-retrieval-grounding-a2-baseline.md), runs [36574906125](https://github.com/siamese-lang/terraformers-platform/actions/runs/36574906125), [36576165161](https://github.com/siamese-lang/terraformers-platform/actions/runs/36576165161), [36577747809](https://github.com/siamese-lang/terraformers-platform/actions/runs/36577747809), held source `96c6442026f6c57a30a9b248a8af115df1e7f2e4` and config fingerprint constant.

Across 3×6 canonical observations the facts and retrieval execution stages both passed **18/18**; positive Terraform validation passed **12/12**; negative controls classified **6/6** correctly. Nonetheless there were **4/12 applicable-positive grounding gaps**, and the VPC case missed required grounding **3/3**. VPC decision `tfref-v2-sg-relations` was ranked 8/missing/missing; `aws_db_instance` official evidence was missing in **all three runs**. Every run already included non-empty VPC/subnet/ALB/SG/DB resource filters. Thus “forgot the resource filter” was contradicted by evidence. Vector rank variability was observed but not uniquely proven causal at the baseline stage.

### Mechanism, alternatives and implementation

A Terraform draft can pass technical validation even when required reference evidence is absent. Therefore reference retrieval must be assessed separately from syntactic/code validity.

Options of inserting case-specific expected document IDs, increasing top-K indiscriminately, or blindly retrying the provider were rejected as poor evidence of generalizable architecture. The chosen design preserves semantic retrieval but distinguishes **official/provider-schema anchors** from **PROJECT_DECISION** evidence, prevents project decisions from displacing required resource coverage, and separates candidate semantic top-K from bounded **final evidence capacity**. One embedding is reused; retrieval remains bounded.

The capacity comparison run [36799722509](https://github.com/siamese-lang/terraformers-platform/actions/runs/36799722509) observed: final capacity 8 selected 8 documents and covered **10/12** requested resource types; adaptive max 16 selected only 10 documents and reached **12/12**, adding missing `aws_route_table` and `aws_db_instance` anchors. This is one controlled coverage comparison, not universal optimal K.

### Post-fix frozen revalidation

[Case A final closure](../evaluation/case-a-final-closure.md) pins source `32d62e21821ed303555ade5e26b03cb669db94ef`, v3 corpus `terraformers-reference-v3`, frozen model/config and canonical N=3 first-attempt runs [36803174654](https://github.com/siamese-lang/terraformers-platform/actions/runs/36803174654), [36803781744](https://github.com/siamese-lang/terraformers-platform/actions/runs/36803781744), [36804600570](https://github.com/siamese-lang/terraformers-platform/actions/runs/36804600570).

| Gate | Actual frozen observation |
|---|---|
| facts and retrieval execution | **18/18 each PASS** |
| VPC PROJECT_DECISION coverage | **3/3 PASS** |
| applicable-positive grounding gaps | **0/12** |
| positive Terraform validation | **12/12 PASS** |
| negative controls | **6/6 correct** |
| first-stage divergence | **0** |

The VPC project decision still ranked **6, 8, 8**; the goal was inclusion within selected evidence, not constant semantic rank.

Previously frozen holdout run [36805478708](https://github.com/siamese-lang/terraformers-platform/actions/runs/36805478708) tested two new positive architectures (including EKS/IRSA and RDS/SG) plus two negative controls: facts/retrieval **4/4**, positive validation **2/2**, negatives **2/2**, grounding gaps **0/2**. Limited holdout size and image diversity remain a qualification.

A separate latency tail of about **130,489ms** did not reappear in A2's three AOSS facts observations (**2,832–4,443ms**). Fact reuse was not proven to eliminate upstream response tails; permanent FACT_REUSE introduction stayed on HOLD. **Case A portfolio closure is grounded retrieval/generalization evidence, not complete v4 image semantics or latency optimization.**

## 3. Early Case B — durable AnalysisJob ownership, retry and object/relational integrity

### M5 before-state: real correctness gaps

An HTTP request finishing, a local worker disappearing, a database Job being accepted and a GCS write succeeding are different state transitions. [M5 baseline](../evaluation/m5-backend-reliability-baseline.md) recorded stranded/forced-failure accepted jobs after process loss, duplicate terminal re-entry, lack of durable timeout retry, and cross-resource partial success.

For the partial-success case, provider/validation and result object write succeeded, then relational file registration failed. The Job ended FAILED with null published result fields, while the already-written object persisted without compensation. This demonstrates why DB transaction rollback **cannot roll back GCS bytes**.

### ADR-007 choice and B1–B4 mechanisms

[ADR-007](../architecture/decisions/ADR-007-durable-analysis-job-processing.md) selected **MariaDB as the durable eligibility/source of truth**; a bounded local executor is only a processing capacity mechanism. Adding RabbitMQ without transactional outbox would introduce a new DB-commit/broker-publish gap; outbox + broker was deferred pending demonstrated scale/fan-out need.

- **B1:** atomic `attemptCount / nextEligibleAt / leaseExpiresAt / claimGeneration` state transitions and stale-generation fencing. Only the current owner can finalize authoritative state.
- **B2:** bounded DB polling rediscovers committed accepted Jobs if local handoff/process is lost; expired leases can be reclaimed.
- **B3:** selectively durable-schedule only approved provider timeout, not generic arbitrary failures; due-time and attempt exhaustion are fenced.
- **B4:** canonical result reference `{prefix}/{projectId}/{analysisJobId}/main.tf`, independent of retry/time. Durable **pre-write intent**, GCS write, fenced relational success, immediate compensation, and `FAILED + PENDING + exact object reference` for uncertain/failed cleanup. Later cleanup removes the exact object **without re-running the provider**. [B4 evidence](../evaluation/case-b-b4-result-idempotency-cleanup.md) specifies locking/cleanup ordering to avoid a stale worker deleting a new generation's canonical object.

### B5 execution and precise test semantics

[B5 integrated closure](../evaluation/case-b-integrated-closure.md) uses source `164fe2f5a83c9d9eba44eca351db604368940598` and GitHub Actions [36555800770](https://github.com/siamese-lang/terraformers-platform/actions/runs/36555800770). It passed a fresh Backend suite and **real MariaDB 11.4.13** schema/repository smoke; B5-10 uses two concurrent real transactions (single winner/single loser). Most other B5 rows rely on **deterministic fault injection and test assertions**, not all-live cloud crashes.

| Matrix rows | Failure/control observed in tests | Claim boundary |
|---|---|---|
| B5-01/02 | committed accepted job survives local handoff loss; terminal=1, stranded=0 | deterministic restart baseline |
| B5-03 | no stolen ownership before lease; post-expiry generation=2 reclaims | controlled time |
| B5-04 | duplicate deliveries=2, provider executions=1, result registrations=1 | not exactly-once delivery |
| B5-05/06/07 | selective timeout +10s, retry success on attempt 2, exhausted attempt 3 with no 4th scheduling | **historical frozen B5 policy** |
| B5-08 | GCS-like object write then DB failure → FAILED, published result null, compensation residue 0 | deterministic partial-success injection |
| B5-09 | failed cleanup leaves attributable PENDING; subsequent removal → COMPLETED with Job FAILED | durable cleanup accountability |
| B5-10 | concurrent initial claim and reclaim: winner 1 / loser 1 in **real MariaDB** | DB concurrency |
| B5-11 | stale generation cannot finalize/register/retry/terminalize | persisted state fencing, not suppression of upstream model RPC |
| B5-12 | normal POST PENDING → GET SUCCEEDED/readback | normal-path regression |

The B5 frozen runtime contract was polling **2s**, batch **4**, lease **60s**, heartbeat **20s**, timeout retries up to **3** with due delay **10s**. Later Product Trust **Policy D** changed the deployed maxAttempts to **1** and accepted-age cutoff to **8min** to reduce uncontrolled provider duplication and bound stuck work. Do **not** claim the current service auto-retries three times. Eight minutes is also not a guaranteed whole-system wall-clock SLO when SQL locks/commit scheduling intervene.

**Remaining boundaries:** no exactly-once external provider invocation, throughput/SLO measurement, universal fairness or live GCS/DB forced-failure. T2 later supplies a real graceful-shutdown failure observation but **not** B5's ungraceful lease-expiry/reclaim invariant.

## 4. Early Case C — one production-representative GCP runtime and credible stop rules

### Why the evaluation Pod was insufficient

A Vertex evaluation-only execution cannot establish authenticated upload, MariaDB-backed Job ownership, GCS result persistence, source/digest delivery, or result availability after application Pod replacement. Capacity inference is also invalid if the workload fails executable Terraform correctness before exercising actual resource pressure. [Case C portfolio closure](../evaluation/case-c-portfolio-closure.md) therefore built **one representative GCP application runtime** rather than a separate second evaluation stack.

### DB and infrastructure choices

Cloud SQL/MySQL 8.4 connected and applied earlier migrations, but rejected retained Flyway **V003** syntax, so a drop-in database switch was not a safe equivalence claim. Running MariaDB on the measured GKE worker would couple DB resource pressure to Backend/OpenSearch. A dedicated **MariaDB 11.4 GCE VM + Persistent Disk** preserved the validated SQL contract and separated contention; HA was not claimed.

Object bytes went to private GCS; Secret Manager + GKE Secret Sync supplied runtime secrets outside Terraform values. Artifact Registry stored immutable Backend images; short-lived WIF identity separated image publishing from Terraform infrastructure mutation.

### Observed deployment and end-to-end proof

| GitHub Actions run | Specific verified behavior |
|---|---|
| [36851221194](https://github.com/siamese-lang/terraformers-platform/actions/runs/36851221194) | GCS, MariaDB VM/PD, IAM, secrets and sync prerequisites |
| [36854218346](https://github.com/siamese-lang/terraformers-platform/actions/runs/36854218346) | DB Secret Sync, internal JWKS and MariaDB Service/EndpointSlice without secret exposure |
| [36889896239](https://github.com/siamese-lang/terraformers-platform/actions/runs/36889896239) | authenticated upload → durable Job → Vertex facts/generation and embedding → REQUIRED OpenSearch → GCS/MariaDB → Terraform readback |
| [36891629279](https://github.com/siamese-lang/terraformers-platform/actions/runs/36891629279) | after Pod replacement: same durable Job, source/result GCS bytes, HCL and Actuator/Prometheus readable |
| [37015159218](https://github.com/siamese-lang/terraformers-platform/actions/runs/37015159218) / [37015932695](https://github.com/siamese-lang/terraformers-platform/actions/runs/37015932695) | exact Git source SHA → immutable image digest → deployed source/digest → Ready=1, Available=1, health UP |

Stage/Job-correlated logs, low-cardinality Prometheus metrics, queue/claim/retry/cleanup categories and Backend source revision were implemented to localize incidents. Job ID and private prompt/object information are not metric label dimensions. Neither distributed tracing end-to-end nor SLO alerting is claimed as complete.

### Capacity experiment and failure interpretation

The bounded harness was designed for concurrency **1→2→4→6→8**, with 240s active windows, 5s resource samples and fail-stop. But [36957682821](https://github.com/siamese-lang/terraformers-platform/actions/runs/36957682821) stopped at concurrency **1** due to a Terraform executable correctness failure. After distinguishing an evaluation-policy false rejection from actual CLI behavior, the source-fixed correctness gate [37016993776](https://github.com/siamese-lang/terraformers-platform/actions/runs/37016993776) passed its first attempt and failed the second on `terraform_validate_configuration`, stopping at **1/5**.

Therefore **no GKE saturation point, throughput ceiling or capacity-tuning gain** was established. The correct engineering decision was to stop rather than mislabel functional failures as CPU, queue or OpenSearch saturation. Case C is portfolio-closed for the **integrated GCP service, persistence across Pod replacement and immutable release identity**, not HA or successful load benchmark.

## 5. Later Product Trust — more realistic inputs exposed a different boundary

The closed v3 Case A grounding study did not prove an image's directed topology would reach the initial generator. Product Trust moved toward actually reviewable drafts, user-visible quality and a clear trust boundary. Earlier relatively narrow corpus/reference selection was replaced with a broad, versioned v4 inventory **5,395 chunks (5,387 official/provider, 8 project decisions), 1,514 AWS resource kinds**, without treating document coverage as semantic ground truth.

The official PT-8A A–E acceptance process exposed provider timeout, truncated outputs, stage data gaps, false/unknown quality and generated HCL defects. Original negative and NOT_RUN records remain authoritative; there was **no full official PT-8A PASS**. A repeated ~220s call budget and subsequent failures were examined with Cloud Logging; HTTP 403 while listing logs was not treated as evidence of no log events, nor was the upstream physical latency mechanism invented.

**Cross-stage defect localization:** A's missing API Gateway→Cognito authorizer had to be separated into (1) image fact detection, (2) structured relationship passing, (3) retrieval and generation, (4) HCL correctness. [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274) wired extracted structured facts into initial/compact generation and removed the too-restrictive first semantic retrieval endpoint filter. [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277) distinguished service icon presence from CREATE/MANAGED intent, required directed wiring, handled aliased providers and bounded Repair adoption.

A separate **A/B/N1/N2/N3** actual-image cohort produced **5/5 terminal SUCCEEDED and 5/5 Terraform CLI PASS**, including a later same-original-image A containing authorizer wiring and N2 Repair changing an initial **22-resource** draft to **30-resource** CLI-valid draft. All five **qualityStatus=UNKNOWN**; CLI PASS is not AWS apply or image-semantic PASS. A still retains domain/auth deployment gaps, N3 custom domain/default certificate consistency is unresolved, and actual domain/certificate/zone ownership cannot be inferred from the image. N1/N3's initial and final draft stayed identical. These new N1–N3 images did **not** replace frozen official C–E. [Qualified evidence](../evaluation/pt8a-qualified-five-case-disposition.md) binds specific image SHA and Job IDs.

**Program boundary:** the user accepted a **qualified supplementary closure**, not the original complete 10-dimension official PT-8A acceptance. The PT-8B `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` gate remains unmet. The right technical response was to stop adding unsupported semantic success percentages and to retain UNKNOWN.

## 6. Final public Firebase / hosted real-user verification (T1)

User authentication with Firebase ID Tokens is not the same as owner-based database authorization; paid model submission adds a cost boundary. [ADR-009](../architecture/decisions/ADR-009-firebase-id-token-backend-boundary.md) selected a server-verified Firebase identity mapper with ACTIVE-state checks and a **server-side, default-empty billable UID allowlist** at both upload and direct Job creation.

In the real browser T1 session, two distinct Firebase email-verified users were signed in. B's authenticated own project-list request was **200**; B's access to A's Private Project **29**, existing Job, Terraform draft and diagnostics were each **403**. B's valid PNG `POST /api/upload` was **403** with no visible new project. The direct `POST /api/analysis/jobs` was **403** with exact `billable analysis is not enabled for this verified account` after the same bearer obtained `AUTH_CHECK=200`.

**T1 PASS scope:** two actual users, four protected private reads, both billable API paths. No independently metered '0 Vertex calls', global quota, all-route penetration audit or instantaneous Firebase-side token revocation is asserted. Sanitized precise [T1/T2 final evidence](terraformers-gcp-final-live-evidence-20261011.md) avoids UID/JWT disclosure.

## 7. Final GKE live interruption (T2) and unambiguous interpretation

The approved bounded experiment allowed one paid Job and one **graceful Pod deletion**, not a force-kill or retry loop. A's temporarily enabled paid access allowed Project **30**, Job `f8c00ef4-fb4e-4cdd-b30a-d1d5f5226559`. The log watcher detected the original `Analysis job execution started` and requested a single Pod deletion while it was active.

| UTC 2026-10-11 | Actual source-bound observation |
|---|---|
| 19:51:36.883 / .906 | original claim `generation=1 attempt=1`, then execution started |
| 19:51:50.613 | Vertex facts `finishReason=STOP`, `elapsedMs=8096` |
| 19:51:50.631 | REQUIRED OpenSearch/RAG retrieval failed with `BeanCreationNotAllowedException` |
| 19:51:50.635 | `analysis_execution` failed after ~13.5s |
| 19:51:50.656 / .662 | original generation 1 recorded `FAILED` |

Spring shutdown explains late bean creation being rejected, but no full stacktrace identified the exact first Bean. Authenticated Job GET returned HTTP **200** with `status=FAILED`, `resultFileId=null`, `resultObjectKey=null`, terminal around **13s**. New Backend Pod was **1/1 Ready**, original was absent, public HTTP **200** and paid-UID deployment override returned **ABSENT**.

**Correct result:** `GRACEFUL_SHUTDOWN_FAILURE_RECORDING: PASS`, `LEASE_EXPIRY_RESTART_RECOVERY: NOT_TESTED`. The original worker finished long before a 60s lease expired. This does not prove abrupt crash recovery, no duplicate upstream provider call under every crash timing, or GCS residue absence. The shutdown-time bean lifecycle issue remains a real observed limitation, not a completed repair.

## 8. Ranked engineering material for portfolio / source-bank revision

| Story | Original problem → choice → proof | Limitation to retain |
|---|---|---|
| **A grounding false-positive** | VPC 3/3 missing despite valid generation → role-aware evidence and adaptive selection → canonical N=3 / frozen holdout | controlled samples; no universal semantic truth |
| **B durable ownership** | stranded/duplicate jobs → DB claim/lease/generation → B5 12-row suite and real MariaDB concurrent winner | no exactly-once model invocation |
| **B cross-store integrity** | GCS write followed by DB failure → pre-write intent, fenced compensation, recoverable PENDING cleanup → B5-08/09 | not new live T3 injection |
| **C cloud/runtime decision** | evaluation-only Pod insufficient → separate MariaDB VM/GKE/Vertex/OpenSearch/GCS + WIF immutable digest → end-to-end and post-replacement reads | no HA or production saturation |
| **C measurement discipline** | functional Terraform failures under nominal load → fail-stop before false bottleneck attribution | no capacity performance number |
| **Product Trust facts flow** | directed facts missed in first generator → #274/#277 correction → later same-image authorizer observation | not 5/5 semantic PASS |
| **T1 ownership and billable gates** | real authenticated cross-owner/cost exposure → server owner/default-deny checks → 403 matrix | not formal penetration test |
| **T2 live diagnosis** | Pod shutdown during active paid Job → stage correlated facts success / RAG bean failure and FAILED without result | lease expiration not tested |

Do not compress early Case A/B/C into tool listings while giving only the latest browser incident a complete causal story. Do not combine different code/model/fixtures into one controlled before/after, and do not take official documentation as the correct intent of an ambiguous picture.

## 9. Completion, data preservation and controlled GCP teardown

The user decided that the bounded modernization's portfolio engineering evidence was sufficient: **stop further feature expansion, repeated AI evaluations and T3 fault injection**. Keep negative/UNKNOWN evidence, and retain these repository documents plus externally held original diagnostics/screenshots where needed. The current GCP surface includes hosted Firebase login, Ingress/managed TLS, load balancer/global static IP and frontend in addition to the Terraform-managed target. An older fixed **29-delete** procedure must not be run unreviewed against the live hosted system. [Hosted teardown preflight](../lifecycle/gcp-2026-hosted-teardown-preflight.md) requires exact read-only inventory/cost and a separate USER destructive approval. No teardown or code modification is authorized by this file.
