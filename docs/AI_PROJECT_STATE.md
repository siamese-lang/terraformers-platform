# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- M0 closure evidence SHA: `3ccf17582ae91ad131d3efe8ce1a38492c401c72`

M0 closure was validated against this main SHA. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current execution mode

- Mode: **Portfolio Case Reassessment**
- Status: **FEATURE/MILESTONE PROGRESSION PAUSED**
- Active reassessment plan:
  [Portfolio Case Reassessment](plans/active/portfolio-case-reassessment.md)
- Measurement/acceptance source of truth:
  [Portfolio Case Measurement & Acceptance Contract](plans/active/portfolio-case-measurement-contract.md)
- Current single task: **Case C architecture closure is active; capacity baseline is SUSPENDED_BY_ARCHITECTURE_AUDIT. Current phase is C2 — Repeated correctness stability gate (ACTIVE).**

<!-- CASE_C_ARCHITECTURE_CLOSURE:START -->
### Case C architecture closure control

- Overall: **ARCHITECTURE_CLOSURE_ACTIVE**
- Capacity baseline: **SUSPENDED_BY_ARCHITECTURE_AUDIT**
- Current phase: **C2 — Repeated correctness stability gate (ACTIVE)**
- Open blockers: C-ARCH-04, C-ARCH-05, C-ARCH-06
- Durable issue register: .agents/state/case-c-architecture-closure.json
- Generated plan: [Case C Architecture Closure](plans/active/case-c-architecture-closure.md)
- Failed baseline attempt retained as evidence: run 36957682821; it stopped at concurrency 1 on a non-capacity Terraform executable-correctness failure.
- Do not retry the capacity baseline until C1–C4 prerequisites are resolved and a new live checkpoint is approved.
<!-- CASE_C_ARCHITECTURE_CLOSURE:END -->
- M0/M1: **AUDITED / RETAINED**
- M2: **RETAINED FOUNDATION**
- M3: **RETAINED CASE A BASELINE**
- M4: **REASSESS**
- M5: **EVIDENCE RETAINED**
- M6: **REASSESS / NOT ACCEPTED AS FINAL RELIABILITY DESIGN**
- M7/M8/M9: **PAUSED**

Historical `COMPLETE` or `ACTIVE` labels later in this file are evidence history only and do not
authorize automatic progression while this reassessment is active.

The project success criterion is now explicit: complete three technically defensible engineering
cases with repository-backed technical decisions, not merely a sequence of completed milestones.

Case A — AI/RAG quality, performance and reliability:
- **PORTFOLIO-CLOSED / PASS** in
  [Case A Final Closure](evaluation/case-a-final-closure.md);
- final post-PR119 canonical N=3 runs `36803174654`, `36803781744`, and `36804600570` all used
  source `32d62e21821ed303555ade5e26b03cb669db94ef` and the same configuration fingerprint;
  fact extraction passed `18/18`, retrieval `18/18`, VPC decision coverage `3/3`, VPC required
  resource coverage `4/4 × 3`, grounding gaps `0/12`, positive validation `12/12`, negative
  controls `6/6`, and first divergence remained zero;
- frozen holdout run `36805478708` passed on the same source/configuration: both positive holdout
  cases had complete project-decision/resource grounding and validation, both negative controls were
  correct, grounding gaps were zero, and first divergence remained zero;
- adaptive retrieval >8-resource live measurement is **CLOSED / PASS** in
  [Adaptive Retrieval Live Measurement Closure](evaluation/adaptive-retrieval-live-measurement-closure.md):
  run `36799722509` on source `9d54fe27bbc24347c9eff09193c7aeaec3171ff6` reused one delegated
  `gemini-embedding-001` vector and the production `OpenSearchReferenceRetriever`; control
  maxEvidence 8 selected 8 documents and covered `10/12` resources, missing
  `aws_route_table` and `aws_db_instance`, while adaptive maxEvidence 16 selected exactly 10
  documents and reached `12/12` by admitting the two missing provider-schema anchors;
- latest latency side-investigation is **CLOSED** in
  [Gemini Fact-Reuse Latency Diagnostic Closure](evaluation/gemini-fact-reuse-latency-diagnostic-closure.md);
  the three successful instrumented attempts in run `36758290144` reproduced a text-only
  CloudFront candidate generation tail of `109773 ms` with one HTTP exchange, zero network
  failures, and HTTP 200; the delay accumulated before response headers, so production fact-reuse
  adoption is **HOLD**, generic retry is not selected, and the diagnostic must not be repeated until
  lucky;
- retain M3 fixed baseline/provenance and M4 failure evidence;
- do not treat M4 as portfolio-closed;
- primary problem selected: **retrieval grounding / required-evidence coverage**;
- the retained VPC trace has required decision coverage `0 / 1` and exact required resource-type
  coverage `2 / 4` at top-K 8, while generation produced `4 / 4` required resources and Terraform
  validation passed; retrieval stage `PASS` therefore does not prove grounding coverage;
- the M4 fact-extraction truncation remediation is retained as a completed precursor, while the
  one-run AOSS `130489 ms` outlier remains measurement-only;
- A1 measurement readiness and the A2 repeated current baseline are **COMPLETE**; the
  [A2 evidence](evaluation/case-a-retrieval-grounding-a2-baseline.md) reproduces the VPC grounding
  gap `3 / 3` with production retrieval behavior unchanged;
- A3 live comparison is **COMPLETE** in run `36600868601` on source
  `ab1ae4b1db6011b2fe72a7c5a2b3913232d63857`; artifact
  `case-a-a3-retrieval-probe-36600868601` has digest
  `sha256:7052c94eac9f00f4d80514f534d5b701ea2f0985493faea487c473e975d51c74`;
- A3 showed required evidence below the shared global K8 cutoff, not that K24 should be a production
  context. A4 therefore selects resource-aware acquisition plus bounded deterministic coverage
  selection and rejects global K24 widening, unfiltered retrieval, relationship-first retrieval,
  and pure global priority reranking;
- the A4 candidate was merged at `34c9dbecfbecdf4bca2be47b64d771c9e68549ca`; subsequent A5
  canonical N=3 evaluations progressed through the PROJECT_DECISION ordering correction in PR #118,
  where VPC decision coverage reached `3/3`, VPC resource coverage stayed `4/4 × 3`, grounding
  gaps fell to `0/12`, and positive validation passed `12/12`, but negative controls were
  `5/6`; PR #119 then corrected the zero-hit REQUIRED-grounding/classification lifecycle boundary.
  The final post-PR119 N=3 and frozen holdout have now passed and Case A is **PORTFOLIO-CLOSED**.

