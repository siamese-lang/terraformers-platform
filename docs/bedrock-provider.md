# Bedrock Provider Integration

Bedrock is selected explicitly through `ANALYSIS_PROVIDER=bedrock` and, for
active retrieval, `EMBEDDING_PROVIDER=bedrock`. The legacy
`BEDROCK_PROVIDER_ENABLED` fallback is removed; model settings remain in the
adapter-specific `BedrockRuntimeProperties` populated by `aws-compat`.

## 1. Purpose

This document explains how the Spring Boot backend integrates the Bedrock vision model without keeping the original Python Flask service as a required runtime.

The implementation is intentionally feature-flagged. Local and CI verification use the stub provider by default. Bedrock is enabled only in a deployment environment with AWS runtime identity and required configuration.

## 2. Runtime switch

Default behavior:

```yaml
terraformers:
  analysis:
    provider: stub
    embedding-provider: bedrock
    bedrock:
      model-id: ""
      embedding-model-id: ""
      max-tokens: 8192
```

Production Bedrock path:

```bash
ANALYSIS_PROVIDER=bedrock
S3_READER_ENABLED=true
BEDROCK_MODEL_ID=<bedrock-model-id>
BEDROCK_MAX_TOKENS=4096
```

When `terraformers.analysis.provider=stub`, `StubAnalysisProvider` remains active and no AWS model call is made.

When `terraformers.analysis.provider=bedrock`, the neutral selection layer delegates to `BedrockAnalysisProvider`. If the generic selector is absent, `BEDROCK_PROVIDER_ENABLED=true` continues to select Bedrock as a transitional compatibility fallback; an explicit generic selector always wins, and removal of this alias is deferred to M1-7.

## 3. Request flow

```text
AnalysisJobOrchestrator
  -> BedrockAnalysisProvider
      -> ObjectReader.readContent(bucket/key)
      -> ReferenceRetriever.retrieve(query)
      -> BedrockPromptBuilder.buildClaudeVisionRequest(...)
      -> BedrockRuntimeClient.invokeModel(...)
      -> BedrockResponseParser.extractText(...)
  -> AnalysisResult(terraformCode, explanation, references)
```

## 4. Implementation components

- `BedrockAnalysisProvider`
  - feature-flagged provider implementation
  - owns Bedrock Runtime invocation
  - does not log image bytes, prompt body, or generated secret values

- `BedrockPromptBuilder`
  - builds the Anthropic Claude vision request body
  - embeds the uploaded image as base64
  - includes sanitized object metadata and reference documents
  - rejects unsupported content types before model invocation

- `BedrockResponseParser`
  - extracts returned text from the Bedrock response body
  - strips markdown code fences if the model returns them

## 5. Operational checks

Before enabling Bedrock provider in a deployed environment, verify:

```text
[ ] backend pod has AWS runtime identity through IRSA or equivalent role
[ ] role can invoke the selected Bedrock model
[ ] S3_READER_ENABLED=true and source object can be read
[ ] ANALYSIS_PROVIDER=bedrock (or transitional BEDROCK_PROVIDER_ENABLED=true)
[ ] BEDROCK_MODEL_ID is set
[ ] model region matches backend AWS region
[ ] request/response bodies are not logged
[ ] failure path marks analysis_jobs.status=FAILED
```

## 6. Failure classification

| Symptom | Likely layer | Check |
|---|---|---|
| Unsupported image content type | backend validation | object metadata and extension |
| Access denied when reading source object | S3/IAM | pod role, bucket policy, object key |
| Bedrock model access denied | IAM/Bedrock | role permission, model access, region |
| Bedrock timeout | model/runtime | timeout, payload size, retry policy |
| Empty model response | Bedrock/parser | response body, parser assumptions |
| job remains RUNNING | backend lifecycle | exception handling, transaction boundary |

## 7. Portfolio explanation

```text
원본 Python 분석 서비스가 담당하던 Bedrock 호출을 Spring Boot backend의 AnalysisProvider port 뒤로 옮겼습니다. 기본값은 stub provider라서 CI와 로컬 검증은 AWS credential 없이 가능하고, 운영 환경에서는 generic analysis provider selector와 S3_READER_ENABLED를 설정해 같은 backend lifecycle 안에서 S3 객체 조회, reference retrieval, Bedrock 호출, 결과 파싱까지 수행하도록 했습니다.
```
