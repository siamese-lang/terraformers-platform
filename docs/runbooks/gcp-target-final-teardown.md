# GCP Target Runtime Final Teardown

## Status

**CLOSURE PROCEDURE — USER AUTHORIZED 2026-10-02**

This procedure is for final project shutdown after Case A/B/C portfolio closure. Its purpose is to
stop ongoing GCP resource charges while preserving a fail-closed deletion boundary.

It does not change application/runtime architecture. It removes the live GCP target and then removes
the bootstrap state/federation resources only after runtime residual verification passes.

## Cost-bearing resources covered

The protected runtime teardown removes the canonical Terraform-managed target, including:

- GKE cluster and node pool;
- custom target VPC/subnet;
- dedicated GKE node service account and target IAM bindings;
- Artifact Registry backend repository and stored images;
- Secret Manager MariaDB secret containers/versions;
- runtime GCS bucket after its application objects are removed;
- MariaDB Compute Engine VM;
- MariaDB boot disk and dedicated 20 GiB Persistent Disk;
- MariaDB firewall and service account;
- runtime bucket/secret IAM;
- Terraform-managed project-service state entries.

Before destroying the GKE cluster, the workflow removes the `terraformers-target` Kubernetes
namespace. The only accepted PVC is:

`data-terraformers-opensearch-0`

with storage class:

`terraformers-pd-standard`

The workflow captures its exact CSI disk handle and requires the corresponding Compute Engine disk
to disappear. If CSI cleanup does not remove it, the workflow deletes only that previously captured
PVC-owned disk after verifying that it has no users.

## Stage 0 — immediate node idle

If the final runtime destroy has not yet started, returning the GKE node pool to zero stops node
compute while the teardown path is reviewed.

Use the current exact `main` SHA:

```bash
gh workflow run gcp-target-terraform-apply.yml \
  --repo siamese-lang/terraformers-platform \
  --ref main \
  -f operation=idle \
  -f expected_sha=<EXACT_CURRENT_MAIN_SHA> \
  -f confirmation=SCALE_GCP_TARGET_TO_ZERO
```

This does **not** remove the MariaDB VM/PD, cluster management surface, OpenSearch persistent disk,
runtime bucket, registry, or secrets. It is only an interim cost reduction.

## Stage 1 — protected runtime teardown

Before dispatching the teardown workflow, refresh the protected apply identity once from an
independently authenticated Cloud Shell administrator:

```bash
cd ~/terraformers-platform
git switch main
git pull --ff-only
bash scripts/deploy/bootstrap_gcp_target_apply.sh apply
```

For final teardown this bootstrap adds the narrow
`roles/iam.serviceAccountDeleter` permission to `terraformers-apply`. The existing
`roles/iam.serviceAccountCreator` role can create/list/get service accounts but does not grant
`iam.serviceAccounts.delete`; Terraform destroy therefore must not begin until the delete
permission is present.

The teardown workflow independently verifies the full required delete-permission set with
`testIamPermissions` and stops before mutation if any required permission is missing.

Workflow:

`.github/workflows/gcp-target-runtime-teardown.yml`

Initial full teardown contract:

- exact source: current trusted `main`;
- expected managed delete count: `29`;
- confirmation: `DESTROY_REVIEWED_GCP_TARGET_RUNTIME_29`;
- creates: forbidden;
- updates: forbidden;
- replacements: forbidden;
- managed addresses outside the reviewed target set: forbidden.

The plan job uses the existing read-only `gcp-target-plan` identity and must first produce a
29-delete destroy plan.

The apply job uses the existing protected `gcp-target-apply` identity. It then:

1. rechecks the canonical remote state count;
2. removes the target Kubernetes namespace and OpenSearch PVC disk;
3. deletes runtime GCS application objects so the protected `force_destroy=false` bucket can be
   removed cleanly;
4. recreates a fresh destroy plan;
5. runs the same 29-delete plan contract again;
6. applies that exact saved plan;
7. requires the Terraform managed state to become empty;
8. verifies the named GKE, MariaDB VM/PD, runtime bucket, Artifact Registry repository, MariaDB
   secrets and target VPC are absent.

Dispatch:

```bash
MAIN_SHA="$(gh api repos/siamese-lang/terraformers-platform/commits/main --jq .sha)"

gh workflow run gcp-target-runtime-teardown.yml \
  --repo siamese-lang/terraformers-platform \
  --ref main \
  -f execute_teardown=true \
  -f expected_sha="$MAIN_SHA" \
  -f expected_delete_count=29 \
  -f confirmation=DESTROY_REVIEWED_GCP_TARGET_RUNTIME_29
```

Do not run Stage 2 until this workflow succeeds and the residual summary is reviewed.

## Stage 1 failure and recovery rule

If teardown fails:

- do not delete the state bucket;
- do not delete the WIF pool or CI service accounts;
- inspect the first residual owner;
- do not broaden the reviewed address set or permissions just to force completion;
- determine the exact remaining managed state count before any recovery dispatch.

The initial full run expects 29 deletes. A recovery run may use a smaller
`expected_delete_count` only when:

1. the canonical remote state contains exactly that many managed instances;
2. every remaining address is still inside the original reviewed 29-address teardown set;
3. the plan contains delete actions only;
4. the confirmation is exactly
   `DESTROY_REVIEWED_GCP_TARGET_RUNTIME_<COUNT>`.

The plan gate rejects unknown addresses, creates, updates and replacements. This lets a partial
Terraform destroy resume without pretending the original 29 resources still exist.

The first observed recovery case on 2026-10-03 was a GCE 409 conflict while Terraform attempted to
detach `terraformers-mariadb-data` concurrently with deletion of the MariaDB VM. The VM and the
other runtime resources had already completed deletion; the remaining PD must be handled through
this reviewed-subset recovery path rather than by rerunning the original 29-delete contract.

## Stage 2 — final bootstrap cleanup

The Terraform state bucket and GitHub-to-GCP bootstrap identities are deliberately outside the
runtime Terraform state so that they survive Stage 1 recovery.

After Stage 1 succeeds, use an independently authenticated Cloud Shell administrator:

```bash
cd ~/terraformers-platform
git switch main
git pull --ff-only

bash scripts/deploy/cleanup_gcp_target_bootstrap.sh check
bash scripts/deploy/cleanup_gcp_target_bootstrap.sh apply DELETE_GCP_TARGET_BOOTSTRAP
```

The script refuses to proceed while any of these runtime residuals still exist:

- GKE cluster;
- MariaDB VM;
- MariaDB dedicated data disk;
- target VPC;
- runtime GCS bucket;
- Artifact Registry backend repository;
- MariaDB Secret Manager secrets.

After that gate it removes:

1. every live and noncurrent object version in
   `terraformers-platform-tfstate-21647422237`;
2. the state bucket itself;
3. project IAM bindings belonging to the exact plan/apply/image-publisher service accounts;
4. `terraformers-plan`, `terraformers-apply`, and `terraformers-image-publish` service accounts;
5. the `terraformers-github` Workload Identity Pool (and its provider).

Cloud Storage Object Versioning means deleting only the current Terraform state object is
insufficient; all object versions are removed before the bucket is deleted.

## Intentionally retained non-billable configuration

The cleanup does not disable Google APIs merely because they were used by this project. An enabled
API does not by itself imply an idle resource charge, and disabling shared APIs can break unrelated
project work.

GitHub Environments and non-secret environment variables also remain repository configuration, not
GCP billable resources. They may be removed later as repository hygiene after GCP closure is
confirmed.

## Final acceptance

GCP runtime closure is complete only when:

- canonical Terraform managed state is empty before the state bucket is removed;
- target GKE cluster is absent;
- target VPC/subnet are absent;
- MariaDB VM and disks are absent;
- OpenSearch PVC disk is absent;
- runtime GCS bucket is absent;
- Artifact Registry backend repository is absent;
- MariaDB Secret Manager secrets are absent;
- Terraform state bucket is absent;
- plan/apply/image-publish service accounts are absent;
- GitHub WIF pool/provider are absent.

At that point the repository retains only source code, GitHub history/evidence and non-billable
repository configuration; the closed GCP target no longer has persistent project resources that were
created for this portfolio runtime.
