# Case C GCP Full-Backend Adapter Readiness

## Status

**IMPLEMENTED / CI PENDING**

Execution base:

`2a58b35b22e1faf9b841a987cd158b3d0a47a6ae`

Work Package:

`.agents/work-packages/case-c-gcp-full-backend-adapter-readiness-v1.yml`

## 1. Problem found before cloud substrate creation

The canonical GCP overlay selects:

- `SPRING_PROFILES_ACTIVE=prod,gcp-target`;
- `ANALYSIS_PROVIDER=vertex`;
- `EMBEDDING_PROVIDER=vertex`;
- `RETRIEVAL_MODE=REQUIRED`.

However, the production `RuntimeAdapterContractValidator` still contained an AWS-era invariant:
whenever retrieval was enabled, the analysis provider had to be Bedrock.

That means the full production Spring application could reject the already-approved
Vertex/OpenSearch GCP runtime even though Case A's ephemeral evaluation path had proven Vertex,
embedding and OpenSearch independently.

Case A did not expose this defect because the evaluation pod did not boot the full `prod` backend
contract.

A second readiness gap remained from the Case C compatibility gate: Cloud Storage mapped cleanly to
the provider-neutral object contract but no `gcs` runtime adapter existed.

Creating MariaDB/Storage/Registry resources before closing these application-side gaps would produce
cloud infrastructure that the full backend could not yet consume authoritatively.

## 2. Implemented correction

### Provider-neutral retrieval startup contract

Removed the historical rule that active retrieval requires
`AnalysisProviderType.BEDROCK`.

The validator still:

- resolves and rejects unsupported analysis-provider values through `AnalysisProviderType`;
- resolves and rejects unsupported embedding-provider values through `EmbeddingProviderType`;
- requires an enabled embedding provider when retrieval is active;
- requires Bedrock generation model configuration when Bedrock is selected;
- requires Bedrock embedding model configuration when Bedrock embedding is selected;
- requires OpenSearch endpoint/index/vector/content fields for active retrieval;
- keeps SQS/Cognito adapter-specific requirements unchanged.

A deterministic test now proves that Vertex generation + Vertex embedding + REQUIRED retrieval does
not require any Bedrock setting.

## 3. GCS application adapter

Added Google Cloud Storage Java client `2.74.0`.

The new production adapters are:

- `GcsObjectReader`;
- `GcsObjectWriter`, also implementing `ObjectRemover`.

Default construction uses:

`StorageOptions.getDefaultInstance().getService()`

so the production path uses Application Default Credentials rather than a service-account key.

Google documents that Cloud Java client libraries use ADC in Google Cloud environments, including
GKE, without application code needing a key file.

### Reader mapping

- logical identity remains bucket + key;
- absent `Storage.get(BlobId)` maps to `NOT_FOUND`;
- GCS HTTP 404 maps to `NOT_FOUND`;
- other `StorageException` values map to `UPSTREAM_FAILURE`;
- content type, size, provider eTag and update time map into `ObjectMetadata`;
- exact object bytes come from the returned `Blob`.

Provider eTag remains metadata only; the application's own SHA-256 behavior is unchanged.

### Writer/remover mapping

- text writes UTF-8 bytes;
- binary writes preserve exact bytes;
- writes retain the caller's bucket and key;
- writing the same bucket/key again uses the same GCS object identity;
- returned provider eTag is recorded only as provider metadata;
- `Storage.delete` returning false for an already-absent object is treated as success;
- write/delete `StorageException` maps to `UPSTREAM_FAILURE`.

## 4. Runtime selector and GCP overlay

`StorageRuntimeProperties` now accepts `gcs` for both reader and writer.

The canonical GCP target overlay now sets:

```text
OBJECT_READER_PROVIDER=gcs
OBJECT_WRITER_PROVIDER=gcs
```

No bucket name, credential, secret value or IAM binding is committed by this Work Package.

## 5. Deterministic tests

Added/updated tests cover:

- GCS selector normalization;
- Vertex + Vertex embedding + REQUIRED retrieval startup contract;
- GCS metadata mapping;
- exact byte read;
- absent-object NOT_FOUND behavior;
- GCS 404 NOT_FOUND behavior;
- non-404 upstream failure behavior;
- text write;
- binary overwrite at the same logical key;
- idempotent absent delete;
- write/delete failure translation.

## 6. Cloud boundary

This Work Package intentionally does **not**:

- create a GCS bucket;
- grant Storage IAM;
- create a MariaDB VM or disk;
- select or create Secret Manager resources;
- create Artifact Registry;
- publish an image;
- deploy the backend;
- run a live GCS or Vertex call.

Those remain later, separately gated runtime-substrate/readiness work.

## 7. Acceptance pending

Repository implementation is complete. Acceptance requires:

1. targeted/new tests compile and pass;
2. existing backend verification remains green;
3. no production/domain schema change appears;
4. no cloud/IAM mutation is introduced.

CI is the next evidence source. No second implementation Work Package is authorized automatically.

## External references

- Cloud Storage Java client libraries / ADC:
  https://docs.cloud.google.com/storage/docs/reference/libraries
- Java authentication in Google Cloud environments:
  https://docs.cloud.google.com/java/docs/authentication
- Cloud Storage Java `Storage.get` absent-object behavior:
  https://docs.cloud.google.com/java/docs/reference/google-cloud-storage/latest/com.google.cloud.storage.Storage
- Cloud Storage Java `Blob` content and metadata:
  https://docs.cloud.google.com/java/docs/reference/google-cloud-storage/latest/com.google.cloud.storage.Blob
