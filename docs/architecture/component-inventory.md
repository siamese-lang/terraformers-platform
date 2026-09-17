# Component inventory

## 범위와 판정 기준

- 기준 커밋: `6f86d077e38663b39171a044ba90d724bca628d3` (조사 시작 시 현재 `main`/`HEAD`).
- 이 문서는 해당 커밋에 존재하는 application code, infrastructure, workflow, test만 분류한다. `PROJECT_DIRECTION.md`와 기존 AWS 구현은 현행 자산으로 보존한다.
- 판정 의미: **KEEP**은 현재 책임과 계약을 재사용, **MODIFY**는 자산을 보존하되 경계나 구성의 변경 가능성을 열어 둠, **REPLACE**는 확인된 대체 대상, **NEW**는 확인된 신규 공백, **ARCHIVE**는 현행 흐름에서 분리할 자산, **DEFER**는 근거 또는 결정이 부족해 판단을 유보한다는 뜻이다.
- 목표 상태는 GCP 이전을 전제하지 않는다. 특히 RabbitMQ, LangGraph, 별도 Python worker, Keycloak, Redis, Kafka는 repository에서 확정된 runtime evidence를 찾지 못했으므로 신규 도입 또는 교체 대상으로 지정하지 않는다.

## Component별 inventory

### 1. Backend domain — project / user / file / comment

| Component | Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|---|
| Project aggregate 및 ownership | `backend/src/main/java/com/terraformers/modernization/projectcore/` | `OwnedProjectEntity`, repository, ownership/access 검증, artifact 갱신을 묶는 핵심 domain/service 계층 | **KEEP** | 인증 사용자를 기준으로 project 접근과 수정 권한을 일관되게 적용하는 실제 business flow이다. | cloud adapter와 무관한 project ownership 및 artifact 계약을 현행 service 중심으로 유지 | repository/service test와 인증된 CRUD 통합 시험; soft-delete/ownership 회귀 확인 |
| Project API/metadata/draft | `backend/src/main/java/com/terraformers/modernization/project/` | 소유/공개 project 조회·삭제·visibility 변경, 공개 호환 endpoint, `main.tf` 조회·수정 | **KEEP** | frontend가 소비하는 현재 API이며 domain service를 재사용한다. | 기존 endpoint와 DTO 호환성을 유지하고 infrastructure 세부사항을 노출하지 않음 | controller test 및 frontend API test; 공개/비공개 접근 matrix |
| User identity/profile | `backend/src/main/java/com/terraformers/modernization/identity/` | JWT subject/email을 `UserEntity`로 동기화하고 display name, role, status를 관리 | **KEEP** | provider claim을 내부 user model로 변환하는 business 경계가 이미 존재한다. | 내부 user/entity 계약은 유지하고 외부 IdP claim 해석은 security adapter에 한정 | `AuthenticatedUserServiceTest`; 신규/기존 사용자와 display-name 동기화 통합 시험 |
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
| `backend/src/main/java/com/terraformers/modernization/analysis/bedrock/` | Bedrock multimodal request, prompt/response parsing, validation 및 AWS client configuration | **MODIFY** | 현재 production AI 구현이지만 AWS-specific integration이다. provider port는 재사용하고 adapter는 분리 상태를 유지해야 한다. | Bedrock adapter를 현행 선택지로 보존; 다른 cloud/provider로의 교체는 요구가 생길 때 별도 결정 | Bedrock unit tests와 승인된 live invocation; model ID, region, quota, 실제 운영 enablement는 **UNKNOWN/TBD** |
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
| `infra/terraform/envs/rag-runtime/` | OpenSearch Serverless collection/VPC endpoint/access policy와 ingestion runtime 생성 | **MODIFY** | 현재 AWS 배포 구현이며 application core가 아니다. | 독립 infrastructure stack으로 보존하고 application에는 endpoint/index 계약만 전달 | `terraform validate/plan`, live collection/index mapping과 network access 확인 |

