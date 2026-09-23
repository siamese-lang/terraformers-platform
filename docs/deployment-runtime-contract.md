# Backend Deployment Runtime Contract

## Canonical configuration and historical AWS compatibility

`application-prod.yml` is provider-neutral. It uses `JWT_PROVIDER`,
`OBJECT_READER_PROVIDER`, `OBJECT_WRITER_PROVIDER`, `ANALYSIS_PROVIDER`,
`EMBEDDING_PROVIDER`, `RETRIEVAL_MODE`, and `PROGRESS_PUBLISHER`, with neutral
issuer, JWK, upload-source, and result-bucket inputs.

`application-aws-compat.yml` retains adapter-owned Cognito, S3, Bedrock,
SigV4/OpenSearch, and SQS settings for historical runtime compatibility. Those
manifests use `SPRING_PROFILES_ACTIVE=prod,aws-compat`; this does not make AWS
the active deployment target.

## 1. Purpose

This document explains how backend runtime values are delivered to the Spring Boot modernization baseline.

The project now has a backend-owned analysis job flow:

```text
POST /api/analysis/jobs
  -> RDB job state
  -> S3 source object read
  -> reference retrieval
  -> Bedrock Terraform draft generation
  -> S3 result object write
  -> SQS progress publish
```

Each AWS dependency is behind a feature flag so local/CI validation can remain deterministic and credential-free.

## 2. Kubernetes files

The public-safe Kubernetes skeleton lives under:

```text
infra/kubernetes/base/
```

Files:

- `backend-configmap.yaml`: non-secret switches and defaults
- `backend-secret.example.yaml`: placeholder-only secret contract; intentionally not included in `kustomization.yaml`
- `backend-deployment.yaml`: backend deployment using `envFrom`
- `backend-serviceaccount.yaml`: service account skeleton; IRSA role annotation belongs in an environment-specific overlay
- `backend-service.yaml`: ClusterIP service
- `kustomization.yaml`: public-safe base resource list

Important boundary:

```text
kubectl kustomize infra/kubernetes/base
```

renders ConfigMap, Deployment, ServiceAccount, and Service only. It does not render `backend-secret.example.yaml`. The runtime Secret must be created through External Secrets, Sealed Secrets, CI/CD secret injection, or a private environment overlay.

## 3. Local/stub runtime mode

Default values select only provider-neutral local-safe implementations.

```text
OBJECT_READER_PROVIDER=disabled
OBJECT_WRITER_PROVIDER=metadata-only
ANALYSIS_PROVIDER=stub
EMBEDDING_PROVIDER=disabled
RETRIEVAL_MODE=DISABLED
PROGRESS_PUBLISHER=logging
```

This mode uses:

```text
StubObjectReader
StubObjectWriter
RetrievalModeReferenceRetriever (DISABLED -> empty references)
StubAnalysisProvider
LoggingProgressPublisher
```

With retrieval disabled, no `EmbeddingProvider` invocation occurs.

Use this mode for Maven verification, Docker image verification, and local smoke tests.

## 4. AWS adapter runtime mode

Enable this mode only after AWS resources and IAM policies exist.

```text
SPRING_PROFILES_ACTIVE=prod,aws-compat
OBJECT_READER_PROVIDER=s3
OBJECT_WRITER_PROVIDER=s3
ANALYSIS_PROVIDER=bedrock
EMBEDDING_PROVIDER=bedrock
RETRIEVAL_MODE=REQUIRED
PROGRESS_PUBLISHER=logging
```

This mode uses:

```text
AwsS3ObjectReader
AwsS3ObjectWriter
BedrockEmbeddingProvider
OpenSearchReferenceRetriever
BedrockAnalysisProvider
LoggingProgressPublisher
```

Set `PROGRESS_PUBLISHER=sqs` only when the historical SQS adapter and its queue URLs are deliberately configured.

## 5. ConfigMap vs Secret

ConfigMap values are operational switches and non-secret defaults.

Secret values include database credentials, Cognito runtime values, bucket names, queue URLs, Bedrock model IDs, OpenSearch endpoint, and index/field names.

Although bucket names and queue URLs are not passwords, they are deployment-specific runtime values. Treat them as controlled configuration and avoid printing them unnecessarily in logs.

Do not commit real account IDs, IAM role ARNs, queue URLs, bucket names, passwords, tokens, kubeconfig, tfstate, or `.tfvars` files.

## 6. Deployment checks

After applying environment-specific manifests or overlays, check:

```bash
kubectl get configmap terraformers-backend-runtime-config
kubectl get secret terraformers-backend-runtime-secrets
kubectl get deploy terraformers-backend
kubectl rollout status deployment/terraformers-backend
kubectl logs deployment/terraformers-backend --tail=200
```

Then run the smoke script:

```bash
BASE_URL=http://<backend-url> \
PROJECT_ID=project-smoke \
SOURCE_BUCKET=<upload-bucket> \
SOURCE_KEY=<uploaded-image-key> \
bash scripts/smoke/create-analysis-job.sh
```

Expected success:

```text
analysis job smoke assertions passed
status=SUCCEEDED
resultObjectKey=analysis-results/...
```

## 7. Portfolio explanation

```text
배포 환경에서는 기능별 AWS 연동을 한 번에 강제하지 않고 S3 read/write, Bedrock generation, Bedrock embedding, OpenSearch retrieval, SQS progress publisher를 각각 feature flag로 분리했습니다. 로컬과 CI에서는 stub adapter로 API/RDB/job lifecycle을 먼저 검증하고, AWS 배포에서는 ConfigMap과 Secret 계약을 통해 실제 adapter를 켜도록 했습니다. public base manifest에는 실계정 Secret이나 IAM ARN을 넣지 않고, 환경별 overlay 또는 External Secrets로 주입하도록 분리했습니다.
```
