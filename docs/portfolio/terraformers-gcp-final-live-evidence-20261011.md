# Terraformers — final GCP live evidence snapshot (2026-10-11 UTC)

> **Disposition: PORTFOLIO_EVIDENCE_BACKED_ENGINEERING_CLAIMS_SUFFICIENT / FULL_PRODUCT_TRUST_ACCEPTANCE_NOT_ESTABLISHED / GCP_TEARDOWN_NOT_YET_EXECUTED.**
>
> This is a **sanitized operator-observed evidence transcript**, not a signed CI artifact or independent replay. Source: USER-supplied Cloud Shell, Cloud Logging, browser Network and authenticated API observations during the 2026-10-11 T1/T2 session. No bearer tokens, Firebase UIDs, passwords, prompts, raw images or private Terraform HCL are committed. The original official PT-8A negative/not-run ledger is preserved unchanged.

## 1. Scope and source provenance

- Repository: [`siamese-lang/terraformers-platform`](https://github.com/siamese-lang/terraformers-platform).
- Verified remote `main` and deployed Backend `BUILD_SOURCE_REVISION` on the test date: `5dd67b046ed802089b1a55b7bf07343b7a6316e6`. This exact value was read both from GitHub and the running GKE container.
- Backend immutable image digest at pre-test readback: `sha256:228c9be092c6e2255617e0f8061b41f4fc0ae63353b86bc43fb75507e82a2ab6`. Frontend separately reported `sha256:dd0ca8ed5e3c3fa54b4f0c9612dd15074ebbff11df5535482681ce3c6b2e7ed4`. These are **observed at that time**, not a timeless production-state guarantee.
- GCP project `terraformers-platform`; GKE namespace `terraformers-target` in `asia-northeast3-a`; public URL `https://otogi.cloud`. Runtime configured with `JWT_PROVIDER=firebase`, `ANALYSIS_PROVIDER=vertex`, `RETRIEVAL_MODE=REQUIRED`, `ANALYSIS_RESULT_BUCKET_NAME=terraformers-runtime-objects-21647422237`.
- Deployed Backend replica `1/1`, `RollingUpdate`, graceful termination period `30s`. Code in `AnalysisRuntimeProperties` enforces `maxAttempts=1` under Policy D, with configured-default lease `60s`, renewal `20s`, accepted-age cutoff `8m`.
- `FIREBASE_BILLABLE_UIDS` ConfigMap value was empty; an earlier Deployment-level override was identified and removed **before** T1. Final post-T2 operator readback: `OVERRIDE=ABSENT`, public root `HTTP=200`.
- This later hosted Firebase/GKE phase extends the earlier repository-only S1 plan. The old S1 phrase “public hosted login not observed” is a historical pre-observation limitation, **superseded for the narrowly observed T1 claims below**. It does not become general production security certification.

## 2. T1 — real Firebase two-user authorization and billable-entry denial

**Actors:** A = existing verified Firebase owner; B = newly created, separately signed-in, email-verified Firebase user in a Chrome incognito session. Account identifiers intentionally withheld. A's owner-scoped private Project `29` contained an existing image/analysis result and Job `af4f5333-54eb-44bf-835e-ab251188b3be`. Normal A owner view was observed. B's own `GET /api/projects` returned `200` including a browser resend of the authenticated request. Earlier Console-only 401s from a manually assembled request were **not** treated as authorization evidence.

| Authenticated B request | Observed HTTP | Interpretation |
|---|---:|---|
| `GET /api/projects` | 200 | Baseline authenticated API |
| `GET /api/projects/29` | 403 | PRIVATE project owner isolation |
| `GET /api/analysis/jobs/af4f5333-54eb-44bf-835e-ab251188b3be` | 403 | AnalysisJob owner isolation |
| `GET /api/projects/29/terraform/main.tf` | 403 | Generated draft access isolation |
| `GET /api/analysis/jobs/af4f5333-54eb-44bf-835e-ab251188b3be/diagnostics` | 403 | Private diagnostic access isolation |
| `POST /api/upload` with a valid nonempty PNG, project name `t1-b-denied-test` | 403 | Denied before upload-driven analysis |
| B project-list readback after denied upload | No new `t1-b-denied-test` project | No user-visible project creation |
| `POST /api/analysis/jobs` with syntactically valid JSON using `projectId=29`, `sourceFileId=1` | 403 | Direct-job submission denied |
| Direct-POST response body | `billable analysis is not enabled for this verified account` | **Explicit billable entitlement gate**, not merely project ownership |

For the direct POST, the **same manually supplied bearer token** returned `AUTH_CHECK: 200` on `GET /api/projects` immediately before the request. The direct Job endpoint responded `T1_DIRECT_JOB: 403` with the exact entitlement denial above. No further POST was issued. The target Project 29 also belonged to A, but the observed explicit response and [`AnalysisJobController`](../../backend/src/main/java/com/terraformers/modernization/analysis/AnalysisJobController.java) confirm the billable gate is evaluated before `AnalysisJobService.create` ownership/finalization.

**Code-backed mechanisms:** [`AnalysisUploadService.upload`](../../backend/src/main/java/com/terraformers/modernization/analysis/AnalysisUploadService.java) checks `BillableAnalysisPolicy.requireAllowed` after user lookup and nonempty-file validation, **before** `resolveProject`, object storage and Job creation. [`BillableAnalysisPolicy`](../../backend/src/main/java/com/terraformers/modernization/security/BillableAnalysisPolicy.java) requires ACTIVE Firebase identity, verified email and server-controlled UID membership. Empty allowlist is default-deny. [`AnalysisJobService.get`](../../backend/src/main/java/com/terraformers/modernization/analysis/AnalysisJobService.java) applies project accessibility checks, while private diagnostics require owner/modifier authorization.

**T1 disposition:** PASS for actual two-user access isolation of the four named private surfaces and for actual denial of **both** billable API entry points under the observed configuration. **Limits:** zero Vertex calls and zero internal Job rows for denied actions were **inferred from checked ordering and absence of a visible new project**, not measured with independent model billing/DB telemetry. No complete penetration test, cross-replica quota, revoked-token immediate invalidation, or arbitrary tenant matrix is claimed.

## 3. T2 — one paid live Job interrupted by Backend graceful pod deletion

Explicit USER approval covered **one** new analysis submission and **one** normal Pod deletion, without force-kill, repeat-until-green or additional model calls. A was temporarily granted billable access via the Backend Deployment env override and rollout readback: `VERIFY_ENTITLEMENT=true`, Backend `1/1 Running`, public root `HTTP=200`. The test uploaded one previously validated architecture image under Project `30` named `t2-pod-interruption`. Its Job ID was **`f8c00ef4-fb4e-4cdd-b30a-d1d5f5226559`**.

The Cloud Shell watcher was armed to tail **new** logs from original Pod `terraformers-backend-674cb5d6d4-lbdd2`, recognize that Job's `Analysis job execution started`, wait 5 seconds, confirm no observed completion, verify the Pod's original UID, then request a **single** `kubectl delete pod ... --grace-period=30 --wait=false`. Output:

```text
T2_WATCH=ARMED
T2_JOB_ID=f8c00ef4-fb4e-4cdd-b30a-d1d5f5226559
T2_EXECUTION_STARTED=OBSERVED
T2_POD_DELETE=REQUESTING
pod "terraformers-backend-674cb5d6d4-lbdd2" deleted from terraformers-target namespace
T2_POD_DELETE=DISPATCHED
```

`DISPATCHED` means Kubernetes accepted the deletion; it does **not** prove abrupt process loss or a hard kill. Subsequent `kubectl rollout status` passed; the original Pod was absent and replacement `terraformers-backend-674cb5d6d4-r7hlk` was `1/1 Running`, restarts `0`; public root `HTTP=200`.

### 3.1 Cloud Logging: exact observed Job sequence

All timestamps below are **2026-10-11 UTC**; log lines carry `analysisJobId=f8c00ef4-fb4e-4cdd-b30a-d1d5f5226559`, deployed source SHA `5dd67b...`, and the same original worker `analysis-job-1`.

| UTC | Log evidence | Supported interpretation |
|---|---|---|
| 19:51:36.878 | Progress `RUNNING` for Project 30 | Analysis started |
| 19:51:36.883 | `claimed outcome=initial_claim generation=1 attempt=1` | Single observed durable claim |
| 19:51:36.906 | `Analysis job execution started` | Actual worker started |
| 19:51:50.613 | `Vertex provider call stage=facts outcome=received finishReason=STOP outputTokens=295 totalTokens=1838 elapsedMs=8096 configuredTimeoutMs=370000` | Vertex **facts** invocation returned successfully |
| 19:51:50.631 | `reference retrieval outcome=failure mode=REQUIRED stage=search embeddingProvider=VERTEX index=terraformers-reference-v4 errorClass=org.springframework.beans.factory.BeanCreationNotAllowedException elapsedMs=4` | RAG search failed during Spring bean lifecycle |
| 19:51:50.632 | `Vertex reference retrieval outcome=failure ... errorClass=BeanCreationNotAllowedException` | Same exception propagated |
| 19:51:50.635 | `analysis stage outcome=failure stage=analysis_execution category=other errorClass=BeanCreationNotAllowedException elapsedMs=13509` | Failed execution, not a Vertex facts timeout |
| 19:51:50.656 | Progress `FAILED` | Original worker published failure |
| 19:51:50.659 | `terminal quality ... technical=FAIL knowledge=UNKNOWN quality=UNKNOWN projectDecision=UNKNOWN ... reasons=[]` | Quality is not a semantic assessment of draft |
| 19:51:50.662 | `Analysis job failed outcome=failed exceptionCategory=other generation=1` | Original generation marked failed |

`BeanCreationNotAllowedException` is consistent with Spring ApplicationContext destruction prohibiting late bean creation while an async worker is still executing. Pod deletion had been requested during RUNNING, making shutdown a strong contextual explanation. **No full exception stack/first failing bean was captured**, so do not claim its exact lazy dependency.

The verified A-owner browser showed Project 30 `FAILED`, accepted at 2026-10-12 04:51:36 KST, terminal at 04:51:50 KST, accepted-to-terminal approximately **13 seconds**. Separate authenticated Job GET returned `HTTP=200`, JSON fields `status=FAILED`, `resultFileId=null`, `resultObjectKey=null`. The web page showed technical `FAIL`, knowledge/project quality `UNKNOWN`. Afterward the temporary Firebase UID override was removed; **final** deployment readback `OVERRIDE=ABSENT`, root `PUBLIC_HTTP=200`.

### 3.2 What the experiment does and does not prove

**Observed:** analysis started with claim generation/attempt `1/1`; a paid Vertex facts call completed; a controlled normal Pod deletion overlapped the active job; late RAG bean creation failed; the original worker persisted `FAILED`; no result metadata/file was attached; a replacement Backend recovered and continued serving; paid access was revoked. The invalid partial outcome was **not** presented as a successful Terraform draft.

**Not observed:** 60-second Lease expiry; stale-owner fencing after an ungraceful kill; a second claim or a durable recovery sweep; exact provider wire-RPC count; DB direct inspection of attempt history; absence of internal GCS objects; cross-zone HA or uninterrupted availability. Since the Job terminalized in ~13 seconds **within its original worker**, classify this as **`GRACEFUL_SHUTDOWN_FAILURE_RECORDING: PASS`; `LEASE_EXPIRY_RESTART_RECOVERY: NOT_TESTED`**. The shutdown-time bean error is a **remaining runtime limitation**, not a defect silently considered fixed.

## 4. Link to previous strongest evidence (do not over-combine conditions)

| Engineering theme | Strongest previous repository evidence | New observation's additional value |
|---|---|---|
| Structured facts → retrieval → generation and Repair | [PT-8A qualified five-case disposition](../evaluation/pt8a-qualified-five-case-disposition.md), [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274), [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277) | Hosted user-facing boundary, **not** new five-image semantic grading |
| Official documentation/RAG scale and v3 controls | [Current evidence ranking](current-evidence-prioritization-20261011.md), [Case A closure](../evaluation/case-a-final-closure.md) | No extra image-ground-truth evidence in T1/T2 |
| Durable analysis, compensation, fencing and MariaDB concurrency | [B5 integrated closure](../evaluation/case-b-integrated-closure.md), workflow `36555800770` | T2 directly observes one real shutdown/error path, **not** B5 lease/fencing or GCS compensation re-proof |
| GCP integrated serving, persistent objects, immutable delivery and successful pod replacement | [Case C closure](../evaluation/case-c-portfolio-closure.md) | T1 actual Firebase cross-user denial and T2 interruption while executing |
| Browser authentication and Firebase host deployment | [ADR-009](../architecture/decisions/ADR-009-firebase-id-token-backend-boundary.md), [F3 hosted runbook](../runbooks/firebase-auth-f3-hosted-gke.md) | Live A/B browser-authenticated authorization and paid-entry-denial observation |

Original official PT-8A A–E **full acceptance remains NOT PASS/NOT RUN as historically recorded**; the distinct supplementary A/B/N1/N2/N3 cohort had **five SUCCEEDED and Terraform CLI PASS**, but **qualityStatus UNKNOWN** and no AWS apply/deployment claim. T1/T2 do not waive original PT-8B admission or authorize further Product Trust phases.

## 5. Final decision and preservation boundary

- **Portfolio decision (USER, 2026-10-11):** engineering evidence is sufficient for final portfolio material. No new AI/RAG generation-path repairs, T3 live GCS compensation injection, full PT-8A retest, PT-8B, performance/HA claims or speculative new verifiers are needed for this objective.
- **Product decision:** This is a reviewable Terraform **draft** generator, not an automatically deployed or semantically guaranteed infrastructure builder. Bound all claims to actual Job/CLI results and known false/unknown semantics.
- **T3:** deferred; prior B5 deterministic compensation/recovery plus MariaDB smoke remains accepted within its actual scope. A real fault-injected GCS write + MariaDB commit failure is **not** asserted.
- **Data privacy:** original raw evidence may be held privately by the operator. This file stores only redacted observations and Job identifiers. The source code, existing PR/workflow history and this transcript survive runtime deletion; MariaDB/GCS/Cloud Logging evidence **may not** survive GCP resource cleanup/retention.
- **Project lifecycle:** document/review and preserve independently usable portfolio material **before** any destructive GCP teardown. [Current completion plan](../plans/active/portfolio-final-completion.md) S3 distinguishes sufficient portfolio claims from unestablished full Product Trust acceptance; S4 requires a separate exact inventory and destructive approval. See [2026 teardown delta/preflight](../lifecycle/gcp-2026-hosted-teardown-preflight.md).

**Acceptance checkpoint:** No repository code, GCP resource, IAM, Cloud Storage object, Firebase account, DNS record or live Job is changed by committing this file. No teardown has been performed.