### 6. Object storage

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/storage/ObjectReader.java`, `ObjectWriter.java`, `ObjectReference.java`, `ObjectContent.java`, `ObjectMetadata.java` | object read/write의 provider-neutral ports와 value objects | **KEEP** | application core와 cloud storage를 분리하는 재사용 가능한 경계이다. | upload/source/result services가 이 계약에만 의존 | reader/writer contract tests 및 content-type/size/metadata validation |
| `backend/src/main/java/com/terraformers/modernization/storage/AwsS3ObjectReader.java`, `AwsS3ObjectWriter.java`, `StubObjectReader.java`, `StubObjectWriter.java` | 조건부 S3 adapter와 local stub | **MODIFY** | S3 구현은 AWS-specific이나 stub과 interface로 격리되어 있다. 삭제/대체 근거는 없다. | S3 adapter는 deployment 선택지로 유지하고 core model과 분리 | stub tests, `scripts/checks/s3-writer-production-validation.sh`, 실제 bucket IAM/read-write smoke |
| `backend/src/main/java/com/terraformers/modernization/storage/UploadObjectStorageService.java`, `SourceObjectReaderService.java`, `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisResultStorage.java` | upload key 생성, source read authorization, result JSON/object 및 project file metadata 저장 | **KEEP** | object port를 사용하는 application flow이다. | storage provider 변경과 무관하게 key/metadata/ownership 계약 유지 | upload/read/result tests; compensating behavior와 orphan cleanup 정책은 **UNKNOWN/TBD** |

### 7. Authentication

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/security/CognitoJwtSecurityConfig.java` | production의 stateless OAuth2 resource server, Cognito issuer/JWKS, `token_use=access`와 `client_id` 검증, endpoint authorization; local permit-all profile | **MODIFY** | authorization contract은 유지해야 하지만 Cognito/JWKS claim 검증은 AWS-specific security adapter이다. Keycloak 전환 근거는 없다. | 내부 identity/authorization과 Cognito decoder 구성을 분리한 현행 경계 유지; provider 변경은 별도 승인 대상 | JWT issuer/client/token-use negative tests, protected/public endpoint matrix, prod profile startup. role-level authorization 요구는 **UNKNOWN/TBD** |
| `infra/terraform/envs/backend-stateful-dependencies/main.tf` | Cognito user pool/client와 MariaDB RDS를 함께 provisioning | **MODIFY** | 실제 AWS stateful stack이나 서로 다른 lifecycle의 자원이 한 stack에 있다. 지금 분해 또는 대체할 근거는 없다. | 현 state를 보존하면서 output/runtime contract를 검증; lifecycle 분리 필요성은 추후 판단 | Terraform plan/state inventory와 실제 Cognito client settings 확인 |

### 8. Frontend authentication / session

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `frontend/src/auth/AuthSessionContext.js`, `frontend/src/auth/ProtectedRoute.js`, `frontend/src/components/EntryPage.js`, `frontend/src/components/ConfirmSignUpPage.js`, `frontend/src/awsConfig.js`, `frontend/src/utils/api.js` | Amplify/Cognito sign-in·sign-up·confirmation·sign-out, current-user session context, protected routing, access token 첨부와 401 expiry event | **KEEP** | 실제 UI/session/API flow가 구현되어 있으며 backend Cognito contract와 대응한다. | session state와 API auth-expiry behavior를 유지; AWS config는 frontend adapter/config에 국한 | `AuthSessionContext.test.js`, `api.test.js`, protected route와 token refresh/expiry E2E. refresh 정책의 운영 검증은 **UNKNOWN/TBD** |

