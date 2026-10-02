#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="terraformers-platform"
STATE_BUCKET="terraformers-platform-tfstate-21647422237"
WIF_POOL="terraformers-github"
PLAN_SA="terraformers-plan@${PROJECT_ID}.iam.gserviceaccount.com"
APPLY_SA="terraformers-apply@${PROJECT_ID}.iam.gserviceaccount.com"
PUBLISH_SA="terraformers-image-publish@${PROJECT_ID}.iam.gserviceaccount.com"

mode="${1:-check}"
confirmation="${2:-}"

[[ "$mode" == check || "$mode" == apply ]] || {
  echo "usage: $0 check | $0 apply DELETE_GCP_TARGET_BOOTSTRAP" >&2
  exit 2
}

[[ "$(gcloud config get-value project 2>/dev/null)" == "$PROJECT_ID" ]] || {
  echo "Active gcloud project must be $PROJECT_ID." >&2
  exit 1
}

runtime_absent() {
  ! gcloud container clusters describe terraformers-target \
      --zone=asia-northeast3-a --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud compute instances describe terraformers-mariadb \
      --zone=asia-northeast3-a --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud compute disks describe terraformers-mariadb-data \
      --zone=asia-northeast3-a --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud compute networks describe terraformers-target \
      --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud storage buckets describe gs://terraformers-runtime-objects-21647422237 \
      >/dev/null 2>&1
  ! gcloud artifacts repositories describe terraformers-backend \
      --location=asia-northeast3 --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud secrets describe terraformers-mariadb-root-password \
      --project="$PROJECT_ID" >/dev/null 2>&1
  ! gcloud secrets describe terraformers-mariadb-app-password \
      --project="$PROJECT_ID" >/dev/null 2>&1
}

runtime_absent || {
  echo "Runtime residuals still exist. Complete the protected runtime teardown first." >&2
  exit 1
}

bucket_exists=false
if gcloud storage buckets describe "gs://${STATE_BUCKET}" >/dev/null 2>&1; then
  bucket_exists=true
fi

pool_exists=false
if gcloud iam workload-identity-pools describe "$WIF_POOL" \
    --project="$PROJECT_ID" --location=global >/dev/null 2>&1; then
  pool_exists=true
fi

printf 'runtime_residuals=absent\n'
printf 'state_bucket_exists=%s\n' "$bucket_exists"
printf 'wif_pool_exists=%s\n' "$pool_exists"
for sa in "$PLAN_SA" "$APPLY_SA" "$PUBLISH_SA"; do
  if gcloud iam service-accounts describe "$sa" --project="$PROJECT_ID" >/dev/null 2>&1; then
    printf 'service_account_exists=%s\n' "$sa"
  fi
done

if [[ "$mode" == check ]]; then
  exit 0
fi

[[ "$confirmation" == DELETE_GCP_TARGET_BOOTSTRAP ]] || {
  echo "apply requires exact confirmation DELETE_GCP_TARGET_BOOTSTRAP" >&2
  exit 1
}

if [[ "$bucket_exists" == true ]]; then
  versions="$(gcloud storage ls --all-versions "gs://${STATE_BUCKET}/**" 2>/dev/null || true)"
  if [[ -n "$versions" ]]; then
    gcloud storage rm --all-versions "gs://${STATE_BUCKET}/**"
  fi
  gcloud storage buckets delete "gs://${STATE_BUCKET}" --quiet
fi

policy="$(mktemp)"
gcloud projects get-iam-policy "$PROJECT_ID" --format=json > "$policy"
for sa in "$PLAN_SA" "$APPLY_SA" "$PUBLISH_SA"; do
  member="serviceAccount:${sa}"
  mapfile -t roles < <(
    jq -r --arg member "$member" '
      .bindings[]?
      | select(any(.members[]?; . == $member))
      | .role
    ' "$policy"
  )
  for role in "${roles[@]}"; do
    gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
      --member="$member" \
      --role="$role" \
      --quiet >/dev/null
  done
  if gcloud iam service-accounts describe "$sa" --project="$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam service-accounts delete "$sa" \
      --project="$PROJECT_ID" \
      --quiet
  fi
done
rm -f "$policy"

if [[ "$pool_exists" == true ]]; then
  gcloud iam workload-identity-pools delete "$WIF_POOL" \
    --project="$PROJECT_ID" \
    --location=global \
    --quiet
fi

runtime_absent
! gcloud storage buckets describe "gs://${STATE_BUCKET}" >/dev/null 2>&1
for sa in "$PLAN_SA" "$APPLY_SA" "$PUBLISH_SA"; do
  ! gcloud iam service-accounts describe "$sa" --project="$PROJECT_ID" >/dev/null 2>&1
done
! gcloud iam workload-identity-pools describe "$WIF_POOL" \
    --project="$PROJECT_ID" --location=global >/dev/null 2>&1

echo "gcp_target_bootstrap_cleanup=passed"
echo "Enabled Google APIs and GitHub environments are intentionally not deleted; they do not incur idle resource charges by themselves."
