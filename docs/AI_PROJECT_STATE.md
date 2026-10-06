# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- M0 closure evidence SHA: `3ccf17582ae91ad131d3efe8ce1a38492c401c72`

M0 closure was validated against this main SHA. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current execution mode

- Mode: **Product Trust Reassessment**
- Status: **PRODUCT TRUST PROGRAM APPROVED — PT-1 CONTRACT READY / AWAITING POST-MERGE ACTIVATION**
- Active plan:
  [Product Trust Modernization](plans/active/product-trust-modernization.md)
- Program contract:
  [.agents/programs/product-trust-v1.yml](../.agents/programs/product-trust-v1.yml)
- Durable program state:
  [.agents/state/product-trust-v1.json](../.agents/state/product-trust-v1.json)
- Current single task: **The user explicitly approved `product-trust-v1` on 2026-10-06. PT-1 realistic-input benchmark design is the first eligible phase. Its Work Package must merge before a Codex executor binds the exact post-merge main SHA and starts repository-only PT-1 work. PT-1 may prepare a candidate but must stop at the `REALISTIC_DATASET_TRUTH_FREEZE` human gate before any live PT-2 run. A7-8 and teardown remain deferred; the recreated GCP runtime is intentionally retained.**


## Product Trust reassessment checkpoint

The latest product-level review found that the previous portfolio closures do not by themselves
prove that a real user can trust the service. The most important new limitation is external validity:
all canonical and holdout positive evaluation fixtures are repository-owned synthetic diagrams. They
remain useful regression controls but cannot alone support a realistic-image generalization claim.

Additional confirmed product gaps:

- runtime evidence quality is conditional on extracted facts;
- the backend can persist UNKNOWN/DEGRADED quality while the current UI primarily presents terminal
  AnalysisJob completion;
- real Vertex/Gemini latency has stochastic multi-minute tails while user progress is coarse;
- representative GCP authentication uses a deterministic JWKS fixture rather than a permanent
  production IdP, although project ownership/authorization mechanics are retained evidence;
- protected-main and image-build CI/CD boundaries remain repository-side audit candidates.

The revised Product Trust plan explicitly excludes Kubernetes/GKE platform reselection, MSA
decomposition, capacity/HPA/HA work, rollback/canary redesign without new evidence, and technology
adoption for breadth.

Historical Case A/B/C evidence is not discarded wholesale. Product Trust classifies it as direct
mechanism evidence or controlled-regression evidence and permits realistic product evidence to
narrow or supersede broader historical claims.

### Case A evidence-backed quality extension control

- Original Case A retrieval-grounding/generalization closure: **RETAINED / NOT INVALIDATED**
- Extension status: **DIRECTION FROZEN — A7-0..A7-6 COMPLETE — A7-7 LIVE EVIDENCE RETAINED / MODEL CORRECTION APPROVED**
- Architecture freeze:
  [ADR-008 Evidence-backed AI Quality](architecture/decisions/ADR-008-evidence-backed-ai-quality.md)
- Active plan:
  [Case A Evidence-backed AI Quality and Semantic Reliability](plans/active/case-a-semantic-reliability-extension.md)
- Extension/A7-0 Work Package:
  [.agents/work-packages/case-a-semantic-reliability-v1.yml](../.agents/work-packages/case-a-semantic-reliability-v1.yml)
- A7-1 execution Work Package:
  [.agents/work-packages/case-a-a7-1-evidence-quality-contract-v1.yml](../.agents/work-packages/case-a-a7-1-evidence-quality-contract-v1.yml)
- A7-2 execution Work Package:
  [.agents/work-packages/case-a-a7-2-false-green-calibration-v1.yml](../.agents/work-packages/case-a-a7-2-false-green-calibration-v1.yml)
- A7-5 execution Work Package:
  [.agents/work-packages/case-a-a7-5-safe-executable-diagnostics-v1.yml](../.agents/work-packages/case-a-a7-5-safe-executable-diagnostics-v1.yml)
- New representative claim: separate technical success, authoritative-knowledge coverage,
  evidence-backed runtime quality, labeled evaluation quality, provider partial failures, and safe
  executable diagnostics.
- Knowledge authority is frozen as:
  provider schema direct authority + pinned official provider docs/examples RAG + curated
  Terraformers project decisions.
