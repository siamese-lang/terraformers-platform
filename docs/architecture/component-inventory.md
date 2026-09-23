# Component inventory

## 범위와 판정 기준

- 기준 커밋: `6f86d077e38663b39171a044ba90d724bca628d3` (조사 시작 시 현재 `main`/`HEAD`).
- 이 문서는 해당 커밋에 존재하는 application code, infrastructure, workflow, test만 분류한다. `PROJECT_DIRECTION.md`와 기존 AWS 구현은 현행 자산으로 보존한다.
- 판정 의미: **KEEP**은 현재 책임과 계약을 재사용, **MODIFY**는 자산을 보존하되 경계나 구성의 변경 가능성을 열어 둠, **REPLACE**는 확인된 대체 대상, **NEW**는 확인된 신규 공백, **ARCHIVE**는 현행 흐름에서 분리할 자산, **DEFER**는 근거 또는 결정이 부족해 판단을 유보한다는 뜻이다.
- 논리/application architecture의 목표는 **cloud-provider-neutral**이다. 현재 deployment target은 **GCP**이지만, GCP migration 자체만을 프로젝트 목표로 삼지 않으며 기존 domain/business flow와 portable boundary의 재사용을 우선한다.
- 기존 AWS 구현은 삭제하지 않고 historical baseline/reference로 보존한다. 특히 RabbitMQ, LangGraph, 별도 상시 Python worker, Keycloak, Redis, Kafka는 repository에서 확정된 runtime evidence를 찾지 못했으므로 신규 도입 또는 교체 대상으로 지정하지 않는다.

## Component별 inventory

### 1. Backend domain — project / user / file / comment

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Project aggregate 및 ownership | `backend/src/main/java/com/terraformers/modernization/projectcore/` | `OwnedProjectEntity`, repository, ownership/access 검증, artifact 갱신을 묶는 핵심 domain/service 계층 | **KEEP** | 인증 사용자를 기준으로 project 접근과 수정 권한을 일관되게 적용하는 실제 business flow이다. | cloud adapter와 무관한 project ownership 및 artifact 계약을 현행 service 중심으로 유지 | repository/service test와 인증된 CRUD 통합 시험; soft-delete/ownership 회귀 확인 |
| Project API/metadata/draft | `backend/src/main/java/com/terraformers/modernization/project/` | 소유/공개 project 조회·삭제·visibility 변경, 공개 호환 endpoint, `main.tf` 조회·수정 | **KEEP** | frontend가 소비하는 현재 API이며 domain service를 재사용한다. | 기존 endpoint와 DTO 호환성을 유지하고 infrastructure 세부사항을 노출하지 않음 | controller test 및 frontend API test; 공개/비공개 접근 matrix |
| Internal user/profile semantics | `backend/src/main/java/com/terraformers/modernization/identity/UserEntity.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserRole.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserStatus.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserProfileController.java` | 내부 `user_id`, profile/display name, role, status 및 profile API를 관리 | **KEEP** | project ownership/comment attribution이 의존하는 provider-independent business identity이다. | 내부 user ID와 profile/role/status semantics는 외부 IdP와 독립적으로 유지 | profile/domain tests와 project/comment ownership 통합 시험 |
| Cognito-bound external identity mapping | `backend/src/main/java/com/terraformers/modernization/identity/UserEntity.java`, `backend/src/main/java/com/terraformers/modernization/identity/UserRepository.java`, `backend/src/main/java/com/terraformers/modernization/identity/AuthenticatedUserService.java`, `backend/src/main/resources/db/migration/V20260714_001__baseline_backend_schema.sql` | `cognito_sub`/`cognitoSub`, `findByCognitoSub`, Cognito JWT claim·username fallback 및 Cognito-specific 오류 메시지로 외부 주체를 내부 user에 연결 | **MODIFY** | 외부 identity key와 claim mapping이 Cognito 명칭에 고정되어 있어 provider-neutral architecture 경계가 아니다. | schema/domain lookup은 provider-neutral external identity subject(필요 시 issuer 포함) 계약을 사용하고 provider claim 해석과 메시지는 auth adapter에 둠 | migration 호환성, subject uniqueness, 기존 Cognito 사용자 연결 보존, provider-neutral claim-mapper contract 및 `AuthenticatedUserServiceTest` |
| Project file | `backend/src/main/java/com/terraformers/modernization/projectcore/ProjectFileEntity.java`, `backend/src/main/java/com/terraformers/modernization/projectcore/ProjectFileRepository.java`, `backend/src/main/java/com/terraformers/modernization/projecttree/` | project file metadata, tree projection, source/generated artifact 연결 | **KEEP** | upload→file→analysis→generated Terraform 흐름의 영속 domain 자산이다. | object bytes와 object-store locator는 분리하되 file metadata와 tree API 계약 유지 | tree/controller tests, upload-to-analysis 통합 시험, object locator 무결성 확인 |
| Comment/board | `backend/src/main/java/com/terraformers/modernization/collaboration/`, `backend/src/main/java/com/terraformers/modernization/projectcomment/` | board/comment JPA 모델과 project comment service; 신규 및 legacy-compatible endpoint 제공 | **KEEP** | 실제 comment flow와 호환 endpoint가 구현·시험되어 있다. | comment ownership/visibility 규칙과 호환 계약을 보존; 사용되지 않는 board 기능 확대는 하지 않음 | `ProjectCommentControllerTest`; author/project 접근과 parent comment 관계 통합 시험. `BoardEntity`의 독립 API 사용 여부는 **UNKNOWN/TBD** |

