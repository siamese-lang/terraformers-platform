# GCP Target Runtime — Free Trial Pre-Apply Check

## Purpose

Use this runbook immediately before the first resource-creating M3-R2 Terraform apply.

It is read-only except where explicitly noted. The goal is to prove that the single target runtime
still fits the active Google Cloud Free Trial account and current quota. Do not create a fallback
environment when one check fails.

## Cost policy

The project target is **Free Trial compatible**, not Always Free.

- Keep the canonical GKE Standard node pool at `0` while idle.
- Use `1 × e2-standard-2` only during an approved live evidence session.
- Return the same node pool to `0` after evidence collection.
- Do not use GPU/TPU nodes.
- Do not create a second evaluation cluster.
- If Free Trial credit is unavailable or expired, stop before paid resource creation unless explicit
  cost approval is provided.

The GKE free-tier credit covers the zonal Standard cluster management fee only. Compute Engine
nodes, persistent disks, networking, and Vertex AI usage remain separately billable or consume Free
Trial credit.

## Diagnostic-shell safety

Do not enable `set -euo pipefail` in the interactive Cloud Shell for this diagnostic runbook.
A missing/disabled API or permission is itself useful readiness evidence and should produce a
`[WARN]` line rather than terminating the shell flow.

Quota fields are repeated values in the Compute API response, so quota commands must use
`--flatten='quotas[]'` before formatting `metric,limit,usage`.

## 1. Resolve active project and account

Run in Cloud Shell:

```bash
PROJECT_ID="$(gcloud config get-value project 2>/dev/null)"

printf 'PROJECT_ID=%s\n' "$PROJECT_ID"

gcloud projects describe "$PROJECT_ID" \
  --format='yaml(projectId,name,lifecycleState)' \
  || echo "[WARN] project describe failed"
```

Do not continue if the selected project is not the intended Terraformers target project.

## 2. Verify billing link

```bash
gcloud billing projects describe "$PROJECT_ID" \
  --format='yaml(projectId,billingEnabled)' \
  || echo "[WARN] billing status query failed"
```

This proves that billing is linked/enabled. It does **not** prove the remaining Free Trial credit.

Before paid resources are created, check **Billing → Overview / Credits** in the Google Cloud
Console and record whether usable Free Trial credit remains. Do not put the billing account ID or
payment information in the repository.

## 3. Inspect global Compute Engine quota

```bash
gcloud compute project-info describe \
  --project="$PROJECT_ID" \
  --flatten='quotas[]' \
  --format='csv[no-heading](quotas.metric,quotas.limit,quotas.usage)' \
  | grep -E '^(CPUS_ALL_REGIONS|IN_USE_ADDRESSES),' \
  || echo "[WARN] global quota query returned no matching data"
```

The expected live shape needs only one 2-vCPU node, but use current quota/usage rather than the
historical 2026-09-16 values.

## 4. Inspect Seoul regional quota

```bash
gcloud compute regions describe asia-northeast3 \
  --project="$PROJECT_ID" \
  --flatten='quotas[]' \
  --format='csv[no-heading](quotas.metric,quotas.limit,quotas.usage)' \
  | grep -E '^(CPUS|E2_CPUS|INSTANCES|DISKS_TOTAL_GB|SSD_TOTAL_GB|IN_USE_ADDRESSES),' \
  || echo "[WARN] Seoul quota query returned no matching data"
```

Record enough free CPU and disk quota for the one-node session and the initial 15 GiB OpenSearch
persistent volume.

## 5. Confirm selected zone and machine type are advertised

```bash
gcloud compute machine-types describe e2-standard-2 \
  --zone=asia-northeast3-a \
  --project="$PROJECT_ID" \
  --format='yaml(name,guestCpus,memoryMb,zone)' \
  || echo "[WARN] e2-standard-2 query failed"
```

This confirms the machine type exists in the zone. It does not guarantee moment-to-moment capacity;
allocation capacity is finally proven only by creation.

If `asia-northeast3-a` is unavailable at creation time, stop and compare another Seoul zone
against ADR-005 rather than creating a second cluster.

## 6. Check GKE server availability

```bash
gcloud container get-server-config \
  --zone=asia-northeast3-a \
  --project="$PROJECT_ID" \
  --format='yaml(defaultClusterVersion)' \
  || echo "[WARN] GKE server config query failed"
```

Do not pin a Kubernetes version unless a compatibility problem requires it; the Terraform root uses
the regular release channel.