### 9. MariaDB / Flyway

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/resources/db/migration/`, `backend/pom.xml`, `backend/src/main/resources/application-prod.yml` | Flyway versioned schema, MariaDB driver/dialect, production `validate`, migrations enabled | **KEEP** | users/projects/files/comments/analysis jobs의 현재 system of record이며 domain repository가 의존한다. | Flyway-only production schema evolution과 JPA validation 유지; 기존 migrations 불변 | `scripts/checks/flyway-migration-uniqueness.sh`, `scripts/checks/mariadb-schema-validation.sh`, `MariaDbRepositorySmokeTest` |
| `backend/src/main/resources/application-local.yml`, `backend/src/test/resources/application-test.yml` | H2 MySQL compatibility mode의 local/test persistence | **MODIFY** | 빠른 test baseline은 유용하지만 MariaDB 동작을 완전히 증명하지 않는다. | unit/slice test는 H2를 유지할 수 있으나 DB-specific 계약은 MariaDB smoke로 보완 | H2 suite와 MariaDB container/CI smoke 결과 비교; Testcontainers 채택 여부는 **UNKNOWN/TBD** |
| `infra/terraform/envs/backend-stateful-dependencies/` | MariaDB RDS instance, subnet/security group, managed master secret 및 datasource output | **MODIFY** | AWS-specific deployment adapter이며 DB/domain 계약과 구분해야 한다. | RDS 현행 배포를 보존하고 JDBC/Flyway runtime contract로 application과 연결 | plan/apply evidence, TLS/backup/restore/failover 요구는 **UNKNOWN/TBD** |

### 10. RAG corpus / ingestion

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `corpus/terraformers-reference/v1/`, `corpus/terraformers-reference/v2/` | versioned manifests, JSONL documents, index schema, source manifest | **KEEP** | repository에 존재하는 재현 가능한 corpus 계약이다. | immutable version/checksum 규칙으로 curated corpus 보존 | `scripts/checks/rag-corpus-contract-verification.py`를 두 version에 실행; 내용 provenance/license 최신성은 **UNKNOWN/TBD** |
| `scripts/rag/build-corpus-v2.py`, `curate-corpus-v2.py`, `ingest-corpus.py`, `scripts/rag/requirements.txt` | source에서 v2 구성/선별, Bedrock embedding, OpenSearch index mapping/upsert/count/k-NN 확인을 수행하는 ingestion tooling | **KEEP** | 별도 상시 Python worker가 아니라 명시적인 build/ingestion utility로 실제 사용된다. | 승인된 batch ingestion utility로 유지하고 backend online lifecycle과 분리 | `tests/rag/`; package checksum, idempotency, dimension 및 representative k-NN live validation |
| `.github/workflows/rag-corpus-ingestion.yml`, `infra/terraform/envs/rag-runtime/buildspec-rag-ingestion.yml` | dry-run package와 승인된 OIDC→S3→private CodeBuild ingestion 실행 | **MODIFY** | workflow/CodeBuild는 AWS-specific delivery이며 corpus contract 자체와 분리된다. | corpus artifact/receipt 계약은 유지하고 executor는 배포 concern으로 취급 | workflow dry-run, expected SHA/account/approval guards, CodeBuild receipt와 document count 확인 |

### 11. Docker

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/Dockerfile`, `backend/.dockerignore` | Java 17 multi-stage backend image, pinned Terraform 1.8.5 checksum 설치, non-root runtime, actuator healthcheck | **KEEP** | Kubernetes가 소비하는 실제 deployable artifact이며 cloud-neutral container 경계이다. | reproducible non-root backend image를 유지; 포함된 Terraform CLI 필요성은 현 flow 기준 보존 | `docker build`, image user/healthcheck/CVE scan, Terraform draft execution smoke |
| repository root | Compose/frontend container file은 발견되지 않음 | **DEFER** | 없는 component를 근거 없이 NEW로 만들지 않는다. | 필요가 확인될 때만 결정 | local runtime 요구 수집; 현재는 **UNKNOWN/TBD** |

### 12. Kubernetes

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/kubernetes/base/`, `infra/kubernetes/overlays/local-stub/` | backend Deployment/Service/ConfigMap/ServiceAccount와 readiness/liveness, local stub Kustomize overlay | **KEEP** | cloud-neutral base와 local validation 경로가 존재한다. | base workload 계약과 local smoke 유지 | `kustomize build`, schema validation, `scripts/checks/kind-local-stub-smoke.sh` |
| `infra/kubernetes/overlays/aws-runtime-template/`, `infra/kubernetes/aws-runtime-origin/`, `infra/kubernetes/external-secrets/` | AWS runtime env/image/IRSA patch, ALB ingress/controller, External Secrets configuration | **MODIFY** | 명확한 AWS-specific integration이며 base와 분리되어 있다. | AWS overlay를 삭제하지 않고 provider-specific layer로 유지 | render checks, secret-key contract, EKS/ALB rollout smoke |

### 13. Argo CD / GitOps

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/kubernetes/argocd/backend-application.yaml`, `infra/kubernetes/argocd/values-dev.yaml`, `infra/kubernetes/gitops/backend-runtime/` | Argo CD Application과 backend runtime Kustomize patch; automated prune/self-heal | **MODIFY** | GitOps 자산은 실제 존재하지만 Application의 `repoURL`이 `Terraformers-modernization.git`, `targetRevision`이 `agent/rdb-domain-realignment`로 현재 repository/base와 일치한다고 확인할 수 없다. | repository URL/revision/image promotion ownership을 확인한 뒤 선언적 sync 계약 유지 | `argocd app diff`, clean checkout render, target repository/branch 및 live Argo ownership은 **UNKNOWN/TBD** |

