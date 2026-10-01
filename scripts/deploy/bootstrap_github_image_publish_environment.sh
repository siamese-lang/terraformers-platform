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

REPO="siamese-lang/terraformers-platform"
ENVIRONMENT="gcp-image-publish"
REVIEWER_ID="174786754"
API_VERSION="2026-03-10"

WIF_PROVIDER="projects/21647422237/locations/global/workloadIdentityPools/terraformers-github/providers/terraformers-main"
PUBLISH_SERVICE_ACCOUNT="terraformers-image-publish@terraformers-platform.iam.gserviceaccount.com"
REGISTRY_HOST="asia-northeast3-docker.pkg.dev"
REGISTRY_REPOSITORY="terraformers-backend"

command -v gh >/dev/null || {
  echo "ERROR: GitHub CLI (gh) is required." >&2
  exit 1
}
command -v jq >/dev/null || {
  echo "ERROR: jq is required." >&2
  exit 1
}

gh auth status >/dev/null
[[ "$(gh repo view "$REPO" --json nameWithOwner --jq '.nameWithOwner')" == "$REPO" ]] || {
  echo "ERROR: cannot resolve expected repository '$REPO'." >&2
  exit 1
}

api() {
  gh api -H "X-GitHub-Api-Version: ${API_VERSION}" "$@"
}

if [[ "$MODE" == apply ]]; then
  api --method PUT "repos/${REPO}/environments/${ENVIRONMENT}" --input - >/dev/null <<JSON
{
  "wait_timer": 0,
  "prevent_self_review": false,
  "reviewers": [
    {
      "type": "User",
      "id": ${REVIEWER_ID}
    }
  ],
  "deployment_branch_policy": {
    "protected_branches": false,
    "custom_branch_policies": true
  }
}
JSON

  policies="$(api "repos/${REPO}/environments/${ENVIRONMENT}/deployment-branch-policies")"
  if ! jq -e '.branch_policies[]? | select(.name == "main")' <<<"$policies" >/dev/null; then
    api --method POST \
      "repos/${REPO}/environments/${ENVIRONMENT}/deployment-branch-policies" \
      -f name='main' \
      -f type='branch' >/dev/null
  fi

  gh variable set GCP_WIF_PROVIDER \
    --repo "$REPO" --env "$ENVIRONMENT" --body "$WIF_PROVIDER"
  gh variable set GCP_IMAGE_PUBLISH_SERVICE_ACCOUNT \
    --repo "$REPO" --env "$ENVIRONMENT" --body "$PUBLISH_SERVICE_ACCOUNT"
  gh variable set GCP_ARTIFACT_REGISTRY_HOST \
    --repo "$REPO" --env "$ENVIRONMENT" --body "$REGISTRY_HOST"
  gh variable set GCP_ARTIFACT_REGISTRY_REPOSITORY \
    --repo "$REPO" --env "$ENVIRONMENT" --body "$REGISTRY_REPOSITORY"
fi

echo "=== GITHUB IMAGE PUBLISH ENVIRONMENT CHECK ==="

environment_json="$(api "repos/${REPO}/environments/${ENVIRONMENT}")"
jq -e --argjson reviewer_id "$REVIEWER_ID" '
  .deployment_branch_policy.protected_branches == false
  and .deployment_branch_policy.custom_branch_policies == true
  and any(.protection_rules[]?;
    .type == "required_reviewers"
    and .prevent_self_review == false
    and any(.reviewers[]?;
      .type == "User" and .reviewer.id == $reviewer_id
    )
  )
' <<<"$environment_json" >/dev/null || {
  echo "FAIL: environment reviewer/self-review/branch-policy configuration differs." >&2
  exit 1
}
echo "PASS: required reviewer configured; self-review remains allowed"

policies="$(api "repos/${REPO}/environments/${ENVIRONMENT}/deployment-branch-policies")"
main_count="$(jq '[.branch_policies[]? | select(.name == "main")] | length' <<<"$policies")"
total_count="$(jq '.branch_policies | length' <<<"$policies")"
[[ "$main_count" == 1 && "$total_count" == 1 ]] || {
  echo "FAIL: expected exactly one custom deployment branch policy named main." >&2
  exit 1
}
echo "PASS: deployment branch restricted to main"

variables="$(api "repos/${REPO}/environments/${ENVIRONMENT}/variables")"
check_variable() {
  local name="$1"
  local expected="$2"
  jq -e --arg name "$name" --arg expected "$expected" '
    any(.variables[]?; .name == $name and .value == $expected)
  ' <<<"$variables" >/dev/null
}

check_variable GCP_WIF_PROVIDER "$WIF_PROVIDER"
check_variable GCP_IMAGE_PUBLISH_SERVICE_ACCOUNT "$PUBLISH_SERVICE_ACCOUNT"
check_variable GCP_ARTIFACT_REGISTRY_HOST "$REGISTRY_HOST"
check_variable GCP_ARTIFACT_REGISTRY_REPOSITORY "$REGISTRY_REPOSITORY"
echo "PASS: image-publish environment variables match"

cat <<'EOF'

This script does not dispatch GCP Backend Image Publish and does not publish
or deploy an image.
EOF