Case B — Backend durable asynchronous processing:
- retain M5 failure evidence and useful M6 primitives such as atomic claim and rollback-safe compensation;
- measurement-readiness evidence after PR #94 is recorded in
  [Case B Measurement Readiness Evidence](evaluation/case-b-measurement-readiness.md);
- ADR-007 accepts MariaDB as the durable AnalysisJob ownership/work source with lease/fencing,
  bounded selective retry, deterministic result identity, and durable cleanup accountability;
- current in-process executor remains a bounded local execution pool rather than the durable delivery source;
- RabbitMQ alone is rejected for the current requirement because it introduces a DB-commit→publish
  gap; Transactional Outbox + RabbitMQ is deferred until measured scale/fan-out/service-boundary
  evidence justifies the added operational layer;
- implementation is split into B1 durable state/fencing, B2 dispatcher/recovery, B3 bounded retry,
  B4 result/cleanup safety, and B5 integrated closure;
- B1 is complete in PR #97 / merge `237c97f35184312cac3af56aee438da830f4ff8a`: additive
  durable state, lease/fencing transitions, strict lease/retry-time invariants, stale-generation
  rejection, and real MariaDB single-owner initial/reclaim contention are verified;
- B1 evidence is recorded in
  [Case B B1 Durable State and Fencing Evidence](evaluation/case-b-b1-durable-state-fencing.md);
- B2 is complete in PR #99 / merge `0f18e437af3aaf3c16c3ed075e8963c32cdab240`, with evidence in
  [Case B B2 Durable Dispatcher and Restart Recovery Evidence](evaluation/case-b-b2-durable-dispatch-recovery.md);
- MariaDB durable eligibility is now the execution recovery source, while the executor remains the
  bounded local concurrency pool; restart-to-FAILED is no longer the recovery model for recoverable
  AnalysisJob work;
- B3 is complete in PR #101 / merge `fb1bc3f7b64a0a0274114f1e29ac9f28f2ae652d`, with evidence in
  [Case B B3 Selective Bounded Retry Evidence](evaluation/case-b-b3-selective-bounded-retry.md);
- automatic provider retry is enabled only for the approved timeout signal; another retry is
  scheduled only while current `attempt_count < 3`, with a fixed `10s` delay durably represented by
  MariaDB `next_attempt_at`; B2 expired-lease reclaim is not capped by that scheduling threshold;
- B4 is complete in PR #103 / merge `90c47cb9c0fcdbe75258ed2ac4613cb1625a5463`, with direct
  changed-date acceptance evidence added by PR #105 / merge `035b950975ef90c292856bd720ed23a729e12971`; evidence is in
  [Case B B4 Result Idempotency and Durable Cleanup Accountability](evaluation/case-b-b4-result-idempotency-cleanup.md);
- B4 provides deterministic identity, pre-write durable intent, fenced canonical mutation,
  serialized compensation, exact `PENDING` accountability, and bounded cleanup recovery;
- B1/B2/B3/B4/B5 are **COMPLETE**. The B5 execution and integrated closure are **PASS** in
  [Case B Durable Processing plan](plans/active/case-b-durable-processing-implementation.md);
- Case B is **portfolio-closed**. The 12-row matrix, fresh GitHub Actions run `36555800770`, real
  MariaDB contention evidence, hard-gate results, trade-offs, and residual limitations are recorded
  in [Case B Integrated Durable-Processing Closure](evaluation/case-b-integrated-closure.md).

Case C — Cloud runtime capacity and safe delivery:
- measurement readiness audit is **COMPLETE / NOT READY FOR LIVE BASELINE** in
  [Case C Measurement Readiness Audit](evaluation/case-c-measurement-readiness-audit.md);
- the representative-runtime compatibility verification is **COMPLETE** in
  [Case C Production-Representative Runtime Decision Gate](plans/active/case-c-representative-benchmark-runtime-decision.md)
  and [Case C Compatibility Verification](evaluation/case-c-compatibility-verification.md):
  Cloud Storage **PASS**, Artifact Registry **PASS**, MySQL 8.4 **FAIL**;
- MySQL 8.4 connected successfully and applied migrations 001–002, then V003 failed on
  `ADD COLUMN IF NOT EXISTS` syntax while MariaDB 11.4 remained green; therefore Cloud SQL for
  MySQL is not a drop-in relational choice under the unchanged-migration gate;
- [Case C Relational Hosting Decision](plans/active/case-c-relational-hosting-decision.md) is
  **SELECTED**: retain MariaDB 11.4 on one dedicated Compute Engine VM, containerized with an exact
  image digest and a dedicated Persistent Disk for DB data; do not place MariaDB in the current GKE
  cluster for the first baseline;
- `e2-medium` plus a 20 GiB `pd-balanced` data disk are initial candidates only; no cloud creation
  is authorized until a read-only `asia-northeast3` quota / project quota / Free Trial-credit
  preflight passes;
- [Case C GCP Quota / Cost Preflight](evaluation/case-c-gcp-quota-cost-preflight.md) is
  **QUOTA / MACHINE PASS** in workflow run `36823850490`; it reused the existing GCP plan workflow and the
  existing read-only plan identity; current quota supports the additional `e2-medium` DB VM and 30 GiB balanced-disk envelope without Terraform init/plan/apply or resource mutation; Free Trial budget gate passed by explicit user confirmation on 2026-10-01;
- [Case C GCP Full-Backend Adapter Readiness](evaluation/case-c-gcp-full-backend-adapter-readiness.md)
  is **COMPLETE / PASS**: Backend Local Verification run `36825659118` and Terraform Static
  Verification run `36825659010` succeeded; the prod startup contract no longer hardcodes Bedrock
  for active retrieval, GCS reader/writer/remover adapters implement the provider-neutral storage
  boundary through ADC, and the canonical GCP overlay selects `gcs`; no GCP resource/IAM mutation
  occurred;
