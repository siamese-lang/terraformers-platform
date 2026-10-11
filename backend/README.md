# Terraformers Backend — Modernization Source

> **상태:** 2026년 GCP 실증 후 런타임 철거 완료. 이 디렉터리는 재사용 가능한 Spring Boot 구현과 테스트를 보존합니다. 이전의 “Backend 기본 골격만 있고 API import를 기다린다”는 초기 설명은 역사적 단계입니다. 현재 배포 중인 서비스는 없습니다.

## 역할

Spring Boot Backend는 Terraformers의 Project/User/File/Comment API, 인증 사용자와 소유권 검사, 비동기 `AnalysisJob`, 이미지 및 생성 결과의 객체 저장 경계, 아키텍처 사실 추출·근거 검색·Terraform 초안 생성과 품질/CLI 진단을 소유합니다.

- **지속 상태:** MariaDB / Flyway 기반 분석 작업, lease/fencing, 선택적 재시도, 결과 의도·보상 정리. [Case B 증거](../docs/evaluation/case-b-integrated-closure.md)
- **AI/RAG:** 이미지에서 식별한 facts 및 directed relationships와 공식/provider 근거를 생성 단계에 전달. 결과의 구문/CLI 타당성과 의미적 품질을 구분. [현재 결과의 한계](../docs/evaluation/pt8a-qualified-five-case-disposition.md)
- **클라우드 adapter:** GCP Vertex AI/GCS/OpenSearch 및 역사적 AWS Bedrock/S3/AOSS 호환 경계. 모든 adapter가 동시에 동일 환경에서 활성화됐다는 의미는 아닙니다.
- **인증:** GCP hosted T1 단계에는 Firebase ID token 기반 인증·owner authorization을 검증했으며, 이전 AWS 단계의 Cognito 연동은 역사적 참고입니다.

## 구성 및 검증

```text
pom.xml                         Maven build
src/main/java/...               Spring Boot application, domain and adapters
src/main/resources/application*.yml
src/main/resources/db/migration Flyway migrations
src/test/                       deterministic tests / local validation
```

로컬 의존성 및 테스트 환경을 갖춘 경우 `cd backend && mvn test`로 해당 소스의 단위/통합 테스트를 실행할 수 있습니다. **GCP 실환경은 이미 철거됐으므로** 해당 명령이 실제 Firebase/GCS/Vertex/GKE 통합 실증을 재현하는 것은 아닙니다. 새 live 배포를 위해 과거 Secret·Terraform 상태를 추측하여 생성하거나 자동으로 클라우드 자원을 만들지 마세요.

[프로젝트 전체 안내](../README.md) · [GCP 실증](../docs/portfolio/terraformers-gcp-final-live-evidence-20261011.md) · [최종 철거](../docs/lifecycle/gcp-2026-final-teardown-receipt.md).