### 2. Analysis job lifecycle

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/` | upload 접수, DB job 생성/조회, transaction commit 후 bounded Java executor scheduling, `PENDING`→`RUNNING`→`SUCCEEDED`/`FAILED`, provider 호출, result object/file 저장, progress 발행, failure 분류 | **KEEP** | 현재 domain/business flow가 완결되어 있고 `EXTERNAL_PYTHON_LEGACY` 신규 실행을 명시적으로 거부한다. 별도 worker/queue orchestration 도입 근거가 없다. | integrated Java lifecycle, 상태 전이, ownership 및 provider/storage port를 유지; 실행 내구성/재시작 요구는 별도 근거가 생길 때 결정 | analysis unit/integration suite; 동시 요청, executor rejection, process restart 시 in-flight job 처리 정책은 **UNKNOWN/TBD**이므로 장애 주입 후 결정 |
| `backend/src/main/java/com/terraformers/modernization/analysis/ProgressPublisher.java`, `LoggingProgressPublisher.java`, `SqsProgressPublisher.java` | progress port와 local logging/AWS SQS adapter | **MODIFY** | core는 port에 의존하지만 SQS는 AWS-specific adapter이다. 삭제나 broker 교체 근거는 없다. | port/event 계약은 유지하고 SQS 구성은 배포 adapter로 격리; 기본은 logging publisher | publisher contract test와 enabled/disabled profile startup; 실제 SQS delivery/consumer 존재 여부는 **UNKNOWN/TBD** |

### 3. AI / RAG provider

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisProvider.java`, `backend/src/main/java/com/terraformers/modernization/analysis/StubAnalysisProvider.java` | analysis core port와 local/test fallback | **KEEP** | provider-neutral seam이 이미 application core와 vendor SDK를 분리한다. | orchestration은 `AnalysisProvider`에만 의존하고 local deterministic path 유지 | provider contract 및 stub tests |
| `backend/src/main/java/com/terraformers/modernization/analysis/bedrock/` | Bedrock multimodal request, prompt/response parsing, validation 및 AWS client configuration | **MODIFY** | repository에 존재하는 AI 구현이지만 AWS-specific integration이다. provider port는 재사용하고 adapter는 분리 상태를 유지해야 한다. | Bedrock adapter는 historical compatibility/reference로 보존하고 provider-neutral port에 대한 GCP target adapter는 근거 후 결정 | Bedrock unit tests와 승인된 live invocation; model ID, region, quota, 실제 운영 enablement는 **UNKNOWN/TBD** |
| `backend/src/main/java/com/terraformers/modernization/reference/ReferenceRetriever.java`, `RetrievalModeReferenceRetriever.java`, `RetrievalQueryTextBuilder.java`, `ArchitectureRetrievalFacts.java` | optional reference retrieval port, disabled/OpenSearch mode 선택, 구조화된 검색 질의 구성 | **KEEP** | RAG 사용 여부와 구현을 core 계약에서 분리하고 disabled mode를 제공한다. | analysis flow의 optional provider-neutral retrieval boundary 유지 | disabled/enabled routing test와 retrieved context가 prompt에 반영되는 통합 시험 |

