# M2 Upload → Analysis → Terraform Baseline

**Status: PASS**

This verification records current behavior; it does not complete M2-4 or select a persistence implementation.

## Reproduction identity

- Base SHA: `37d338de9a32e140843d8fbec501196644ee05b9`
- Runtime: `infra/kubernetes/overlays/portable-authenticated` with disabled object reader, metadata-only object writer, stub analysis provider, disabled embedding provider, and disabled retrieval.
- Exact command: `bash scripts/checks/kind-portable-upload-analysis-baseline.sh`
- Upload input: generated `architecture.png`, 68 bytes, SHA-256 `431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460`.
- Identity: ephemeral RSA-2048 signing key and local JWKS fixture; no production IdP or cloud call.

## Evidence to be classified

The workflow artifact `m2-upload-analysis-baseline` records:

1. The upload HTTP status and response fields (`analysisJobId`, `projectId`, `sourceFileId`, source location, storage provider, binary-persisted flag, status, and analysis mode).
2. The source `ARCHITECTURE_IMAGE` row, including compatibility-named `s3_bucket`/`s3_key` fields, without interpreting those names as active AWS evidence.
3. The analysis terminal status, provider, result linkage, object key, and failure reason.
4. Whether successful stub analysis traversed Terraform draft validation and result storage.
5. The generated Terraform row, inline-content presence, API read-back, and SHA-256/checksum comparison.
6. Source and result binary-persistence classifications plus observed `/source-image` and `/source-object` HTTP statuses.
7. Project and project-tree responses needed to establish source, analysis, result, and Terraform-node linkage.

## Authoritative result

GitHub Actions **M2 Upload Analysis Baseline Verification** run #1 passed at head
`b156cdd87e54d3a4041fe7b9231f65eea35ab9d1`. The workflow succeeded because the current
behavior was executed and classified; the persistence failures below are the observed baseline gap,
not harness failures.

```text
runtime_ready=PASS
authenticated_upload=PASS
project_created=PASS
source_file_registered=PASS
analysis_job_created=PASS
analysis_terminal=PASS
analysis_status=SUCCEEDED
analysis_provider=stub-integrated-java
terraform_validation=PASS
result_file_registered=PASS
terraform_inline_readback=PASS
terraform_checksum_match=PASS
source_binary_persisted=FAIL
source_byte_readback=FAIL
source_image_http_status=409
source_object_http_status=409
result_binary_persisted=FAIL
first_confirmed_gap=source_binary_not_persisted
cloud_credentials_required=false
```

The authenticated upload returned HTTP 201 and created project `1`, source file `1`, and analysis
job `eb434ea5-72f4-4091-96cf-0dce357f2674`. The source row recorded
`storage_provider=metadata-only`, `binary_persisted=0`, and the expected 68-byte upload metadata.
The job reached `SUCCEEDED` through provider `stub-integrated-java`; generated Terraform file
`2` was registered with non-empty inline content and checksum
`6d1090d8c6dab2944a745322324a3646bfdc933f4bb0a267d847493c6e5d448c`. The owner API returned the
Terraform draft with HTTP 200 and its computed SHA-256 matched the database checksum.

Project metadata and project-tree reads both returned HTTP 200 and linked the source file, successful
analysis job, result file, and Terraform node. In contrast, the source file remained
`binary_persisted=false`; both `/source-image` and `/source-object` returned HTTP 409. The
generated Terraform row also recorded `binary_persisted=0`. Therefore inline database read-back is
working, while source and result object-byte persistence is not.

The **first confirmed M2-4 gap** is `source_binary_not_persisted`. This evidence also confirms a
second related limitation: result object bytes are not persisted by the current metadata-only
writer. No storage product or implementation is selected by this baseline.

## Not covered

- A production object-storage adapter or product decision (no filesystem, MinIO, GCS, S3, PVC, or database blob is selected).
- Live Terraform provider initialization or apply.
- Production identity-provider or cloud credentials.
- The M2-3 non-owner authorization matrix.
- M2-5 end-to-end UX behavior.

M2-4 remains **TODO**. The next task is to select the smallest deterministic test-runtime storage mechanism that fixes the confirmed object-byte persistence gap through the repository technology decision gate, then rerun this same-condition evidence.