- [Case C Immutable Backend Image Delivery Readiness](evaluation/case-c-immutable-backend-image-delivery-readiness.md)
  is **LIVE-CLOSED / PASS**. PR #147 merged as
  `0774c80bb3cf9f684567e30c1e329fc27dc05d04` after final Terraform Static Verification run
  `36832747254` passed. Separately approved live checkpoints then succeeded: the dedicated
  `terraformers-image-publish` WIF publisher/environment bootstrap passed with no project resource
  role or user-managed key; the protected Terraform apply identity was refreshed with
  `roles/artifactregistry.admin`; delivery-foundation run `36833957446` applied exactly
  `5 added, 0 changed, 0 destroyed`; and image publication run `36834457170` built source
  `0774c80bb3cf9f684567e30c1e329fc27dc05d04`, verified `BUILD_SOURCE_REVISION`, published only
  the full-SHA tag, and resolved remote digest
  `sha256:a9331bc8026075390cedd8bfcdc8625b5cc69cdf16cd3799e2029beff6f857ae`.
  No Kubernetes backend Deployment has been performed;
- the existing GKE/Vertex/OpenSearch substrate and Case B durable-job implementation are reusable,
  but the live target does not yet expose the full authenticated API → durable job → AI/RAG →
  persistence path that Case C must measure;
- the canonical GCP overlay explicitly leaves the full backend Deployment deferred because database,
  object-storage and JWT dependencies are not supplied in the live target boundary;
- existing portable MariaDB/JWKS/filesystem fixtures are reusable patterns, but MariaDB `emptyDir`
  and filesystem `/tmp` are not valid rollout-durability evidence;
- the immutable backend artifact is now available at
  `asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:a9331bc8026075390cedd8bfcdc8625b5cc69cdf16cd3799e2029beff6f857ae`,
  but the GCP backend Deployment is still deferred until its database, object-storage, JWT/secret,
  and runtime wiring are explicitly completed;
- do **not** load-test the Case A evaluation pod: it would omit API acceptance, durable job queueing,
  executor saturation, persistence, accepted-work survival and rollout/rollback behavior;
- [Case C Runtime Dependency / Deployment Readiness Decision](plans/active/case-c-runtime-dependency-deployment-readiness-decision.md)
  is **SELECTED**; repository implementation, static CI, IAM bootstrap, secret-container foundation,
  initial secret versions, runtime dependency apply, and isolated Kubernetes prerequisites are now
  complete;
- the mandatory read-only capability gate passed on GKE control plane and node pool
  `1.35.8-gke.1225000`, satisfying the native Secret Sync >=1.33 requirement without a cluster upgrade;
- IAM bootstrap was verified after PR #151 hardened quota-project selection and fail-closed policy
  reads; Terraform Static Verification run `36846676527` passed;
- protected run `36847201560` applied exactly `3 added, 0 changed, 0 destroyed` for Secret Manager
  API enablement plus the two empty MariaDB secret containers; the separately approved operator
  bootstrap then created initial version `1` for both secrets without printing payloads;
- protected run `36851221194` applied exactly `9 added, 1 changed, 0 destroyed` using immutable
  MariaDB image `mariadb:11.4@sha256:70cc072b29b4a89ae07abb2d4da2c64678a7f2dfe092751bb51c87d67dc1338b`;
  the only update enabled native Secret Sync, while the creates established the reviewed private GCS,
  MariaDB VM/PD/firewall/service-account, and secret-access boundary;
- protected run `36854218346` applied only the isolated Kubernetes prerequisite surface; Secret Sync
  produced the expected DB-password key without printing it, the internal JWKS Deployment rolled out,
  the MariaDB Service/EndpointSlice exists, and the backend Deployment remained absent;
- the current bounded implementation adds one `runtime-live-acceptance` operation to the existing
  runtime-dependency workflow rather than creating another workflow. It reuses the published backend
  image for a backend-KSA GCS write/read/delete probe and the exact MariaDB image for a private DB
  marker-row test across one reviewed VM stop/start; marker survival plus changed MariaDB container
  hostname is the acceptance evidence for PD-backed `/var/lib/mysql` persistence and startup-script
  container recreation;
- the live-acceptance workflow implementation must pass PR CI and review before the actual VM restart;
  backend Deployment, load generation, rollout/rollback, HPA/replicas, executor tuning, node/OpenSearch
  resizing and external IdP remain excluded.

Observability and failure/load work may continue only when it closes a measurement gap for Case A,
Case B, or Case C. PR #86/#88 evidence is retained but is not independent permission to advance M7.


M4-1 is complete. PR #69 added provider-neutral fact-extraction failure subtypes, sanitized
provider evidence, and deterministic offline coverage without adding retry/backoff or changing the
prompt/model/dataset/corpus/retrieval/validator contract. The PR head was
`81b6088e70cb6440fe6319271abdc22f80a4cebf`; all eight relevant pull-request workflows completed
successfully before merge. The implementation was merged as
`6b6cd4bec6e0ecf78c8d9fb2d1f505201b6260a0`.

The existing target runtime is currently idle after the bounded M4-2 evidence session. PR #71 added the
protected `activate` operation and merged as
`64dbaae156906e3705e4bfeb161fb11bacd79f52`. Protected run `36384234371` passed its contract
gate with exactly one managed-resource change, updated only
`google_container_node_pool.target`, applied `0 added, 1 changed, 0 destroyed`, and verified the
cluster/node pool `RUNNING` with Terraform `node_count=1`. The saved plan JSON SHA-256 was
`49055c3593910e55d3a7f68a18a8502f09ef18d06523203817c9439e35942b22`.

M4-2 is complete on the unchanged canonical configuration. Run `36385950712` reproduced
`arch-vpc-three-tier` as `FACT_EXTRACTION / OUTPUT_TRUNCATED` with
`reason=RESPONSE_TRUNCATED`; retrieval, generation, and validation did not run. Run
`36386233526` did not reproduce the AOSS failure: fact extraction, retrieval, generation, and
validation all passed with no first divergence and generation `retryOccurred=false`. Both runs used
configuration fingerprint
`sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`.
Detailed evidence is in
[`m4-r2-live-reproduction-evidence.md`](evaluation/m4-r2-live-reproduction-evidence.md). Generic
retry/backoff is therefore not justified; M4-3 is constrained to the demonstrated truncation path.

Post-evidence idle run `36386950442` on `c8ae238cfcad31c03f5e5b3bec1b59f5c9b513be` passed with
one in-place `google_container_node_pool.target` update, plan JSON SHA-256
`2d36638ec05b13cdc7c3f9b4b99d12b2f4b945df4658864c8ef8460d1599afcd`,
`0 added, 1 changed, 0 destroyed`, and the final idle `node_count=0` boundary check.

