# ADR-004: Gate architectural changes on reproducible evidence

## Status

Accepted

## Context

Repository working contract는 새 구조나 기술을 도입하기 전에 관찰된 문제, 재현 가능한
evidence, 문제에 직접 대응하는 변경, 동일 조건의 재검증을 요구한다. 이 규칙을
substantive architecture/technology change에 일관되게 적용하지 않으면 확인되지 않은
가정이나 기술 선호만으로 기존 `KEEP` contract와 현재 architecture가 복잡해질 수 있다.

## Decision

모든 substantive architecture/technology change는 다음 네 질문에 모두 `yes`라고 답할
수 있어야 한다.

1. 현재 implementation에서 실제 문제가 관찰되었는가?
2. 문제를 재현하거나 측정하는 evidence가 있는가?
3. Proposed change가 그 문제를 직접 해결하는가?
4. 동일 조건에서 변경 후 결과를 재검증할 수 있는가?

하나라도 충족되지 않으면 기본 상태는 **DEFER**다. 단순 dependency version update나
작은 bug fix까지 불필요하게 ADR 대상으로 만들지는 않는다.

Technology를 사용하고 싶거나 다른 조직에서 널리 사용한다는 사실은 evidence가 아니다.
Theoretical scalability와 아직 발생하지 않은 hypothetical problem만으로 현재
architecture를 교체하거나 복잡하게 만들지 않는다. 반대로 reproducible failure가
확인되면 기존 결정을 이유 없이 고집하지 않고 같은 gate로 변경을 평가한다.

## Required evidence

Change proposal은 최소한 다음 내용을 기록해야 한다.

**Problem**

- 문제가 되는 현재 동작
- 해당하는 실제 code path 또는 component

**Reproduction**

- test, command, experiment, log 또는 metric 등의 evidence
- 동일 조건에서 다시 실행할 수 있는 방법

**Proposed change**

- 관찰된 문제와 직접 관련된 최소 변경
- 기존 `KEEP` contract를 불필요하게 깨지 않는다는 근거

**Validation**

- 변경 전과 동일하거나 비교 가능한 조건
- success/failure criterion
- regression validation

## Decision outcomes

- **ACCEPT:** 네 질문을 모두 통과하고 proposed change를 진행한다.
- **DEFER:** evidence 또는 검증 조건이 부족하므로 추가 evidence가 생길 때까지 결정을
  유보한다.
- **REJECT:** evidence에 비추어 proposed change가 문제를 직접 해결하지 않거나 허용할
  수 없는 trade-off를 만든다.

## Examples of gated changes

이 gate는 특히 message broker/queue, transactional outbox, new database/cache, service
split, Python worker/service, AI orchestration framework, additional retrieval/reranking
architecture, identity provider replacement, observability backend change,
Kubernetes/runtime replacement, major persistence migration, additional cloud managed
service에 적용한다.

현재 특별히 gated 상태인 항목은 RabbitMQ, Transactional Outbox, LangGraph, persistent
Python AI worker/service, Keycloak, Redis, Kafka, multi-agent architecture다. 이 목록은
완전한 목록이 아니며 향후 새로운 substantial technology에도 같은 gate를 적용한다.

- **Backend reliability:** 현재 `AnalysisJob` lifecycle의 restart, concurrency, executor
  saturation, DB/object-store consistency를 먼저 실험한다. 실제 durability 또는 handoff
  문제가 확인되기 전에는 RabbitMQ나 outbox를 자동 해결책으로 선택하지 않는다.
- **AI/RAG:** evaluation baseline과 failure taxonomy를 먼저 만든다. 복잡한
  orchestration은 실제 failure class가 필요성을 보여줄 때만 고려한다.
- **Observability:** dashboard 수를 늘리기 위해 component를 도입하지 않는다. Failure
  diagnosis, correlation 또는 validation gap이 확인될 때만 변경을 고려한다.

## Consequences

- Substantive proposal은 문제, 재현, 최소 변경과 재검증을 연결하는 evidence를 남긴다.
- Evidence가 없는 technology 선택은 `NEW` 또는 `REPLACE`로 승격되지 않고 `DEFER`로
  유지된다.
- 기존 portable boundary와 `KEEP` contract의 재사용이 기본이며, 변경 필요성이
  입증되면 같은 조건에서 결과를 비교한다.
- 이 gate는 특정 제품 선택이나 상세 implementation plan을 확정하지 않는다.

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [Component inventory](../component-inventory.md)
- [ADR-001: Define modernization project scope](ADR-001-project-scope.md)
- [ADR-002: Preserve cloud-neutral application boundaries](ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Require evaluation before AI/RAG complexity](ADR-003-evaluation-before-complexity.md)
