# Terraformers Modernization Master Plan

## Purpose

이 문서는 Terraformers modernization의 장기 실행 순서와 milestone별 완료 evidence를 고정한다. 프로젝트는 단순한 AWS → GCP migration이 아니라 기존 domain/business flow와 portable contract를 재사용하면서 backend reliability, AI/RAG evaluation과 targeted improvement, 실제 장애 진단 중심 observability, cloud portability를 개선한다. Logical/application architecture는 cloud-provider-neutral로 유지하고 현재 deployment target만 GCP로 둔다.

각 milestone은 기술 목록이 아니라 **problem → work → evidence → exit condition**으로 관리한다. 날짜나 예상 기간은 이 계획에서 정하지 않는다.

## Planning principles

- Milestone은 기본적으로 M0부터 M10까지 순서대로 진행한다.
- Completed milestone을 임의로 재설계하거나 다시 열지 않는다.
- Architecture change가 필요하면 구현 전에 [ADR-004 change gate](../architecture/decisions/ADR-004-change-gates.md)를 적용하고 필요한 ADR을 남긴다.
- Active milestone에는 별도 active plan이 있어야 하며, 그 plan의 첫 미완료 작업부터 진행한다.
- 한 작업은 하나의 logical하고 verifiable한 결과로 제한한다.
- Exit evidence 없이 milestone 상태를 `COMPLETE`로 바꾸지 않는다.
- Evidence로 인해 sequencing 변경이 필요하면 `MASTER_PLAN.md`와 해당 active plan을 같은 작업에서 갱신한다.
- Live cloud environment는 기본적으로 하나의 target runtime만 구축하고 milestone 간 재사용한다. Evaluation-only cloud stack과 later "real" stack을 별도로 구현하지 않는다.
- Target runtime이 선행되어야 baseline을 실행할 수 있음이 확인되면 dependency task를 앞으로 당긴다. 이는 completed milestone을 다시 여는 것이 아니다.
- `DEFER` technology는 milestone 이름이나 진입만으로 승인되지 않는다.
- `KEEP`으로 판정된 domain/business contract와 deterministic validation 자산을 우선 재사용하고 historical AWS 자산은 active runtime이 아닌 compatibility/baseline/reference로 다룬다.
- 성능, AI 품질, reliability, observability 개선은 각각 비교 가능한 측정, fixed evaluation baseline, 동일 failure scenario 재검증, 실제 진단 evidence 없이 완료로 주장하지 않는다.

## Milestone overview

| Milestone | Status | Purpose | Exit evidence |
| --- | --- | --- | --- |
| M0 — Baseline & Governance | **COMPLETE** | Repository 사실, 재사용 자산, 목표 architecture, decision rule과 전체 plan을 source of truth로 고정 | 모든 M0 문서와 valid links, 상호 모순 없음, closure SHA와 M1 진입 기록 |
| M1 — Cloud Decoupling | **COMPLETE** | AWS-specific integration과 application core의 결합을 code boundary에서 제거 | Provider-neutral contract 및 configuration evidence, business regression pass |
| M2 — Runtime Parity | **COMPLETE** | Portable/current runtime에서 기존 핵심 사용자 흐름을 재현 | Reproducible startup/deployment, end-to-end smoke, persistence와 identity/config evidence |
| M3 — AI Evaluation Baseline | **COMPLETE** | AI/RAG 변경 전 반복 가능한 품질 baseline 수립 | 동일 dataset/config로 재실행 가능한 stage-provenance baseline과 failure taxonomy |
| M4 — AI Targeted Improvement | **COMPLETE** | M3에서 확인한 failure class만 최소 변경으로 개선 | 동일 조건 before/after comparison, trade-off 및 regression evidence |
| M5 — Backend Reliability Baseline | **COMPLETE** | 현재 `AnalysisJob` lifecycle의 실제 failure behavior 측정 | Reproducible scenarios, invariants, confirmed failure/non-failure report |
| M6 — Backend Reliability Improvement | **ACTIVE** | M5에서 확인된 reliability 문제만 수정 | 동일 failure scenarios에서 해소 또는 통제됨을 보이는 evidence |
| M7 — Observability | PLANNED | 실제 장애를 signal 간 연결로 RCA하고 recovery 확인 | 하나 이상의 실제 failure에 대한 metric/log/trace 기반 원인 및 recovery evidence |
| M8 — Failure & Load Verification | PLANNED | AI, reliability, observability 결합 상태를 failure/load 조건에서 검증 | Reproducible scenario, telemetry, resulting state, recovery와 operator evidence |
| M9 — GCP Runtime Closure | PLANNED | 앞선 milestone에서 사용한 동일 GCP target runtime의 delivery, rollback, teardown evidence 최종 정리 | Reused target IaC/runtime, immutable release, smoke, rollback, teardown evidence |
| M10 — Portfolio Closure | PLANNED | 문제 해결 evidence를 역추적 가능한 최종 결과물로 구성 | Repository evidence에 연결된 2~3개의 strongest case |

