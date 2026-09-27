# GCP target Terraform plan: one-time state and identity setup

This is a reviewed, one-time Cloud Shell procedure for project `terraformers-platform`.
The associated GitHub workflow **only plans** the existing target runtime. Creating a bucket,
identity pool, or IAM binding changes GCP resources; review these commands and current billing
before running them. No cluster or node is created by this procedure.

Operator screenshots show the GitHub `main` Ruleset active, requiring PRs, blocking force
pushes/deletion with no bypass, and requiring the `terraform-static-verification` GitHub Actions
check. PR approvals are set to 0 for the sole operator. Before granting cloud permissions to CI,
restrict the `gcp-target-plan` environment to `main` and review ADR-006 and the draft workflow PR.
The manual GCP plan workflow is not a PR status check.

## 1. Verify the target and choose a globally unique bucket name

Run in an authenticated Cloud Shell with permissions to manage project IAM, Workload Identity
Federation, and Storage buckets. Run each block in order and stop at the first error. Keep the
bucket name in your Cloud Shell session; do not put credentials, state objects, or a downloaded
service account key into GitHub.

```bash
GCP_PROJECT=terraformers-platform
GCP_BUCKET=terraformers-platform-tfstate-REPLACE_WITH_UNIQUE_SUFFIX
GCP_POOL=terraformers-github
GCP_PROVIDER=terraformers-main
GCP_PLAN_SA=terraformers-plan@${GCP_PROJECT}.iam.gserviceaccount.com
GITHUB_OWNER_ID=174786754
GITHUB_REPOSITORY_ID=1374031315
GITHUB_OIDC_SUBJECT=repo:siamese-lang@174786754/terraformers-platform@1374031315:environment:gcp-target-plan

gcloud config get-value project
gcloud projects describe "$GCP_PROJECT" --format='value(projectNumber)'
gcloud storage buckets list --project="$GCP_PROJECT" --format='value(name)'
gcloud iam workload-identity-pools list --project="$GCP_PROJECT" \
  --location=global --format='value(name)'

# Read-only project-resource permission preflight; this does not test a bucket.
curl -fsS -X POST \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H 'Content-Type: application/json' \
  -d '{"permissions":["storage.buckets.create","iam.workloadIdentityPools.create","iam.workloadIdentityPoolProviders.create","iam.serviceAccounts.create","resourcemanager.projects.setIamPolicy","serviceusage.services.enable"]}' \
  "https://cloudresourcemanager.googleapis.com/v1/projects/${GCP_PROJECT}:testIamPermissions"
```

Stop if the active project differs, or if a suitable state bucket / provider already exists;
inspect existing settings and reuse it with the same access restrictions rather than creating
a duplicate. There was no GCS state bucket or GitHub WIF pool in the initial inventory. The
Compute Engine default service account is not the GitHub plan identity.

The 2026-09-27 operator preflight returned all six project-resource permissions above. A
separate test against the **project** did not return `storage.buckets.get`,
`storage.buckets.update` or `storage.buckets.setIamPolicy`; that result does **not** establish
whether the operator can manage a particular bucket. The operator's direct, unconditional
project role was confirmed as `roles/owner`. Google documents that a project Owner receives
`roles/storage.legacyBucketOwner` on a new uniform-access bucket through convenience values;
that bucket role includes get, update and IAM-policy permissions. No extra project IAM grant
is indicated by this evidence. After approved creation, verify permissions on the actual
bucket before enabling Object Versioning or granting plan access. Also verify the plan
service account's IAM policy before binding the GitHub principal. Organization policy and
other constraints can still block an operation. These checks do not authorize cloud changes.

## 2. Create the state bucket

The bucket and its object versions can incur storage charges. Restrict its use to this Terraform
root; object versioning protects recovery, and public access prevention blocks public grants.

```bash
gcloud services enable storage.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com --project="$GCP_PROJECT"

gcloud storage buckets create "gs://${GCP_BUCKET}" \
  --project="$GCP_PROJECT" --location=asia-northeast3 \
  --uniform-bucket-level-access --public-access-prevention
```

Before changing bucket settings, run the Cloud Storage permission test **on that bucket**.
Each response must list the permission requested; stop if any does not. This check is read-only.

```bash
for GCP_PERMISSION in storage.buckets.get storage.buckets.update \
  storage.buckets.getIamPolicy storage.buckets.setIamPolicy; do
  printf '%s: ' "$GCP_PERMISSION"
  curl -fsS -G \
    -H "Authorization: Bearer $(gcloud auth print-access-token)" \
    --data-urlencode "permissions=${GCP_PERMISSION}" \
    "https://storage.googleapis.com/storage/v1/b/${GCP_BUCKET}/iam/testPermissions"
  printf '\n'
done
```

Only after all four permissions are confirmed, enable versioning and verify metadata:

```bash
gcloud storage buckets update "gs://${GCP_BUCKET}" --versioning
gcloud storage buckets describe "gs://${GCP_BUCKET}" \
  --format='default(name,location,versioning_enabled,uniform_bucket_level_access)'
```

Protect this bucket from routine deletion. Keep its state versions private; never copy state,
plans, or full Terraform outputs into a PR. If a local state file for **this exact GCP root**
appears later, stop and use a reviewed `terraform init -migrate-state` procedure before any
remote plan. The local OpenTofu states from other repositories must never be migrated here.

## 3. Create GitHub OIDC federation and a plan identity

The provider accepts only this repository on `main`, using the immutable GitHub owner ID
`174786754` and repository ID `1374031315` in addition to the branch claim. This repository was
created on 2026-09-17, after GitHub's 2026-07-15 immutable-subject rollout boundary, so the default
OIDC `sub` includes both immutable IDs. The service account binding therefore matches the exact
GitHub job subject
`repo:siamese-lang@174786754/terraformers-platform@1374031315:environment:gcp-target-plan`.
The environment's deployment branch restriction must also permit only `main`. Run once after
checking existing pool/provider names. Claims used by the provider condition are explicitly
included in the attribute mapping.

```bash
GCP_NUMBER="$(gcloud projects describe "$GCP_PROJECT" --format='value(projectNumber)')"

gcloud iam workload-identity-pools create "$GCP_POOL" \
  --project="$GCP_PROJECT" --location=global \
  --display-name='Terraformers GitHub Actions'
gcloud iam workload-identity-pools providers create-oidc "$GCP_PROVIDER" \
  --project="$GCP_PROJECT" --location=global \
  --workload-identity-pool="$GCP_POOL" \
  --issuer-uri='https://token.actions.githubusercontent.com' \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository_owner_id=assertion.repository_owner_id,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository_owner_id=='${GITHUB_OWNER_ID}' && assertion.repository_id=='${GITHUB_REPOSITORY_ID}' && assertion.ref=='refs/heads/main'"

gcloud iam service-accounts create terraformers-plan \
  --project="$GCP_PROJECT" --display-name='Terraformers Terraform plan only'

gcloud iam service-accounts add-iam-policy-binding "$GCP_PLAN_SA" \
  --project="$GCP_PROJECT" --role='roles/iam.workloadIdentityUser' \
  --member="principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_OIDC_SUBJECT}"
```

## 4. Verify federation, then grant plan permissions

In the GitHub repository create an environment named **`gcp-target-plan`**, allow deployment
from `main` only, and set these environment variables (not secrets):

| Variable | Value |
| --- | --- |
| `GCP_TF_STATE_BUCKET` | Chosen globally unique bucket name without `gs://` |
| `GCP_WIF_PROVIDER` | `projects/<GCP_NUMBER>/locations/global/workloadIdentityPools/terraformers-github/providers/terraformers-main` |
| `GCP_TF_PLAN_SERVICE_ACCOUNT` | `terraformers-plan@terraformers-platform.iam.gserviceaccount.com` |

Set optional environment reviewers according to your actual reviewer availability; a sole
maintainer must not enable a rule that blocks their own review. No GitHub service-account key
secret is required. After the workflow PR merges to `main`, run **GCP Target Terraform Plan**
manually on `main`, choosing **`identity-check`**. This mode exchanges the GitHub OIDC token for
the dedicated service account and stops before any state or target-resource query. Confirm it
passes before granting the following roles.

Give the plan identity only read permissions for managed resource refresh. The backend also
needs object create/delete to write and remove its state lock; grant object administration on
**this one dedicated bucket**, never on the project. Do not reuse this identity for apply.

```bash
for GCP_ROLE in roles/browser roles/compute.viewer roles/container.viewer \
  roles/iam.serviceAccountViewer roles/iam.securityReviewer \
  roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$GCP_PROJECT" \
    --member="serviceAccount:${GCP_PLAN_SA}" --role="$GCP_ROLE" --quiet
done

for GCP_ROLE in roles/storage.objectAdmin roles/storage.bucketViewer; do
  gcloud storage buckets add-iam-policy-binding "gs://${GCP_BUCKET}" \
    --member="serviceAccount:${GCP_PLAN_SA}" --role="$GCP_ROLE"
done
```

Now run the same workflow manually on `main`, choosing **`plan`**. Inspect the action counts and
full plan securely before a separate
apply decision by running the equivalent plan in an authorized Cloud Shell with the same bucket,
prefix and variables. The workflow never uploads the raw plan. `node_count=1` here only describes
the intended evidence session; the workflow cannot apply it.

The exact provider read calls are verified by the first live plan. If a 403 occurs, inspect the
specific missing permission and add only the narrowest required read role before retrying.

The first successful plan, including its run URL, commit SHA, bucket metadata check and
resource action counts, is the evidence for the next M3-R2 decision. A plan alone does not
prove available Free Trial credit, zonal capacity, Vertex model access, or successful runtime
readiness; complete the existing pre-apply runbook before resource creation.