### 4. Embedding

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/reference/EmbeddingProvider.java` | query embedding port | **KEEP** | vendor-independent application boundary이다. | retrieval은 interface에 의존 | dimension/error contract tests |
| `backend/src/main/java/com/terraformers/modernization/reference/BedrockEmbeddingProvider.java` | Bedrock Titan-style JSON request/response로 query vector 생성 및 configured dimension 검사 | **MODIFY** | AWS-specific adapter이며 corpus ingestion의 embedding model/dimension과 정확히 맞아야 한다. | adapter를 보존하되 model ID와 vector dimension을 runtime/corpus contract로 검증 | `BedrockEmbeddingProviderTest`; ingestion/query 동일 model과 실제 vector dimension 검증. 운영 model 값은 **UNKNOWN/TBD** |

### 5. OpenSearch retrieval

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/reference/opensearch/` | k-NN query 생성, SigV4 HTTP 호출, endpoint 검증, response parsing | **MODIFY** | `ReferenceRetriever` 뒤에 있으나 SigV4와 `aoss`가 AWS-specific이다. 현 구현 삭제/교체 근거는 없다. | generic retrieval/query 계약과 AWS OpenSearch Serverless transport를 명확히 분리한 현행 형태 유지 | parser/query/client tests, private endpoint에서 corpus-version-filtered k-NN smoke; index/endpoint 실제 적용 상태는 **UNKNOWN/TBD** |
| `infra/terraform/envs/rag-runtime/` | OpenSearch Serverless collection/VPC endpoint/access policy와 ingestion runtime 생성 | **ARCHIVE** | GCP target의 active infrastructure가 아니라 AWS RAG deployment의 historical baseline/reference이다. | 미래 active AWS state를 전제하지 않고 보존하며 collection/index/network contract와 검증 패턴만 portable retrieval 설계에 재사용 | historical `terraform validate`; 기존 live collection/index/network 상태는 **UNKNOWN/TBD** |

### 6. Object storage

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/storage/ObjectReader.java`, `ObjectWriter.java`, `ObjectReference.java`, `ObjectContent.java`, `ObjectMetadata.java` | object read/write의 provider-neutral ports와 value objects | **KEEP** | application core와 cloud storage를 분리하는 재사용 가능한 경계이다. | upload/source/result services가 이 계약에만 의존 | reader/writer contract tests 및 content-type/size/metadata validation |
| `backend/src/main/java/com/terraformers/modernization/storage/AwsS3ObjectReader.java`, `AwsS3ObjectWriter.java`, `StubObjectReader.java`, `StubObjectWriter.java` | 조건부 S3 adapter와 local stub | **MODIFY** | S3 구현은 AWS-specific이나 stub과 interface로 격리되어 있다. 삭제/대체 근거는 없다. | S3 adapter는 historical compatibility/reference로 보존하고 core model과 분리 | stub tests, `scripts/checks/s3-writer-production-validation.sh`, 실제 bucket IAM/read-write smoke |
| `backend/src/main/java/com/terraformers/modernization/storage/UploadObjectStorageService.java`, `SourceObjectReaderService.java`, `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisResultStorage.java` | upload key 생성, source read authorization, result JSON/object 및 project file metadata 저장 | **KEEP** | object port를 사용하는 application flow이다. | storage provider 변경과 무관하게 key/metadata/ownership 계약 유지 | upload/read/result tests; compensating behavior와 orphan cleanup 정책은 **UNKNOWN/TBD** |

### 7. Authentication

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/security/CognitoJwtSecurityConfig.java` | production의 stateless OAuth2 resource server, Cognito issuer/JWKS, `token_use=access`와 `client_id` 검증, endpoint authorization; local permit-all profile | **MODIFY** | authorization contract은 유지해야 하지만 Cognito/JWKS claim 검증은 AWS-specific security adapter이다. Keycloak 전환 근거는 없다. | 내부 identity/authorization과 provider-specific decoder를 분리하고 Cognito 구현은 historical compatibility/reference로 보존; GCP target IdP는 TBD | JWT issuer/client/token-use negative tests, protected/public endpoint matrix, prod profile startup. role-level authorization 요구는 **UNKNOWN/TBD** |
| `infra/terraform/envs/backend-stateful-dependencies/main.tf` | Cognito user pool/client와 MariaDB RDS를 함께 provisioning | **ARCHIVE** | GCP target의 active identity/database IaC가 아니라 AWS stateful deployment의 historical baseline이며 서로 다른 lifecycle도 한 stack에 있다. | 코드는 reference로 보존하고 identity/database lifecycle 분리 및 typed output 검증 원칙만 재사용 | historical Terraform validation; 실제 Cognito client/state는 **UNKNOWN/TBD** |

