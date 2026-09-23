#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY_DIR="${REPO_ROOT}/infra/kubernetes/overlays/portable-authenticated"
OUTPUT_DIR="${REPO_ROOT}/artifacts/m2-authenticated-identity"
NAMESPACE="${PORTABLE_NAMESPACE:-terraformers-portable}"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-terraformers-portable-authenticated}"
IMAGE_NAME="${PORTABLE_BACKEND_IMAGE:-terraformers-backend:portable-persistent}"
BACKEND_PORT="${PORTABLE_BACKEND_PORT:-18080}"
ISSUER="https://identity.example.test/portable-authenticated"
CLIENT_ID="portable-runtime-client"
KEY_ID="terraformers-m2-test-key"
BACKEND_FORWARD_PID=""
TEMP_DIR=""

for check in runtime_ready jwks_ready owner_token_accepted owner_user_created provider_subject_persisted numeric_user_id same_identity_reused second_identity_distinct anonymous_protected_rejected invalid_token_rejected owner_private_access non_owner_private_forbidden non_owner_modification_forbidden owner_modification_allowed display_name_update display_name_preserved; do
  printf -v "${check}" FAIL
done

require_command() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }; }

collect_diagnostics() {
  mkdir -p "${OUTPUT_DIR}"
  kubectl -n "${NAMESPACE}" get pods -o wide >"${OUTPUT_DIR}/pods.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" get services -o wide >"${OUTPUT_DIR}/services.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" logs deployment/terraformers-backend --tail=-1 >"${OUTPUT_DIR}/backend.log" 2>&1 || true
  kubectl -n "${NAMESPACE}" logs deployment/terraformers-jwks --tail=-1 >"${OUTPUT_DIR}/jwks-server.log" 2>&1 || true
  {
    for check in runtime_ready jwks_ready owner_token_accepted owner_user_created provider_subject_persisted numeric_user_id same_identity_reused second_identity_distinct anonymous_protected_rejected invalid_token_rejected owner_private_access non_owner_private_forbidden non_owner_modification_forbidden owner_modification_allowed display_name_update display_name_preserved; do
      printf '%s=%s\n' "${check}" "${!check}"
    done
    echo 'cloud_credentials_required=false'
  } >"${OUTPUT_DIR}/summary.txt"
}
cleanup() {
  [[ -z "${BACKEND_FORWARD_PID}" ]] || kill "${BACKEND_FORWARD_PID}" >/dev/null 2>&1 || true
  collect_diagnostics
  [[ -z "${TEMP_DIR}" ]] || rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

http_status() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}" args=(-sS -o /dev/null -w '%{http_code}' -X "${method}")
  [[ -z "${token}" ]] || args+=(-H "Authorization: Bearer ${token}")
  [[ -z "${body}" ]] || args+=(-H 'Content-Type: application/json' --data "${body}")
  curl "${args[@]}" "http://127.0.0.1:${BACKEND_PORT}${path}"
}
assert_status() { local expected="$1"; shift; local actual; actual="$(http_status "$@")"; echo "$2 $1=$actual" >>"${OUTPUT_DIR}/http-status-summary.txt"; [[ "${actual}" == "${expected}" ]]; }
db_query() {
  kubectl -n "${NAMESPACE}" exec deployment/terraformers-mariadb -- sh -c \
    'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names -e "$1"' -- "$1"
}
b64url_file() { openssl base64 -A <"$1" | tr '+/' '-_' | tr -d '='; }
make_token() {
  local subject="$1" email="$2" client="$3" now header payload unsigned
  now="$(date +%s)"
  header="$(printf '{"alg":"RS256","kid":"%s","typ":"JWT"}' "${KEY_ID}" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  payload="$(printf '{"iss":"%s","sub":"%s","email":"%s","token_use":"access","client_id":"%s","iat":%s,"exp":%s}' "${ISSUER}" "${subject}" "${email}" "${client}" "${now}" "$((now + 900))" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  unsigned="${header}.${payload}"
  printf '%s' "${unsigned}" >"${TEMP_DIR}/unsigned"
  openssl dgst -sha256 -sign "${TEMP_DIR}/private.pem" -out "${TEMP_DIR}/signature" "${TEMP_DIR}/unsigned"
  printf '%s.%s' "${unsigned}" "$(b64url_file "${TEMP_DIR}/signature")"
}

for command_name in docker kind kubectl curl openssl python3 xxd; do require_command "${command_name}"; done
rm -rf "${OUTPUT_DIR}"; mkdir -p "${OUTPUT_DIR}"
TEMP_DIR="$(mktemp -d)"
cd "${REPO_ROOT}"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -pkeyopt rsa_keygen_pubexp:65537 -out "${TEMP_DIR}/private.pem" 2>/dev/null
modulus_hex="$(openssl rsa -in "${TEMP_DIR}/private.pem" -noout -modulus 2>/dev/null | cut -d= -f2)"
printf '%s' "${modulus_hex}" | xxd -r -p >"${TEMP_DIR}/modulus.bin"
modulus="$(b64url_file "${TEMP_DIR}/modulus.bin")"
printf '{"keys":[{"kid":"%s","kty":"RSA","alg":"RS256","use":"sig","n":"%s","e":"AQAB"}]}' "${KEY_ID}" "${modulus}" >"${TEMP_DIR}/jwks.json"
openssl pkey -in "${TEMP_DIR}/private.pem" -pubout -outform DER 2>/dev/null | openssl dgst -sha256 | awk '{print $2}' >"${OUTPUT_DIR}/public-key-fingerprint.txt"
printf 'issuer=%s\nkid=%s\nowner_subject=m2-owner-subject\nother_subject=m2-other-subject\ncloud_credentials_required=false\n' "${ISSUER}" "${KEY_ID}" >"${OUTPUT_DIR}/runtime-config-summary.txt"

kind get clusters | grep -Fxq "${CLUSTER_NAME}" || kind create cluster --name "${CLUSTER_NAME}"
kubectl config use-context "kind-${CLUSTER_NAME}"
docker build -t "${IMAGE_NAME}" "${REPO_ROOT}/backend"
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"
kubectl delete namespace "${NAMESPACE}" --ignore-not-found --wait=true
kubectl apply -k "${OVERLAY_DIR}"
kubectl -n "${NAMESPACE}" create configmap terraformers-jwks --from-file=jwks.json="${TEMP_DIR}/jwks.json" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "${NAMESPACE}" rollout restart deployment/terraformers-jwks
kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-mariadb --timeout=240s
kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-jwks --timeout=180s
jwks_ready=PASS
kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-backend --timeout=300s
kubectl -n "${NAMESPACE}" port-forward service/terraformers-backend "${BACKEND_PORT}:80" >"${OUTPUT_DIR}/backend-port-forward.log" 2>&1 & BACKEND_FORWARD_PID="$!"
for _ in $(seq 1 60); do curl -fsS "http://127.0.0.1:${BACKEND_PORT}/actuator/health" >"${OUTPUT_DIR}/backend-health.json" 2>/dev/null && break; sleep 2; done
grep -q '"status":"UP"' "${OUTPUT_DIR}/backend-health.json"
runtime_ready=PASS

owner_token="$(make_token m2-owner-subject owner@example.test "${CLIENT_ID}")"
other_token="$(make_token m2-other-subject other@example.test "${CLIENT_ID}")"
invalid_token="$(make_token m2-invalid-subject invalid@example.test wrong-client)"
assert_status 401 GET /api/projects; anonymous_protected_rejected=PASS
assert_status 401 GET /api/projects "${invalid_token}"; invalid_token_rejected=PASS
assert_status 200 GET /api/projects "${owner_token}"; owner_token_accepted=PASS
assert_status 200 GET /api/projects "${owner_token}"
owner_row="$(db_query "SELECT user_id,external_identity_provider,external_identity_subject,email FROM users WHERE external_identity_provider='cognito' AND external_identity_subject='m2-owner-subject';")"
printf '%s\n' "${owner_row}" >"${OUTPUT_DIR}/sanitized-identity-rows.txt"
owner_id="$(cut -f1 <<<"${owner_row}")"; [[ "${owner_id}" =~ ^[0-9]+$ ]]; numeric_user_id=PASS
grep -q $'cognito\tm2-owner-subject\towner@example.test' <<<"${owner_row}"; owner_user_created=PASS; provider_subject_persisted=PASS
[[ "$(db_query "SELECT COUNT(*) FROM users WHERE external_identity_provider='cognito' AND external_identity_subject='m2-owner-subject';")" == 1 ]]; same_identity_reused=PASS
assert_status 200 GET /api/projects "${other_token}"
other_id="$(db_query "SELECT user_id FROM users WHERE external_identity_provider='cognito' AND external_identity_subject='m2-other-subject';")"
[[ "${other_id}" =~ ^[0-9]+$ && "${other_id}" != "${owner_id}" ]]; second_identity_distinct=PASS
db_query "SELECT user_id,external_identity_provider,external_identity_subject,email FROM users WHERE external_identity_provider='cognito' AND external_identity_subject='m2-other-subject';" >>"${OUTPUT_DIR}/sanitized-identity-rows.txt"
db_query "INSERT INTO projects(owner_user_id,name,description,visibility,status,created_at,updated_at) VALUES (${owner_id},'M2 Ownership Fixture',NULL,'PRIVATE','ACTIVE',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));" >/dev/null
project_id="$(db_query "SELECT project_id FROM projects WHERE owner_user_id=${owner_id} AND name='M2 Ownership Fixture' ORDER BY project_id DESC LIMIT 1;")"
assert_status 200 GET "/api/projects/${project_id}" "${owner_token}"; owner_private_access=PASS
curl -fsS -H "Authorization: Bearer ${owner_token}" "http://127.0.0.1:${BACKEND_PORT}/api/projects" | python3 -c "import json,sys; assert ${project_id} in [p['projectId'] for p in json.load(sys.stdin)]"
assert_status 403 GET "/api/projects/${project_id}" "${other_token}"; non_owner_private_forbidden=PASS
assert_status 403 PATCH "/api/projects/${project_id}/visibility" "${other_token}" '{"visibility":"PUBLIC"}'; non_owner_modification_forbidden=PASS
assert_status 200 PATCH "/api/projects/${project_id}/visibility" "${owner_token}" '{"visibility":"PUBLIC"}'
[[ "$(db_query "SELECT visibility FROM projects WHERE project_id=${project_id};")" == PUBLIC ]]; owner_modification_allowed=PASS
assert_status 204 PATCH /api/users/me/display-name "${owner_token}" '{"displayName":"M2 Owner"}'
[[ "$(db_query "SELECT display_name FROM users WHERE user_id=${owner_id};")" == 'M2 Owner' ]]; display_name_update=PASS
assert_status 200 GET /api/projects "${owner_token}"
[[ "$(db_query "SELECT display_name FROM users WHERE user_id=${owner_id};")" == 'M2 Owner' ]]; display_name_preserved=PASS
printf 'project_id=%s\nowner_user_id=%s\nother_user_id=%s\nfinal_visibility=PUBLIC\n' "${project_id}" "${owner_id}" "${other_id}" >"${OUTPUT_DIR}/ownership-verification.txt"
echo '[portable-authenticated] identity and ownership verification passed'
