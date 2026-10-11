# Terraformers Modernization

**아키텍처 이미지에서 검토 가능한 Terraform 초안까지 — 백엔드 신뢰성, AI/RAG 근거 전달, 장애 진단, 클라우드 이식성을 개선한 프로젝트**

> **프로젝트 상태 (2026-10-12 KST): 개발·실증 종료 / GCP 런타임 철거 완료 / 포트폴리오 증거 보존.** 이 저장소는 실행 중인 서비스나 공개 데모가 아닙니다. GCP 결제 연결은 해제됐고, Firebase Authentication 테스트 사용자 2명은 운영자가 Console에서 삭제했습니다. Google Cloud/Firebase 프로젝트 설정과 도메인 등록은 일부 남아 있습니다. [종료 증거 및 보존 범위](docs/lifecycle/gcp-2026-final-teardown-receipt.md).

## 프로젝트 개요

**Terraformers**는 2024년 AWS Cloud School에서 5인 팀으로 개발한 서비스입니다. 사용자가 AWS 클라우드 아키텍처 이미지를 업로드하면 구성요소와 관계를 분석하고, Terraform 코드 **초안**을 생성·조회·관리합니다. 원본 팀 프로젝트와 [기존 RDB 리팩터링 자산](https://github.com/siamese-lang/rdb-refactor)을 재사용했습니다.

이 저장소의 **후속 현대화 작업**은 새 AI 모델을 만드는 것이 아니라, 실제 이미지 분석 결과가 코드 생성까지 전달되는지, 긴 분석 작업이 실패·재시작 상황에서 어떻게 처리되는지, 실패 원인을 추적할 수 있는지, 그리고 AWS 의존성이 있는 서비스를 GCP의 **단일 실증 환경**에서 운영할 수 있는지를 검증하는 데 집중했습니다.

**범위:** 생성 대상은 주로 AWS Terraform 코드입니다. GCP는 해당 생성·관리 애플리케이션을 실행하고 검증한 클라우드 환경이었습니다. 생성한 Terraform은 사람의 검토가 필요한 초안이며 AWS 인프라에 자동 `apply`하지 않습니다.

## 마지막 실증 아키텍처 (현재 철거됨)

```text
사용자 브라우저 (React + Firebase Authentication)
    │ HTTPS (otogi.cloud, 당시 실증)
    ▼
GCE Ingress → GKE Spring Boot Backend
    ├─ 인증·소유권 검증 / Project·File·AnalysisJob API
    ├─ MariaDB 11.4 (Compute Engine VM) : 사용자·프로젝트·작업 상태
    ├─ GCS : 업로드 이미지와 Terraform 결과 객체
    ├─ Vertex AI : 아키텍처 사실 추출·Terraform 초안 생성
    ├─ OpenSearch : 공식/provider 문서 및 프로젝트 근거 검색
    ├─ Provider schema / Terraform CLI : 초안 구조·실행 가능성 진단
    └─ Job/단계별 로그·품질 및 장애 진단
GitHub Actions + OIDC/WIF → Artifact Registry immutable image → GKE
```

Spring Boot의 domain·분석 흐름을 중심에 두고, 인증·모델·객체 저장·검색·클라우드 런타임 의존성은 adapter와 배포 경계로 구분했습니다. [논리 아키텍처](docs/architecture/target-architecture.md) · [GCP 실증 엔지니어링 기록](docs/portfolio/terraformers-modernization-final-engineering-account-20261011.md).

이전 AWS 실증은 **별개의 역사적 단계**입니다. 당시 EKS/RDS/S3/Cognito/Bedrock/AOSS/CloudFront, Terraform state 분리, ECR 이미지·Argo CD GitOps를 사용했으며 현재 활성 배포를 뜻하지 않습니다. [AWS 시대 상세 시스템 안내](docs/project-system-overview.md).

## 주요 엔지니어링 사례와 확인한 결과

| 실제 문제 / 결정 | 구현 및 검증 근거 | 결과와 한계 |
| --- | --- | --- |
| **이미지에서 추출한 연결 관계가 코드 생성 단계에 전달되지 않음** | [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274)에서 구조화된 사실을 초기 생성·fallback에 전달하고 검색 제한을 수정; [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277)에서 객체 존재와 CREATE 의도, 관계 배선, repair 검증 구분 | 원본 공식 테스트 이미지 A에서 과거 누락됐던 API Gateway→Cognito 인증 연결을 후속 생성 결과에서 관찰. **전체 아키텍처 의미 정확도가 검증된 것은 아님** |
| **긴 비동기 분석 작업의 중복 처리·중단·결과 유실 위험** | MariaDB 기반 durable eligibility, lease/fencing, 선택적 재시도, 결과 의도·보상 정리; [Case B B5 통합 검증](docs/evaluation/case-b-integrated-closure.md) | 실제 MariaDB 경합과 제한된 장애 주입 검증. 모든 장애 상황에서 exactly-once 외부 모델 호출이나 서비스 가용성을 보장하지 않음 |
| **실패 표지만으로 원인을 구분하기 어려움** | facts / retrieval / generation / Terraform 진단을 구분하고 Job·소스·품질 증거를 지속화; [T2 실측](docs/portfolio/terraformers-gcp-final-live-evidence-20261011.md) | 실행 중 Pod 정상 삭제 1회에서 Vertex facts 완료 후 RAG 단계의 shutdown 오류를 구분했고 Job은 FAILED로 기록됨. 무중단 복구 성공을 뜻하지 않음 |
| **평가용 환경과 실제 사용자 요청·저장·배포 검증 사이의 간극** | GKE + MariaDB + GCS + OpenSearch + Vertex AI 단일 환경, immutable 이미지 전달, Firebase 로그인과 접근 통제; [Case C](docs/evaluation/case-c-portfolio-closure.md), [T1/T2](docs/portfolio/terraformers-gcp-final-live-evidence-20261011.md) | T1 실사용자 2명으로 타인 비공개 리소스 조회 403 및 분석 유료 진입 403 관찰. 완전한 보안 인증·고가용성 증거는 아님 |

### AI/Terraform 검증의 정확한 범위

별도 **A/B/N1/N2/N3 5건**의 실이미지 관찰에서 AnalysisJob 5건이 완료됐고 최종 코드가 모두 **실제 Terraform CLI 검사 PASS**를 기록했습니다. 그러나 `qualityStatus=UNKNOWN`이었으며, 코드가 원본 이미지의 의도를 올바르게 구현했는지에 대한 **의미적 품질 5/5 PASS를 뜻하지 않습니다**.

원래 공식 PT-8A A–E 검증의 미충족·NOT_RUN 이력은 별도로 남아 있고, **전체 Product Trust acceptance는 확립되지 않았습니다.** PT-8B의 원래 진입 조건도 충족된 것으로 처리하지 않았습니다. [다섯 건의 한정 관찰과 원본 검증의 관계](docs/evaluation/pt8a-qualified-five-case-disposition.md) · [실증 증거 우선순위](docs/portfolio/current-evidence-prioritization-20261011.md).

## 기술 구성 및 실행 이력

| 영역 | 2026 GCP 실증 환경 (철거 완료) | 역사적 AWS 구현/참고 |
| --- | --- | --- |
| 서비스 | React, Java/Spring Boot, Spring Data JPA, Flyway | Spring Boot, React |
| 인증 | Firebase ID token + Backend 소유권 검증 | Cognito JWT |
| 영속 데이터 | MariaDB on Compute Engine + GCS | RDS MariaDB + S3 |
| AI·검색 | Vertex AI + OpenSearch / 공식 provider 근거 | Bedrock + AOSS |
| 런타임 | GKE, Secret Manager, Kubernetes | EKS, IRSA/External Secrets |
| CI/CD | GitHub Actions OIDC/WIF, Artifact Registry 이미지 digest, 승인형 Terraform | ECR immutable digest, Argo CD GitOps |
| 검증 | 실제 Job/CLI, 브라우저 T1, Pod interruption T2, Terraform destroy | AWS 단계별 배포·철거 및 과거 평가 기록 |

이는 **수행한 시점별 아키텍처 비교**이며, 두 클라우드의 현재 운영 서비스나 완전 동일 조건 벤치마크가 아닙니다.

## 원본 팀 작업과 이후 개인 현대화의 구분

- **2024년 5인 팀 프로젝트:** 이미지 기반 Terraform 생성 웹서비스를 팀으로 완성했습니다. 개인 기여는 백엔드 기능 일부, DB/스토리지 연동, 클라우드 인프라·배포 흐름 참여 범위로 설명합니다.
- **후속 현대화:** 기존 코드·도메인 재사용 판단, 분석 Job 내구성 및 진단 설계, AI/RAG 근거 전달과 코드 품질 검증, AWS/GCP 클라우드 배포 경계, 실제 테스트와 종료 증거를 다뤘습니다. 모든 팀 산출물을 개인이 단독 구현했다고 주장하지 않습니다.

## 현재 저장소 이용 안내

- **운영 상태:** GKE/VM/디스크/IP/GCS 등 실증 리소스, Terraform 관리 29개 인스턴스, state/bootstrap 인증 자원을 철거했습니다. 종료 과정에서 워크플로가 누락한 OpenSearch 15 GiB 디스크를 수동으로 찾아 제거했습니다. GCP 결제는 해제됐습니다. 이 사실은 모든 Google 제품에 대한 과금 0원 보증이나 Firebase/GCP 프로젝트 자체 삭제를 뜻하지 않습니다. [단계별 종료 영수증](docs/lifecycle/gcp-2026-final-teardown-receipt.md).
- **실행:** 코드와 Dockerfile, Terraform/Kubernetes 구성 및 CI 워크플로는 학습·재현을 위해 남겼습니다. 외부 계정·비밀정보·비용 승인 없이 과거 배포 스크립트를 실행할 수 있는 환경은 아닙니다. 향후 새 실환경 배포는 별도의 요구사항, 인벤토리 및 사용자 승인이 필요합니다.
- **보안:** 실제 비밀값·토큰·개인 사용자 데이터와 운영 Terraform state는 저장소에 포함하지 않습니다. 과거 원본 진단 파일 일부는 별도 보관되고, 공개 문서는 개인정보를 제거한 증거 요약입니다.

### 어디부터 읽을까?

| 목적 | 문서 |
| --- | --- |
| **현대화의 전체 기술적 의사결정과 사례** | [최종 엔지니어링 기록](docs/portfolio/terraformers-modernization-final-engineering-account-20261011.md) |
| **실제 Firebase 두 사용자(T1)와 장애 재현(T2)** | [GCP 실증 결과](docs/portfolio/terraformers-gcp-final-live-evidence-20261011.md) |
| **AI/RAG 생성 개선과 한정 품질 판정** | [포트폴리오 근거 우선순위](docs/portfolio/current-evidence-prioritization-20261011.md), [PT-8A 한정 검증](docs/evaluation/pt8a-qualified-five-case-disposition.md) |
| **Backend 내구성** | [Case B 통합 종료](docs/evaluation/case-b-integrated-closure.md) |
| **클라우드 배포와 제한된 실증** | [Case C 종료](docs/evaluation/case-c-portfolio-closure.md) |
| **2026 GCP 철거와 남은 설정** | [GCP 최종 종료 영수증](docs/lifecycle/gcp-2026-final-teardown-receipt.md) |
| **과거 AWS 단계의 구조와 근거** | [AWS 시대 전체 구성 안내](docs/project-system-overview.md) |
| **코드 구조** | [Backend](backend/) · [Frontend](frontend/) · [Terraform/Kubernetes](infra/) · [검증 근거](docs/evaluation/) · [작업 이력](docs/AI_PROJECT_STATE.md) |

**최종 판정:** `PORTFOLIO_EVIDENCE_BACKED_ENGINEERING_CLAIMS_SUFFICIENT` / `FULL_PRODUCT_TRUST_ACCEPTANCE_NOT_ESTABLISHED` / `GCP_RUNTIME_TORN_DOWN`. 완료된 구현과 실증을 기록하되, 아직 입증하지 못한 품질·복구·보안 범위를 별도로 남깁니다.
