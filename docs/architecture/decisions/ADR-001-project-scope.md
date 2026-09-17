# ADR-001: Define modernization project scope

## Status

Accepted

## Context

Terraformers에는 이미 domain/business flow, `AnalysisJob` lifecycle, provider ports,
MariaDB/Flyway persistence, versioned RAG corpus, Kubernetes workload contract와 검증
자산이 있다. 따라서 현재 작업은 이 기반을 버리는 greenfield rewrite가 아니다.

현재 deployment environment는 GCP이지만, 이 프로젝트를 단순한 AWS → GCP
migration으로 정의하면 application 개선 목적과 재사용할 경계를 놓치게 된다. 기존
AWS runtime, infrastructure, delivery는 active runtime으로 간주하지 않되 삭제하지
않고 historical baseline/reference로 보존한다.

## Decision

이 프로젝트를 기존 Terraformers를 기반으로 하는 modernization project로 정의한다.
검증된 domain/business flow와 구현은 가능한 한 재사용한다. Cloud 변경 자체를 최종
목적으로 삼지 않으며, active improvement scope를 backend reliability, AI/RAG의
평가 기반 개선, 실제 장애 진단 중심 observability, cloud portability의 네 축으로
제한한다.

## In scope

1. **Backend reliability**
   - 기존 `AnalysisJob` asynchronous lifecycle을 baseline으로 사용한다.
   - restart, concurrency, executor saturation, consistency와 같은 실제 failure를 먼저
     재현하고, 확인된 문제만 수정한 뒤 같은 failure condition에서 재검증한다.
2. **AI/RAG evaluation and targeted improvement**
   - 기존 corpus와 retrieval/provider abstraction을 재사용한다.
   - fixed evaluation baseline을 먼저 만들고 failure type을 확인한 뒤 targeted
     improvement를 수행한다. Framework 도입 자체는 목표가 아니다.
3. **Observability based on real failure diagnosis**
   - monitoring stack 설치 자체가 아니라 metric, log 또는 trace로 실제 failure의
     root cause를 찾고 recovery를 검증할 수 있는 상태를 목표로 한다.
4. **Cloud portability**
   - application core와 vendor-specific integration의 경계를 명확히 한다.
   - GCP를 현재 runtime target으로 삼되 application/domain architecture를
     GCP-specific하게 만들지 않는다.

## Out of scope

- 모든 기존 코드의 재작성
- 모든 AWS 자산의 즉시 삭제
- Kubernetes를 다른 runtime으로 교체
- MariaDB를 다른 database로 교체
- microservice 분리, queue 도입, AI framework 도입 또는 기술 개수 증가 자체

RabbitMQ, Transactional Outbox, LangGraph, persistent Python AI worker/service,
Keycloak, Redis, Kafka, multi-agent architecture는 evidence와 decision gate를 통과하기
전에는 scope에 포함하거나 확정 기술로 취급하지 않는다.

## Consequences

- 변경은 관찰되고 재현된 문제 또는 평가 결과에 대응하는 범위로 제한된다.
- 기존 portable contract와 deterministic validation 자산을 우선 재사용한다.
- GCP runtime 도입은 project scope를 바꾸지 않으며, 구체적인 managed service 선택은
  별도 evidence와 decision을 필요로 한다.
- Historical AWS 자산은 compatibility와 검증 패턴의 reference로 남지만 active target을
  의미하지 않는다.

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [Component inventory](../component-inventory.md)
- [Historical AWS modernization direction](../../../PROJECT_DIRECTION.md)
