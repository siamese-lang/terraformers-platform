#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY_DIR="${REPO_ROOT}/infra/kubernetes/overlays/portable-persistent"
OUTPUT_DIR="${REPO_ROOT}/artifacts/m2-portable-persistent-runtime"
NAMESPACE="${PORTABLE_NAMESPACE:-terraformers-portable}"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-terraformers-portable-persistent}"
IMAGE_NAME="${PORTABLE_BACKEND_IMAGE:-terraformers-backend:portable-persistent}"
BACKEND_PORT="${PORTABLE_BACKEND_PORT:-18080}"
DATABASE_PORT="${PORTABLE_DATABASE_PORT:-13306}"
BACKEND_FORWARD_PID=""
DATABASE_FORWARD_PID=""

namespace_apply=FAIL
mariadb_ready=FAIL
backend_rollout=FAIL
backend_health=FAIL
flyway_schema=FAIL
repository_smoke=FAIL

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

wait_for_http() {
  local url="$1"
  for _ in $(seq 1 60); do
    curl -fsS "${url}" >"${OUTPUT_DIR}/backend-health.json" 2>/dev/null && return 0
    sleep 2
  done
  return 1
}

wait_for_port() {
  local host="$1" port="$2"
  for _ in $(seq 1 60); do
    (echo >/dev/tcp/"${host}"/"${port}") >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

collect_diagnostics() {
  mkdir -p "${OUTPUT_DIR}"
  kubectl -n "${NAMESPACE}" get pods -o wide >"${OUTPUT_DIR}/pods.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" get services -o wide >"${OUTPUT_DIR}/services.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" logs deployment/terraformers-backend --tail=-1 >"${OUTPUT_DIR}/backend.log" 2>&1 || true
  kubectl -n "${NAMESPACE}" logs deployment/terraformers-mariadb --tail=-1 >"${OUTPUT_DIR}/mariadb.log" 2>&1 || true
  cat >"${OUTPUT_DIR}/summary.txt" <<EOF
namespace_apply=${namespace_apply}
mariadb_ready=${mariadb_ready}
backend_rollout=${backend_rollout}
backend_health=${backend_health}
flyway_schema=${flyway_schema}
repository_smoke=${repository_smoke}
cloud_credentials_required=false
EOF
}

cleanup() {
  for pid in "${BACKEND_FORWARD_PID}" "${DATABASE_FORWARD_PID}"; do
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done
  collect_diagnostics
}
trap cleanup EXIT

for command_name in docker kind kubectl curl mvn; do
  require_command "${command_name}"
done

rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
cd "${REPO_ROOT}"

# Preserve a useful rendered artifact without disclosing fixture credentials.
kubectl kustomize "${OVERLAY_DIR}" \
  | sed -E '/^  (MARIADB_|SPRING_DATASOURCE_|JWT_|TERRAFORMERS_SECURITY_JWT_|UPLOAD_SOURCE_BUCKET:)/s/:.*/: REDACTED/' \
  >"${OUTPUT_DIR}/rendered-manifests.yaml"

if ! kind get clusters | grep -Fxq "${CLUSTER_NAME}"; then
  echo "[portable-persistent] creating Kind cluster ${CLUSTER_NAME}"
  kind create cluster --name "${CLUSTER_NAME}"
else
  echo "[portable-persistent] reusing Kind cluster ${CLUSTER_NAME}"
fi
kubectl config use-context "kind-${CLUSTER_NAME}"

echo "[portable-persistent] building and loading ${IMAGE_NAME}"
docker build -t "${IMAGE_NAME}" "${REPO_ROOT}/backend"
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"

# A reused cluster still gets a clean ephemeral database and schema.
kubectl delete namespace "${NAMESPACE}" --ignore-not-found --wait=true
echo "[portable-persistent] applying self-contained overlay"
kubectl apply -k "${OVERLAY_DIR}"
namespace_apply=PASS

kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-mariadb --timeout=240s
mariadb_ready=PASS
kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-backend --timeout=300s
backend_rollout=PASS

kubectl -n "${NAMESPACE}" port-forward service/terraformers-backend "${BACKEND_PORT}:80" \
  >"${OUTPUT_DIR}/backend-port-forward.log" 2>&1 &
BACKEND_FORWARD_PID="$!"
wait_for_http "http://127.0.0.1:${BACKEND_PORT}/actuator/health"
backend_health=PASS

MARIADB_POD="$(kubectl -n "${NAMESPACE}" get pod -l app.kubernetes.io/name=terraformers-mariadb -o jsonpath='{.items[0].metadata.name}')"
kubectl -n "${NAMESPACE}" exec "${MARIADB_POD}" -- sh -c \
  'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names -e "SELECT installed_rank, version, description, success FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank;"' \
  | tee "${OUTPUT_DIR}/flyway-history.txt"
test -s "${OUTPUT_DIR}/flyway-history.txt"
flyway_schema=PASS

kubectl -n "${NAMESPACE}" port-forward service/terraformers-mariadb "${DATABASE_PORT}:3306" \
  >"${OUTPUT_DIR}/database-port-forward.log" 2>&1 &
DATABASE_FORWARD_PID="$!"
wait_for_port 127.0.0.1 "${DATABASE_PORT}"

echo "[portable-persistent] running existing repository smoke against the Kind MariaDB"
(
  cd "${REPO_ROOT}/backend"
  SPRING_DATASOURCE_URL="jdbc:mariadb://127.0.0.1:${DATABASE_PORT}/terraformers" \
  SPRING_DATASOURCE_USERNAME=terraformers \
  SPRING_DATASOURCE_PASSWORD=portable-test-password \
  JWT_PROVIDER=cognito \
  JWT_ISSUER_URI=https://identity.example.test/portable-runtime \
  JWT_JWK_SET_URI=http://127.0.0.1:65535/.well-known/jwks.json \
  TERRAFORMERS_SECURITY_JWT_COGNITO_CLIENT_ID=portable-runtime-client \
  UPLOAD_SOURCE_BUCKET=portable-validation-bucket \
  MANAGEMENT_CLOUDWATCH_METRICS_EXPORT_ENABLED=false \
  mvn -q -Dtest=MariaDbRepositorySmokeTest test
) >"${OUTPUT_DIR}/repository-smoke.log" 2>&1
repository_smoke=PASS

echo "[portable-persistent] runtime substrate verification passed"
