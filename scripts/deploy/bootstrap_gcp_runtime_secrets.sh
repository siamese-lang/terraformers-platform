#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-check}"
case "$MODE" in
  check|apply-iam|add-versions) ;;
  *)
    echo "Usage: $0 [check|apply-iam|add-versions]" >&2
    exit 2
    ;;
esac

PROJECT="terraformers-platform"
PROJECT_NUMBER="21647422237"
ZONE="asia-northeast3-a"
CLUSTER="terraformers-target"
APPLY_SA="terraformers-apply@${PROJECT}.iam.gserviceaccount.com"
PLAN_SA="terraformers-plan@${PROJECT}.iam.gserviceaccount.com"
ROOT_SECRET="terraformers-mariadb-root-password"
APP_SECRET="terraformers-mariadb-app-password"

APPLY_PROJECT_ROLES=(
  roles/compute.instanceAdmin.v1
  roles/compute.securityAdmin
  roles/secretmanager.admin
  roles/storage.admin
)

PLAN_PROJECT_ROLES=(
  roles/browser
  roles/compute.viewer
  roles/container.viewer
  roles/iam.serviceAccountViewer
  roles/iam.securityReviewer
  roles/serviceusage.serviceUsageConsumer
  roles/secretmanager.viewer
  roles/storage.bucketViewer
)

active_project="$(gcloud config get-value project 2>/dev/null)"
[[ "$active_project" == "$PROJECT" ]] || {
  echo "ERROR: active project is '$active_project'; expected '$PROJECT'." >&2
  exit 1
}

actual_number="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
[[ "$actual_number" == "$PROJECT_NUMBER" ]] || {
  echo "ERROR: project number is '$actual_number'; expected '$PROJECT_NUMBER'." >&2
  exit 1
}

cluster_version="$(
  gcloud container clusters describe "$CLUSTER"     --project="$PROJECT"     --zone="$ZONE"     --format='value(currentMasterVersion)'
)"
[[ "$cluster_version" =~ ^([0-9]+)\.([0-9]+)(\.|-) ]] || {
  echo "ERROR: cannot parse GKE version: $cluster_version" >&2
  exit 1
}
major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
if (( major < 1 || (major == 1 && minor < 33) )); then
  echo "ERROR: GKE Secret Sync requires GKE 1.33+; current=$cluster_version" >&2
  exit 1
fi

project_role_bound() {
  local service_account="$1"
  local role="$2"
  gcloud projects get-iam-policy "$PROJECT" --format=json     | jq -e --arg role "$role" --arg member "serviceAccount:${service_account}" '
        any(.bindings[]?;
          .role == $role and any(.members[]?; . == $member)
        )
      ' >/dev/null
}

if [[ "$MODE" == apply-iam ]]; then
  for role in "${APPLY_PROJECT_ROLES[@]}"; do
    if ! project_role_bound "$APPLY_SA" "$role"; then
      gcloud projects add-iam-policy-binding "$PROJECT"         --member="serviceAccount:${APPLY_SA}"         --role="$role"         --quiet >/dev/null
    fi
  done
  for role in "${PLAN_PROJECT_ROLES[@]}"; do
    if ! project_role_bound "$PLAN_SA" "$role"; then
      gcloud projects add-iam-policy-binding "$PROJECT"         --member="serviceAccount:${PLAN_SA}"         --role="$role"         --quiet >/dev/null
    fi
  done
fi

echo "=== CASE C RUNTIME DEPENDENCY IAM/CAPABILITY CHECK ==="
echo "PASS: GKE version $cluster_version satisfies >=1.33"

iam_ok=true
for role in "${APPLY_PROJECT_ROLES[@]}"; do
  if project_role_bound "$APPLY_SA" "$role"; then
    echo "PASS: apply identity project role $role"
  else
    echo "MISSING: apply identity project role $role"
    iam_ok=false
  fi
done
for role in "${PLAN_PROJECT_ROLES[@]}"; do
  if project_role_bound "$PLAN_SA" "$role"; then
    echo "PASS: plan identity read role $role"
  else
    echo "MISSING: plan identity read role $role"
    iam_ok=false
  fi
done

if [[ "$MODE" == check && "$iam_ok" != true ]]; then
  cat <<'EOF'
Runtime dependency IAM is not bootstrapped.
After explicit IAM approval, run:
  bash scripts/deploy/bootstrap_gcp_runtime_secrets.sh apply-iam
EOF
  exit 1
fi

if [[ "$MODE" != add-versions ]]; then
  exit 0
fi

for secret in "$ROOT_SECRET" "$APP_SECRET"; do
  gcloud secrets describe "$secret" --project="$PROJECT" --format='value(name)' >/dev/null || {
    echo "ERROR: secret container does not exist yet: $secret" >&2
    echo "Apply the separately reviewed runtime-secret-foundation first." >&2
    exit 1
  }
  existing="$(
    gcloud secrets versions list "$secret"       --project="$PROJECT"       --filter='state=ENABLED'       --format='value(name)'       --limit=1
  )"
  [[ -z "$existing" ]] || {
    echo "ERROR: enabled secret version already exists for $secret; this bootstrap will not rotate it." >&2
    exit 1
  }
done

command -v openssl >/dev/null || {
  echo 'ERROR: openssl is required to generate secret values in memory.' >&2
  exit 1
}

root_password="$(openssl rand -base64 36 | tr -d '\n')"
app_password="$(openssl rand -base64 36 | tr -d '\n')"
root_version=""
app_version=""

cleanup_partial() {
  local rc=$?
  unset root_password app_password
  if (( rc != 0 )) && [[ -n "$root_version" && -z "$app_version" ]]; then
    version_id="${root_version##*/}"
    echo "Secret bootstrap failed after root version creation; destroying the partial root version." >&2
    gcloud secrets versions destroy "$version_id"       --secret="$ROOT_SECRET"       --project="$PROJECT"       --quiet >/dev/null || true
  fi
  exit "$rc"
}
trap cleanup_partial EXIT

root_version="$(
  printf '%s' "$root_password"     | gcloud secrets versions add "$ROOT_SECRET"         --project="$PROJECT"         --data-file=-         --format='value(name)'
)"
app_version="$(
  printf '%s' "$app_password"     | gcloud secrets versions add "$APP_SECRET"         --project="$PROJECT"         --data-file=-         --format='value(name)'
)"

unset root_password app_password
trap - EXIT

[[ -n "$root_version" && -n "$app_version" ]] || {
  echo 'ERROR: secret version creation did not return version identities.' >&2
  exit 1
}

echo "PASS: initial MariaDB root/app secret versions were created without printing payloads."
echo "This script does not create VM, disk, bucket, firewall, Kubernetes resources, or backend Deployment."
