# M2 Object Byte Storage Parity

**Status: PENDING**

This implementation awaits authoritative CI and evidence review. M2-4 remains **TODO** and M2
remains **ACTIVE** until that review succeeds.

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
| Source binary persisted | FAIL | Expected PASS |
| Source byte read-back | FAIL | Expected PASS, exact 68-byte SHA-256 match |
| `source-image` | HTTP 409 | Expected HTTP 200 |
| `source-object` | HTTP 409 | Expected HTTP 200 with filesystem provider, persisted flag, length, type, and logical location |
| Result binary persisted | FAIL | Expected PASS |
| Terraform inline read-back | PASS | Expected PASS |
| Result integrity | Not externally stored | Expected filesystem SHA-256 = database checksum = API-content SHA-256 |

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

Pending the **M2 Object Byte Storage Verification** workflow and artifact review. No after-state
PASS or checksum is claimed before that run. M2-4 remains **TODO**.
