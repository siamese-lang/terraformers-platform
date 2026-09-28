#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-check}"
case "$MODE" in
  check|apply) ;;
  *)
    echo "Usage: $0 [check|apply]" >&2
    exit 2
    ;;
esac

GCP_PROJECT="terraformers-platform"
GCP_NUMBER="21647422237"
GCP_BUCKET="terraformers-platform-tfstate-21647422237"
GCP_POOL="terraformers-github"
GCP_APPLY_SA="terraformers-apply@${GCP_PROJECT}.iam.gserviceaccount.com"
GITHUB_APPLY_SUBJECT="repo:siamese-lang@174786754/terraformers-platform@1374031315:environment:gcp-target-apply"

PROJECT_ROLES=(
  roles/browser
  roles/compute.networkAdmin
  roles/container.clusterAdmin
  roles/iam.serviceAccountCreator
  roles/iam.serviceAccountUser
  roles/resourcemanager.projectIamAdmin
  roles/serviceusage.serviceUsageAdmin
)

BUCKET_ROLES=(
  roles/storage.objectAdmin
  roles/storage.bucketViewer
)

FORBIDDEN_PROJECT_ROLES=(
  roles/owner
  roles/editor
  roles/iam.serviceAccountKeyAdmin
  roles/iam.serviceAccountTokenCreator
)

active_project="$(gcloud config get-value project 2>/dev/null)"
[[ "$active_project" == "$GCP_PROJECT" ]] || {
  echo "ERROR: active project is '$active_project'; expected '$GCP_PROJECT'." >&2
  exit 1
}

actual_number="$(gcloud projects describe "$GCP_PROJECT" --format='value(projectNumber)')"
[[ "$actual_number" == "$GCP_NUMBER" ]] || {
  echo "ERROR: project number is '$actual_number'; expected '$GCP_NUMBER'." >&2
  exit 1
}

gcloud storage buckets describe "gs://${GCP_BUCKET}" --format='value(name)' >/dev/null

gcloud iam workload-identity-pools describe "$GCP_POOL"   --project="$GCP_PROJECT"   --location=global   --format='value(name)' >/dev/null

service_account_exists() {
  gcloud iam service-accounts describe "$GCP_APPLY_SA"     --project="$GCP_PROJECT"     --format='value(email)' >/dev/null 2>&1
}

project_role_bound() {
  local role="$1"
  gcloud projects get-iam-policy "$GCP_PROJECT"     --flatten='bindings[].members'     --filter="bindings.role=${role} AND bindings.members=serviceAccount:${GCP_APPLY_SA}"     --format='value(bindings.role)'     | grep -Fxq "$role"
}

bucket_role_bound() {
  local role="$1"
  gcloud storage buckets get-iam-policy "gs://${GCP_BUCKET}"     --flatten='bindings[].members'     --filter="bindings.role=${role} AND bindings.members=serviceAccount:${GCP_APPLY_SA}"     --format='value(bindings.role)'     | grep -Fxq "$role"
}

wif_binding_present() {
  gcloud iam service-accounts get-iam-policy "$GCP_APPLY_SA"     --project="$GCP_PROJECT"     --flatten='bindings[].members'     --filter="bindings.role=roles/iam.workloadIdentityUser AND bindings.members=principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_APPLY_SUBJECT}"     --format='value(bindings.role)'     | grep -Fxq roles/iam.workloadIdentityUser
}

if [[ "$MODE" == apply ]]; then
  if ! service_account_exists; then
    gcloud iam service-accounts create terraformers-apply       --project="$GCP_PROJECT"       --display-name='Terraformers Terraform apply only'
  fi

  gcloud iam service-accounts add-iam-policy-binding "$GCP_APPLY_SA"     --project="$GCP_PROJECT"     --role='roles/iam.workloadIdentityUser'     --member="principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_APPLY_SUBJECT}"     --quiet >/dev/null

  for role in "${PROJECT_ROLES[@]}"; do
    if ! project_role_bound "$role"; then
      gcloud projects add-iam-policy-binding "$GCP_PROJECT"         --member="serviceAccount:${GCP_APPLY_SA}"         --role="$role"         --quiet >/dev/null
    fi
  done

  for role in "${BUCKET_ROLES[@]}"; do
    if ! bucket_role_bound "$role"; then
      gcloud storage buckets add-iam-policy-binding "gs://${GCP_BUCKET}"         --member="serviceAccount:${GCP_APPLY_SA}"         --role="$role"         --quiet >/dev/null
    fi
  done
fi

echo "=== GCP APPLY IDENTITY CHECK ==="

service_account_exists || {
  echo "FAIL: apply service account does not exist."
  [[ "$MODE" == check ]] && echo "Run: $0 apply"
  exit 1
}
echo "PASS: service account exists"

wif_binding_present || {
  echo "FAIL: exact gcp-target-apply OIDC subject is not bound."
  exit 1
}
echo "PASS: exact GitHub environment OIDC subject is bound"

for role in "${PROJECT_ROLES[@]}"; do
  project_role_bound "$role" || {
    echo "FAIL: missing project role $role"
    exit 1
  }
  echo "PASS: project role $role"
done

for role in "${BUCKET_ROLES[@]}"; do
  bucket_role_bound "$role" || {
    echo "FAIL: missing bucket role $role"
    exit 1
  }
  echo "PASS: bucket role $role"
done

for role in "${FORBIDDEN_PROJECT_ROLES[@]}"; do
  if project_role_bound "$role"; then
    echo "FAIL: forbidden broad/sensitive project role is present: $role" >&2
    exit 1
  fi
done
echo "PASS: no forbidden Owner/Editor/key/token-creator project role"

cat <<'EOF'

=== REQUIRED GITHUB ENVIRONMENT ===
Name: gcp-target-apply
Deployment branch: main only
Required reviewer: repository operator
Prevent self-review: OFF

Environment variables:
GCP_TF_STATE_BUCKET=terraformers-platform-tfstate-21647422237
GCP_WIF_PROVIDER=projects/21647422237/locations/global/workloadIdentityPools/terraformers-github/providers/terraformers-main
GCP_TF_APPLY_SERVICE_ACCOUNT=terraformers-apply@terraformers-platform.iam.gserviceaccount.com

After that environment is configured, run the GitHub workflow:
GCP Target Terraform Apply
  operation: identity-check
  expected_sha: current exact main SHA
  confirmation: IDENTITY_CHECK

Do not run operation=foundation from this bootstrap script.
EOF