- A7-0 exact coverage is closed: AWS Provider 5.100.0 exposes 1,526 managed-resource schemas;
  1,514 intersect pinned official resource documentation and 12 have no official resource document.
  Historical v3 covers only 30 resources (1.9659% of schema). The measured broad v4 candidate covers
  all 1,514 documented schema resources with 5,395 total documents / 5,387 provider chunks,
  14,833,335 JSONL bytes, approximately 2,654,504 embedding-input tokens, and zero official-evidence
  extraction gaps. Structural authority remains all 1,526 schema resources; RAG scope is the full
  1,514-resource schema/document intersection; the remaining 12 are explicit official-knowledge
  gaps. v3 remains immutable.
- A7-1 implementation defines `evidence-quality-v1` as a deterministic JSON-serializable
  assessment with separate technical, knowledge, quality, and project-decision status. It
  distinguishes official-knowledge absence from retrieval miss, checks generated resource types
  against provider schema and selected official evidence, keeps project decisions as a separate
  `TERRAFORMERS_PATTERN` dimension, and records the runtime quality boundary as
  `CONDITIONAL_ON_EXTRACTED_FACTS`. AnalysisJob DB/API persistence remains deferred to A7-4.
- A7-1 closure evidence: `docs/evaluation/case-a-a7-1-evidence-quality-contract.md`.
  PR #202 merged as `f9009ce8f9a744afee848b8e196b57de5860c78f`; Backend Local Verification
  run `37188607802` passed `mvn clean test`, package, MariaDB Flyway/Hibernate validation, and
  canonical repository smoke queries. Terraform Static Verification run `37188607799` completed
  successfully at scope level; its terraform/RAG job was skipped because PR #202 changed no
  terraform/RAG paths.
- A7-2 closure evidence: `docs/evaluation/case-a-a7-2-false-green-calibration.md`.
  PR #205 merged as `9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`; Backend Local Verification
  run `37191939296` passed `mvn clean test`, package, MariaDB Flyway/Hibernate validation, and
  canonical repository smoke queries. Terraform Static Verification run `37191939292` completed
  successfully at scope level; its terraform/RAG job was skipped because PR #205 changed no
  terraform/RAG paths. Frozen canonical/holdout datasets were unchanged and no live AI/cloud
  execution was used. A7-3 through A7-6 are complete; A7-7 is approved because final closure now requires a real broad-v4 + persisted-quality end-to-end proof.
- A7-7 live embedding evidence supersedes only the v4 model/dimension implementation detail:
  `gemini-embedding-001` has an effective project quota of 5 requests/minute and cannot embed
  5,395 documents inside the reviewed workflow bound; `gemini-embedding-2` has a measured
  300,000 requests/minute effective project quota. The approved v4 identity is now
  `gemini-embedding-2 / 1536`; v3 remains `gemini-embedding-001 / 1024`. The provider/corpus
  authority model and ADR-008 quality architecture are unchanged.
- No evaluator LLM or multi-model voting is part of the default design.
- Historical VPC evidence is the canonical false-green before-state: retrieval, generation, and
  validation PASS while required grounding was incomplete.
- Case C run `36949479621` remains a repository-owned sensitive-credential policy false failure,
  not proven Vertex censorship; PR #190 removed that over-constrained draft policy.
- C2 run `37016993776` remains the executable-diagnostic gap: attempt 1 PASS, attempt 2
  `terraform_validate_configuration`, exact invalid construct unavailable.
- Frozen implementation sequence:
  A7-0 knowledge coverage → A7-1 evidence-backed quality contract → A7-2 false-green calibration →
  A7-3 provider partial failures → A7-4 durable runtime quality observability → A7-5 safe Terraform
  diagnostics → A7-6 observability product decision → A7-7 representative live proof if required →
  A7-8 integrated closure.
- Direction changes require evidence that a frozen ADR-008 assumption is materially false or
  insufficient, a written ADR amendment, and explicit user approval. New frameworks/articles alone
  do not authorize scope changes.
- PR #196 final-documentation reconciliation remains held until A7-8.
- GCP live recreation, model calls, deployment, and cost-bearing actions require a separate user
  checkpoint after implementation/CI readiness.

<!-- CASE_C_ARCHITECTURE_CLOSURE:START -->
### Case C portfolio closure control