### 14. Terraform

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `infra/terraform/bootstrap/aws-live-foundation/` | S3 state, GitHub OIDC plan/apply roles와 제한된 AWS apply permissions bootstrap | **MODIFY** | AWS-specific control plane이며 계정/리소스 ARN도 포함한다. 삭제하지 않되 application core와 구분한다. | 별도 bootstrap state/least-privilege boundary 유지 | fmt/init/validate/plan, IAM policy simulation 및 현재 account/state ownership 확인 |
| `infra/terraform/envs/aws-runtime-network/`, `backend-runtime-dependencies/`, `backend-stateful-dependencies/`, `eks-runtime/`, `frontend-delivery/`, `rag-runtime/` | VPC, ECR/S3/SQS/Secrets, RDS/Cognito, EKS/IRSA/observability, CloudFront/frontend, OpenSearch/CodeBuild RAG stacks | **MODIFY** | 현재 AWS infrastructure implementation이다. GCP migration은 본 inventory의 목표가 아니며 교체 근거가 없다. | stack별 state와 output/runtime contract를 유지하며 AWS-specific concern으로 취급 | `scripts/checks/terraform-static-verification.sh`, 각 stack plan, state/import/drift 확인; 실제 apply 상태는 **UNKNOWN/TBD** |
| `infra/terraform/runtime-contract/` | backend config/secret keys와 values를 산출하는 provider 경계 | **KEEP** | Kubernetes/application이 cloud resource 세부사항 대신 명시적 runtime contract를 소비하게 한다. | config/secret key contract의 단일 검증 지점 유지 | `scripts/checks/runtime-contract-verification.sh`와 rendered manifest 비교 |

### 15. GitHub Actions

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `.github/workflows/backend-maven-verification.yml`, `backend-local-verification.yml`, `frontend-ci.yml`, `terraform-static-verification.yml`, `kind-local-stub-smoke.yml` 및 `*-verification.yml` | backend/frontend tests와 다수 contract/static/local deployment checks | **KEEP** | 현재 code/infrastructure 계약을 자동 검증하는 핵심 안전망이다. | PR에서 deterministic CI와 reusable contract checks 유지 | GitHub Actions clean run과 required-check 설정 확인 |
| `.github/workflows/aws-*.yml`, `backend-image-publish.yml`, `frontend-delivery.yml`, `rag-corpus-ingestion.yml`, 관련 `scripts/deploy/`, `scripts/teardown/` | OIDC 기반 AWS plan/apply/deploy/evidence/teardown과 guarded live operations | **MODIFY** | AWS-specific delivery automation이며 승인, SHA/account 검증 등 보존 가치가 있다. application core가 아니며 대체 근거도 없다. | environment approval과 immutable SHA를 유지한 provider-specific delivery plane | workflow syntax/contract checks, GitHub environment/secret/role 실제 설정은 **UNKNOWN/TBD**, 승인된 dry-run/live evidence 검토 |

