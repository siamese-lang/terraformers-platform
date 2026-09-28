# Reference Retrieval

## 1. Purpose

Reference retrieval is the backend boundary that replaces the OpenSearch query portion of the original Python analysis service.

The purpose is not to treat OpenSearch as the core project topic. The purpose is to make the backend analysis flow explicit:

```text
source object metadata
  -> image / service analysis text
  -> embedding query
  -> OpenSearch/AOSS reference search
  -> ranked reference documents
  -> Terraform draft generation
```

## 2. Current implementation

The public baseline currently uses `StubReferenceRetriever`.

This stub returns deterministic reference documents so that the following can be tested without AWS credentials:

- Maven compile/test
- analysis provider wiring
- API response behavior
- RDB analysis job state transition
- local smoke scripts

## 3. Target production implementation

A production implementation behind `ReferenceRetriever` should do the following:

1. Build a reference query from image analysis text, detected AWS service names, or project context.
2. Invoke the Bedrock embedding model.
3. Query OpenSearch/AOSS with the configured index and vector field.
4. Return ranked reference documents to the `AnalysisProvider`.
5. Fail with classified errors when index, field, IAM permission, endpoint, or timeout problems occur.

## 4. Runtime configuration

The implementation must use runtime configuration, not hardcoded values.

Required values:

- `EMBEDDING_PROVIDER=bedrock`
- `BEDROCK_EMBEDDING_MODEL_ID`
- `OPENSEARCH_ENDPOINT`
- `INDEX_NAME`
- `VECTOR_FIELD_NAME`
- `CONTENT_FIELD_NAME`
- AWS region and runtime identity

The selector is provider-neutral. The model ID is owned by the selected Bedrock compatibility
adapter, while `OpenSearchReferenceRetriever` validates only the index/retrieval contract. These
values are provided through `application-prod.yml`, environment variables, and the Secret/runtime config delivery mechanism described in the deployment documents.

## 5. Failure classification

OpenSearch/AOSS failures should be classified separately from Bedrock and S3 failures.

Recommended categories:

| Failure | Likely check |
|---|---|
| endpoint missing | runtime config / Secret sync |
| access denied | IAM role / IRSA / policy |
| index missing | OpenSearch bootstrap / Terraform output |
| vector field mismatch | index mapping / `VECTOR_FIELD_NAME` |
| timeout | network path / OpenSearch health / query size |
| empty result | reference data ingestion / embedding mismatch |

## 6. GCP target corpus ingestion

The target runtime uses the one-shot `scripts/rag/ingest-gcp-target-corpus.py` adapter. It is not a
serving process. The adapter reads all corpus, index, field, model, dimension, and document-count
identity from the committed v3 manifest, validates that identity with the existing corpus contract
checker, obtains short-lived Application Default Credentials for Vertex AI, and talks to the
cluster-internal OpenSearch HTTP endpoint without AWS credentials.

Install the isolated GCP ingestion dependencies and run the adapter from a trusted operator shell
that can reach the internal service:

```bash
python3 -m venv /tmp/terraformers-gcp-ingestion
/tmp/terraformers-gcp-ingestion/bin/pip install -r scripts/rag/requirements-gcp.txt
/tmp/terraformers-gcp-ingestion/bin/python scripts/rag/ingest-gcp-target-corpus.py \
  --project "$GOOGLE_CLOUD_PROJECT" \
  --location "${GOOGLE_CLOUD_LOCATION:-global}" \
  --opensearch-endpoint http://terraformers-opensearch:9200 \
  --receipt /tmp/terraformers-reference-v3-ingestion-receipt.json
```

The caller must provide ADC/Workload Identity with Vertex prediction permission and network access
to OpenSearch. No service-account key, endpoint, AWS region, S3 bucket, AOSS identity, or receipt
store is embedded. The tool creates the missing index from `index-schema.json`, records the full
contract checksum in mapping metadata, and rejects an existing index whose mapping/checksum is not
the committed v3 contract. Documents use `documentId` as the OpenSearch `_id`, so interrupted runs
and reruns retain stable identity and skip already-present documents. Success additionally requires
exactly 128 v3 documents and a non-empty representative 1024-dimensional k-NN result.

Vertex ingestion requests use `RETRIEVAL_DOCUMENT` and `outputDimensionality=1024`. This is
deliberately separate from `VertexEmbeddingProvider`, which continues to use `RETRIEVAL_QUERY` for
serving-time search queries. The JSON receipt written to stdout and optionally to `--receipt`
contains the corpus version, full checksum, count, index/model/dimension identity, representative
hit IDs, outcome, and elapsed seconds. The receipt contains no document content or credentials.

## 7. Portfolio explanation

```text
Python 서비스가 담당하던 OpenSearch 검색을 별도 런타임으로 유지하지 않고, Spring Boot backend의 ReferenceRetriever port로 분리했습니다. 현재는 로컬/CI 검증 가능한 stub 구현을 두고, 운영 구현에서는 Bedrock embedding과 OpenSearch/AOSS k-NN 검색을 붙일 수 있도록 runtime config와 failure boundary를 문서화했습니다.
```