- Overall: **PORTFOLIO_CLOSED_WITH_RESIDUALS**
- Capacity baseline: **DEFERRED_NOT_REQUIRED_FOR_PORTFOLIO_CLOSURE**
- Current phase: **C8 — Case C evidence closure (COMPLETE)**
- Open blockers: none
- Durable issue register: .agents/state/case-c-architecture-closure.json
- Generated plan: [Case C Architecture Closure](plans/active/case-c-architecture-closure.md)
- Portfolio closure: [Case C Final Portfolio Closure](evaluation/case-c-portfolio-closure.md)
- Failed capacity run 36957682821 and C2 run 37016993776 are retained as evidence, not rerun obligations.
- Capacity and deferred hardening phases reopen only for a new operational requirement or explicit user-approved portfolio revision.
<!-- CASE_C_ARCHITECTURE_CLOSURE:END -->
- M0/M1: **AUDITED / RETAINED**
- M2: **RETAINED FOUNDATION**
- M3: **RETAINED CASE A BASELINE**
- M4: **HISTORICAL CASE A EVIDENCE / NO AUTOMATIC REOPEN**
- M5: **HISTORICAL CASE B BEFORE-STATE / RETAINED**
- M6: **SUPERSEDED BY CLOSED CASE B DURABILITY DESIGN**
- M7/M8/M9: **DEFERRED / NOT PORTFOLIO PREREQUISITES**

Historical `COMPLETE` or `ACTIVE` labels later in this file are evidence history only and do not
authorize automatic progression while this reassessment is active.

The project success criterion is now explicit: complete three technically defensible engineering
cases with repository-backed technical decisions, not merely a sequence of completed milestones.

Case A — AI/RAG quality, performance and reliability:
- **PORTFOLIO-CLOSED / PASS** in
  [Case A Final Closure](evaluation/case-a-final-closure.md);
- Case C executable-validity evidence does not reopen Case A: Case A claims retrieval grounding,
  generalization, and negative-control behavior and explicitly does not claim deployment-correct
  Terraform;
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

Case C — GCP production-representative runtime and immutable delivery:
- **PORTFOLIO-CLOSED / RESIDUAL RISKS ACCEPTED** in
  [Case C Final Portfolio Closure](evaluation/case-c-portfolio-closure.md);
- selected runtime boundary:
  GKE backend/OpenSearch + Vertex AI + dedicated MariaDB 11.4 Compute Engine VM/Persistent Disk +
  GCS + Secret Manager/Secret Sync + Artifact Registry;
- Cloud SQL/MySQL was not treated as a drop-in replacement after MySQL 8.4 rejected the retained
  Flyway migration contract; MariaDB-in-GKE was rejected for the first representative runtime
  because it would couple DB pressure to the measured GKE worker;
- runtime dependency run `36851221194` and Kubernetes prerequisite run `36854218346` established
  the private GCS/DB/secret/runtime boundary;
- integrated live-validation run `36889896239` **PASS** proves authenticated upload, durable
  AnalysisJob acceptance, real Vertex generation/embedding, OpenSearch retrieval, GCS source/result
  persistence, terminal success and Terraform read-back;
- backend-replacement run `36891629279` **PASS** proves preserved immutable image/source identity,
  durable AnalysisJob identity, source/result/Terraform bytes, and Actuator/Prometheus reachability
  across backend pod replacement;
- GitHub delivery uses OIDC/WIF with separated infrastructure-apply and image-publisher
  responsibilities; no user-managed long-lived GCP key is part of the selected delivery path;
- image publication run `37015159218` built exact source
  `b420291fa1534184d2a260883df718cc96511505` and digest
  `sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`;
- exact rollout run `37015932695` verified the prior image, deployed the target digest, reached
  Ready/Available 1/1, preserved embedded source identity, and reported health UP;
- observability is supporting infrastructure, not a fourth Case: Actuator/Prometheus plus bounded
  job/stage/failure/queue/retry metrics and `analysisJobId`/source-revision log correlation are
  retained; a full Grafana/OpenTelemetry/tracing platform is not claimed;
- capacity run `36957682821` and C2 run `37016993776` are retained only as stopping evidence:
  correctness variance appeared before trustworthy saturation attribution, so capacity tuning was
  not continued or misreported as solved;
- capacity saturation, HPA/replica/node/OpenSearch tuning, zero-downtime rollout, faulty-release
  rollback, MariaDB HA, integrated-path readiness redesign and full tracing/dashboard hardening are
  **DEFERRED**;
- there is no automatic next Case C implementation task.

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

A7-0 — Authoritative Knowledge Coverage — is **COMPLETE**. Closure evidence is recorded in
[Case A A7-0 — Authoritative Knowledge Coverage](evaluation/case-a-a7-0-authoritative-knowledge-coverage.md).

A7-1 — Evidence-backed Quality Contract — is **COMPLETE**. Closure evidence is recorded in
[Case A A7-1 — Evidence-backed Quality Contract](evaluation/case-a-a7-1-evidence-quality-contract.md).
PR #202 merged as `f9009ce8f9a744afee848b8e196b57de5860c78f`.

The original Case A retrieval-grounding/generalization closure remains retained; this extension does
not invalidate it. Case B and Case C portfolio closures also remain retained.