M4-3 is complete. PR #74 merged as
`4369feb20da5ef4c48986667c88606cc6488259f` and changed only Vertex fact extraction to explicit
`LOW` thinking while keeping the existing `MAX_FACT_TOKENS=800` bound, prompt/schema, models,
corpus, retrieval/top-K, validator, and no-retry behavior. Targeted VPC validation run
`36388548679` passed fact extraction, retrieval, generation, and validation with no first
divergence and `retryOccurred=false`.

Historical M4 implementation checkpoint: M4-4/M4-5 had been marked complete before the current reassessment. Full six-case run `36391698161` on commit
`2526f85b4bef781976126f99e5ed0c25344d0f94` used the unchanged dataset and canonical
configuration fingerprint
`sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`.
Fact-extraction PASS changed from 4/6 to 6/6 and first divergence from 2 to 0; retrieval and
generation passed for all six cases, all four architecture cases passed validation, both negative
controls remained correctly classified, and no generation retry occurred. The after artifact is
`m3-live-baseline-36391698161` (id `10955719668`, digest
`sha256:3697bf1a703b437132d6bf98eb89a51ab45810154afb02d3e5d3e1ffcbb7d1c6`).
Residual findings are explicit rather than tuned away: the newly reachable VPC retrieval stage
missed part of its fixed top-8 retrieval requirement, and AOSS fact extraction had a one-run
`130489 ms` latency outlier. Detailed closure evidence is in
[`m4-targeted-improvement-closure.md`](evaluation/m4-targeted-improvement-closure.md).

The final M4 live session was closed by protected idle run `36392580754`. It updated only
`google_container_node_pool.target` from 1 to 0, applied `0 added, 1 changed, 0 destroyed`,
used plan JSON SHA-256
`e8a7bd03f416c66c707dd44f01fbcae9e61eeed02a14d5d9b18f84d7ccc70367`, and passed the final
`node_count=0` runtime boundary.

PR #75 also reduced accumulated automatic PR CI from 18 workflows to the three core automatic
checks and added a machine-enforced allowlist in the existing Terraform Static scope job. Completed
milestone and historical AWS workflows remain available through manual `workflow_dispatch` rather
than running on every PR.

M5 planning is now active. Current code inspection establishes the baseline candidates without
pre-classifying them as defects: jobs are persisted `PENDING` and scheduled only after commit through
the in-process executor; `markRunning` has no status guard/row lock/version transition; result object
write occurs before final DB result registration; and no stale `PENDING`/`RUNNING` reconciliation
component or startup hook is currently identified. Executor rejection is already caught and mapped
to `FAILED` with deterministic unit coverage, so M5 reuses that evidence before adding any pressure
measurement. The M5 plan forbids selecting RabbitMQ, Transactional Outbox, locks, retry
infrastructure, or another solution until these behaviors are reproduced and classified.

M5-1 is complete. PR #78 merged as
`c58b2902556d30f4e85ff84b249a8dccfd83e207`; Backend Local Verification run `36394053570`
passed. `AnalysisJobRestartBaselineTest` starts the real Spring Boot application, persists one
`PENDING` and one `RUNNING` job, closes the process context, and starts a fresh context against
the same database. Both states remain unchanged. Classification:
`CONFIRMED_RELIABILITY_GAP` — if the in-process task disappears, the current application has no
startup reconciliation path for those persisted non-terminal jobs.

M5-2 is captured by
`AnalysisJobDuplicateExecutionBaselineTest`. The deterministic duplicate-delivery scenario runs
one job to `SUCCEEDED`, then delivers the same job id again. The existing transition accepts
`SUCCEEDED → RUNNING`; provider/store orchestration and generated-Terraform registration are each
attempted twice. This proves the same-job idempotency/state-guard gap without adding a flaky
concurrency harness. Detailed M5 evidence is accumulated in
[`m5-backend-reliability-baseline.md`](evaluation/m5-backend-reliability-baseline.md).

M5-3 is complete. PR #80 merged as
`18503b656de090b6fc26b317908189b7090ecfc3`; final successful Backend Local Verification run
`36395373207` passed. The partial-success baseline proves that a result-object write can persist
before generated-file relational registration fails. The runner then marks the job `FAILED`, while
`resultFileId` and `resultObjectKey` remain null and the already-written object remains without
compensation. Classification: `CONFIRMED_RELIABILITY_GAP`.

M5 is therefore complete with three confirmed gaps: restart-stranded non-terminal jobs, duplicate
same-job re-execution, and untracked object residue after DB finalization failure. Executor rejection
remains `CONTROLLED_CURRENT_BEHAVIOR` because it is already observed and terminates the job as
`FAILED`.

Historical M6 implementation checkpoint before reassessment: M6 had been marked complete. That portfolio/final-design conclusion is withdrawn; the following implementation evidence remains valid. PR #82 merged as
`d36a36148353027118f0c5eb1c86543fffa00dad`; Backend Local Verification run `36399415255`
passed. The repository now claims `PENDING → RUNNING` atomically, and duplicate delivery of an
already-terminal job no longer re-enters provider/storage execution.

PR #83 merged as `3385114b365859aaa8d688621f33176d411d3ab6`; run `36399836413` passed.
Before readiness, the current single-replica runtime now reconciles previous-process `PENDING` and
`RUNNING` jobs to a safe terminal `FAILED` state rather than replaying uncertain side effects.
Existing terminal jobs remain unchanged.

PR #84 merged as `bb25229da188f690bc88f9abce5248911db3514f`; run `36400693033` passed.
M6-3 added provider-neutral result-object removal for S3/filesystem and explicit metadata-only
no-op compensation. Relational finalization failures that occur inside the rollback-safe transaction
body now remove the immediately preceding persistent object before the job is marked `FAILED`.
Commit-phase ambiguous errors are intentionally outside that compensation classification. Detailed
before/after evidence is in
[`m6-backend-reliability-closure.md`](evaluation/m6-backend-reliability-closure.md).

Historical state before reassessment: M7 had been marked active. That progression is now superseded and PAUSED. Existing observability assets are Actuator/Prometheus metrics, job-level Micrometer
counters/timers, the `analysisJobId` MDC scope, and source revision in logs. The console pattern
contains `trace_id`/`span_id`, but the backend currently has no Micrometer tracing bridge/exporter
dependency proving those fields are populated. Historical Bedrock/AOSS external metrics also do not
cover the active Vertex/OpenSearch path. M7 therefore starts by capturing the current signals for
one existing deterministic failure before adding stage telemetry, tracing, dashboards, or a
collector.

