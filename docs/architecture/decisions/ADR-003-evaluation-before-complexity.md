# ADR-003: Require evaluation before AI/RAG complexity

## Status

Accepted

## Context

현재 repository에는 architecture/image input을 받아 architecture facts를 추출하고,
reference를 검색한 뒤 model로 Terraform draft를 생성하여 검증하고 결과를 저장하는
pipeline이 있다. 이 흐름은 `BedrockArchitectureFactsExtractor`, `ReferenceRetriever`,
`AnalysisProvider`, `TerraformDraftValidator`, `AnalysisResultStorage`에서 확인된다.

또한 versioned RAG corpus와 `EmbeddingProvider`를 포함한 재사용 가능한 경계가 있지만,
고정된 evaluation dataset과 반복 가능한 quality comparison은 아직 확인되지 않았다.
따라서 framework나 orchestration을 먼저 추가하면 어떤 failure를 해결했는지, 같은
조건에서 결과가 실제로 개선되었는지 판단할 baseline이 없다.

## Decision

AI/RAG 개선에 앞서 fixed evaluation baseline을 구축한다. 동일한 evaluation dataset과
비교 가능한 configuration으로 변경 전후를 평가할 수 있어야 하며, failure taxonomy를
작성한 뒤 확인된 failure class만 targeted improvement의 대상으로 삼는다.

Architecture complexity나 framework adoption 자체는 improvement로 간주하지 않는다.
현재 versioned corpus, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`,
Terraform draft validation 자산을 우선 재사용한다. 이 ADR은 evaluator framework,
LLM-as-judge, 특정 model 또는 임의의 목표 수치와 threshold를 선택하지 않는다.

## Evaluation-first workflow

1. Fixed evaluation cases를 정의한다.
2. Current baseline을 실행한다.
3. 결과를 기록한다.
4. Failure taxonomy를 작성한다.
5. 가장 의미 있는 failure class를 선택한다.
6. 그 failure class를 직접 다루는 최소한의 변경을 적용한다.
7. 동일한 dataset과 configuration 조건에서 재평가한다.
8. 비교 evidence가 있을 때만 improvement로 기록한다.

## Evaluation scope

Evaluation baseline은 최소한 다음 범주를 다룰 수 있어야 한다. 구체적인 metric 이름과
계산 방식은 향후 evaluation design에서 정한다.

- architecture/component extraction correctness
- relationship correctness
- retrieval relevance와 retrieval success
- unsupported 또는 ungrounded generation
- Terraform formatting, parsing, validation success
- latency
- 실제로 측정 가능한 경우의 model call, token, cost 정보

## Complexity that remains deferred

Baseline 결과가 필요성을 입증하기 전에는 다음 항목을 도입하지 않는다.

- LangGraph
- agent 또는 multi-agent orchestration
- persistent Python AI service/worker
- complex retry/repair graph
- additional vector database
- additional reranker
- architectural sophistication만을 위한 additional model calls

이 항목들은 영구 금지 기술이 아니다. Evaluation evidence가 실제 문제를 보여주고,
제안 기술이 그 문제를 직접 해결하며, 동일 조건에서 결과를 검증할 수 있을 때 별도
ADR과 change gate를 거쳐 도입할 수 있다.

## Consequences

- AI/RAG 변경은 fixed cases에 대한 baseline과 before/after evidence를 남겨야 한다.
- 개선 작업은 관찰된 failure class에 집중하며 기존 portable contract와 corpus를
  불필요하게 교체하지 않는다.
- Evaluation design이 확정되기 전까지 evaluator 구현, 목표 수치와 model 선택은
  deferred 상태로 남는다.
- Framework 도입이 아니라 측정된 결과의 변화가 improvement 판단 기준이 된다.

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [Component inventory](../component-inventory.md)
- [ADR-001: Define modernization project scope](ADR-001-project-scope.md)
- [ADR-002: Preserve cloud-neutral application boundaries](ADR-002-cloud-neutral-boundaries.md)
