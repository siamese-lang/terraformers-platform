# GCP Target Kubernetes Overlay

This overlay is the workload side of the single target runtime selected by ADR-005.

It reuses the provider-neutral backend base and adds the target AI/RAG settings plus one internal
OpenSearch 3.8.0 StatefulSet. It is not an evaluation-only overlay.

## Current M3-R2 boundary

- OpenSearch is single-node and exposed only through a ClusterIP Service.
- The OpenSearch security plugin is disabled for the initial internal-only portfolio runtime so the
  existing provider-neutral HTTP transport can be proven without introducing another credential
  system. This is **not** a public/HA production-security claim.
- The backend image name is intentionally a non-live immutable placeholder because target registry
  delivery is not selected in M3-R2.
- `GOOGLE_CLOUD_PROJECT` and the backend runtime Secret are intentionally not committed. Deployment
  tooling supplies them from the approved project/runtime configuration.
- The initial 30 GiB OpenSearch PVC and resource requests are pre-apply values; refresh quota and
  node headroom before live creation.

M3-R3 creates the v3 corpus/index and performs the first serving-path smoke on this same runtime.
Later security/observability/delivery work hardens this same environment rather than replacing it.