M0, M1, and M2 are complete. M3-1 defined the stage-provenance contract, M3-2 fixed the first
repository-owned evaluation dataset, and M3-3 added one reusable runner. M3-R2 now has a corrected
main-branch GitHub Terraform plan (run `36335535056`, commit
`fc95a7f54d375dce900bac573dcd79ea0215750f`) with exactly 12 creates and no
update/delete/replacement. The separate apply trust boundary is also proven: `terraformers-apply`
was bootstrapped with the reviewed role matrix, `gcp-target-apply` is protected and main-only, and
identity-check run `36363635464` completed successfully on
`334dc611dd44eccb286739d95c193dd3ffeedbb3`. The automated mutable preflight is also now proven:
run `36365247935` on `40750799aba5bf531ee939e0c50fe2b8042c1dd3` passed the plan-identity
non-mutation check, mutable quota/API/machine/duplicate-runtime checks, state-bucket protection,
empty canonical runtime state, and the corrected exact 12-create foundation contract; its apply job
was skipped. Cloud Billing API remains disabled, so billing status and remaining Free Trial credit
remain an operator-owned approval fact rather than a reason to broaden the plan identity.

The protected foundation run `36365702742` then applied the reviewed plan successfully:
`12 added, 0 changed, 0 destroyed`. Its final workflow status was failure only because the
post-apply check read deprecated GKE `Cluster.currentNodeCount`, which returned blank after the
cluster and node pool had already been created. The verification was corrected without re-applying
resources. Read-only runtime-check run `36369764529` on
`d47c85526f2b2a290b394084cbb19e0356883a0c` passed with the canonical 12 managed Terraform
resources, GKE cluster/node-pool runtime checks, Terraform `node_count=1`, and a zero-drift refresh
plan; its apply job was skipped.

The first OpenSearch readiness run `36370511427` then exposed the missing Kubernetes-object
permission before mutation. After the separately approved `roles/container.developer` addition,
readiness run `36371424659` on `31785eb0f9f1afc83584412c1e1ad8c011fc3b74` completed
successfully: the namespace/StorageClass/Service/StatefulSet were applied, the single replica became
Ready, the 15 GiB PVC was Bound to `terraformers-pd-standard`, the Service remained
`ClusterIP`, and the OpenSearch 3.8.0 API reached yellow/green health. M3-R2 is therefore
complete on the single target runtime. Historical AWS live infrastructure remains intentionally absent and is no longer a blocker.
M3-R1 through M3-R3 established the actual GCP/open-source-oriented target AI/RAG runtime once;
that same runtime is now the live M3-4 baseline target and remains reusable by M4, later
observability/failure work, and M9 closure.

## Completed

- [Repository working contract](../AGENTS.md)
- [Component inventory](architecture/component-inventory.md)
- [ADR-001: Project scope](architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Cloud-neutral boundaries](architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Evaluation before complexity](architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Evidence-based change gates](architecture/decisions/ADR-004-change-gates.md)
- [ADR-005: Target AI/RAG runtime](architecture/decisions/ADR-005-target-ai-rag-runtime.md)
- [Provider-neutral target architecture](architecture/target-architecture.md)
- [GCP deployment architecture and capability mapping](architecture/deployment-gcp.md)
- [Modernization master plan](plans/MASTER_PLAN.md)
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)
- [AI project state](AI_PROJECT_STATE.md)
- M0 final cross-document consistency validation — **PASS**
- M0 closure evidence SHA — `3ccf17582ae91ad131d3efe8ce1a38492c401c72`
- M1-1 Backend external identity neutralization — **COMPLETE**
  - neutral provider-plus-subject persistence and lookup with an additive compatibility migration;
  - existing Cognito user linkage and internal numeric `user_id` preserved; and
  - Backend Local Verification and MariaDB schema/repository validation — **PASS** at `edc9eb87e9bb1105c4f5d94f117356768190db92`.
- M1-2 Backend JWT / Resource Server boundary — **COMPLETE**
  - generic resource-server wiring separated from provider-specific JWT validation;
  - Cognito token validation and JWT claim interpretation isolated behind provider boundaries while preserving current token/user compatibility; and
  - Backend Local Verification, including MariaDB schema/repository validation — **PASS** at `9b32f0ded961da74e5a83f4af7ac80b91872cc81`.
- M1-3 Frontend auth/session boundary — **COMPLETE**
  - application/UI auth calls now use a provider-neutral frontend auth client;
  - Cognito/Amplify configuration, guest-error normalization, user attributes, token objects, and provider calls are isolated in the compatibility adapter while existing session/routing/API/auth-flow semantics are preserved; and
  - Frontend CI and Frontend Delivery Contract Verification — **PASS** at `1e72c4d9320f3e4af82aeede8fb4c63366e80de9`.
- M1-4 Object storage decoupling completion — **COMPLETE**
  - source reads and upload/result writes now flow through provider-neutral `ObjectReader`/`ObjectWriter` contracts;
  - AWS S3 SDK types are isolated to compatibility adapters, while explicit provider/persistence semantics eliminate eTag-based false-S3 classification; and
  - Backend Local Verification and MariaDB schema/repository validation — **PASS** at `ca7f60382143a25a010ec49131075011c76c7c1f`.
- M1-5 OpenSearch transport/auth boundary — **COMPLETE**
  - `OpenSearchReferenceRetriever` now depends on provider-neutral `OpenSearchTransport` and no longer knows SigV4 or signing service names;
  - AWS credentials, region, payload signing, and `aoss`/`es` signing semantics remain inside the `SignedOpenSearchHttpClient` compatibility adapter, while retrieval/query/parser behavior is preserved; and
  - Backend Local Verification, MariaDB schema/repository validation, and Terraform Static Verification — **PASS** at `7d25243f0ee727d251d21ff5affeb9841df5fc3e`.
- M1-6 Model / embedding provider configuration — **COMPLETE**
  - generic analysis and embedding provider selectors now choose implementations behind the existing `AnalysisProvider` and `EmbeddingProvider` ports;
  - Bedrock generation/embedding model identifiers and max-token settings are isolated in `BedrockRuntimeProperties`, with legacy `BEDROCK_PROVIDER_ENABLED` retained only as a transitional fallback; and
  - Backend Local Verification, MariaDB schema/repository validation, Terraform Static Verification, and AWS Deployment Contract Inventory Verification — **PASS** at `631fefe9ebae72becc66d33a9a672e9ffcb36bb2`.
