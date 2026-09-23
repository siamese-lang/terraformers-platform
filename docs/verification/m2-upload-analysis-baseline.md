# M2 Upload → Analysis → Terraform Baseline

**Status: PENDING**

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

The authoritative upload response, database state, analysis lifecycle, Terraform result, inline read-back, source byte read-back, result object persistence, and **first confirmed gap** remain `PENDING` until the GitHub Actions run is reviewed. A failed persistence classification is an expected baseline observation and does not itself fail the evidence collector. If an earlier stage fails, later stages remain `NOT_COVERED` rather than inferred.

## Not covered

- A production object-storage adapter or product decision (no filesystem, MinIO, GCS, S3, PVC, or database blob is selected).
- Live Terraform provider initialization or apply.
- Production identity-provider or cloud credentials.
- The M2-3 non-owner authorization matrix.
- M2-5 end-to-end UX behavior.

M2-4 remains **TODO**. After authoritative evidence review, the next task is to select the smallest fix for the first observed gap through the repository technology decision gate.