A7-2 — False-green Measurement and Offline Calibration — is **COMPLETE**. Closure evidence is
recorded in [Case A A7-2 — False-green Measurement and Offline Calibration](evaluation/case-a-a7-2-false-green-calibration.md).
PR #205 merged as `9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`.

A7-3 — Provider Partial Failures — is **COMPLETE** at merge SHA `fdb0e8a3f7e6656c46dd24e173894460ec09fd7f`. Its accepted evidence is `docs/evaluation/case-a-a7-3-provider-partial-failures.md`; corrective PR #209 merged as `94869668ef177370701adcaae7e5c5473b3076ac`. Backend Local Verification `37198612965` and Terraform Static Verification `37198612964` both passed.

A7-4 — Durable Runtime Quality Observability — is **COMPLETE** at implementation merge SHA `ab1b1f410b6aabf674af51d6161a2efd0f6df6a4`. Accepted evidence is `docs/evaluation/case-a-a7-4-durable-runtime-quality-observability.md`. The first Backend Local Verification run `37209621451` exposed one transaction-boundary regression while MariaDB validation already passed; bounded repair commit `0e457f7bd9339fc44f901bdd90b2c95f40c081b0` corrected it. Final Backend Local Verification `37210008792` and Terraform Static Verification `37210008786` both passed. No live provider/GCP action or v4 ingestion was performed.

A7-5 — Safe Executable Diagnostics — is **COMPLETE** at implementation merge SHA
`c5840c05c60ec78dc3a8c698453424b587014814`. Feature implementation PR #216 merged as
`0ce33a2302cfda87bcbf505d67b71257958a2b80`. Backend Local Verification `37215839871`
passed Maven clean tests/package plus MariaDB schema/repository validation. Terraform Static
Verification `37215839970` passed scope/policy checks; its Terraform job was skipped because no
Terraform paths changed. The accepted implementation preserves `terraform_validate_configuration`
and A7-4 `TERRAFORM_EXECUTABLE_FAILURE`, adds only bounded fixture-backed diagnostic enum/count
evidence, and retains no raw failed HCL or arbitrary Terraform diagnostic text. Historical C2 run
`37016993776` remains subtype-unknown. No live C2/provider/GCP/OpenSearch action was performed.

A7-6 — Observability Backend Decision — is **COMPLETE** at merge SHA
`a58d78181be642f3b3c2fdd38486c7d19fcc045e`; Terraform Static Verification `37217252459`
passed. Repository-native observability remains selected; OpenTelemetry and Langfuse are deferred
behind explicit evidence triggers.

A7-7 — Broad v4 End-to-End Live Proof — is **REPOSITORY IMPLEMENTED / AWAITING REVIEW** under
`.agents/work-packages/case-a-a7-7-broad-v4-live-proof-v1.yml` on frozen execution base
`13e398599ce69e087e9098b1413cad37c41edfd1`. Repository preparation now generalizes the protected
GCP ingestion path to exact v3/v4 manifest contracts, deterministically builds broad v4 from the
pinned provider source plus the schema copied from the exact backend runtime, switches only the GCP
target runtime ConfigMap to `terraformers-reference-v4`, and adds one bounded existing-workflow
operation for positive/negative authenticated persisted-quality proof.

No live GCP, Vertex embedding/generation, OpenSearch ingestion, backend image publication, deployment,
or teardown action has been performed in A7-7 yet. The independent Cloud Shell inventory now proves
that the WIF pool is retained in DELETED soft-delete state rather than permanently absent. The
corrected live sequence is: bootstrap-restore checkpoint → undelete/verify the existing WIF and
recreate only the exact reviewed state bucket/service accounts → existing read-only OIDC/quota/
Terraform preflight → runtime-live checkpoint → bounded A7-7 runtime/proof/cleanup. Do not create a
replacement WIF provider, adopt OpenTelemetry/Langfuse/another observability product, or perform
actions outside the bounded A7-7 contract.

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
document, (4) if `product-trust-v1` is approved/active read
`.agents/programs/product-trust-v1.yml` and `.agents/state/product-trust-v1.json`, and (5) read only
the plan/evidence referenced by the current program phase or active Work Package. Historical M0-M9
and A1-A7 sections below remain evidence history; they do not authorize automatic progression.

Until `product-trust-v1` receives explicit user approval, no PT phase is authorized. A7-8 and
teardown remain deferred. Do not create a second cloud runtime. The existing representative GCP
runtime may be retained only for the bounded Product Trust measurements/proofs declared by the
program, then must return through the reviewed teardown boundary.

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