- M1-7 Runtime configuration neutralization — **COMPLETE**
  - canonical production/runtime configuration now uses provider-neutral JWT, storage, analysis, embedding, retrieval, and progress-publisher selectors, while Cognito/S3/Bedrock/SQS/AWS OpenSearch signing values are isolated in the explicit `aws-compat` profile;
  - canonical Kubernetes/runtime-secret/deployment contracts were updated to neutral base keys, legacy Bedrock/S3/SQS enable switches were removed from the canonical selection contract, and historical AWS compatibility remains explicitly preserved; and
  - Backend Local Verification, MariaDB schema/repository validation, AWS Deployment Contract Inventory Verification, AWS Runtime Deployment Package Verification, and Terraform Static Verification — **PASS** at `b4e3c4cb0cf42e64904111447395f7a1435ca9aa`.
- M1-8 Contract/regression verification and closure — **COMPLETE**
  - dedicated closure verification passed provider-neutral boundary inspection plus full backend, MariaDB/Flyway/repository, frontend, and runtime-contract regressions;
  - closure inspection removed provider-specific failure types from the generic analysis lifecycle and preserved failure-message plus observability-category semantics through provider-neutral failure signals; and
  - M1 Cloud Decoupling Closure Verification, Backend Local Verification, Terraform Static Verification, and AWS Deployment Contract Inventory Verification — **PASS** at `d843fb9f08cd6255c1a4738ac220a40b5e760d75`.
- M2-1 Portable runtime baseline and parity gap inventory — **COMPLETE**
  - backend regression, MariaDB 11.4 + Flyway/schema/repository validation, frontend tests/build, and deterministic runtime-contract verification were **PASS** as distinct runtime identities;
  - Kind cluster creation and backend image build/load reached workload application, which then **FAIL**ed because namespace `terraformers-local` did not exist; downstream rollout/HTTP/auth/object-byte/active-provider paths remain **NOT COVERED** rather than inferred; and
  - M2 Runtime Parity Baseline evidence collection — **PASS** at `2623303347e15e580d83f583262c51d2573edbbd`, with the target Kind runtime failure preserved in the uploaded evidence.
- M2-2 Portable persistent runtime substrate — **COMPLETE**
  - added a separate self-contained `terraformers-portable` Kind fixture with MariaDB 11.4, canonical `prod` backend configuration, stub/disabled cloud adapters, fixture-only credentials, and no live cloud credentials;
  - namespace/apply, MariaDB readiness, backend rollout/readiness, health, Flyway schema history, and the existing repository smoke all passed against the same in-cluster MariaDB instance; and
  - M2 Portable Persistent Runtime Verification — **PASS** at `02a00efec167a92c8170e8787b68e28ce6dc2339`. The fixture deliberately does not claim DB restart durability, authentication/ownership, object-byte persistence, active provider behavior, or production topology.
- M2-3 Authenticated identity and ownership parity — **COMPLETE**
  - added a separate `portable-authenticated` Kind fixture with ephemeral RSA signing material and an in-cluster JWKS server, exercising the real JWT decoder/provider validator and existing external-identity mapping without a live IdP or cloud credential;
  - provider-plus-subject persistence, numeric internal user creation/reuse, distinct second identity, anonymous and invalid-token 401 rejection, owner/private access, non-owner 403 rejection, ownership modification checks, and display-name preservation all passed against the same portable MariaDB runtime; and
  - M2 Authenticated Identity Parity Verification — **PASS** at `eda4b9469f258b71aae8e28a0b3ec1a226c4412a`. Historical fixture/harness failures were fixed and rerun before closure.
- M2-4 Upload → analysis → Terraform result parity — **COMPLETE**
  - PR #24 first reproduced the current metadata-only limitation under an authenticated runtime: upload, analysis, Terraform validation, and inline result read-back passed, while source/result object bytes were not persisted and source reads returned 409;
  - added a JDK-only `filesystem` implementation behind the existing neutral `ObjectReader`/`ObjectWriter` ports and enabled it only in the `portable-object-store` test overlay, without selecting a production storage product; and
  - M2 Object Byte Storage Verification — **PASS** at `99fa2864ae3ef86536332172c4a3e50be612e686`: the same 68-byte source input round-tripped exactly, source/result rows recorded `binary_persisted=1`, source endpoints returned 200, the analysis job reached `SUCCEEDED`, and generated Terraform filesystem bytes matched the database/API checksum. The fixture is single-runtime evidence only and makes no restart/shared-storage durability claim.
- M2-5 User/project/comment and frontend experience parity — **COMPLETE**
  - the authoritative portable runtime matrix passed project list/get, private/public authorization, Terraform read/update/read-back, project tree, canonical and frontend-compatible comments, authenticated attribution, and deletion;
  - frontend Jest regression, production build, and built entrypoint passed, while the baseline isolated three provider-specific user-visible strings and classified browser E2E as not required for M2; and
  - the frontend-only follow-up at `7d8cdf357cb16a9073c86c6eb5140e617219180d` removed the Cognito/Bedrock/`s3://` presentation leakage without changing APIs or provider selection. M2 User Experience Baseline Verification run #5 then passed with all three provider-neutral classifications `PASS`, `known_provider_specific_visible_copy=0`, and `first_confirmed_gap=none`.
- M2-6 Runtime parity closure — **COMPLETE**
  - reviewed the accepted M2-1 through M2-5 evidence against all 14 M2 exit criteria without adding a closure-specific verifier or workflow;
  - confirmed no subsequent production/runtime change invalidated the latest successful M2-5 claims; and
  - [M2 Runtime Parity Closure](verification/m2-runtime-parity-closure.md) records **PASS** with explicit residual limitations.
- M3-1 Evaluation contract and stage-provenance schema — **COMPLETE**
  - added one provider-neutral case/result contract for extraction, retrieval, generation, and validation provenance;
  - retained retrieved document score/source/authority/risk metadata and explicit first-observable-divergence classification; and
  - [M3 Evaluation Contract v1](evaluation/m3-evaluation-contract-v1.md) plus `EvaluationContractTest` cover success and four representative failure locations without changing production AI behavior.
