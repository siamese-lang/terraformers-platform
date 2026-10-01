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
GCP_POOL="terraformers-github"
GCP_PROVIDER="terraformers-main"
PUBLISH_SA="terraformers-image-publish@${GCP_PROJECT}.iam.gserviceaccount.com"
GITHUB_PUBLISH_SUBJECT="repo:siamese-lang@174786754/terraformers-platform@1374031315:environment:gcp-image-publish"

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

gcloud iam workload-identity-pools providers describe "$GCP_PROVIDER" \
  --project="$GCP_PROJECT" \
  --location=global \
  --workload-identity-pool="$GCP_POOL" \
  --format='value(name)' >/dev/null

service_account_exists() {
  gcloud iam service-accounts describe "$PUBLISH_SA" \
    --project="$GCP_PROJECT" \
    --format='value(email)' >/dev/null 2>&1
}

wif_binding_present() {
  local principal="principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_PUBLISH_SUBJECT}"
  gcloud iam service-accounts get-iam-policy "$PUBLISH_SA" \
    --project="$GCP_PROJECT" \
    --format=json \
    | jq -e --arg member "$principal" '
        any(.bindings[]?;
          .role == "roles/iam.workloadIdentityUser"
          and any(.members[]?; . == $member)
        )
      ' >/dev/null
}

direct_project_roles() {
  gcloud projects get-iam-policy "$GCP_PROJECT" --format=json \
    | jq -r --arg member "serviceAccount:${PUBLISH_SA}" '
        .bindings[]?
        | select(any(.members[]?; . == $member))
        | .role
      '
}

user_managed_keys() {
  gcloud iam service-accounts keys list \
    --iam-account="$PUBLISH_SA" \
    --project="$GCP_PROJECT" \
    --managed-by=user \
    --format='value(name)'
}

if [[ "$MODE" == apply ]]; then
  if ! service_account_exists; then
    gcloud iam service-accounts create terraformers-image-publish \
      --project="$GCP_PROJECT" \
      --display-name='Terraformers immutable backend image publisher'
  fi

  gcloud iam service-accounts add-iam-policy-binding "$PUBLISH_SA" \
    --project="$GCP_PROJECT" \
    --role='roles/iam.workloadIdentityUser' \
    --member="principal://iam.googleapis.com/projects/${GCP_NUMBER}/locations/global/workloadIdentityPools/${GCP_POOL}/subject/${GITHUB_PUBLISH_SUBJECT}" \
    --quiet >/dev/null
fi

echo "=== GCP IMAGE PUBLISHER IDENTITY CHECK ==="

service_account_exists || {
  echo "FAIL: image publisher service account does not exist."
  [[ "$MODE" == check ]] && echo "Run after approval: $0 apply"
  exit 1
}
echo "PASS: dedicated publisher service account exists"

wif_binding_present || {
  echo "FAIL: exact gcp-image-publish GitHub environment subject is not bound." >&2
  exit 1
}
echo "PASS: exact GitHub environment OIDC subject is bound"

mapfile -t project_roles < <(direct_project_roles)
if (( ${#project_roles[@]} != 0 )); then
  echo "FAIL: publisher must not have direct project resource roles:" >&2
  printf '  %s\n' "${project_roles[@]}" >&2
  exit 1
fi
echo "PASS: publisher has no direct project resource role"

mapfile -t keys < <(user_managed_keys)
if (( ${#keys[@]} != 0 )); then
  echo "FAIL: user-managed service-account keys are forbidden:" >&2
  printf '  %s\n' "${keys[@]}" >&2
  exit 1
fi
echo "PASS: no user-managed service-account key exists"

cat <<'EOF'

The publisher intentionally has no Artifact Registry project role here.
Repository-scoped roles/artifactregistry.writer is owned by the canonical
gcp-target-runtime Terraform delivery-foundation contract.

This script does not create a repository, grant repository access, push an
image, dispatch a workflow, or deploy Kubernetes resources.
EOF