### 16. Observability

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisObservability.java`, `AnalysisLogCorrelation.java`, `backend/src/main/resources/application.yml`, `application-prod.yml` | Micrometer analysis/job/provider/retrieval metrics, MDC correlation, Actuator health/info/Prometheus, production CloudWatch export | **KEEP** | application-level signals은 provider export와 분리되어 재사용 가능하다. | metric names, correlation fields, health probes를 안정적인 application contract로 유지 | observability tests, `/actuator/health`와 `/actuator/prometheus` smoke, cardinality 검토 |
| `backend/src/main/java/com/terraformers/modernization/config/CloudWatchMetricsConfiguration.java`, `infra/terraform/envs/eks-runtime/main.tf` | CloudWatch registry namespace, EKS CloudWatch observability add-on/IRSA, dashboard와 backend/analysis alarms | **MODIFY** | exporter와 dashboard/alarm은 AWS-specific이지만 현재 운영 자산이다. | application metrics와 AWS export/alarms를 분리해 보존 | Terraform plan, metric emission, dashboard widgets와 alarm state live check; notification destination/on-call 연결은 **UNKNOWN/TBD** |

### 17. Tests

| Actual path | Current role | Decision | Decision rationale | Expected target state | Future validation |
|---|---|---|---|---|---|
| `backend/src/test/java/`, `backend/src/test/resources/` | Spring/unit/repository/controller/provider/storage/security/config tests와 MariaDB smoke | **KEEP** | 주요 backend business flow 및 adapter boundary에 실제 coverage가 있다. | core와 adapter 계약 회귀 suite 유지, MariaDB-specific 검증 강화 | `cd backend && mvn test`; coverage threshold는 **UNKNOWN/TBD** |
| `frontend/src/**/*.test.js`, `frontend/src/setupTests.js` | auth session/API, upload, project pages/tree/public UI component tests | **KEEP** | 현재 frontend flow에 대응하는 Jest/Testing Library suite이다. | authentication과 backend contract 중심 회귀 suite 유지 | `cd frontend && npm test -- --watchAll=false`; browser E2E suite는 발견되지 않아 **UNKNOWN/TBD** |
| `tests/rag/` | corpus build/curation/ingestion utility unit tests | **KEEP** | version/checksum/filter/index behavior를 검증하는 실제 ingestion 안전망이다. | offline deterministic tests와 별도의 승인된 live smoke 유지 | `python3 -m unittest discover -s tests/rag` |
| `scripts/checks/` | Flyway/MariaDB, Terraform, Kubernetes, runtime contracts, AWS packaging/deployment guard 검증 | **KEEP** | CI workflow가 재사용하는 executable contract tests이다. | 문서가 아니라 실행 가능한 검증 자산으로 유지 | 각 연결 workflow와 script exit status 확인; 전체를 묶는 단일 local command는 발견되지 않아 **UNKNOWN/TBD** |

## 경계 요약

### KEEP 핵심 자산

1. `projectcore`, `project`, `identity`, `projectcomment`, `projecttree`의 domain/business flow와 JPA model.
2. integrated Java analysis job lifecycle, `AnalysisProvider`/`ReferenceRetriever`/`EmbeddingProvider`/object storage ports.
3. Flyway schema와 MariaDB system-of-record 계약.
4. versioned RAG corpus, ingestion utilities, backend/frontend/RAG tests와 executable checks.
5. cloud-neutral Docker image 및 Kubernetes base/local overlay, runtime contract.

### MODIFY 핵심 경계

1. Cognito, Bedrock, S3, SQS, SigV4 OpenSearch adapter는 삭제 대상이 아니라 AWS-specific integration으로 분명히 유지·격리한다.
2. RDS/EKS/OpenSearch/CloudFront/CodeBuild를 포함한 Terraform과 AWS GitHub Actions는 application core가 아닌 provider-specific delivery plane으로 다룬다.
3. AWS Kubernetes overlay, External Secrets, ALB, CloudWatch exporter/dashboard/alarm은 base workload 및 application metrics와 구분한다.
4. Argo CD의 repository/revision은 현재 base와의 일치 여부를 먼저 확인해야 한다.

### 확인하지 못한 사항 (UNKNOWN/TBD)

- 현재 AWS resources, Terraform state, Argo CD application, GitHub environments/secrets/required checks가 실제로 적용·동작 중인지 여부.
- Bedrock analysis/embedding model ID, vector dimension, OpenSearch endpoint/index와 corpus version의 실제 운영 값 및 상호 일치 여부.
- SQS progress event를 소비하는 component의 repository 내/외 존재와 운영 필요성.
- process restart 시 in-flight analysis job 복구, orphan object 정리, RDS backup/restore/failover, alert notification/on-call 정책.
- 독립 board API의 사용 여부, browser E2E/coverage 기준, Docker Compose 또는 frontend container의 필요성.
- RabbitMQ, LangGraph, 상시 Python worker, Keycloak, Redis, Kafka의 채택 근거. 따라서 모두 **DEFER**이며 **NEW** 또는 **REPLACE** 판정이 아니다.