- M3-2 Fixed/versioned evaluation dataset — **COMPLETE**
  - added `terraformers-eval-v1` with four positive architecture cases, one ambiguous case, and one non-architecture case;
  - fixed input identity with repository-owned WebP fixtures and SHA-256, plus stage-level extraction/retrieval/generation/validation expectations;
  - required five existing `PROJECT_DECISION` documents across positive cases so retrieval quality can be distinguished from generic vector hits; and
  - `EvaluationDatasetLoaderTest` validates deterministic loading, fixture identity, case composition, expectation completeness, and corpus-reference existence.

- M3-3 Reusable evaluation runner and provenance capture — **COMPLETE**
  - added one runner/result/writer path that emits M3-1 stage provenance for the fixed dataset;
  - extracted Bedrock generation into a shared `AnalysisGenerationStage` so production and evaluation use the same model-call/retry path; and
  - full backend regression and deterministic runner cases passed, including retrieval provenance, input classification, validation, and first-divergence localization.
- M3-R1 Target runtime evidence and capability decision — **COMPLETE**
  - selected one reusable GKE Standard + Vertex AI + OpenSearch OSS target runtime rather than an evaluation-only cloud stack;
  - retained the existing 1024-dimensional retrieval contract with `gemini-embedding-001` and required a new immutable `terraformers-reference-v3` embedding identity; and
  - recorded a mandatory fresh billing/quota/model-access check before M3-R2 creates resources.


## Verified architectural direction

This project is **not** a greenfield rewrite, a simple AWS-to-GCP migration, or a technology-count expansion exercise. It **is** modernization of the existing Terraformers system through reuse of existing domain/business flows, a provider-neutral logical architecture, a current GCP deployment target, reproduced-failure-based backend reliability work, fixed-evaluation-based AI/RAG work, actual-RCA-based observability, and cloud portability. The Spring Boot application remains the logical center.

Reuse the domain/project/user/file/comment flow, `AnalysisJob` lifecycle baseline, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, Terraform draft validation, versioned RAG corpus/contract, Kubernetes-compatible workload contract, and deterministic test/validation assets.

## Current gaps

Current M3 evidence leaves these concrete gaps:

- M4-2 proves at least one baseline fact-extraction failure is output truncation:
  `arch-vpc-three-tier` reproduced as `FACT_EXTRACTION / OUTPUT_TRUNCATED` with
  `reason=RESPONSE_TRUNCATED`;
- `arch-private-aoss` did not reproduce its original failure in the bounded M4-2 run and instead
  passed through validation, so no generic transient-provider fix is justified;
- provider usage telemetry is incomplete: output-token counts exist for successful architecture
  generation, but input tokens and cost are unavailable;
- the earlier one-case serving smoke exposed a validator false-positive candidate around the phrase
  `Placeholder EC2 instance`, but that behavior was not reproduced in the canonical six-case
  baseline and is secondary evidence rather than the primary M4 target;
- M4-3 still needs the smallest truncation-targeted behavior change and same-condition validation.

The target GKE/OpenSearch/Vertex runtime, v3 corpus ingestion, Java serving path, six-case baseline,
retrieval provenance, generation evidence, and Terraform validation evidence are all available.
LangChain/LangGraph remain evidence-gated.

## Historical AWS implementation

Cognito, Amplify Cognito integration, Bedrock, S3, the SQS adapter, AOSS/SigV4, CloudWatch, AWS Terraform runtime stacks, EKS-specific integrations, and AWS delivery/deploy/teardown workflows are **HISTORICAL** compatibility/baseline/reference assets, not the active target. Do not assume they must be deleted.

Reusable patterns include provider abstraction, immutable image/SHA identity, least privilege, state separation, approval gates, plan/apply separation, deterministic validation, and deployment/rollback/teardown evidence.

## Deferred / gated decisions

**DEFER — evidence required:** RabbitMQ, Transactional Outbox, LangGraph, a persistent Python AI worker/service, Keycloak, Redis, Kafka, and multi-agent architecture.

**GCP target selected/implemented for M3-R2:** one zonal GKE Standard cluster with an approved idle
`node_count=0` and bounded `1 × e2-standard-2` live sessions; internal single-node OpenSearch OSS
with a 15 GiB `pd-standard` PVC; Vertex AI `gemini-3.8-flash` and
`gemini-embedding-001` at 1024 dimensions; Workload Identity Federation for GKE. The canonical
runtime has been applied and reused for M3/M4 evidence; protected `activate` and `idle` operations
control the node-pool lifecycle.

**Still GATED:** database hosting, object storage implementation, external identity provider, frontend/ingress, broader secrets and observability topology, image registry, GitHub Actions-to-GCP identity and delivery method, and Terraform remote backend. The GKE workload identity decision does not select GitHub Actions federation.

Do not invent TPS or latency targets, AI quality-improvement percentages, or additional node/resource counts. Remaining gated decisions are not selected by this state document.

## Capacity and quota status

The [2026-09-24 live readiness evidence](evaluation/m3-r2-live-readiness-evidence.md) records project `terraformers-platform`, enabled billing and remaining Free Trial credit confirmed by the operator, global CPU limit/usage `12/0`, Seoul `E2_CPUS=8/0`, `INSTANCES=8/0`, `DISKS_TOTAL_GB=2048/0`, GKE server availability, and successful minimal requests to both selected Vertex models. It also records an empty GKE cluster list and no matching target VM. These are dated observations, not a live account connection or permission to create resources. Refresh billing, quota and duplicate-runtime checks before apply. Other deployment capacity/choices remain gated as listed above.

## Remaining M0 work

No remaining M0 work.

## Remaining M1 work

No remaining M1 work.

## Immediate next work

Case B and Case A are now **portfolio-closed**. Case A final evidence is recorded in
[Case A Final Closure](evaluation/case-a-final-closure.md); do not add more canonical or holdout
runs without a newly reproduced defect or changed requirement.

The next representative portfolio case is **Case C — Cloud Runtime Capacity & Safe Delivery**.
The [Case C Measurement Readiness Audit](evaluation/case-c-measurement-readiness-audit.md) found the
live baseline **NOT READY**. The
[Case C Production-Representative Runtime Decision Gate](plans/active/case-c-representative-benchmark-runtime-decision.md)
now records the production-representative backend runtime as the **preferred candidate**, not a
final product selection.

Before the runtime direction can be approved, three compatibility gates must be closed:

1. MySQL 8.4 must run the full Flyway/JPA/Case B durable-processing contract;
2. Cloud Storage must preserve the existing ObjectReader/ObjectWriter/ObjectRemover contract and
   integrity semantics without domain/schema rewrites;
3. Artifact Registry must fit the existing GitHub OIDC + GKE identity model with a dedicated,
   least-privilege publisher/read path rather than reusing broad Terraform apply authority.

Do not create the representative-runtime implementation Work Package, publish an image, create
Cloud SQL/Storage/Artifact Registry resources, mutate IAM, apply Kubernetes resources, activate the
node pool, generate load, or run rollout/rollback experiments until those compatibility results are
reviewed and the final runtime selection is approved.

Historical next-work text before A3 readiness was: obtain approval for the bounded **A3
fixed-facts retrieval alternative comparison / decision**. It must isolate upstream fact/query
variability from retrieval/ranking behavior without selecting or changing production retrieval
semantics in advance. Do not start it automatically, do not begin an AI production change, and do
not start Case C.

## Do not revisit

Without new evidence, an ADR where needed, and the change gate, do not:

- turn the project into a greenfield rewrite or reduce it to a simple AWS-to-GCP migration;
- split the Spring Boot-centered structure into microservices;
- replace MariaDB with PostgreSQL or another database;
- redesign existing domain/business flows;
- discard `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, or `ObjectReader`/`ObjectWriter`;
- introduce LangGraph/agent architecture before AI evaluation;
- introduce RabbitMQ/outbox before reliability evidence;
- assume Keycloak is the default IdP;
- select a GCP product/topology without quota evidence;
- replace Terraform with OpenTofu; or
- restore the historical AWS stack as the active deployment target.

“Do not revisit” is not a permanent ban; it prevents reopening accepted direction arbitrarily without new evidence, the required ADR, and the change gate.

## Working rules

Before any future task: (1) verify current GitHub `main` SHA, (2) read `AGENTS.md`, (3) read this
document, (4) read `MASTER_PLAN.md`, and (5) read the
[active M4 plan](plans/active/M4-ai-targeted-improvement.md). Follow the M4 evidence chain:
M4-1 diagnostics → existing-runtime resume prerequisite → M4-2 bounded live reproduction → M4-3
truncation-targeted change → M4-4 same-dataset comparison. Do not create a second cloud runtime and
do not add generic retry/backoff without new retryable-provider evidence. Retain the
[completed M2 plan](plans/active/M2-runtime-parity.md) and [completed M1 plan](plans/active/M1-cloud-decoupling.md)
as historical milestone evidence.

For a substantive change, record an observed problem, reproducible evidence, a change that directly addresses it, and same-condition revalidation. Establish a fixed evaluation baseline before AI changes, reproduce a failure before reliability changes, and require diagnosis evidence—not dashboard count—for observability completion.

## Evidence and references

Interpret this checkpoint through the [repository working contract](../AGENTS.md) and these primary sources:

- [Component inventory](architecture/component-inventory.md)
- [Target architecture](architecture/target-architecture.md)
- [GCP deployment architecture](architecture/deployment-gcp.md)
- [ADR-001](architecture/decisions/ADR-001-project-scope.md)
- [ADR-002](architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003](architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004](architecture/decisions/ADR-004-change-gates.md)
- [Modernization master plan](plans/MASTER_PLAN.md)
- [Active M2 plan](plans/active/M2-runtime-parity.md)
- [Completed M1 plan](plans/active/M1-cloud-decoupling.md)
- [M1 closure verification](verification/m1-cloud-decoupling-closure.md)
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)

## Case A A1 measurement-readiness checkpoint

Case A's primary problem remains **retrieval grounding / required-evidence coverage**. A1 measurement readiness is **COMPLETE**; see [Case A Retrieval-Grounding Measurement Readiness](evaluation/case-a-retrieval-grounding-measurement-readiness.md). At A1 closure, production retrieval change was **NOT AUTHORIZED / NOT PERFORMED**, no live GCP evaluation had been executed, and A2 was the next candidate. Those were historical A1 boundaries; A2 and A3 have since completed and the current A4/A5 state is recorded above.


## Case A A2 repeated baseline checkpoint

Case A's A2 repeated current baseline is **COMPLETE** on source commit
`96c6442026f6c57a30a9b248a8af115df1e7f2e4`; see [Case A A2 Repeated Current Baseline
Evidence](evaluation/case-a-retrieval-grounding-a2-baseline.md). The VPC grounding gap reproduced
`3 / 3`, production retrieval behavior at A2 was **UNCHANGED**, and the historical AOSS `130489 ms` latency
outlier was **NOT reproduced in N=3**. Non-empty VPC resource filters in every run invalidate absent
filters as an established A2 root cause. At that checkpoint vector ranking was not proven causal and
A3 was the next candidate. A3 has since completed, A4 was merged at
`34c9dbecfbecdf4bca2be47b64d771c9e68549ca`, and later A5 canonical evaluations progressed
through the PR #118 ordering correction and PR #119 zero-hit REQUIRED-grounding/classification
correction. The final post-PR119 canonical N=3 and frozen holdout are complete; Case A is **PORTFOLIO-CLOSED**.

## Case C capacity-baseline harness checkpoint

The bounded `case-c-capacity-baseline-v1` Work Package is **IMPLEMENTED / STATIC VALIDATION
PASS** on the authoritative execution base `b96120a8f065d54f05183d9f9642af5d0e860375`.
The implementation adds a fail-closed, closed-loop AnalysisJob capacity harness and one protected,
manual-only `capacity-baseline` operation to the existing runtime-dependency workflow. The frozen
steps are `1 → 2 → 4 → 6 → 8`; no runtime tuning or new monitoring platform is included. See
[Case C Capacity Baseline Harness Readiness](evaluation/case-c-capacity-baseline.md).

The corrective readiness implementation adds cause-chain-safe provider HTTP 429 classification to the existing failure metrics, per-job attribution only from the matching `analysisJobId` correlated failure log, correctness fail-stop, and per-success DB/result/GCS integrity checks. Executor `2 / 4 / 50` values are source-bound compile-time invariants, not dynamically read configuration.

The live capacity baseline is **NOT YET EXECUTED**. No bottleneck or performance threshold is
claimed. The immediate next single checkpoint is explicit user approval for one protected live run;
it remains `AWAITING_APPROVAL` and must not be started automatically.
