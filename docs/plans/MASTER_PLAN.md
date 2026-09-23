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
- `DEFER` technology는 milestone 이름이나 진입만으로 승인되지 않는다.
- `KEEP`으로 판정된 domain/business contract와 deterministic validation 자산을 우선 재사용하고 historical AWS 자산은 active runtime이 아닌 compatibility/baseline/reference로 다룬다.
- 성능, AI 품질, reliability, observability 개선은 각각 비교 가능한 측정, fixed evaluation baseline, 동일 failure scenario 재검증, 실제 진단 evidence 없이 완료로 주장하지 않는다.

## Milestone overview

| Milestone | Status | Purpose | Exit evidence |
| --- | --- | --- | --- |
| M0 — Baseline & Governance | **COMPLETE** | Repository 사실, 재사용 자산, 목표 architecture, decision rule과 전체 plan을 source of truth로 고정 | 모든 M0 문서와 valid links, 상호 모순 없음, closure SHA와 M1 진입 기록 |
| M1 — Cloud Decoupling | **COMPLETE** | AWS-specific integration과 application core의 결합을 code boundary에서 제거 | Provider-neutral contract 및 configuration evidence, business regression pass |
| M2 — Runtime Parity | **COMPLETE** | Portable/current runtime에서 기존 핵심 사용자 흐름을 재현 | Reproducible startup/deployment, end-to-end smoke, persistence와 identity/config evidence |
| M3 — AI Evaluation Baseline | **ACTIVE** | AI/RAG 변경 전 반복 가능한 품질 baseline 수립 | 동일 dataset/config로 재실행 가능한 stage-provenance baseline과 failure taxonomy |
| M4 — AI Targeted Improvement | PLANNED | M3에서 확인한 failure class만 최소 변경으로 개선 | 동일 조건 before/after comparison, trade-off 및 regression evidence |
| M5 — Backend Reliability Baseline | PLANNED | 현재 `AnalysisJob` lifecycle의 실제 failure behavior 측정 | Reproducible scenarios, invariants, confirmed failure/non-failure report |
| M6 — Backend Reliability Improvement | PLANNED | M5에서 확인된 reliability 문제만 수정 | 동일 failure scenarios에서 해소 또는 통제됨을 보이는 evidence |
| M7 — Observability | PLANNED | 실제 장애를 signal 간 연결로 RCA하고 recovery 확인 | 하나 이상의 실제 failure에 대한 metric/log/trace 기반 원인 및 recovery evidence |
| M8 — Failure & Load Verification | PLANNED | AI, reliability, observability 결합 상태를 failure/load 조건에서 검증 | Reproducible scenario, telemetry, resulting state, recovery와 operator evidence |
| M9 — GCP Runtime Closure | PLANNED | GCP runtime의 delivery, rollback, teardown evidence 최종 정리 | Reproducible IaC/deployment, immutable release, smoke, rollback, teardown evidence |
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

**Status.** **ACTIVE.** The [active M3 plan](active/M3-ai-evaluation-baseline.md) defines the
evaluation-first sequence. The first implementation task is **M3-1 — Evaluation contract and
stage-provenance schema**.

**Problem.** Fixed evaluation data and stage-level provenance do not yet exist, so a bad final
Terraform result cannot be reliably localized to architecture-fact extraction, retrieval, grounding,
generation, or validation.

**Work.** Build one fixed/versioned dataset, one reusable runner, one machine-readable provenance
record, and one baseline/failure-taxonomy report. Preserve extracted facts, retrieval query,
retrieved document IDs/scores/source metadata, generation outputs, Terraform resource types,
validation result, runtime/model/corpus identity, and stage timing. Explainability means observable
provenance; hidden model chain-of-thought is not required or treated as evidence.

**Evidence.** Per-case machine-readable traces and a reproducible baseline report must show what data
was retrieved and supplied to generation and identify the first observable divergent stage for each
failure where evidence permits.

**Exit condition.** The same dataset/configuration can be rerun, failures are attributable to
specific pipeline stages, and M4 receives evidence-backed failure classes. LangChain, LangGraph, or
other framework adoption remains evidence-gated rather than being an M3 prerequisite.

**Immediate next single task.** Execute M3-1 only. Define the evaluation case/result contract and
stage-provenance schema without changing production AI behavior.

## M4 — AI Targeted Improvement

**Problem.** AI architecture complexity가 실제 failure와 무관하게 증가하면 개선을 검증할 수 없다.

**Work.** `baseline failure → root cause → minimal targeted change → same-dataset re-evaluation` 순서를 따른다. LangGraph, reranker, additional model call, Python service 등은 자동 포함하지 않는다.

**Evidence.** 동일 조건 before/after 결과, 개선 대상으로 삼은 failure class, root cause, trade-off와 regression 결과를 기록한다.

**Exit condition.** 선택한 failure class의 개선이 동일 dataset/configuration comparison으로 입증되고 regression과 trade-off가 문서화된다.

## M5 — Backend Reliability Baseline

**Problem.** 현재 `AnalysisJob`/runtime lifecycle의 failure behavior와 실제 reliability gap이 측정되지 않았다.

**Work.** Process restart, `PENDING`/`RUNNING` recovery, concurrent requests, executor saturation/rejection, duplicate execution, DB/object-store consistency, result write와 DB state transition 사이 failure를 investigation 후보로 실험한다. 이 후보를 결함으로 미리 단정하지 않는다.

**Evidence.** Reproducible failure scenarios, current behavior report, invariants, confirmed failure와 non-failure 구분을 남긴다.

**Exit condition.** 후보별 실제 behavior가 재현 가능하게 분류되고 개선 milestone이 사용할 명확한 invariants와 confirmed problems가 존재한다. RabbitMQ나 outbox는 이 baseline에서 해결책으로 선택하지 않는다.

## M6 — Backend Reliability Improvement

**Problem.** M5에서 확인된 failure가 business/runtime invariant를 위반한다.

**Work.** Evidence가 지시하는 최소 변경만 수행한다. Guarded state transition, idempotency, restart reconciliation, duplicate protection, recovery policy는 가능한 예일 뿐 선결정이 아니다. RabbitMQ나 Transactional Outbox는 M5 evidence가 필요성을 입증하고 ADR-004 gate를 통과할 때만 고려한다.

**Evidence.** 변경 전과 동일한 failure scenarios, regression suite, resulting state와 recovery behavior를 비교한다.

**Exit condition.** 확인된 문제가 동일 scenario 재실행에서 해소되거나 명확하게 통제됨을 증명한다.

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

**Problem.** 현재 deployment target인 GCP에서 delivery lifecycle과 resource/cost closure가 재현 가능하게 정리되어야 한다.

**Work.** Deployment architecture와 quota/capacity evidence에 따라 GCP IaC, workload identity, image publishing, immutable release identity, deployment, smoke, rollback, teardown/cost closure를 정리한다. 구체 topology와 product는 근거 없이 선택하지 않는다.

**Evidence.** Reproducible IaC/deployment, release identity, smoke, rollback, teardown/closure 기록을 보존한다.

**Exit condition.** 승인된 GCP target에서 deploy-to-close lifecycle 전체가 repository evidence로 재현 가능하다.

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
