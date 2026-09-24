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
  Vertex AI and consume the project API quota.

It does not deploy OpenSearch or the backend itself; Kubernetes manifests own workload deployment.

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

Do not run a resource-creating apply until the current project/billing/quota evidence required by
ADR-005 has been refreshed. In particular verify CPU/instance/disk quota, exact zonal capacity,
Vertex model access, and current cost.

If the refreshed values invalidate this one-cluster shape, amend ADR-005 before creating resources.
Do not create a second fallback/evaluation environment.
