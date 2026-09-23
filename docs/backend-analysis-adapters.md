# Backend Analysis Adapter Design

Runtime selection uses `ANALYSIS_PROVIDER=stub|bedrock`,
`EMBEDDING_PROVIDER=disabled|bedrock`, and
`PROGRESS_PUBLISHER=logging|sqs`. Canonical safe defaults are `stub`,
`disabled`, and `logging`; Bedrock and SQS settings remain in `aws-compat`.

## 1. Purpose

This document explains how the public modernization baseline replaces the original Python analysis runtime with Spring Boot backend-owned orchestration.

The goal is not to finish every AWS integration in one step. The goal is to make the backend own the analysis lifecycle and expose clear adapter boundaries for AWS dependencies.

## 2. Current backend-owned flow

```text
POST /api/analysis/jobs
  -> AnalysisJobService creates analysis_jobs row
  -> AnalysisJobOrchestrator moves status PENDING -> RUNNING
  -> ObjectReader checks the uploaded S3 object boundary
  -> ReferenceRetriever returns reference patterns
  -> AnalysisProvider analyzes the source object
  -> AnalysisResultStorage writes generated Terraform HCL through ObjectWriter
  -> ProgressPublisher emits progress/result state
  -> analysis_jobs row moves RUNNING -> SUCCEEDED or FAILED
GET /api/analysis/jobs/{id}
  -> returns current RDB job state, provider, resultObjectKey, resultPreview, and failureReason
```

## 3. Ports

### ObjectReader

`ObjectReader` is the boundary for reading uploaded image objects.

Current implementations:

- `StubObjectReader`: default local/CI-safe object reader. It infers a content type from the object key and does not require AWS credentials.
- `AwsS3ObjectReader`: compatibility adapter selected by `OBJECT_READER_PROVIDER=s3`.

Target responsibility:

- verify bucket/key reachability;
- read object metadata such as content type, content length, and ETag;
- read object bytes when Bedrock vision integration needs base64 image payload;
- fail clearly on missing object or access denial.

### ObjectWriter

`ObjectWriter` is the boundary for writing generated Terraform HCL.

Current implementations:

- `StubObjectWriter`: default local/CI-safe writer. It returns the requested bucket/key without calling AWS.
- `AwsS3ObjectWriter`: compatibility adapter selected by `OBJECT_WRITER_PROVIDER=s3`.

Target responsibility:

- write generated Terraform HCL to object storage;
- return the persisted object key;
- let `AnalysisJobOrchestrator` store that key in `analysis_jobs.result_object_key`;
- fail clearly on missing bucket, access denial, or blocked prefix.

### EmbeddingProvider

`EmbeddingProvider` is the boundary for turning retrieval text into an embedding vector.

The current concrete implementation is `BedrockEmbeddingProvider`. The generic
`terraformers.analysis.embedding-provider` selector accepts `bedrock` (and `disabled` for a
retrieval-disabled runtime); it does not expose the Bedrock model ID to generic retrieval code.

This keeps OpenSearch reference retrieval testable without model calls while still exposing the real Bedrock embedding path for deployment.

### ReferenceRetriever

`ReferenceRetriever` is the boundary for retrieving reference patterns before Terraform draft generation.

Current implementations:

- `StubReferenceRetriever`
  - returns deterministic reference documents for local/CI verification;
  - allows `AnalysisProvider` tests to verify the reference retrieval step without OpenSearch credentials.

- `OpenSearchReferenceRetriever`
  - active retrieval implementation selected through `RETRIEVAL_MODE=REQUIRED|OPTIONAL`;
  - uses `EmbeddingProvider` to build a vector;
  - builds an OpenSearch k-NN query with `OpenSearchKnnQueryBuilder`;
  - sends a SigV4 signed request with `SignedOpenSearchHttpClient`;
  - parses ranked reference documents with `OpenSearchResponseParser`.

Operational boundary:

```text
Retrieval disabled: no EmbeddingProvider invocation
Active retrieval: selected EmbeddingProvider + OpenSearchReferenceRetriever
```

Target responsibility:

- build an embedding request from image analysis text or detected service names;
- invoke Bedrock embedding model;
- query OpenSearch/AOSS k-NN index;
- return ranked reference documents;
- fail clearly on missing index, wrong vector field, access denial, or timeout.

### AnalysisProvider

`AnalysisProvider` is the boundary for Terraform draft generation.

Current implementations:

- `StubAnalysisProvider`
  - default local/CI-safe provider;
  - reads source object metadata through `ObjectReader`;
  - retrieves reference patterns through `ReferenceRetriever`;
  - returns deterministic Terraform draft text;
  - exists so Maven, Docker, API, RDB, storage boundary, reference boundary, and status transition can be verified without AWS model calls.

- `BedrockAnalysisProvider`
  - compatibility adapter selected by `terraformers.analysis.provider=bedrock`;
  - reads uploaded object content through `ObjectReader`;
  - builds a Claude vision request through `BedrockPromptBuilder`;
  - invokes Bedrock Runtime through AWS SDK v2;
  - parses returned Terraform HCL through `BedrockResponseParser`;
  - keeps Bedrock call logic inside backend-owned orchestration rather than a Python side service.

Important boundary:

```text
Local/CI default: StubObjectReader + StubReferenceRetriever + StubAnalysisProvider + StubObjectWriter
Production optional: AwsS3ObjectReader + OpenSearchReferenceRetriever + BedrockAnalysisProvider + AwsS3ObjectWriter
```

This keeps CI deterministic and credential-free while making the production adapter path explicit.
The legacy Bedrock boolean fallback has been removed; analysis implementations are selected only
through `ANALYSIS_PROVIDER`.

### ProgressPublisher

`ProgressPublisher` is the boundary for progress and result emission.

Current implementations:

- `LoggingProgressPublisher`: default local/CI-safe publisher
- `SqsProgressPublisher`: compatibility adapter selected by `PROGRESS_PUBLISHER=sqs`

This allows local build and tests to run without AWS credentials while keeping the production SQS boundary explicit.

## 4. Why this structure is preferable

The original Python service handled request orchestration, S3 read, Bedrock call, OpenSearch query, and SQS publish. These are important responsibilities, but they do not require Python by default.

By moving orchestration ownership to Spring Boot backend, the project can show:

- backend API contract
- RDB job lifecycle
- provider/adapter separation
- S3 object access boundary
- S3 result persistence boundary
- embedding generation boundary
- reference retrieval boundary
- Bedrock invocation boundary
- SQS progress boundary
- deployment-time runtime configuration
- failure state management
- smoke-testable backend behavior

## 5. Runtime properties

Production runtime config is injected through `application-prod.yml` and environment variables.

Important values:

- `OBJECT_READER_PROVIDER`
- `OBJECT_WRITER_PROVIDER`
- `ANALYSIS_PROVIDER`
- `EMBEDDING_PROVIDER`
- `RETRIEVAL_MODE`
- `PROGRESS_PUBLISHER`
- `ANALYSIS_RESULT_BUCKET_NAME`
- `ANALYSIS_RESULT_KEY_PREFIX`
- `BEDROCK_MODEL_ID`
- `BEDROCK_EMBEDDING_MODEL_ID`
- `BEDROCK_MAX_TOKENS`
- `OPENSEARCH_ENDPOINT`
- `OPENSEARCH_SERVICE_NAME`
- `OPENSEARCH_TOP_K`
- `INDEX_NAME`
- `VECTOR_FIELD_NAME`
- `CONTENT_FIELD_NAME`
- `AI_LOG_QUEUE_URL`
- `TERRAFORM_LOG_QUEUE_URL`

The selectors and logical OpenSearch/object locations belong to the canonical contract. Bedrock
model identifiers, SQS queue URLs, and the AWS OpenSearch signing name are AWS compatibility
adapter configuration in `application-aws-compat.yml`. Secret values must not be logged.

## 6. Current completion boundary

Implemented in the public baseline:

- backend-owned analysis job API
- RDB job lifecycle
- S3 object reader port and optional S3 adapter
- S3 object writer port and optional S3 result storage adapter
- embedding provider port and optional Bedrock embedding adapter
- reference retrieval port, stub retriever, and optional SigV4-signed OpenSearch/AOSS retriever
- Bedrock provider boundary and optional Bedrock Runtime adapter
- SQS progress publisher boundary and optional SQS adapter
- local/CI-safe stub path
- smoke script assertions for `SUCCEEDED`, `resultObjectKey`, and `resultPreview`
- adapter failure runbook for S3, Bedrock, OpenSearch/AOSS, SQS, and RDB job state

Not yet complete:

- replacement provider/runtime implementations for the current GCP deployment target
- full browser E2E validation
- deployed AWS evidence

## 7. Next implementation steps

1. Add Terraform/Kubernetes runtime variables for the new adapter switches.
2. Add browser E2E validation after deployment manifests are available.
3. Collect deployed AWS evidence after runtime validation.
