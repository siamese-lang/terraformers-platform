# GCP target Terraform apply: identity and protected environment bootstrap

This procedure prepares the **separate apply trust boundary** selected by ADR-006. It does not
authorize a Terraform apply. Creating the apply service account, IAM bindings and GitHub environment
are security-sensitive bootstrap changes and require operator review before execution.

The normal delivery path after this bootstrap is:

`trusted main → gcp-target-plan → reviewed summary → gcp-target-apply approval → exact saved plan → apply`

Cloud Shell remains a recovery/raw-plan path rather than the normal apply path.

## 1. GitHub environment

Create a GitHub environment named **`gcp-target-apply`** before dispatching the apply workflow.

Required configuration:

- deployment branch: `main` only;
- required reviewer: the repository operator;
- **Prevent self-review: off** while there is only one operator, otherwise the deployment deadlocks;
- no long-lived GCP key secret;
- environment variables:
  - `GCP_TF_STATE_BUCKET=terraformers-platform-tfstate-21647422237`
  - `GCP_WIF_PROVIDER=projects/21647422237/locations/global/workloadIdentityPools/terraformers-github/providers/terraformers-main`
  - `GCP_TF_APPLY_SERVICE_ACCOUNT=terraformers-apply@terraformers-platform.iam.gserviceaccount.com`

The workflow is manual-only and also rejects any ref other than `main`, requires the caller to
supply the exact 40-character `main` SHA, and applies only after its local plan passes the
repository-owned fail-closed contract.

## 2. Bootstrap the dedicated apply service account

Run only after reviewing the commands and current project.

```bash
GCP_PROJECT=terraformers-platform
GCP_NUMBER=21647422237
GCP_BUCKET=terraformers-platform-tfstate-21647422237
GCP_POOL=terraformers-github
GCP_APPLY_SA=terraformers-apply@${GCP_PROJECT}.iam.gserviceaccount.com
GITHUB_APPLY_SUBJECT=repo:siamese-lang@174786754/terraformers-platform@1374031315:environment:gcp-target-apply

gcloud config get-value project

gcloud iam service-accounts create terraformers-apply   --project="$GCP_PROJECT"   --display-name='Terraformers Terraform apply only'

gcloud iam service-accounts add-iam-policy-binding "$GCP_APPLY_SA"   --project="$GCP_PROJECT"   --role='roles/iam.workloadIdentityUser'   --member="principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_APPLY_SUBJECT}"
```

The existing WIF provider is reused, but the service-account binding is restricted to the separate
`gcp-target-apply` GitHub environment subject. No JSON service-account key is created.

## 3. Grant only the current target-root mutation roles

The corrected foundation plan creates a custom VPC/subnet, one GKE cluster/node pool, one
node service account, three project IAM bindings and four project-service resources. The node
service account uses Google's documented least-privilege
`roles/container.defaultNodeServiceAccount` role. Because GKE must briefly create a default node
pool before Terraform removes it, the cluster resource also pins that temporary pool to the same
custom node service account rather than depending on the Compute Engine default service account.
The apply identity therefore needs mutation permissions that the plan identity intentionally does
not have.

```bash
for GCP_ROLE in   roles/compute.networkAdmin   roles/container.clusterAdmin   roles/iam.serviceAccountCreator   roles/iam.serviceAccountUser   roles/resourcemanager.projectIamAdmin   roles/serviceusage.serviceUsageAdmin
do
  gcloud projects add-iam-policy-binding "$GCP_PROJECT"     --member="serviceAccount:${GCP_APPLY_SA}"     --role="$GCP_ROLE"     --quiet
done

for GCP_ROLE in roles/storage.objectAdmin roles/storage.bucketViewer; do
  gcloud storage buckets add-iam-policy-binding "gs://${GCP_BUCKET}"     --member="serviceAccount:${GCP_APPLY_SA}"     --role="$GCP_ROLE"
done
```

Why these roles exist:

