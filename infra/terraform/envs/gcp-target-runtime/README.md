# GCP Target Runtime Foundation

This Terraform root implements the single reusable GCP runtime selected by ADR-005.

It is **not** an evaluation-only environment. M3 live evaluation, M4 AI improvements, later
observability/failure work, and M9 closure reuse this same cluster and IaC.

## Scope

The root creates:

- required Compute Engine, GKE, Vertex AI and IAM Credentials APIs;
- one dedicated VPC/subnet;
- one zonal GKE Standard cluster and node pool;
- node-level `vm.max_map_count=262144` required by OpenSearch;
- Workload Identity Federation for GKE;
- project IAM bindings that allow only the Terraformers backend Kubernetes ServiceAccount to call
  Vertex AI and consume the project API quota;
- one Seoul Artifact Registry Docker repository for immutable backend images, with repository-scoped
  writer access for the dedicated GitHub image publisher and reader access for the GKE node and
  Terraform plan identities.

Artifact Registry is created only by the separately reviewed `delivery-foundation` apply contract.
The dedicated publisher service account/WIF trust is bootstrapped separately and must exist before
that apply. This Terraform root does not publish an image or deploy the backend; Kubernetes
manifests own workload deployment.


## Case C runtime dependency foundation

The same canonical root also owns the bounded runtime dependencies required before the immutable
backend image can be deployed:

- Secret Manager API plus two empty MariaDB secret containers;
- one private Seoul runtime-object bucket used for upload and result objects;
- bucket-scoped `roles/storage.objectUser` for the backend Workload Identity principal;
- one dedicated `terraformers-mariadb` service account;
- one `e2-medium` MariaDB VM with a dedicated 20 GiB `pd-balanced` data disk;
- a TCP/3306 firewall limited to the target GKE subnet and Pod CIDR;
- GKE native Secret Sync, without automatic rotation.

Secret payload versions are deliberately excluded from Terraform. Live execution is split into
separate approvals:

1. bootstrap the additional plan/apply IAM needed by this unit;
2. apply `runtime-secret-foundation` (Secret Manager API + two empty secret containers);
3. add initial secret versions with `scripts/deploy/bootstrap_gcp_runtime_secrets.sh add-versions`;
4. apply `runtime-dependencies` with an exact `mariadb:11.4@sha256:<digest>` input;
5. apply isolated Kubernetes prerequisites with the manual
   `GCP Target Runtime Dependencies` workflow.

The runtime dependency workflow rechecks the current GKE version and stops below 1.33. The operator
gate on 2026-10-01 observed control plane and node pool version `1.35.8-gke.1225000`, so the
selected Secret Sync path is currently compatible.

The isolated Kubernetes prerequisite surface lives under
`infra/kubernetes/overlays/gcp-target/runtime-dependencies`. It creates the secret-sync
ServiceAccount, SecretProviderClass/SecretSync, a stable in-cluster MariaDB Service whose
EndpointSlice is populated from the VM private IP, and the internal JWKS fixture surface. It does
not include or apply `terraformers-backend` Deployment.

The ordinary target `activate` / `idle` / `runtime-check` workflow detects whether these runtime
resources already exist in canonical state and preserves their enable flags and immutable MariaDB
digest. It must not plan deletion of Case C dependencies merely because a scale operation is being
performed.

## Free Trial operating profile

This root is designed to be usable with a Google Cloud Free Trial account, not to claim that the
entire runtime is Always Free.

- The default node pool size is `0`, so an applied but idle zonal Standard cluster does not keep a
  Compute Engine node running.
- For an approved live verification session, set `node_count=1`. The initial machine type is
  `e2-standard-2` (2 vCPU / 8 GiB).
- After collecting the required evidence, return `node_count=0` with this same Terraform root.
  This pauses node compute without creating a second environment.
- The initial node boot disk uses 30 GiB `pd-standard`; OpenSearch data uses a separate 15 GiB
  claim in the target Kubernetes overlay.
- The GKE free-tier credit covers one zonal Standard cluster's management fee, but node compute,
  persistent disks, networking, and Vertex AI usage remain billable/credit-consuming.
- If the active account no longer has Free Trial credit, do not perform a paid live apply without
  explicit cost approval.

A typical live session therefore changes only `node_count: 0 → 1 → 0`. The cluster/IaC identity
remains the same across M3 and later milestones.

## Pre-apply gate

The canonical state for this root uses a versioned GCS bucket and the
`gcp-target-runtime` prefix. Bootstrap the bucket and the GitHub plan identity using
[`gcp-target-plan-bootstrap.md`](../../../../docs/runbooks/gcp-target-plan-bootstrap.md)
before `terraform init`. The bucket name is supplied with
`-backend-config="bucket=BUCKET_NAME"`; the prefix is supplied with
`-backend-config='prefix=gcp-target-runtime'`. Never initialize this root with a local backend
for a live plan. The GitHub workflow can run a plan only; no apply identity has been created.

Do not run a resource-creating apply until the current project/billing/quota evidence required by
ADR-005 has been refreshed. In particular verify CPU/instance/disk quota, exact zonal capacity,
Vertex model access, and current cost.

If the refreshed values invalidate this one-cluster shape, amend ADR-005 before creating resources.
Do not create a second fallback/evaluation environment.
