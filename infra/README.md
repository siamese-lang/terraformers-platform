# Terraformers Infrastructure — Historical and GCP Reference Code

> **상태:** 2026-10-11 UTC GCP 실증 런타임 및 Terraform bootstrap 철거 완료. 저장소에는 Terraform/Kubernetes 소스와 실행 이력을 **참고·포트폴리오 용도로 보존**합니다. 현재 `terraform apply` 대상이 존재한다는 뜻이 아닙니다.

## 실제 디렉터리

```text
infra/
├── terraform/
│   ├── bootstrap/           # bootstrap/state 및 연동 코드 참고
│   ├── envs/
│   │   ├── gcp-target-runtime/
│   │   ├── aws-runtime-network/
│   │   ├── backend-runtime-dependencies/
│   │   ├── backend-stateful-dependencies/
│   │   ├── eks-runtime/
│   │   ├── frontend-delivery/
│   │   └── rag-runtime/
│   └── runtime-contract/
└── kubernetes/
    ├── base/
    ├── overlays/
    │   ├── gcp-target/
    │   ├── gcp-target-hosted/
    │   ├── aws-runtime-template/
    │   └── local-stub/      # 이외 portable-* overlays 포함
    ├── gitops/
    ├── argocd/
    └── external-secrets/
```

2026 GCP 단계는 GKE, MariaDB VM/디스크, GCS, OpenSearch, Vertex AI, Artifact Registry와 GitHub WIF 기반 배포를 실증했습니다. 과거 AWS 단계는 EKS/RDS/S3/Cognito/Bedrock/AOSS/CloudFront와 Argo CD GitOps를 참고용으로 보존합니다. 두 환경이 현재 가동 중이거나 동일한 구성으로 자동 재배포 가능한 상태인 것은 아닙니다.

**실행 주의:** Terraform 원격 state 버킷, GitHub WIF 및 대부분의 대상 클라우드 자원을 제거했고 GCP 결제 연결을 해제했습니다. 워크플로와 스크립트는 실수로 실행하지 마세요. 재배포는 새로운 인벤토리, state/bootstrap 설계, 비용·IAM·삭제 범위에 대한 별도 승인부터 필요합니다. `terraform.tfstate`, 실 `tfvars`, kubeconfig, 키·토큰은 커밋하지 않습니다.

[프로젝트 README](../README.md) · [최종 GCP 철거 영수증](../docs/lifecycle/gcp-2026-final-teardown-receipt.md) · [GCP 실증 기록](../docs/portfolio/terraformers-modernization-final-engineering-account-20261011.md).