### 8. Frontend authentication / session

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Frontend session/navigation behavior | `frontend/src/auth/AuthSessionContext.js`, `frontend/src/auth/ProtectedRoute.js`, `frontend/src/utils/api.js`, `frontend/src/app/AppShell.js` | authenticated/guest/checking session state, protected routing, API 401 시 `terraformers:auth-expired` 전파와 session 해제 | **KEEP** | provider와 무관하게 필요한 UI/session behavior이며 실제 화면과 API flow가 의존한다. | session state machine, protected-route semantics, 401/auth-expiry contract를 provider-neutral frontend behavior로 유지 | `AuthSessionContext.test.js`, `api.test.js`, protected route와 expiry E2E |
| Cognito/Amplify browser adapter | `frontend/src/awsConfig.js`, `frontend/src/index.js`, `frontend/src/components/EntryPage.js`, `frontend/src/components/ConfirmSignUpPage.js`, `frontend/src/auth/AuthSessionContext.js`, `frontend/src/utils/api.js` | `aws-amplify/auth` 기반 Cognito sign-in/sign-up/confirmation/sign-out/current-user/token acquisition과 Cognito browser config | **MODIFY** | 인증 UX와 session behavior는 재사용 가능하지만 SDK, config keys, token acquisition 및 메시지가 Cognito에 결합되어 있다. | provider-neutral auth client interface 뒤에 Cognito adapter를 historical compatibility 구현으로 두고 GCP target의 구체 provider/SDK는 별도 결정 | provider-neutral auth-client contract tests, Cognito regression tests, 신규 provider의 sign-in/sign-up/token/refresh/expiry E2E; refresh 정책은 **UNKNOWN/TBD** |

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

### 18. Confirmed NEW gaps

이 inventory 작성 이후 M1과 M3에서 일부 gap이 닫혔다. Provider-neutral auth/session boundary와
runtime OpenSearch transport/auth boundary는 M1에서 구현되었고, fixed AI/RAG evaluation
dataset·stage-provenance contract·reusable runner는 M3-1~M3-3에서 구현되었다. 이들은 더 이상
NEW gap이 아니다.

아래 표는 현재도 남아 있는 capability gap만 기록한다. 구체 product/service/framework
선정은 별도 evidence와 decision gate 없이 자동 승인되지 않는다.

