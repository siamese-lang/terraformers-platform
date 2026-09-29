# Component inventory

## 범위와 판정 기준

- 기준 커밋: `5a28e579fb54edccd801df3082d6f7fafdf676ec` (PR #91 병합 후 current `main`).
- 이 문서는 현재 `main`의 application code, infrastructure, workflow, test 및 이미 확보된 M3~M6 evidence를 기준으로 분류한다. `PROJECT_DIRECTION.md`와 기존 AWS 구현은 현행 자산으로 보존한다.
- 판정 의미: **KEEP**은 현재 책임과 계약을 재사용, **MODIFY**는 자산을 보존하되 경계나 구성의 변경 가능성을 열어 둠, **REPLACE**는 확인된 대체 대상, **NEW**는 확인된 신규 공백, **ARCHIVE**는 현행 흐름에서 분리할 자산, **DEFER**는 근거 또는 결정이 부족해 판단을 유보한다는 뜻이다.
- 논리/application architecture의 목표는 **cloud-provider-neutral**이다. 현재 deployment target은 **GCP**이지만, GCP migration 자체만을 프로젝트 목표로 삼지 않으며 기존 domain/business flow와 portable boundary의 재사용을 우선한다.
- 기존 AWS 구현은 삭제하지 않고 historical baseline/reference로 보존한다. RabbitMQ, Transactional Outbox, LangGraph, 별도 상시 Python worker, Keycloak, Redis, Kafka는 자동 채택 대상이 아니지만, 실제로 관찰된 문제를 직접 해결하는 후보라면 Case Decision Gate에서 다른 대안과 공정하게 비교한다. `DEFER`는 배제 규칙이 아니라 미승인 상태를 뜻한다.

## Component별 inventory

### 1. Backend domain — project / user / file / comment

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Project aggregate 및 ownership | `backend/src/main/java/com/terraformers/modernization/projectcore/` | `OwnedProjectEntity`, repository, ownership/access 검증, artifact 갱신을 묶는 핵심 domain/service 계층 | **KEEP** | 인증 사용자를 기준으로 project 접근과 수정 권한을 일관되게 적용하는 실제 business flow이다. | cloud adapter와 무관한 project ownership 및 artifact 계약을 현행 service 중심으로 유지 | repository/service test와 인증된 CRUD 통합 시험; soft-delete/ownership 회귀 확인 |
| Project API/metadata/draft | `backend/src/main/java/com/terraformers/modernization/project/` | 소유/공개 project 조회·삭제·visibility 변경, 공개 호환 endpoint, `main.tf` 조회·수정 | **KEEP** | frontend가 소비하는 현재 API이며 domain service를 재사용한다. | 기존 endpoint와 DTO 호환성을 유지하고 infrastructure 세부사항을 노출하지 않음 | controller test 및 frontend API test; 공개/비공개 접근 matrix |
| Internal user/profile semantics | `backend/src/main/java/com/terraformers/modernization/identity/UserEntity.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserRole.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserStatus.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserProfileController.java` | 내부 `user_id`, profile/display name, role, status 및 profile API를 관리 | **KEEP** | project ownership/comment attribution이 의존하는 provider-independent business identity이다. | 내부 user ID와 profile/role/status semantics는 외부 IdP와 독립적으로 유지 | profile/domain tests와 project/comment ownership 통합 시험 |
| Provider-neutral external identity mapping | `backend/src/main/java/com/terraformers/modernization/identity/UserEntity.java`, `UserRepository.java`, `AuthenticatedUserService.java`, `JwtExternalIdentityMapper.java`, `AuthenticatedExternalIdentity.java` | `external_identity_provider + external_identity_subject`로 외부 주체를 내부 user에 연결하고 JWT claim 해석은 mapper/validator adapter에 위임. `cognito_sub`는 rollback-compatible legacy mirror로만 유지 | **KEEP** | M1에서 neutral lookup boundary가 구현되었고 current main은 Cognito-specific lookup을 사용하지 않는다. | provider-neutral external identity contract 유지; provider-specific claim/validation logic은 adapter에 격리 | migration compatibility, provider+subject uniqueness, mapper/validator contract, `AuthenticatedUserServiceTest` |
| Project file | `backend/src/main/java/com/terraformers/modernization/projectcore/ProjectFileEntity.java`, `backend/src/main/java/com/terraformers/modernization/projectcore/ProjectFileRepository.java`, `backend/src/main/java/com/terraformers/modernization/projecttree/` | project file metadata, tree projection, source/generated artifact 연결 | **KEEP** | upload→file→analysis→generated Terraform 흐름의 영속 domain 자산이다. | object bytes와 object-store locator는 분리하되 file metadata와 tree API 계약 유지 | tree/controller tests, upload-to-analysis 통합 시험, object locator 무결성 확인 |
| Comment/board | `backend/src/main/java/com/terraformers/modernization/collaboration/`, `backend/src/main/java/com/terraformers/modernization/projectcomment/` | board/comment JPA 모델과 project comment service; 신규 및 legacy-compatible endpoint 제공 | **KEEP** | 실제 comment flow와 호환 endpoint가 구현·시험되어 있다. | comment ownership/visibility 규칙과 호환 계약을 보존; 사용되지 않는 board 기능 확대는 하지 않음 | `ProjectCommentControllerTest`; author/project 접근과 parent comment 관계 통합 시험. `BoardEntity`의 독립 API 사용 여부는 **UNKNOWN/TBD** |

### 2. Analysis job lifecycle

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/` | durable dispatch/recovery, selective retry, deterministic canonical result, durable intent, fenced finalization/compensation, cleanup accountability/recovery, telemetry | **MODIFY — ADR-007 / B1/B2/B3/B4 COMPLETE** | B1–B4가 durable state/fencing/recovery/retry와 deterministic identity, pre-write intent, owned-row-lock finalization 및 bounded cleanup recovery를 구현했다. MariaDB가 durable source이고 executor는 local concurrency만 담당한다. | B5 integrated closure | B5 integrated failure matrix; Case B는 아직 portfolio-closed가 아님 |
| `backend/src/main/java/com/terraformers/modernization/analysis/ProgressPublisher.java`, `LoggingProgressPublisher.java`, `SqsProgressPublisher.java` | progress port와 local logging/AWS SQS adapter | **MODIFY** | core는 port에 의존하지만 SQS는 AWS-specific adapter이다. 삭제나 broker 교체 근거는 없다. | port/event 계약은 유지하고 SQS 구성은 배포 adapter로 격리; 기본은 logging publisher | publisher contract test와 enabled/disabled profile startup; 실제 SQS delivery/consumer 존재 여부는 **UNKNOWN/TBD** |

### 3. AI / RAG provider

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisProvider.java`, `backend/src/main/java/com/terraformers/modernization/analysis/StubAnalysisProvider.java` | analysis core port와 local/test fallback | **KEEP** | provider-neutral seam이 이미 application core와 vendor SDK를 분리한다. | orchestration은 `AnalysisProvider`에만 의존하고 local deterministic path 유지 | provider contract 및 stub tests |
| `backend/src/main/java/com/terraformers/modernization/analysis/bedrock/`, `analysis/vertex/` | Bedrock 및 Vertex generation adapter가 공통 `AnalysisProvider` 뒤에 존재하고 `SelectedAnalysisProvider`가 runtime selection을 수행 | **KEEP / ADAPTER-SPECIFIC** | provider-neutral analysis port와 vendor-specific adapters가 분리되어 있으며 current GCP target은 Vertex를 선택한다. Bedrock은 historical compatibility/reference로 유지한다. | orchestration은 `AnalysisProvider`에만 의존하고 vendor SDK/type은 adapter 내부에 유지 | provider contract tests, selected-provider routing, fixed M3 evaluation path 및 승인된 live invocation |
| `backend/src/main/java/com/terraformers/modernization/reference/ReferenceRetriever.java`, `RetrievalModeReferenceRetriever.java`, `RetrievalQueryTextBuilder.java`, `ArchitectureRetrievalFacts.java` | optional reference retrieval port, disabled/OpenSearch mode 선택, 구조화된 검색 질의 구성 | **KEEP** | RAG 사용 여부와 구현을 core 계약에서 분리하고 disabled mode를 제공한다. | analysis flow의 optional provider-neutral retrieval boundary 유지 | disabled/enabled routing test와 retrieved context가 prompt에 반영되는 통합 시험 |

### 4. Embedding

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/reference/EmbeddingProvider.java` | query embedding port | **KEEP** | vendor-independent application boundary이다. | retrieval은 interface에 의존 | dimension/error contract tests |
| `BedrockEmbeddingProvider.java`, `VertexEmbeddingProvider.java`, `SelectedEmbeddingProvider.java` | Bedrock/Vertex embedding adapter와 runtime provider selection, configured dimension 검사 | **KEEP / ADAPTER-SPECIFIC** | provider-neutral `EmbeddingProvider` 뒤에 두 vendor adapter가 분리되어 있고 current GCP target은 Vertex를 선택한다. | query embedding은 interface에 의존하고 model/dimension identity를 corpus/runtime contract와 일치시킴 | provider routing, dimension/error contract, fixed corpus/query model identity 검증 |

### 5. OpenSearch retrieval

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/reference/opensearch/` | k-NN query/response 계약과 `OpenSearchTransport` boundary, plain HTTP transport, AWS SigV4 transport 및 runtime selection | **KEEP / ADAPTER-SPECIFIC** | M1에서 retrieval/query와 transport/auth가 분리되었다. current GCP target은 in-cluster OpenSearch + HTTP transport를 사용하고 AWS SigV4는 historical adapter로 유지한다. | generic retrieval/query contract 유지; transport/auth는 runtime adapter로 선택 | parser/query/transport tests, current GCP target corpus-version-filtered k-NN smoke |
| `infra/terraform/envs/rag-runtime/` | OpenSearch Serverless collection/VPC endpoint/access policy와 ingestion runtime 생성 | **ARCHIVE** | GCP target의 active infrastructure가 아니라 AWS RAG deployment의 historical baseline/reference이다. | 미래 active AWS state를 전제하지 않고 보존하며 collection/index/network contract와 검증 패턴만 portable retrieval 설계에 재사용 | historical `terraform validate`; 기존 live collection/index/network 상태는 **UNKNOWN/TBD** |

### 6. Object storage

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `ObjectReader.java`, `ObjectWriter.java`, `ObjectRemover.java`, `ObjectReference.java`, `ObjectContent.java`, `ObjectMetadata.java` | object read/write/remove의 provider-neutral ports와 value objects | **KEEP** | application core와 cloud storage를 분리하는 재사용 가능한 경계이며 M6 partial-success compensation도 이 boundary를 사용한다. | upload/source/result services와 compensation이 provider-neutral storage contract에 의존 | reader/writer/remover contract tests 및 content-type/size/metadata validation |
| `backend/src/main/java/com/terraformers/modernization/storage/AwsS3ObjectReader.java`, `AwsS3ObjectWriter.java`, `StubObjectReader.java`, `StubObjectWriter.java` | 조건부 S3 adapter와 local stub | **MODIFY** | S3 구현은 AWS-specific이나 stub과 interface로 격리되어 있다. 삭제/대체 근거는 없다. | S3 adapter는 historical compatibility/reference로 보존하고 core model과 분리 | stub tests, `scripts/checks/s3-writer-production-validation.sh`, 실제 bucket IAM/read-write smoke |
| `UploadObjectStorageService.java`, `SourceObjectReaderService.java`, `AnalysisResultStorage.java` | upload/source access와 deterministic result metadata/compensation | **KEEP / B4 ACCOUNTABILITY IMPLEMENTED** | B4가 canonical key, pre-write intent, fenced finalization, exact `PENDING` accountability와 bounded cleanup recovery를 구현했다. | provider-neutral key/metadata/ownership 및 AnalysisJob cleanup 상태 유지 | B5 integrated object/finalization/cleanup matrix |

### 7. Authentication

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `JwtResourceServerSecurityConfig.java`, `JwtProviderTokenValidator.java`, `CognitoAccessTokenValidator.java` | generic stateless OAuth2 resource-server/JWT decoder와 provider-specific validation adapter 분리; local permit-all profile 유지 | **KEEP / ADAPTER-SPECIFIC** | M1에서 generic JWT security boundary와 provider token validator가 분리되었다. Cognito validation은 현재 adapter이며 concrete target IdP는 별도 결정 대상이다. | endpoint authorization/JWT core는 provider-neutral하게 유지하고 provider claim validation만 adapter로 교체 가능하게 유지 | issuer/JWK/provider-token negative tests, protected/public endpoint matrix, prod profile startup |
| `infra/terraform/envs/backend-stateful-dependencies/main.tf` | Cognito user pool/client와 MariaDB RDS를 함께 provisioning | **ARCHIVE** | GCP target의 active identity/database IaC가 아니라 AWS stateful deployment의 historical baseline이며 서로 다른 lifecycle도 한 stack에 있다. | 코드는 reference로 보존하고 identity/database lifecycle 분리 및 typed output 검증 원칙만 재사용 | historical Terraform validation; 실제 Cognito client/state는 **UNKNOWN/TBD** |

### 8. Frontend authentication / session

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Frontend session/navigation behavior | `frontend/src/auth/AuthSessionContext.js`, `frontend/src/auth/ProtectedRoute.js`, `frontend/src/utils/api.js`, `frontend/src/app/AppShell.js` | authenticated/guest/checking session state, protected routing, API 401 시 `terraformers:auth-expired` 전파와 session 해제 | **KEEP** | provider와 무관하게 필요한 UI/session behavior이며 실제 화면과 API flow가 의존한다. | session state machine, protected-route semantics, 401/auth-expiry contract를 provider-neutral frontend behavior로 유지 | `AuthSessionContext.test.js`, `api.test.js`, protected route와 expiry E2E |
| Cognito/Amplify browser adapter | `frontend/src/auth/authClient.js`, `frontend/src/auth/providers/cognitoAmplifyAuthClient.js`, related entry/confirmation UI | provider-neutral application-facing auth client 뒤의 Cognito/Amplify adapter | **KEEP / ADAPTER-SPECIFIC** | M1에서 session/navigation code가 provider-neutral `authClient`에 의존하도록 분리되었고 Cognito SDK/config는 provider adapter에 격리되어 있다. | session/application logic은 neutral client를 유지하고 concrete provider adapter는 별도 decision으로 교체 가능하게 유지 | auth-client/provider tests, sign-in/sign-up/token/refresh/expiry E2E |

### 9. MariaDB / Flyway

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/resources/db/migration/`, `backend/pom.xml`, `backend/src/main/resources/application-prod.yml` | Flyway versioned schema, MariaDB driver/dialect, production `validate`, migrations enabled | **KEEP** | users/projects/files/comments/analysis jobs의 현재 system of record이며 domain repository가 의존한다. | Flyway-only production schema evolution과 JPA validation 유지; 기존 migrations 불변 | `scripts/checks/flyway-migration-uniqueness.sh`, `scripts/checks/mariadb-schema-validation.sh`, `MariaDbRepositorySmokeTest` |
| `backend/src/main/resources/application-local.yml`, `backend/src/test/resources/application-test.yml` | H2 MySQL compatibility mode의 local/test persistence | **MODIFY** | 빠른 test baseline은 유용하지만 MariaDB 동작을 완전히 증명하지 않는다. | unit/slice test는 H2를 유지할 수 있으나 DB-specific 계약은 MariaDB smoke로 보완 | H2 suite와 MariaDB container/CI smoke 결과 비교; Testcontainers 채택 여부는 **UNKNOWN/TBD** |
| `infra/terraform/envs/backend-stateful-dependencies/` | MariaDB RDS instance, subnet/security group, managed master secret 및 datasource output | **ARCHIVE** | GCP target의 active database IaC가 아닌 AWS RDS historical baseline이며 DB/domain 계약과 구분해야 한다. | 코드는 reference로 보존하고 JDBC/Flyway runtime contract, state separation, backup validation pattern만 재사용 | historical plan validation; 기존 apply 상태와 TLS/backup/restore/failover 요구는 **UNKNOWN/TBD** |

### 10. RAG corpus / ingestion

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Versioned corpus | `corpus/terraformers-reference/v1/`, `corpus/terraformers-reference/v2/` | versioned manifests, JSONL documents, index schema, source manifest | **KEEP** | repository에 존재하는 재현 가능한 corpus 계약이다. | immutable version/checksum 규칙으로 curated corpus 보존 | `scripts/checks/rag-corpus-contract-verification.py`를 두 version에 실행; 내용 provenance/license 최신성은 **UNKNOWN/TBD** |
| Corpus build/curation/contract | `scripts/rag/build-corpus-v2.py`, `scripts/rag/curate-corpus-v2.py`, `scripts/checks/rag-corpus-contract-verification.py`, `tests/rag/test_build_corpus_v2.py`, `tests/rag/test_curate_corpus_v2.py` | source에서 v2 corpus를 구성·선별하고 manifest/checksum/document/index-schema 계약을 검증 | **KEEP** | corpus의 재현성·provenance·version contract는 cloud provider와 독립적으로 재사용 가능하다. | deterministic build/curation/contract validation 유지 | corpus unit tests와 v1/v2 contract validation; source license/최신성 검토 |
| Batch ingestion concept | `scripts/rag/ingest-corpus.py`, `tests/rag/test_ingest_corpus.py` | version/checksum guard, mapping 검증, idempotent document upsert, count 및 representative k-NN 확인을 수행하는 one-shot batch | **KEEP** | online backend와 분리된 승인형 batch ingestion lifecycle 자체는 portable하다. | provider-neutral ingestion orchestration/validation contract를 유지하고 cloud clients는 adapter로 분리 | offline ingestion contract tests, idempotency와 failure/retry/report validation |
| AWS-bound ingestion implementation | `scripts/rag/ingest-corpus.py`, `scripts/rag/requirements.txt`, `.github/workflows/rag-corpus-ingestion.yml`, `infra/terraform/envs/rag-runtime/buildspec-rag-ingestion.yml` | CodeBuild-only 실행, `boto3`, S3 package/receipt, `AWS4Auth`/`aoss`, Bedrock embedding 및 AWS OIDC dispatch | **MODIFY** | batch concept과 달리 executor, receipt store, transport authentication, embedding client가 AWS에 결합되어 있다. | portable batch contract 뒤의 historical AWS adapter로 보존하고 GCP target adapter의 구체 서비스/SDK는 근거 후 결정 | AWS regression evidence; provider-neutral fake contract; target 환경에서 package/checksum/receipt-equivalent, embedding dimension, k-NN validation |

### 11. Docker

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/Dockerfile`, `backend/.dockerignore` | Java 17 multi-stage backend image, pinned Terraform 1.8.5 checksum 설치, non-root runtime, actuator healthcheck | **KEEP** | Kubernetes가 소비하는 실제 deployable artifact이며 cloud-neutral container 경계이다. | reproducible non-root backend image를 유지; 포함된 Terraform CLI 필요성은 현 flow 기준 보존 | `docker build`, image user/healthcheck/CVE scan, Terraform draft execution smoke |
| repository root | Compose/frontend container file은 발견되지 않음 | **DEFER** | 없는 component를 근거 없이 NEW로 만들지 않는다. | 필요가 확인될 때만 결정 | local runtime 요구 수집; 현재는 **UNKNOWN/TBD** |

### 12. Kubernetes

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/kubernetes/base/`, `infra/kubernetes/overlays/local-stub/` | backend Deployment/Service/ConfigMap/ServiceAccount와 readiness/liveness, local stub Kustomize overlay | **KEEP** | cloud-neutral base와 local validation 경로가 존재한다. | base workload 계약과 local smoke 유지 | `kustomize build`, schema validation, `scripts/checks/kind-local-stub-smoke.sh` |
| `infra/kubernetes/overlays/aws-runtime-template/`, `infra/kubernetes/aws-runtime-origin/`, `infra/kubernetes/external-secrets/` | AWS runtime env/image/IRSA patch, ALB ingress/controller, External Secrets configuration | **ARCHIVE** | 현재 GCP deployment target의 active manifest가 아니라 과거 AWS runtime의 재현 가능한 baseline/reference이다. | 삭제하지 않고 historical AWS overlay로 보존; cloud-neutral base에서 재사용할 patch/secret-validation pattern만 추출 | historical render checks와 secret-key contract; active GCP overlay는 Confirmed NEW gap으로 별도 추적 |

### 13. Argo CD / GitOps

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/kubernetes/argocd/backend-application.yaml`, `infra/kubernetes/argocd/values-dev.yaml`, `infra/kubernetes/gitops/backend-runtime/` | AWS-era backend runtime을 가리키는 Argo CD Application/Kustomize patch; automated prune/self-heal pattern 포함 | **ARCHIVE** | 현재 GCP active target으로 확인되지 않았고 Application의 `repoURL`/`targetRevision`도 현재 repository/base와 일치하지 않는다. | historical GitOps baseline으로 보존하고 선언적 reconciliation, immutable revision, diff-before-sync 원칙만 차기 delivery 설계에 재사용 | historical clean render; live Argo ownership과 기존 target repository/branch는 **UNKNOWN/TBD** |

### 14. Terraform

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/terraform/bootstrap/aws-live-foundation/` | S3 state, GitHub OIDC plan/apply roles와 제한된 AWS apply permissions bootstrap | **ARCHIVE** | AWS 계정/ARN에 결합된 historical live control-plane baseline이며 새 GCP target의 active IaC가 아니다. | 코드는 삭제하지 않고 reference로 보존; bootstrap/application state separation, least privilege, OIDC 및 plan/apply separation 원칙을 재사용 | historical `fmt`/`validate`; 기존 account/state ownership은 **UNKNOWN/TBD** |
| `infra/terraform/envs/aws-runtime-network/`, `infra/terraform/envs/backend-runtime-dependencies/`, `infra/terraform/envs/backend-stateful-dependencies/`, `infra/terraform/envs/eks-runtime/`, `infra/terraform/envs/frontend-delivery/`, `infra/terraform/envs/rag-runtime/` | VPC, ECR/S3/SQS/Secrets, RDS/Cognito, EKS/IRSA/CloudWatch, CloudFront, OpenSearch/CodeBuild의 AWS live stacks | **ARCHIVE** | 새 프로젝트의 active target이 아니라 기존 AWS deployment를 설명하는 historical baseline/reference이다. | 미래 active AWS state를 전제하지 않고 코드와 state-boundary 지식을 보존; isolated state, typed outputs, plan review, drift validation pattern을 GCP IaC 설계에 재사용 | historical static validation; 실제 AWS apply/state/drift 상태는 **UNKNOWN/TBD** |
| `infra/terraform/runtime-contract/` | backend config/secret keys와 values를 산출하는 provider 경계 | **KEEP** | Kubernetes/application이 cloud resource 세부사항 대신 명시적 runtime contract를 소비하게 한다. | config/secret key contract의 단일 검증 지점 유지 | `scripts/checks/runtime-contract-verification.sh`와 rendered manifest 비교 |

### 15. GitHub Actions

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `.github/workflows/backend-maven-verification.yml`, `backend-local-verification.yml`, `frontend-ci.yml`, `terraform-static-verification.yml`, `kind-local-stub-smoke.yml` 및 `*-verification.yml` | backend/frontend tests와 다수 contract/static/local deployment checks | **KEEP** | 현재 code/infrastructure 계약을 자동 검증하는 핵심 안전망이다. | PR에서 deterministic CI와 reusable contract checks 유지 | GitHub Actions clean run과 required-check 설정 확인 |
| `.github/workflows/aws-*.yml`, `.github/workflows/backend-image-publish.yml`, `.github/workflows/frontend-delivery.yml`, `.github/workflows/rag-corpus-ingestion.yml`, `scripts/deploy/`, `scripts/teardown/` | OIDC 기반 AWS plan/apply/deploy/evidence/teardown과 guarded live operations | **ARCHIVE** | 기존 AWS live delivery의 historical baseline이며 GCP active delivery target이 아니다. | workflow를 삭제하지 않고 reference로 보존; environment approval gate, expected account, immutable SHA, plan/apply separation, evidence artifact, ordered teardown 및 post-action validation pattern을 provider-neutral delivery 원칙으로 재사용 | historical workflow/contract syntax checks; 기존 GitHub environment/secret/role의 live 상태는 **UNKNOWN/TBD** |

### 16. Observability

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisObservability.java`, `AnalysisLogCorrelation.java`, `backend/src/main/resources/application.yml`, `application-prod.yml` | Micrometer analysis/job/provider/retrieval metrics, MDC correlation, Actuator health/info/Prometheus, production CloudWatch export | **KEEP** | application-level signals은 provider export와 분리되어 재사용 가능하다. | metric names, correlation fields, health probes를 안정적인 application contract로 유지 | observability tests, `/actuator/health`와 `/actuator/prometheus` smoke, cardinality 검토 |
| `backend/src/main/java/com/terraformers/modernization/config/CloudWatchMetricsConfiguration.java`, `infra/terraform/envs/eks-runtime/main.tf` | CloudWatch registry namespace, EKS CloudWatch observability add-on/IRSA, dashboard와 backend/analysis alarms | **ARCHIVE** | AWS exporter/dashboard/alarm은 current GCP target의 active observability stack이 아니라 historical deployment baseline이다. | application metric contract와 분리하여 reference로 보존하고 dashboard/alarm validation pattern만 재사용 | historical Terraform validation; 기존 alarm state와 notification destination/on-call 연결은 **UNKNOWN/TBD** |

### 17. Tests

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/test/java/`, `backend/src/test/resources/` | Spring/unit/repository/controller/provider/storage/security/config tests와 MariaDB smoke | **KEEP** | 주요 backend business flow 및 adapter boundary에 실제 coverage가 있다. | core와 adapter 계약 회귀 suite 유지, MariaDB-specific 검증 강화 | `cd backend && mvn test`; coverage threshold는 **UNKNOWN/TBD** |
| `frontend/src/**/*.test.js`, `frontend/src/setupTests.js` | auth session/API, upload, project pages/tree/public UI component tests | **KEEP** | 현재 frontend flow에 대응하는 Jest/Testing Library suite이다. | authentication과 backend contract 중심 회귀 suite 유지 | `cd frontend && npm test -- --watchAll=false`; browser E2E suite는 발견되지 않아 **UNKNOWN/TBD** |
| `tests/rag/` | corpus build/curation/ingestion utility unit tests | **KEEP** | version/checksum/filter/index behavior를 검증하는 실제 ingestion 안전망이다. | offline deterministic tests와 별도의 승인된 live smoke 유지 | `python3 -m unittest discover -s tests/rag` |
| `scripts/checks/` | Flyway/MariaDB, Terraform, Kubernetes, runtime contracts, AWS packaging/deployment guard 검증 | **KEEP** | CI workflow가 재사용하는 executable contract tests이다. | 문서가 아니라 실행 가능한 검증 자산으로 유지 | 각 연결 workflow와 script exit status 확인; 전체를 묶는 단일 local command는 발견되지 않아 **UNKNOWN/TBD** |

### 18. Current gaps and reassessment items

이 inventory 작성 이후 M1과 M3에서 일부 gap이 닫혔다. Provider-neutral auth/session boundary와
runtime OpenSearch transport/auth boundary는 M1에서 구현되었고, fixed AI/RAG evaluation
dataset·stage-provenance contract·reusable runner는 M3-1~M3-3에서 구현되었다. 이들은 더 이상
NEW gap이 아니다.

아래 표는 현재 남아 있는 capability gap, 재평가 항목, 이미 구현되어 재사용 중인 foundation의 상태를
함께 기록한다. 구체 product/service/framework 선정은 별도 evidence와 decision gate 없이 자동 승인되지 않는다.

| Item | Current evidence / basis | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| Backend reliability decision depth | ADR-007; Case B B1/B2/B3/B4 evidence | **IMPLEMENTING / CASE B IN PROGRESS** | B1–B4가 durable state, fencing, recovery, retry, deterministic identity, fenced finalization과 cleanup accountability/recovery를 구현했다. B5 integrated evidence가 남아 있다. | B5 integrated closure | B5 integrated before/after evidence |
| GCP runtime/IaC | `infra/terraform/envs/gcp-target-runtime/`, `infra/kubernetes/overlays/gcp-target/`, Vertex/OpenSearch target adapters | **IMPLEMENTED / REUSED TARGET RUNTIME** | 동일 GKE Standard + Workload Identity + Vertex AI + in-cluster OpenSearch runtime이 M3 live baseline과 M4 targeted evaluation에 실제 사용되었고, latest accepted M4 closure evidence에서 target node pool을 `node_count=0`으로 반환했다. | 동일 target IaC/runtime을 approved later cases와 M9 closure까지 재사용; 별도 evaluation cloud 금지 | 필요 시 fresh quota/billing/model-access 확인 후 same-runtime activate→evidence→idle; 새 환경 생성 금지 |
| Batch-ingestion transport portability | `scripts/rag/ingest-corpus.py`, `tests/rag/` | **NEW** | runtime query transport/auth는 M1에서 provider-neutral `OpenSearchTransport` boundary로 분리되었지만 corpus ingestion은 여전히 boto3/S3 receipt/AWS4Auth/AOSS/Bedrock embedding에 결합되어 있다. | versioned corpus/index contract를 유지하면서 target runtime의 embedding/index/auth path로 재사용 가능한 ingestion implementation | deterministic corpus contract, target endpoint ingestion/query smoke, embedding dimension/version consistency |
| Observability depth for representative cases | `AnalysisObservability.java`, `AnalysisLogCorrelation.java`, stage telemetry from PR #88 | **PAUSED / SUPPORTING CASES** | metrics, job-correlation logs, bounded stage telemetry가 이미 존재한다. 독립적인 tracing stack 도입 자체는 목표가 아니며 M7은 reassessment 동안 paused다. | Case A/B의 실제 RCA에 필요한 최소 signal만 추가하고 별도 observability project로 확장하지 않음 | approved failure scenario에서 metric/log/stage correlation로 cause→result→recovery 설명 가능 여부 |

## 경계 요약

### KEEP 핵심 자산

1. `projectcore`, `project`, `identity`, `projectcomment`, `projecttree`의 domain/business flow와 JPA model.
2. AnalysisJob의 domain/status/ownership contract와 `AnalysisProvider`/`ReferenceRetriever`/`EmbeddingProvider`/object storage ports는 KEEP한다. MariaDB durable eligibility, lease/fencing, `next_attempt_at` retry scheduling이 active restart/durability contract이며 executor는 durable delivery source가 아닌 bounded local concurrency pool이다. B4 cross-resource safety는 구현되었고 B5 integrated closure가 남아 있다.
3. Flyway schema와 MariaDB system-of-record 계약.
4. versioned RAG corpus, ingestion utilities, backend/frontend/RAG tests와 executable checks.
5. cloud-neutral Docker image 및 Kubernetes base/local overlay, runtime contract.

### MODIFY / ARCHIVE 핵심 경계

1. Cognito/Amplify, Bedrock, S3, SQS, SigV4 OpenSearch 구현은 삭제 대상이 아니라 provider-specific adapter/historical compatibility 자산으로 유지하고, 이미 형성된 provider-neutral boundary를 보존한다.
2. AWS-bound batch ingestion처럼 아직 provider-specific 실행/transport가 직접 결합된 부분만 별도 **MODIFY** 대상으로 남긴다.
3. 기존 AWS Terraform live stacks, AWS Kubernetes overlay, Argo CD runtime, CloudWatch 및 AWS live deployment/teardown workflow는 **ARCHIVE** historical baseline/reference이며 새 프로젝트의 active target으로 유지한다고 가정하지 않는다.
4. historical delivery에서 확인된 approval gate, immutable SHA, expected-account check, state separation, plan/apply separation, ordered teardown 및 validation/evidence pattern은 provider-neutral 설계 원칙으로 재사용한다.

B4에서 compensation 실패 residue accountability는 AnalysisJob-owned intent bucket/key + `cleanup_status=PENDING` + bounded cleanup recovery로 결정·구현되었다.

### 확인하지 못한 사항 (UNKNOWN/TBD)

- 현재 AWS resources, Terraform state, Argo CD application, GitHub environments/secrets/required checks가 실제로 적용·동작 중인지 여부.
- Bedrock analysis/embedding model ID, vector dimension, OpenSearch endpoint/index와 corpus version의 실제 운영 값 및 상호 일치 여부.
- SQS progress event를 소비하는 component의 repository 내/외 존재와 운영 필요성.
- RDS/AWS historical backup/restore/failover 및 alert notification/on-call 정책의 현재 live relevance.
- 독립 board API의 사용 여부, browser E2E/coverage 기준, Docker Compose 또는 frontend container의 필요성.
- RabbitMQ, Transactional Outbox, LangGraph, 상시 Python worker, Keycloak, Redis, Kafka의 **최종 채택 여부**. 이들은 자동 배제 대상이 아니며 실제 문제를 직접 해결하는 경우 Case Decision Gate에서 비교한다.