## M0 — Baseline & Governance

**Problem.** 장기 작업에서 repository의 현재 사실, reusable asset, target architecture, decision rule과 실행 순서가 흔들리지 않도록 하나의 governance baseline이 필요하다.

**Work.** [Component inventory](../architecture/component-inventory.md), [working contract](../../AGENTS.md), ADR-001~004와 [target architecture](../architecture/target-architecture.md)를 baseline으로 삼고 이 master plan과 [active M0 plan](active/M0-baseline-and-governance.md)을 추가했다. M0에서는 application/runtime implementation을 하지 않았다.

**Evidence.** M0 closure validation은 main SHA `3ccf17582ae91ad131d3efe8ce1a38492c401c72`에서 **PASS**했다. Required source-of-truth 존재와 repository-relative link validity, accepted decision consistency를 확인했고, deferred/gated decisions를 유지했으며 application/runtime implementation이 없음을 확인했다.

**Exit condition.** **MET.** Project scope와 architecture source of truth가 존재하고 logical architecture와 GCP deployment target이 분리되어 있다. Deferred technology가 명확하고 M0 plan의 미완료 항목이 없으며, `AI_PROJECT_STATE.md`에 exact M0 closure evidence SHA와 next milestone인 M1이 기록되어 있다.

**Immediate next single task.** `docs/plans/active/M1-cloud-decoupling.md`를 생성하여 M1 Cloud Decoupling의 실제 첫 미완료 작업과 exit evidence를 source of truth로 고정한다. 해당 plan이 merge되기 전에는 M1 implementation을 시작하지 않는다.

## M1 — Cloud Decoupling

**Status.** **COMPLETE.** M1-1~M1-8이 완료되었고 closure evidence는 [active M1 plan](active/M1-cloud-decoupling.md)과 [M1 closure verification](../verification/m1-cloud-decoupling-closure.md)에 기록되어 있다.

**Problem.** Cognito, Amplify, S3, SigV4/AOSS, Bedrock과 vendor runtime configuration이 application boundary에 결합된 지점은 portable runtime을 방해한다.

