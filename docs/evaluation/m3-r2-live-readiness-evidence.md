# M3-R2 Live Readiness Evidence

## Status

**PRE-APPLY ACCOUNT/MODEL GATE: PASS WITH ONE FINAL DUPLICATE-RUNTIME CHECK PENDING**

Observed on: 2026-09-24

This evidence records the actual Free Trial account/runtime constraints observed immediately before
the first M3-R2 resource-creating plan/apply. It intentionally omits account email, billing-account
identifier, payment data, and project number.

## Billing and Free Trial

- active project lifecycle: **ACTIVE**
- project billing: **enabled**
- usable Google Cloud Free Trial credit: **confirmed present by operator**
- automatic Always Free claim: **not made**

Interpretation: the bounded one-node live session may consume Free Trial credit. If the trial credit
is later unavailable, the project must stop before paid live apply unless explicit cost approval is
provided.

## Current quota snapshot

### Global

| Quota | Limit | Usage | Required by initial live session |
| --- | ---: | ---: | ---: |
| `CPUS_ALL_REGIONS` | 12 | 0 | 2 |
| `IN_USE_ADDRESSES` | 4 | 0 | no public load balancer planned |

### Seoul (`asia-northeast3`)

| Quota | Limit | Usage | Initial target need |
| --- | ---: | ---: | ---: |
| `CPUS` | 32 | 0 | 2 |
| `E2_CPUS` | 8 | 0 | 2 |
| `INSTANCES` | 8 | 0 | 1 |
| `DISKS_TOTAL_GB` | 2048 | 0 | 30 GiB node boot disk + 15 GiB OpenSearch data disk |
| `SSD_TOTAL_GB` | 250 | 0 | 0 for current `pd-standard` plan |
| `IN_USE_ADDRESSES` | 4 | 0 | no public load balancer planned |

Interpretation: the initial `1 × e2-standard-2` live-session profile fits the observed CPU,
instance, and disk quota with headroom. No quota increase is required for M3-R2.

## Zone and GKE availability

- target zone checked: `asia-northeast3-a`
- `e2-standard-2`: advertised
  - guest CPU: 2
  - memory: 8192 MiB
- Kubernetes Engine API: enabled during the readiness check
- GKE server config: reachable
- observed default cluster version: `1.35.8-gke.1036000`

This proves service/API availability, not moment-to-moment VM allocation capacity. Actual zonal
capacity is finally established by the first reviewed apply.

## Required APIs

The following APIs were confirmed enabled after the readiness check:

- `aiplatform.googleapis.com`
- `compute.googleapis.com`
- `container.googleapis.com`
- `iamcredentials.googleapis.com`

Enabling these APIs did not create the GKE cluster, node VM, OpenSearch disk, or another live
environment.

## Vertex AI access

### Generation

A minimal real call to `gemini-3.8-flash` through the global Vertex AI endpoint succeeded.

Observed:

- model returned the requested `OK` response;
- finish reason: `STOP`;
- model version: `gemini-3.8-flash`;
- traffic type: `ON_DEMAND`.

This is sufficient M3-R2 evidence that the selected generation model is reachable from the active
account. It is not an AI quality baseline.

### Embedding

A minimal real `gemini-embedding-001` request using `RETRIEVAL_QUERY` and requested
`outputDimensionality=1024` completed successfully. The returned metadata included
`billableCharacterCount: 24`.

This establishes model/API access. The M3-R3 corpus/index smoke remains responsible for proving the
actual persisted/query vector shape end to end.

## Storage-cost correction discovered during the gate

GKE defaults do not guarantee the desired low-cost `pd-standard` OpenSearch disk. Therefore the
target runtime now explicitly:

- enables the GCE Persistent Disk CSI driver;
- defines `terraformers-pd-standard` with provisioner `pd.csi.storage.gke.io`;
- sets `type: pd-standard`;
- uses `WaitForFirstConsumer`; and
- binds the OpenSearch PVC explicitly to that StorageClass.

This keeps the 15 GiB OpenSearch data claim aligned with the Free Trial cost boundary instead of
silently provisioning the default balanced disk class.

## Remaining pre-apply item

Before the first Terraform plan/apply that creates resources, perform one final read-only duplicate
runtime check after GKE API enablement:

- list GKE clusters in the project;
- list Compute Engine instances carrying the Terraformers target label.

Expected result: no existing conflicting Terraformers target runtime.

If a conflicting runtime exists, stop and reconcile it. Do not create a second cluster.

## Decision

Current observed Free Trial, quota, API, zone, generation-model, and embedding-model evidence
supports continuing M3-R2 with the existing single-target design.

The next action is **Terraform plan review**, not a second architecture decision and not a separate
test environment.
