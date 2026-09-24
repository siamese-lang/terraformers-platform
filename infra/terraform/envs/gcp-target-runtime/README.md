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

## Pre-apply gate

Do not run a resource-creating apply until the current project/billing/quota evidence required by
ADR-005 has been refreshed. In particular verify CPU/instance/disk quota, exact zonal capacity,
Vertex model access, and current cost.

If the refreshed values invalidate this one-cluster shape, amend ADR-005 before creating resources.
Do not create a second fallback/evaluation environment.