**Work.** Backend external identity mapping, frontend auth integration, object storage adapter, OpenSearch transport/auth, model/embedding provider configuration과 vendor-specific runtime configuration의 경계를 분리한다. 특정 IdP, queue 또는 model은 이 milestone 자체로 선택하지 않는다. 기존 business flow를 재작성하지 않고 `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, internal user semantics, `AnalysisJob` lifecycle, MariaDB/Flyway를 재사용한다.

**Evidence.** M1 closure head `d843fb9f08cd6255c1a4738ac220a40b5e760d75`에서 provider-neutral boundary static verification, full backend regression, MariaDB/Flyway/repository validation, frontend regression, runtime-contract verification, Backend Local Verification, Terraform Static Verification, AWS deployment contract inventory가 모두 **PASS**했다. 검증 과정에서 generic `AnalysisJobRunner`의 AWS/Bedrock-specific failure-type leakage와 neutral failure wrapper 도입 뒤 observability category regression을 발견했고, provider-neutral failure semantics로 최소 수정한 뒤 동일 closure suite로 재검증했다.

**Exit condition.** **MET.** Core application lifecycle과 provider-neutral ports/configuration은 vendor-specific SDK/type에서 분리되었고, 기존 AWS 구현은 compatibility/reference adapter로 남아 있다. Existing user/project/file/comment/analysis behavior와 MariaDB/Flyway compatibility는 deterministic regression suite에서 유지되었으며, GCP product/topology 선택은 M1 범위 밖의 gated decision으로 남는다.

**Immediate next single task.** Current repository evidence를 기준으로 M2 — Runtime Parity active plan을 작성한다. M2 plan이 source of truth로 merge되기 전에는 M2 implementation을 시작하지 않는다.

## M2 — Runtime Parity

**Status.** **COMPLETE.** The [completed M2 plan](active/M2-runtime-parity.md) records M2-1 through
M2-6 as done, and [M2 Runtime Parity Closure](../verification/m2-runtime-parity-closure.md) records
the final exit-criteria review.

**Problem.** Cloud-neutralized application이 기존 핵심 사용자 경험과 persistence semantics를 portable/current runtime에서 실제 수행할 수 있는지 증명되어야 했다.

**Work.** `architecture/image input → upload/source object → project/file → analysis job → retrieval/model → Terraform draft validation → result persistence` flow와 기존 user/project/comment flow를 portable runtime에서 검증했다. 새 기능 개발이나 GCP product 선택이 아니라 parity가 목적이었다.

**Evidence.** M2-1 through M2-5 established accepted evidence for portable startup, MariaDB/Flyway,
authenticated identity/ownership, upload/analysis/Terraform result flow, exact object-byte
persistence/read-back, user/project/comment behavior, and frontend regression/build. M2-6 reviewed
those results against all 14 exit criteria without adding closure-specific verification
infrastructure.

**Exit condition.** **MET.** All 14 M2 exit criteria are recorded as **PASS** with explicit residual
limitations in the closure report. No production IdP/storage/model, arbitrary GCP topology, or M3
implementation was selected as part of M2.

**Immediate next single task.** Create **M3 — AI Evaluation Baseline** active plan. Do not begin M3
implementation until that plan is the repository source of truth.

## M3 — AI Evaluation Baseline

**Status.** **COMPLETE.** The completed
[active M3 plan](active/M3-ai-evaluation-baseline.md) and
[M3 closure evidence](../verification/m3-ai-evaluation-closure.md) record the full evaluation
baseline lifecycle.

**Problem.** AI/RAG changes required one fixed, explainable before-state so later improvements could
target measured failures rather than adding architecture by convention.

**Work.** M3 created the stage-provenance contract, fixed six-case dataset, reusable evaluation
runner, single reusable GKE/Vertex/OpenSearch target runtime, immutable
`terraformers-reference-v3` corpus/index, production Java serving-path smoke, six-case live
baseline, reproducible metrics/failure taxonomy, and framework-decision checkpoint.

**Evidence.** Canonical live baseline run `36379633596` used configuration fingerprint
`sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`.
The primary first-divergence class is `FACT_EXTRACTION / PROVIDER_RUNTIME` in 2 of 6 cases.
Where the pipeline reached later stages, required project-decision retrieval, required resource
coverage, grounded generation, validation, and negative-control classification were successful.
The baseline analysis and machine-readable metrics are committed under
`docs/evaluation/m3-live-baseline-analysis.md` and
`evaluation/baselines/m3-live-baseline-metrics.json`.

**Exit condition.** **MET.** All 15 M3 exit criteria are PASS. Current quality is reproducibly
measurable, failures are localized to the first observable stage, and M4 has an evidence-backed
first investigation target. LangGraph, LangChain, rerankers, judge-model expansion, and a persistent
Python AI worker remain DEFER because M3 does not show they address the measured first divergence.

**Immediate next single task.** Create
`docs/plans/active/M4-ai-targeted-improvement.md` from the M3 failure taxonomy. Do not implement an
M4 change until that plan is the repository source of truth.

## M4 — AI Targeted Improvement

**Status.** **COMPLETE.** The source-of-truth plan is
[`active/M4-ai-targeted-improvement.md`](active/M4-ai-targeted-improvement.md), and closure evidence
is recorded in
[`m4-targeted-improvement-closure.md`](../evaluation/m4-targeted-improvement-closure.md).

**Problem.** M3 identified `FACT_EXTRACTION / PROVIDER_RUNTIME` as the first divergence in 2 of 6
fixed cases. M4-1 preserved actionable provider/response diagnostics, and M4-2 reproduced the VPC
case specifically as `FACT_EXTRACTION / OUTPUT_TRUNCATED` with
`reason=RESPONSE_TRUNCATED`; the AOSS failure did not reproduce in its bounded rerun.

**Work.** Code inspection showed that Vertex fact extraction used a fixed 800-token output bound
while Gemini 3.8 Flash thinking was not explicitly constrained for the compact structured-output
request. PR #74 set only fact extraction to `LOW` thinking, keeping the 800-token bound, prompt,
schema, models, corpus, retrieval/top-K, validator, and no-retry behavior unchanged. The targeted VPC
rerun then passed end to end. M4-4 reran all six unchanged cases on the same reusable GCP target
runtime and fixed dataset/configuration identity.

**Evidence.** Canonical before run `36379633596` had fact extraction PASS 4/6 and two first
divergences. Targeted post-change VPC run `36388548679` passed fact extraction, retrieval,
generation, and validation with no retry. Full after run `36391698161` on commit
`2526f85b4bef781976126f99e5ed0c25344d0f94` had fact extraction PASS 6/6, retrieval PASS 6/6,
generation PASS 6/6, all four architecture cases validation PASS, both negative controls still
correct, and zero first divergences. Artifact digest is
`sha256:3697bf1a703b437132d6bf98eb89a51ab45810154afb02d3e5d3e1ffcbb7d1c6`.
Residual evidence is preserved: the newly reachable VPC retrieval stage missed part of its fixed
top-8 requirement, and AOSS fact extraction had a one-run 130489 ms latency outlier. Final protected
idle run `36392580754` returned the canonical node pool to `node_count=0`.

**Exit condition.** **MET.** The selected M3 first-divergence class was reproduced/root-caused, the
smallest justified change was implemented, the same fixed dataset/runtime contract was rerun, the
targeted failure count fell from 2 to 0, and previously successful downstream/negative-control
behavior did not regress. No generic retry, reranker, framework expansion, model switch, prompt
tuning, or corpus change was introduced.

**Immediate next single task.** Create the M5 — Backend Reliability Baseline active plan from the
current `AnalysisJob` lifecycle. Measure and classify current failure behavior before selecting a
reliability mechanism.

## M5 — Backend Reliability Baseline

**Status.** **COMPLETE.** The completed plan is
[`active/M5-backend-reliability-baseline.md`](active/M5-backend-reliability-baseline.md), and
classification evidence is recorded in
[`m5-backend-reliability-baseline.md`](../evaluation/m5-backend-reliability-baseline.md).

**Problem.** The `AnalysisJob` lifecycle had unmeasured failure boundaries around in-process
scheduling, restart, duplicate delivery, and object/DB partial success.

**Work.** M5 reproduced the current behavior without implementing fixes. Restart tests proved
persisted `PENDING`/`RUNNING` jobs remain stranded after process recreation. Duplicate-delivery
tests proved an already-`SUCCEEDED` job can re-enter `RUNNING` and repeat provider/storage and
generated-file registration attempts. Partial-success tests proved result object persistence can
succeed before relational finalization fails, leaving a persistent object with no committed
job/file reference. Existing executor rejection coverage was reused and classified as controlled
because rejection already terminates the job as `FAILED`.

**Evidence.** PR #78 / merge `c58b2902556d30f4e85ff84b249a8dccfd83e207` covers restart-stranded
states. PR #79 / merge `8e4c27c5b0c179ba7187e1e92af2698a7bb4b316` covers duplicate
same-job delivery. PR #80 / merge `18503b656de090b6fc26b317908189b7090ecfc3` covers the
object/DB partial-success window; final Backend Local Verification run `36395373207` passed.

**Exit condition.** **MET.** Three confirmed reliability gaps and one controlled executor-rejection
path are reproducibly classified. M6 receives explicit invariants without a preselected broker,
outbox, distributed lock, retry infrastructure, or new cloud service.

**Immediate next single task.** Execute M6-1: enforce an atomic `PENDING → RUNNING` claim so a
terminal or duplicate-delivered job cannot re-enter execution.

## M6 — Backend Reliability Improvement

**Status.** **ACTIVE.** The source-of-truth plan is
[`active/M6-backend-reliability-improvement.md`](active/M6-backend-reliability-improvement.md).

**Problem.** M5 confirmed three concrete gaps: stranded non-terminal jobs after process restart,
duplicate re-execution of the same job id, and untracked result-object residue after relational
finalization failure.

**Work.** Apply the smallest evidence-backed controls in sequence. First, make the `PENDING →
RUNNING` claim atomic and terminal states immutable. Second, reconcile stale non-terminal jobs on
startup under the current single-replica runtime contract. Third, inspect existing storage
capabilities and control partial-success residue through the smallest provider-neutral compensation
or durable-accountability mechanism. Reuse M5 scenarios as before/after tests.

**Evidence.** M6 must invert the M5 failure scenarios rather than replace them with unrelated tests.
No RabbitMQ, Transactional Outbox, Kafka, Redis, distributed lock service, generic retry loop, or
second worker is justified by the current evidence.

**Exit condition.** One caller can claim a pending job, terminal jobs cannot re-enter execution,
current single-replica restart does not leave M5 stranded states indefinitely, partial-success
residue is removed or durably accountable, and existing successful/rejection behavior remains
intact.

**Immediate next single task.** M6-1 atomic claim / terminal-state guard only.

## M7 — Observability

**Problem.** 현재 portable telemetry runtime과 repository-owned trace propagation/export/validation gap 때문에 실제 failure의 end-to-end RCA가 보장되지 않는다.

**Work.** Micrometer metrics, Actuator/health와 correlation semantics를 재사용하며 `request → AnalysisJob → retrieval → model/provider → validation → result` correlation을 구축·검증한다.

**Evidence.** 실제 injected/observed failure 하나 이상을 metric, log, trace 또는 가능한 신호 조합으로 연결하고 root cause 및 recovery를 설명한다.

**Exit condition.** Repository evidence로 failure 원인과 recovery를 추적할 수 있다. Dashboard 설치만으로 완료하지 않는다.

## M8 — Failure & Load Verification

**Problem.** AI, backend reliability와 observability가 결합된 시스템 behavior는 실제 failure/load 조건에서 검증되어야 한다.

**Work.** Dependency latency/error, model timeout/error, retrieval failure, executor pressure, backend restart, DB pressure, invalid model output 중 evidence에 맞는 scenario를 실행한다. Arbitrary TPS나 구체 load target을 미리 정하지 않는다.

**Evidence.** Reproducible scenario, telemetry, resulting system state, recovery evidence와 runbook/operator evidence를 남긴다.

**Exit condition.** 선택한 조건에서 system state와 recovery가 반복 가능하게 관찰되고 운영자가 evidence를 따라 진단·대응할 수 있다.

## M9 — GCP Runtime Closure

**Problem.** The target GCP runtime built and used earlier in the project must have a reproducible
delivery, rollback, and resource/cost closure lifecycle.

**Work.** Reuse the same target runtime, IaC modules, application image, provider adapters, and
configuration introduced before M3-4. Complete immutable release identity, deployment, smoke,
rollback, and teardown/cost closure. Do **not** create a separate "production" architecture or
rebuild the runtime from scratch merely because M9 has been reached.

**Evidence.** Reproducible plan/apply for the reused target IaC, release identity, smoke, rollback,
and teardown/closure records.

**Exit condition.** The single approved GCP target runtime used by prior milestones has a complete,
reproducible deploy-to-close lifecycle in repository evidence.

## M10 — Portfolio Closure

**Problem.** 기술 목록이 아니라 검증된 문제 해결 과정을 최종 결과물로 제시해야 한다.

**Work.** Backend reliability, AI/RAG evaluation/improvement, observability/RCA 후보에서 strongest 2~3개 case를 선택한다. 각 case를 `problem → reproduction/baseline → root cause → decision → implementation → validation → result/trade-off`로 구성한다.

**Evidence.** 각 주장과 단계가 code, test, report, log/metric/trace 또는 reproducible command에 연결되어야 한다.

**Exit condition.** Repository evidence로 역추적 가능한 최종 portfolio material이 존재한다.

## Deferred architecture decisions

아래 항목은 금지가 아니라 evidence와 별도 decision이 부족한 **DEFER** 상태다. [ADR-004](../architecture/decisions/ADR-004-change-gates.md)의 네 질문을 모두 통과하기 전에는 `NEW` 또는 `REPLACE`로 승격하거나 milestone 진입만으로 승인하지 않는다.

- RabbitMQ, Transactional Outbox, Redis, Kafka
- LangGraph, multi-agent architecture, persistent Python AI worker/service
- Keycloak 또는 concrete GCP identity provider
- Concrete GCP managed database
- GKE 사용 여부와 topology, node count, VM type/size
- Exact OpenSearch hosting topology와 authentication mechanism
- Specific generation model과 specific embedding model
- Exact observability backend topology
- Arbitrary performance/quality/load target

## Source-of-truth references

우선순위와 해석은 [repository working contract](../../AGENTS.md)를 따른다.

- [Component inventory](../architecture/component-inventory.md)
- [Target architecture](../architecture/target-architecture.md)
- [ADR-001: Define modernization project scope](../architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Preserve cloud-neutral application boundaries](../architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Require evaluation before AI/RAG complexity](../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Gate architectural changes on reproducible evidence](../architecture/decisions/ADR-004-change-gates.md)
- [Active M0 plan](active/M0-baseline-and-governance.md)