| Gap | Absence evidence checked | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| Backend reliability/failure-injection experiment harness | `backend/src/test/`, `scripts/checks/`, `.github/workflows/` | **NEW** | exception/rejection unit tests는 있으나 timeout, dependency outage, restart/concurrency를 주입·관찰하는 experiment harness는 없다. | 기존 analysis lifecycle을 대상으로 재현 가능한 failure scenarios와 recovery/result report 제공; 도구는 **TBD** | deterministic scenario execution, state/object invariants, repeatable failure report |
| GCP runtime/IaC | `infra/terraform/`, `infra/kubernetes/`, `.github/workflows/`, `scripts/deploy/` | **NEW** | Google/GCP provider, GCP runtime resources, GCP deployment workflow 또는 active GCP overlay를 찾지 못했다. | 현재 GCP deployment target을 위한 최소 runtime/IaC와 delivery validation; 구체 managed services는 **TBD** | format/validate/plan, isolated state, least-privilege identity, deploy/smoke/rollback evidence |
| Batch-ingestion transport portability | `scripts/rag/ingest-corpus.py`, `tests/rag/` | **NEW** | runtime query transport/auth는 M1에서 provider-neutral `OpenSearchTransport` boundary로 분리되었지만 corpus ingestion은 여전히 boto3/S3 receipt/AWS4Auth/AOSS/Bedrock embedding에 결합되어 있다. | versioned corpus/index contract를 유지하면서 target runtime의 embedding/index/auth path로 재사용 가능한 ingestion implementation | deterministic corpus contract, target endpoint ingestion/query smoke, embedding dimension/version consistency |
| Trace-level observability | `backend/pom.xml`, `backend/src/main/resources/application.yml`, `infra/kubernetes/gitops/backend-runtime/backend-deployment-patch.yaml`, `infra/terraform/envs/eks-runtime/main.tf` | **NEW** | trace/span log placeholders, OpenTelemetry injection annotation, X-Ray permission은 있으나 repository-owned tracing dependency/configuration, exporter pipeline 및 end-to-end trace validation은 없다. | request→analysis→provider/storage 구간의 trace context propagation/export와 검증 가능한 trace evidence; SDK/backend는 **TBD** | trace ID propagation, async executor context, exporter failure behavior 및 end-to-end trace query |

## 경계 요약

### KEEP 핵심 자산

1. `projectcore`, `project`, `identity`, `projectcomment`, `projecttree`의 domain/business flow와 JPA model.
2. integrated Java analysis job lifecycle, `AnalysisProvider`/`ReferenceRetriever`/`EmbeddingProvider`/object storage ports.
3. Flyway schema와 MariaDB system-of-record 계약.
4. versioned RAG corpus, ingestion utilities, backend/frontend/RAG tests와 executable checks.
5. cloud-neutral Docker image 및 Kubernetes base/local overlay, runtime contract.

### MODIFY / ARCHIVE 핵심 경계

1. Cognito, Bedrock, S3, SQS, SigV4 OpenSearch adapter는 삭제 대상이 아니라 AWS-specific integration으로 분명히 유지·격리한다.
2. Cognito/Amplify identity binding, Bedrock/S3/SQS/SigV4 adapter와 AWS batch-ingestion binding은 provider-neutral port/behavior에서 분리한다.
3. 기존 AWS Terraform live stacks, AWS Kubernetes overlay, Argo CD runtime, CloudWatch 및 AWS live deployment/teardown workflow는 **ARCHIVE** historical baseline/reference이며 새 프로젝트의 active target으로 유지한다고 가정하지 않는다.
4. historical delivery에서 확인된 approval gate, immutable SHA, expected-account check, state separation, plan/apply separation, ordered teardown 및 validation/evidence pattern은 provider-neutral 설계 원칙으로 재사용한다.

### 확인하지 못한 사항 (UNKNOWN/TBD)

- 현재 AWS resources, Terraform state, Argo CD application, GitHub environments/secrets/required checks가 실제로 적용·동작 중인지 여부.
- Bedrock analysis/embedding model ID, vector dimension, OpenSearch endpoint/index와 corpus version의 실제 운영 값 및 상호 일치 여부.
- SQS progress event를 소비하는 component의 repository 내/외 존재와 운영 필요성.
- process restart 시 in-flight analysis job 복구, orphan object 정리, RDS backup/restore/failover, alert notification/on-call 정책.
- 독립 board API의 사용 여부, browser E2E/coverage 기준, Docker Compose 또는 frontend container의 필요성.
- RabbitMQ, LangGraph, 상시 Python worker, Keycloak, Redis, Kafka의 채택 근거. 따라서 모두 **DEFER**이며 **NEW** 또는 **REPLACE** 판정이 아니다.