- `roles/compute.networkAdmin`: create/read the target VPC and subnet, without general VM admin.
- `roles/container.clusterAdmin`: create/update the GKE cluster and node pool. GKE requires
  `iam.serviceAccounts.actAs` separately when a custom node service account is attached.
- `roles/iam.serviceAccountCreator`: create/read the Terraform-managed
  `terraformers-gke-nodes` account without granting key administration.
- `roles/iam.serviceAccountUser`: provides `iam.serviceAccounts.actAs` needed to attach that
  custom node identity. It is project-scoped for first creation because the node account does not
  exist before Terraform creates it.
- `roles/resourcemanager.projectIamAdmin`: provides project policy get/set required by the three
  `google_project_iam_member` resources. This is the most sensitive grant and is why the apply
  identity is environment-gated and the plan contract permits only the reviewed IAM bindings.
- `roles/serviceusage.serviceUsageAdmin`: the Terraform root owns four
  `google_project_service` resources and therefore needs service enable/read operations even
  though those APIs are already enabled.
- bucket-level object admin + bucket viewer: Terraform state/lock access only on the dedicated
  state bucket.

Do **not** grant Owner, Editor, Compute Admin, Service Account Key Admin or Service Account Token
Creator to the apply service account.

## 4. Identity-only proof before target permissions are exercised

After the service account, WIF binding, GitHub environment and variables exist, dispatch
**GCP Target Terraform Apply** on `main` with:

- operation: `identity-check`
- expected_sha: the exact current 40-character `main` SHA
- confirmation: `IDENTITY_CHECK`

A successful run proves only the protected environment/OIDC/apply-service-account path. It performs
no Terraform init, state read or target mutation.

## 5. First foundation apply boundary

Do not run this until a fresh `GCP Target Terraform Plan` on the **same SHA** is reviewed and the
pre-apply billing/credit/quota/duplicate-runtime checks still pass.

The first foundation dispatch uses:

- operation: `foundation`
- expected_sha: the exact reviewed current `main` SHA
- confirmation: `APPLY_REVIEWED_GCP_FOUNDATION_12`

The workflow then:

1. verifies the protected identity and state bucket;
2. requires empty canonical runtime state and no unmanaged target cluster/network/subnet/node SA;
3. creates a saved Terraform plan with `node_count=1`;
4. requires the exact reviewed 12-resource create-only contract and critical values;
5. rejects any delete, replacement, additional resource, IAM role/member change, CIDR/zone/machine
   size/disk/runtime-identity drift;
6. applies that exact saved plan without `-auto-approve`; and
7. verifies the target cluster is RUNNING with one node.

The raw plan and state are not uploaded.

## 6. Return the same runtime to idle

After the M3-R2 live evidence session, dispatch the same workflow with:

- operation: `idle`
- expected_sha: the exact current reviewed `main` SHA
- confirmation: `SCALE_GCP_TARGET_TO_ZERO`

The idle contract permits only one in-place update of
`google_container_node_pool.target`, from `node_count=1` to `node_count=0`. Any network,
cluster, IAM, service, service-account, delete or replacement action fails before apply.

OpenSearch/PVC deployment remains a separate bounded operation after foundation creation, as
required by ADR-006. This workflow does not silently deploy the complete application overlay.

## Current role references

The role mapping was checked against current Google Cloud IAM documentation before this runbook was
written:

- Compute Network Admin:
  https://docs.cloud.google.com/iam/docs/roles-permissions/compute
- Kubernetes Engine roles and API permissions:
  https://docs.cloud.google.com/iam/docs/roles-permissions/container
  https://cloud.google.com/kubernetes-engine/docs/reference/api-permissions
- Service Account Creator/User:
  https://docs.cloud.google.com/iam/docs/roles-permissions/iam
- Project IAM Admin:
  https://docs.cloud.google.com/iam/docs/roles-permissions/resourcemanager
- Service Usage Admin:
  https://docs.cloud.google.com/iam/docs/roles-permissions/serviceusage
