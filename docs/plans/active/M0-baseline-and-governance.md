# M0 — Baseline & Governance

## Status

**ACTIVE**

## Objective

현재 repository의 사실, reusable asset, cloud-provider-neutral logical target architecture, GCP deployment target, decision rule과 전체 modernization sequence를 source of truth로 고정한다. M0는 planning/governance milestone이며 application 또는 runtime을 구현하지 않는다.

## Constraints

- Current code와 repository evidence를 기준으로 하며 historical AWS 문서를 active planning source로 사용하지 않는다.
- 기존 `KEEP` domain/business contract와 accepted decision을 재설계하지 않는다.
- Logical/application architecture와 현재 GCP deployment target을 구분한다.
- 새로운 architecture나 technology를 선택하지 않는다. Substantive change는 [ADR-004 change gate](../../architecture/decisions/ADR-004-change-gates.md)를 먼저 통과해야 한다.
- RabbitMQ, Transactional Outbox, LangGraph, persistent Python AI worker/service, Keycloak, Redis, Kafka, multi-agent architecture, concrete GCP managed database/identity provider, GKE topology, exact OpenSearch hosting topology, specific generation/embedding model, exact observability backend topology와 arbitrary performance target은 **DEFER**다.
- 한 작업은 하나의 logical/verifiable result로 제한하며 아래 첫 미완료 작업부터 진행한다.

## Completed work

현재 repository에 존재하는 primary evidence는 다음과 같다.

- [x] [Repository working contract](../../../AGENTS.md)
- [x] [Component inventory](../../architecture/component-inventory.md)
- [x] [ADR-001: Define modernization project scope](../../architecture/decisions/ADR-001-project-scope.md)
- [x] [ADR-002: Preserve cloud-neutral application boundaries](../../architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [x] [ADR-003: Require evaluation before AI/RAG complexity](../../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [x] [ADR-004: Gate architectural changes on reproducible evidence](../../architecture/decisions/ADR-004-change-gates.md)
- [x] [Target architecture](../../architecture/target-architecture.md)
- [x] [Modernization master plan](../MASTER_PLAN.md)과 이 active M0 plan 생성 (현재 변경)

## Remaining work

아래 세 작업만 M0 remaining work다. Application/runtime implementation을 추가하지 않는다.

1. [ ] `docs/architecture/deployment-gcp.md` 작성
   - Logical target architecture를 GCP deployment 관점에 매핑한다.
   - Known, unknown, gated decision을 구분하고 product/topology를 과도하게 확정하지 않는다.
2. [ ] `docs/AI_PROJECT_STATE.md` 작성
   - Current main SHA, current milestone, completed evidence, blocked/deferred items, immediate next work, do-not-revisit decisions를 기록한다.
3. [ ] M0 final consistency / closure
   - Source-of-truth links와 architecture/ADR/plan 사이 contradiction을 확인한다.
   - Exact closure main SHA와 M1 entry readiness를 기록한다.

## Exit criteria

- [x] `AGENTS.md`가 존재한다.
- [x] `docs/architecture/component-inventory.md`가 존재한다.
- [x] ADR-001~004가 존재한다.
- [x] `docs/architecture/target-architecture.md`가 존재한다.
- [x] `docs/plans/MASTER_PLAN.md`가 존재한다.
- [x] `docs/plans/active/M0-baseline-and-governance.md`가 존재한다.
- [ ] `docs/architecture/deployment-gcp.md`가 존재한다.
- [ ] `docs/AI_PROJECT_STATE.md`가 존재한다.
- [ ] 모든 문서의 repository-relative links가 valid하다.
- [ ] Accepted decisions 사이 contradiction이 없다.
- [x] M0에서 runtime/application 변경을 하지 않았다.
- [ ] Exact closure main SHA가 기록되어 있다.
- [ ] Immediate next milestone이 **M1 — Cloud Decoupling**으로 기록되어 있다.

M0는 모든 criterion과 remaining work가 완료 evidence를 가질 때만 `COMPLETE`가 된다.

## Immediate next work

이 변경이 merge된 뒤 수행할 next single task는 **`docs/architecture/deployment-gcp.md` 작성**이다.

이 문서는 GKE 사용 여부/구체 topology, node count, VM type/size, managed DB product, specific IdP, model provider/model, exact OpenSearch hosting, exact observability backend를 임의로 확정하지 않는다. 해당 선택은 quota/capacity/runtime evidence와 별도 decision을 필요로 한다.

## Evidence links

- [Repository working contract](../../../AGENTS.md)
- [Component inventory](../../architecture/component-inventory.md)
- [Target architecture](../../architecture/target-architecture.md)
- [ADR-001: Define modernization project scope](../../architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Preserve cloud-neutral application boundaries](../../architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Require evaluation before AI/RAG complexity](../../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Gate architectural changes on reproducible evidence](../../architecture/decisions/ADR-004-change-gates.md)
- [Terraformers Modernization Master Plan](../MASTER_PLAN.md)
