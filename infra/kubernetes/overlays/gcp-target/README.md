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
- The initial 15 GiB OpenSearch PVC is explicitly bound to the repository-owned
  `terraformers-pd-standard` StorageClass using the GCE PD CSI driver, rather than relying on
  GKE's default balanced disk class.
- The initial PVC size and resource requests are pre-apply values; refresh quota and node headroom
  before live creation.
- The canonical cluster may remain applied with its node pool at 0 while idle. Live verification
  raises the same node pool to 1 and returns it to 0 afterward; this is not a second environment.

The directory is the canonical target overlay, but **M3-R2 live readiness does not apply the full
overlay** because the backend base still expects database/object-storage/JWT runtime dependencies
outside the M3-R2 scope. M3-R2 applies only the namespace and OpenSearch Service/StatefulSet as
documented in `docs/runbooks/gcp-target-free-trial-preapply.md`.

M3-R3 creates the v3 corpus/index and performs the first Spring-facing target-adapter smoke on this
same runtime. Later security/observability/delivery work hardens this same environment rather than
replacing it.
