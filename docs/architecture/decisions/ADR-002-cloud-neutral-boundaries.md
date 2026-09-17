# ADR-002: Preserve cloud-neutral application boundaries

## Status

Accepted

## Context

현재 application에는 cloud-neutral port와 AWS-specific adapter가 함께 존재한다. GCP가
현재 deployment target이라는 이유로 domain/service interface를 다시 vendor에 결합하면
기존 경계의 재사용성과 향후 portability를 잃는다. 반대로 AWS 구현을 즉시 삭제할
근거도 없으므로, 확인된 application contract와 provider-specific concern의 경계를
명시적으로 유지해야 한다.

## Decision

Application/domain core는 cloud vendor SDK 또는 product-specific runtime에 직접
의존하지 않는 방향으로 유지한다. 이미 확인된 provider-neutral port와 business
contract를 재사용하고, vendor SDK, transport, authentication, resource naming과
deployment integration은 adapter 또는 deployment concern으로 격리한다.

내부 user ID 및 profile/role/status semantics는 유지한다. 외부 identity의 subject와
provider mapping은 provider-neutral boundary로 변경할 수 있지만, 이 ADR은 특정
identity provider를 선택하지 않는다.

## Boundary rules

- GCP adapter는 추가할 수 있으나 GCP SDK나 resource naming이 domain/service interface에
  침투해서는 안 된다.
- 구체적인 GCP managed service는 여기서 고정하지 않는다. GCP quota, capacity와 runtime
  requirement를 확인한 뒤 별도 deployment ADR에서 결정한다.
- Provider-specific 구현은 portable contract의 구현체로 취급하며 application core의
  기본 전제로 만들지 않는다.
- AWS runtime/infrastructure/delivery는 historical baseline/reference로 보존하고 active
  GCP infrastructure로 간주하지 않는다.

## Existing reusable ports

- `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`
- `ObjectReader` / `ObjectWriter`
- internal user ID와 profile/role/status semantics
- `AnalysisJob` lifecycle
- MariaDB + Flyway persistence contract
- versioned RAG corpus contract
- Kubernetes base/workload contract
- runtime configuration contract

## Vendor-specific implementations

- **Cognito:** authorization, session, user semantics는 재사용한다. Cognito-specific JWT
  validation, claims, `cognito_sub`, Amplify integration은 `MODIFY` 대상이다.
- **Bedrock:** `AnalysisProvider` / `EmbeddingProvider` contract는 재사용한다. Bedrock
  구현은 historical compatibility/reference 또는 provider adapter로 취급한다.
- **S3:** `ObjectReader` / `ObjectWriter` contract는 재사용하고 S3 구현은
  provider-specific adapter로 유지한다.
- **OpenSearch:** retrieval/query/parser concept는 재사용한다. SigV4/AOSS transport와
  authentication은 provider-specific concern이며 portable transport/auth boundary가
  필요하다.
- **SQS:** `ProgressPublisher` contract와 event semantics를 SQS adapter와 구분한다.
  SQS adapter를 향후 mandatory architecture로 간주하지 않는다.
- **CloudWatch:** application metric과 correlation semantics는 재사용한다. CloudWatch
  exporter, dashboard, alarm은 historical AWS deployment concern이다.
- **AWS Terraform/workflows:** active GCP infrastructure가 아니다. 다만 state separation,
  least privilege, OIDC, immutable SHA, approval gate, plan/apply separation, evidence
  collection과 같은 검증 패턴은 재사용할 수 있다.

## Consequences

- Application orchestration과 business rules는 재사용 가능한 port에 의존한다.
- 새로운 provider integration은 기존 contract에 대한 adapter로 검증되어야 한다.
- 기존 AWS adapter는 삭제 대상으로 단정하지 않으며 compatibility/reference 역할을
  유지할 수 있다.
- 외부 identity subject/provider mapping은 내부 business identity와 분리할 수 있으며,
  기존 사용자 연결과 semantics의 보존이 필요하다.

## Deferred decisions

- GCP의 구체적인 managed services, SDK, resource topology와 deployment configuration
- GCP target identity provider 및 외부 identity migration 방식; Keycloak을 포함한 특정
  IdP는 선택하지 않는다.
- Portable OpenSearch transport/auth의 구체 구현
- SQS를 대체하거나 보완할 queue/broker의 필요성과 제품 선택
- Quota, capacity, runtime evidence가 필요한 deployment 세부사항

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [Component inventory](../component-inventory.md)
- [Historical AWS modernization direction](../../../PROJECT_DIRECTION.md)
