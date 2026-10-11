#!/usr/bin/env bash
# Local Cloud Shell browser preview, not a public frontend deployment.
set -euo pipefail
umask 077
EXPECTED_SOURCE="${GCP_PREVIEW_BACKEND_SOURCE_SHA:-435ca32d4ef194f512c3019925acaac19507b7af}"
NAMESPACE="terraformers-target"
PROJECT_ID="terraformers-platform"
CLUSTER="terraformers-target"
ZONE="asia-northeast3-a"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
[[ "${GCP_BROWSER_PREVIEW_CONFIRMATION:-}" == "APPROVE_TEMPORARY_GCP_JWKS_BROWSER_PREVIEW" ]] || {
  echo "STOP: Temporary GCP JWKS change requires separate explicit live approval." >&2
  exit 2
}
for command in gcloud kubectl npm node jq curl timeout; do
  command -v "$command" >/dev/null || { echo "STOP: missing $command" >&2; exit 2; }
done
[[ "$EXPECTED_SOURCE" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid source SHA" >&2; exit 2; }
test -f "$ROOT/frontend/package-lock.json"
test -f "$ROOT/scripts/smoke/ephemeral-jwks-fixture.sh"
gcloud container clusters get-credentials "$CLUSTER" --zone "$ZONE" --project "$PROJECT_ID" >/dev/null
ready="$(kubectl -n "$NAMESPACE" get deployment terraformers-backend -o jsonpath='{.status.readyReplicas}')"
actual="$(kubectl -n "$NAMESPACE" exec deployment/terraformers-backend -c backend -- printenv BUILD_SOURCE_REVISION)"
[[ "$ready" == 1 && "$actual" == "$EXPECTED_SOURCE" ]] || {
  echo "STOP: #277 backend source not running. Expected $EXPECTED_SOURCE, observed $actual, ready=$ready." >&2
  echo "Use the existing approved image-publication/rollout path. No mutation attempted." >&2
  exit 3
}
kubectl -n "$NAMESPACE" get configmap terraformers-jwks \
  -o jsonpath='{.data.jwks\.json}' | jq -e '.keys == []' >/dev/null || {
    echo "STOP: JWKS is not the empty placeholder; refuse override." >&2
    exit 3
  }
echo "[preview] Build React first, without touching GKE."
(cd "$ROOT/frontend" && npm ci --no-audit --no-fund)

export RUNNER_TEMP
RUNNER_TEMP="$(mktemp -d)"
export NAMESPACE
export ISSUER_URI="https://identity.example.test/case-c"
export CLIENT_ID="case-c-runtime-client"
export OPERATION="local-gcp-browser-preview"
export GITHUB_RUN_ID="$(date +%s)"
fixture_started=false
backend_pf=""
cleanup() {
  code=$?
  trap - EXIT INT TERM HUP
  [[ -z "$backend_pf" ]] || kill "$backend_pf" 2>/dev/null || true
  if [[ "$fixture_started" == true ]]; then
    if ! bash "$ROOT/scripts/smoke/ephemeral-jwks-fixture.sh" restore; then
      echo "CRITICAL: JWKS restore failed. Check ConfigMap before further tests." >&2
      code=1
    fi
  fi
  rm -rf -- "$RUNNER_TEMP"
  exit "$code"
}
trap cleanup EXIT INT TERM HUP

# Reuses the existing fixture's owned marker/placeholder/refusal/readback logic.
fixture_started=true
bash "$ROOT/scripts/smoke/ephemeral-jwks-fixture.sh" prepare
kubectl -n "$NAMESPACE" port-forward service/terraformers-backend 8080:80 \
  >"$RUNNER_TEMP/backend-pf.log" 2>&1 &
backend_pf=$!
ready=false
for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || { echo "STOP: local backend port-forward not ready." >&2; exit 4; }
echo "[preview] Backend image source confirmed: $actual"
echo "[preview] Token file (private): $RUNNER_TEMP/case-c-access.token"
echo "[preview] Open Cloud Shell Web Preview port 3000. Paste token ONLY into the local development login."
echo "[preview] Token expires after 15 minutes. Closing this command restores empty JWKS and removes keys."
cd "$ROOT/frontend"
timeout --signal=INT --kill-after=15s 13m \
  env HOST=0.0.0.0 PORT=3000 REACT_APP_GCP_BROWSER_PREVIEW=true \
  REACT_APP_API_BASE_URL= BROWSER=none npm start