## 7. Check required APIs

```bash
gcloud services list \
  --enabled \
  --project="$PROJECT_ID" \
  --filter='config.name:(aiplatform.googleapis.com OR compute.googleapis.com OR container.googleapis.com OR iamcredentials.googleapis.com)' \
  --format='table(config.name,state)' \
  || echo "[WARN] API list query failed"
```

The Terraform root can enable the required services. This check is only to detect the current state
before apply.

## 8. Confirm no duplicate target runtime exists

```bash
gcloud container clusters list \
  --project="$PROJECT_ID" \
  --format='table(name,location,status,currentNodeCount)' \
  || echo "[WARN] GKE cluster list failed"

gcloud compute instances list \
  --project="$PROJECT_ID" \
  --filter='labels.terraformers-runtime=target' \
  --format='table(name,zone,status,machineType)' \
  || echo "[WARN] target VM list failed"
```

There must not be a second Terraformers target cluster created just for evaluation.

## 9. Vertex AI access boundary

The Free Trial can cover Vertex AI usage, but product/model access can be restricted by account and
current service availability. Do not claim model access solely because the API is enabled.

M3-R2 live readiness should make the **smallest real target-provider call** after the target identity
is ready. That call is the model-access proof and its cost is part of the bounded live session.

Target models:

- generation: `gemini-3.8-flash`
- embedding: `gemini-embedding-001`
- embedding output dimension: 1024

A model-access failure blocks M3-R2 readiness; do not silently substitute a different model.

## 10. Apply decision

Proceed to resource creation only when all are true:

- intended project confirmed;
- billing enabled;
- usable Free Trial credit confirmed in the console;
- sufficient current CPU/disk/instance quota;
- `e2-standard-2` advertised in the selected Seoul zone;
- GKE server configuration available;
- no conflicting target runtime;
- no requirement for a second cloud environment.

For the first live session:

```bash
terraform -chdir=infra/terraform/envs/gcp-target-runtime init
terraform -chdir=infra/terraform/envs/gcp-target-runtime plan \
  -var-file=terraform.tfvars \
  -var='node_count=1'
```

Review the plan before apply. Do not use `-auto-approve` for the first live creation.

After the required live evidence is collected, return the same node pool to its idle state:

```bash
terraform -chdir=infra/terraform/envs/gcp-target-runtime plan \
  -var-file=terraform.tfvars \
  -var='node_count=0'
```

Apply that reviewed plan to stop node compute while retaining the single canonical target-runtime
identity.

## M3-R2 live workload scope

After the GKE Terraform apply succeeds, do **not** apply the complete `gcp-target` overlay yet.
The canonical overlay also contains the backend base, whose database/object-storage/JWT runtime
dependencies belong to later integration work.

For M3-R2 readiness, create only the namespace and the internal OpenSearch workload:

```bash
kubectl apply -f infra/kubernetes/overlays/gcp-target/namespace.yaml

kubectl -n terraformers-target apply \
  -f infra/kubernetes/overlays/gcp-target/opensearch-service.yaml \
  -f infra/kubernetes/overlays/gcp-target/opensearch-statefulset.yaml

kubectl -n terraformers-target rollout status statefulset/terraformers-opensearch --timeout=10m
kubectl -n terraformers-target get pods,pvc,svc
```

This proves the GKE/OpenSearch substrate without creating unrelated database, object storage,
identity, ingress, or a second environment. M3-R3 owns corpus/index creation and the first
Spring-facing target-adapter smoke.

Before returning `node_count` to 0, preserve the required readiness evidence. The OpenSearch PVC
remains part of the same canonical cluster while nodes are paused.

## Public references checked 2026-09-24

- Google Cloud Free Program: https://docs.cloud.google.com/free/docs/free-cloud-features
- GKE pricing: https://cloud.google.com/kubernetes-engine/pricing
- GKE cluster lifecycle / scale nodes to zero:
  https://docs.cloud.google.com/kubernetes-engine/docs/get-started/cluster-lifecycle
- Compute Engine general-purpose pricing:
  https://cloud.google.com/products/compute/pricing/general-purpose
- Billing project describe:
  https://docs.cloud.google.com/sdk/gcloud/reference/billing/projects/describe
- Compute project-info describe:
  https://docs.cloud.google.com/sdk/gcloud/reference/compute/project-info/describe
- GKE server config:
  https://docs.cloud.google.com/sdk/gcloud/reference/container/get-server-config
