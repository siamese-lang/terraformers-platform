# M2 Object Byte Storage Parity

**Status: PASS**

The authoritative CI and artifact review passed. M2-4 is **DONE** and M2 remains **ACTIVE** with
M2-5 as the first incomplete task.

## Observed baseline gap and change gate

PR #24's authoritative baseline used a metadata-only writer and disabled reader. Authenticated
upload and stub analysis succeeded, as did Terraform inline read-back, but neither source nor result
object bytes were persisted. Source image and metadata reads returned HTTP 409.

ADR-004's four questions are satisfied: (1) missing object bytes and HTTP 409 were observed, (2)
the baseline run reproduces them with a fixed input, (3) a real reader/writer directly addresses the
gap, and (4) the same authenticated flow and input can validate the change. The accepted change is
a minimal JDK local-filesystem **test-runtime adapter**, not a production storage selection.

## Decision and rejected alternatives

The `filesystem` selector activates one `ObjectReader`/`ObjectWriter` implementation only when both
reader and writer selectors explicitly choose it. It maps logical bucket/key values beneath the
absolute normalized root `/tmp/terraformers-object-store`, stores exact bytes, and stores content
type in a deterministic `.terraformers-meta` sidecar. SHA-256 is the deterministic eTag.

- MinIO adds a server, container, and API dependency larger than the observed gap.
- GCS would prematurely select a production cloud product before M9 evidence.
- S3 would restore the historical AWS target.
- A database BLOB would require an unnecessary schema/domain change.
- An in-memory map would not provide externally inspectable file persistence or strong result-byte evidence.

No new dependency, cloud credential, PVC, storage framework, or production endpoint is introduced.
Normalized bucket and object paths must remain beneath their root and bucket root respectively;
parent traversal, absolute escape, and mixed nested traversal are rejected.

## Reproduction identity

- Base SHA: `dd0d9feb3b2e5e8d1f2c5d7b2c114790ab6174ae` (the checked-out current main; no Git remote is configured in the supplied checkout).
- Runtime overlay: `infra/kubernetes/overlays/portable-object-store` composed over `portable-authenticated`.
- Runtime selectors: filesystem reader/writer, stub analysis, disabled embedding and retrieval.
- Input: `architecture.png`, exactly 68 bytes, SHA-256 `431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460`.
- Identity/database: the same ephemeral JWT/JWKS fixture and MariaDB analysis chain as the baseline.
- Exact command: `bash scripts/checks/kind-portable-upload-analysis-filesystem.sh`.
- Artifact: `artifacts/m2-object-byte-storage/summary.txt`.

## Before/after evidence matrix

| Check | Before — PR #24 baseline | After — filesystem fixture (pending authoritative CI) |
|---|---|---|
| Source binary persisted | FAIL | PASS |
| Source byte read-back | FAIL | PASS — exact 68-byte SHA-256 match |
| `source-image` | HTTP 409 | HTTP 200 |
| `source-object` | HTTP 409 | HTTP 200 with `filesystem`, persisted flag, 68-byte length, and `image/png` |
| Result binary persisted | FAIL | PASS |
| Terraform inline read-back | PASS | PASS |
| Result integrity | Not externally stored | PASS — filesystem SHA-256 = database checksum = API-content SHA-256 |

The verifier also checks project and project-tree HTTP linkage, source and generated-file database
classification, the successful `stub-integrated-java` lifecycle, and direct object files inside the
backend container. Evidence artifacts do not include the ephemeral private key, raw JWT, or database
password.

## Durability boundary

Filesystem object bytes are guaranteed only for the lifetime of the current container/pod fixture.
This task does **not** validate pod recreation durability, node failure, multi-replica shared
storage, cross-replica locking/concurrency, backup/restore, production retention, HA, PVC design, or
production GCP storage. Those remain M5/M9 or later evidence-gated concerns and are not M2-4 success
criteria.

## Authoritative result

**M2 Object Byte Storage Verification** run #2 passed at validated head
`99fa2864ae3ef86536332172c4a3e50be612e686`. The first run stopped before Kind because the
unit test contained an incorrect expected SHA-256 constant for byte sequence `00 01 02 FF`; the
implementation returned the correct digest, the test vector was corrected, and all regression plus
runtime verification was rerun.

The authoritative summary recorded:

```text
runtime_ready=PASS
authenticated_upload=PASS
project_created=PASS
source_file_registered=PASS
source_binary_persisted=PASS
source_byte_readback=PASS
source_byte_checksum_match=PASS
source_object_metadata=PASS
analysis_job_created=PASS
analysis_terminal=PASS
analysis_status=SUCCEEDED
analysis_provider=stub-integrated-java
terraform_validation=PASS
result_file_registered=PASS
result_binary_persisted=PASS
result_object_exists=PASS
terraform_inline_readback=PASS
terraform_checksum_match=PASS
result_object_checksum_match=PASS
project_linkage=PASS
project_tree_linkage=PASS
source_storage_provider=filesystem
result_storage_provider=filesystem
source_image_http_status=200
source_object_http_status=200
first_confirmed_gap=none
cloud_credentials_required=false
```

The source database row recorded `storage_provider=filesystem`, `binary_persisted=1`, and
`size_bytes=68`. The HTTP source read-back SHA-256 and the directly inspected filesystem source
object both matched the fixed input SHA-256
`431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460`.

The generated Terraform row recorded `storage_provider=filesystem`, `binary_persisted=1`, and
non-empty inline content. The directly inspected filesystem result SHA-256
`6d1090d8c6dab2944a745322324a3646bfdc933f4bb0a267d847493c6e5d448c`
matched both the database checksum and API-content SHA-256. Project metadata and project-tree
linkage also passed.

The filesystem implementation remains a deterministic **test-runtime adapter only**. It does not
select production storage and does not claim persistence across pod/container recreation, node
failure, multiple replicas, backup/restore, HA, or production retention. Those concerns remain
outside M2-4.
