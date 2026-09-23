#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY_DIR="${REPO_ROOT}/infra/kubernetes/overlays/portable-object-store"
OUTPUT_DIR="${REPO_ROOT}/artifacts/m2-object-byte-storage"
NAMESPACE="${PORTABLE_NAMESPACE:-terraformers-portable}"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-terraformers-portable-object-store}"
IMAGE_NAME="${PORTABLE_BACKEND_IMAGE:-terraformers-backend:portable-persistent}"
BACKEND_PORT="${PORTABLE_BACKEND_PORT:-18080}"
ISSUER="https://identity.example.test/portable-authenticated"
CLIENT_ID="portable-runtime-client"
KEY_ID="terraformers-m2-test-key"
BACKEND_FORWARD_PID=""
TEMP_DIR=""

checks=(runtime_ready authenticated_upload project_created source_file_registered source_binary_persisted source_byte_readback source_byte_checksum_match source_object_metadata analysis_job_created analysis_terminal terraform_validation result_file_registered result_binary_persisted result_object_exists terraform_inline_readback terraform_checksum_match result_object_checksum_match project_linkage project_tree_linkage)
for check in "${checks[@]}"; do printf -v "${check}" NOT_COVERED; done
analysis_status=NOT_COVERED
analysis_provider=NOT_COVERED
result_file_id=NOT_COVERED
result_object_key=NOT_COVERED
failure_reason=NOT_COVERED
source_image_http_status=NOT_COVERED
source_object_http_status=NOT_COVERED
source_storage_provider=NOT_COVERED
result_storage_provider=NOT_COVERED
first_confirmed_gap=none

require_command() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }; }
db_query() {
  kubectl -n "${NAMESPACE}" exec deployment/terraformers-mariadb -- sh -c \
    'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names -e "$1"' -- "$1"
}
b64url_file() { openssl base64 -A <"$1" | tr '+/' '-_' | tr -d '='; }
make_token() {
  local now header payload unsigned
  now="$(date +%s)"
  header="$(printf '{"alg":"RS256","kid":"%s","typ":"JWT"}' "${KEY_ID}" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  payload="$(printf '{"iss":"%s","sub":"m2-upload-owner","email":"m2-upload-owner@example.test","token_use":"access","client_id":"%s","iat":%s,"exp":%s}' "${ISSUER}" "${CLIENT_ID}" "${now}" "$((now + 900))" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  unsigned="${header}.${payload}"
  printf '%s' "${unsigned}" >"${TEMP_DIR}/unsigned"
  openssl dgst -sha256 -sign "${TEMP_DIR}/private.pem" -out "${TEMP_DIR}/signature" "${TEMP_DIR}/unsigned"
  printf '%s.%s' "${unsigned}" "$(b64url_file "${TEMP_DIR}/signature")"
}
json_value() { python3 -c 'import json,sys; v=json.load(open(sys.argv[1])).get(sys.argv[2]); print("" if v is None else str(v).lower() if isinstance(v,bool) else v)' "$1" "$2"; }
api_get() {
  local path="$1" output="$2"
  curl -sS -o "${output}" -w '%{http_code}' -H "Authorization: Bearer ${owner_token}" "http://127.0.0.1:${BACKEND_PORT}${path}"
}
set_gap() { [[ "${first_confirmed_gap}" != none ]] || first_confirmed_gap="$1"; }

collect_diagnostics() {
  mkdir -p "${OUTPUT_DIR}"
  kubectl -n "${NAMESPACE}" get pods -o wide >"${OUTPUT_DIR}/pods.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" get services -o wide >"${OUTPUT_DIR}/services.txt" 2>&1 || true
  kubectl -n "${NAMESPACE}" logs deployment/terraformers-backend --tail=-1 >"${OUTPUT_DIR}/backend.log" 2>&1 || true
  {
    for check in "${checks[@]}"; do printf '%s=%s\n' "${check}" "${!check}"; done
    printf 'analysis_status=%s\nsource_storage_provider=%s\nresult_storage_provider=%s\nsource_image_http_status=%s\nsource_object_http_status=%s\nfirst_confirmed_gap=%s\ncloud_credentials_required=false\n' \
      "${analysis_status}" "${source_storage_provider}" "${result_storage_provider}" "${source_image_http_status}" "${source_object_http_status}" "${first_confirmed_gap}"
    printf 'analysis_provider=%s\nresult_file_id=%s\nresult_object_key=%s\nfailure_reason=%s\nresult_object_byte_persistence=%s\n' \
      "${analysis_provider}" "${result_file_id}" "${result_object_key}" "${failure_reason}" "${result_binary_persisted}"
  } >"${OUTPUT_DIR}/summary.txt"
}
cleanup() {
  [[ -z "${BACKEND_FORWARD_PID}" ]] || kill "${BACKEND_FORWARD_PID}" >/dev/null 2>&1 || true
  collect_diagnostics
  [[ -z "${TEMP_DIR}" ]] || rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

for command_name in base64 curl docker kind kubectl openssl python3 sha256sum stat xxd; do require_command "${command_name}"; done
rm -rf "${OUTPUT_DIR}"; mkdir -p "${OUTPUT_DIR}"
TEMP_DIR="$(mktemp -d)"
cd "${REPO_ROOT}"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -pkeyopt rsa_keygen_pubexp:65537 -out "${TEMP_DIR}/private.pem" 2>/dev/null
modulus_hex="$(openssl rsa -in "${TEMP_DIR}/private.pem" -noout -modulus 2>/dev/null | cut -d= -f2)"
printf '%s' "${modulus_hex}" | xxd -r -p >"${TEMP_DIR}/modulus.bin"
printf '{"keys":[{"kid":"%s","kty":"RSA","alg":"RS256","use":"sig","n":"%s","e":"AQAB"}]}' "${KEY_ID}" "$(b64url_file "${TEMP_DIR}/modulus.bin")" >"${TEMP_DIR}/jwks.json"
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' | base64 -d >"${TEMP_DIR}/architecture.png"
upload_size="$(stat -c %s "${TEMP_DIR}/architecture.png")"
upload_sha256="$(sha256sum "${TEMP_DIR}/architecture.png" | cut -d' ' -f1)"
printf 'overlay=infra/kubernetes/overlays/portable-object-store\nobject_reader_provider=filesystem\nobject_writer_provider=filesystem\nanalysis_provider=stub\nembedding_provider=disabled\nretrieval_mode=DISABLED\nissuer=%s\ncloud_credentials_required=false\n' "${ISSUER}" >"${OUTPUT_DIR}/runtime-config-summary.txt"
printf 'filename=architecture.png\nsize_bytes=%s\nsha256=%s\n' "${upload_size}" "${upload_sha256}" >"${OUTPUT_DIR}/upload-input-identity.txt"

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
kubectl -n "${NAMESPACE}" rollout status deployment/terraformers-backend --timeout=300s
kubectl -n "${NAMESPACE}" port-forward service/terraformers-backend "${BACKEND_PORT}:80" >"${OUTPUT_DIR}/backend-port-forward.log" 2>&1 & BACKEND_FORWARD_PID="$!"
for _ in $(seq 1 60); do curl -fsS "http://127.0.0.1:${BACKEND_PORT}/actuator/health" >"${OUTPUT_DIR}/backend-health.json" 2>/dev/null && break; sleep 2; done
grep -q '"status":"UP"' "${OUTPUT_DIR}/backend-health.json"
runtime_ready=PASS
owner_token="$(make_token)"

upload_status="$(curl -sS -o "${OUTPUT_DIR}/upload-response.json" -w '%{http_code}' -H "Authorization: Bearer ${owner_token}" -F "file=@${TEMP_DIR}/architecture.png;type=image/png" -F 'projectName=M2 Object Byte Storage Parity' "http://127.0.0.1:${BACKEND_PORT}/api/upload")"
printf '%s\n' "${upload_status}" >"${OUTPUT_DIR}/upload-http-status.txt"
if [[ "${upload_status}" != 201 ]]; then authenticated_upload=FAIL; set_gap "authenticated_upload_http_${upload_status}"; exit 0; fi
authenticated_upload=PASS
if ! python3 - "${OUTPUT_DIR}/upload-response.json" <<'CHECK_UPLOAD'
import json, sys
d=json.load(open(sys.argv[1]))
assert d.get("storageProvider") == "filesystem"
assert d.get("binaryPersisted") is True
CHECK_UPLOAD
then authenticated_upload=FAIL; set_gap upload_storage_classification; exit 0; fi
project_id="$(json_value "${OUTPUT_DIR}/upload-response.json" projectId)"
source_file_id="$(json_value "${OUTPUT_DIR}/upload-response.json" sourceFileId)"
job_id="$(json_value "${OUTPUT_DIR}/upload-response.json" analysisJobId)"
python3 - "${OUTPUT_DIR}/upload-response.json" >"${OUTPUT_DIR}/upload-response-classification.txt" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
for key in ("analysisJobId", "projectId", "sourceFileId", "sourceBucket", "sourceKey", "storageProvider", "binaryPersisted", "status", "analysisMode"):
    print(f"{key}={data.get(key)}")
PY
[[ "${project_id}" =~ ^[0-9]+$ ]] && project_created=PASS || { project_created=FAIL; set_gap upload_missing_project_id; exit 0; }
[[ "${source_file_id}" =~ ^[0-9]+$ ]] && source_file_registered=PASS || { source_file_registered=FAIL; set_gap upload_missing_source_file_id; exit 0; }
[[ -n "${job_id}" ]] && analysis_job_created=PASS || { analysis_job_created=FAIL; set_gap upload_missing_analysis_job_id; exit 0; }

source_row="$(db_query "SELECT project_id,file_type,COALESCE(storage_provider,'NULL'),binary_persisted,COALESCE(s3_bucket,'NULL'),COALESCE(s3_key,'NULL'),COALESCE(size_bytes,'NULL') FROM project_files WHERE file_id=${source_file_id};")"
{
  echo -e 'SOURCE_PROJECT_FILE\nproject_id\tfile_type\tstorage_provider\tbinary_persisted\ts3_bucket\ts3_key\tsize_bytes'
  printf '%s\n' "${source_row}"
} >"${OUTPUT_DIR}/database-evidence.txt"
source_storage_provider="$(cut -f3 <<<"${source_row}")"
source_persisted="$(cut -f4 <<<"${source_row}")"
if [[ "${source_persisted}" == 1 ]]; then source_binary_persisted=PASS; else source_binary_persisted=FAIL; set_gap source_binary_not_persisted; fi

for _ in $(seq 1 60); do
  job_http="$(api_get "/api/analysis/jobs/${job_id}" "${OUTPUT_DIR}/analysis-final-response.json")"
  [[ "${job_http}" == 200 ]] || { sleep 2; continue; }
  analysis_status="$(json_value "${OUTPUT_DIR}/analysis-final-response.json" status)"
  [[ "${analysis_status}" == SUCCEEDED || "${analysis_status}" == FAILED ]] && break
  sleep 2
done
if [[ "${analysis_status}" != SUCCEEDED && "${analysis_status}" != FAILED ]]; then analysis_terminal=FAIL; set_gap analysis_timeout; exit 0; fi
analysis_terminal=PASS
analysis_provider="$(json_value "${OUTPUT_DIR}/analysis-final-response.json" provider)"
result_object_key="$(json_value "${OUTPUT_DIR}/analysis-final-response.json" resultObjectKey)"
failure_reason="$(json_value "${OUTPUT_DIR}/analysis-final-response.json" failureReason)"
if [[ "${analysis_status}" != SUCCEEDED ]]; then terraform_validation=FAIL; set_gap analysis_failed; exit 0; fi
[[ "${analysis_provider}" == stub-integrated-java && -n "${result_object_key}" && -z "${failure_reason}" ]] \
  || { terraform_validation=FAIL; set_gap analysis_result_contract; exit 0; }
terraform_validation=PASS

result_file_id="$(json_value "${OUTPUT_DIR}/analysis-final-response.json" resultFileId)"
[[ "${result_file_id}" =~ ^[0-9]+$ ]] && result_file_registered=PASS || { result_file_registered=FAIL; set_gap analysis_missing_result_file_id; exit 0; }
result_row="$(db_query "SELECT file_id,project_id,path,COALESCE(storage_provider,'NULL'),binary_persisted,COALESCE(s3_bucket,'NULL'),COALESCE(s3_key,'NULL'),COALESCE(size_bytes,'NULL'),COALESCE(checksum,'NULL'),IF(COALESCE(inline_content,'')='',0,1) FROM project_files WHERE file_id=${result_file_id} AND file_type='GENERATED_TERRAFORM';")"
{
  echo -e '\nGENERATED_TERRAFORM_PROJECT_FILE\nfile_id\tproject_id\tpath\tstorage_provider\tbinary_persisted\ts3_bucket\ts3_key\tsize_bytes\tchecksum\tinline_content_present'
  printf '%s\n' "${result_row}"
  echo -e '\nANALYSIS_JOB\nid\tproject_id\tsource_file_id\tresult_file_id\tstatus\tprovider\tresult_object_key\tfailure_reason'
  db_query "SELECT id,project_id,source_file_id,COALESCE(result_file_id,'NULL'),status,COALESCE(provider,'NULL'),COALESCE(result_object_key,'NULL'),COALESCE(failure_reason,'NULL') FROM analysis_jobs WHERE id='${job_id}';"
  echo -e '\nROW_COUNTS\nusers\tprojects\tproject_files\tanalysis_jobs'
  db_query 'SELECT (SELECT COUNT(*) FROM users),(SELECT COUNT(*) FROM projects),(SELECT COUNT(*) FROM project_files),(SELECT COUNT(*) FROM analysis_jobs);'
} >>"${OUTPUT_DIR}/database-evidence.txt"
result_storage_provider="$(cut -f4 <<<"${result_row}")"
result_persisted="$(cut -f5 <<<"${result_row}")"
if [[ "${result_persisted}" == 1 ]]; then result_binary_persisted=PASS; else result_binary_persisted=FAIL; set_gap result_binary_not_persisted; fi

terraform_http="$(api_get "/api/projects/${project_id}/terraform/main.tf" "${OUTPUT_DIR}/terraform-main-tf.json")"
printf '%s\n' "${terraform_http}" >"${OUTPUT_DIR}/terraform-http-status.txt"
if [[ "${terraform_http}" == 200 ]]; then
  terraform_inline_readback=PASS
  api_checksum="$(python3 -c 'import hashlib,json,sys; print(hashlib.sha256(json.load(open(sys.argv[1]))["content"].encode()).hexdigest())' "${OUTPUT_DIR}/terraform-main-tf.json")"
  db_checksum="$(cut -f9 <<<"${result_row}")"
  [[ "${api_checksum}" == "${db_checksum}" ]] && terraform_checksum_match=PASS || { terraform_checksum_match=FAIL; set_gap terraform_checksum_mismatch; }
else terraform_inline_readback=FAIL; terraform_checksum_match=NOT_COVERED; set_gap "terraform_readback_http_${terraform_http}"; fi

project_http="$(api_get "/api/projects/${project_id}" "${OUTPUT_DIR}/project-response.json")"
printf '%s\n' "${project_http}" >"${OUTPUT_DIR}/project-http-status.txt"
tree_http="$(api_get "/api/project-tree/${project_id}" "${OUTPUT_DIR}/project-tree.json")"
printf '%s\n' "${tree_http}" >"${OUTPUT_DIR}/project-tree-http-status.txt"
if [[ "${project_http}" == 200 ]] && python3 - "${OUTPUT_DIR}/project-response.json" "${source_file_id}" "${result_file_id}" "${job_id}" <<'CHECK_PROJECT'
import json, sys
serialized=json.dumps(json.load(open(sys.argv[1])), separators=(",", ":"))
assert all(value in serialized for value in sys.argv[2:])
CHECK_PROJECT
then project_linkage=PASS; else project_linkage=FAIL; set_gap project_linkage; fi
if [[ "${tree_http}" == 200 ]] && python3 - "${OUTPUT_DIR}/project-tree.json" "${source_file_id}" "${result_file_id}" <<'CHECK_TREE'
import json, sys
serialized=json.dumps(json.load(open(sys.argv[1])), separators=(",", ":"))
assert all(value in serialized for value in sys.argv[2:])
assert "main.tf" in serialized
CHECK_TREE
then project_tree_linkage=PASS; else project_tree_linkage=FAIL; set_gap project_tree_linkage; fi
source_image_http_status="$(api_get "/api/projects/${project_id}/source-image" "${TEMP_DIR}/source-read-response.bin")"
printf '%s\n' "${source_image_http_status}" >"${OUTPUT_DIR}/source-read-http-status.txt"
if [[ "${source_image_http_status}" == 200 ]]; then
  source_byte_readback=PASS
  readback_sha256="$(sha256sum "${TEMP_DIR}/source-read-response.bin" | cut -d' ' -f1)"
  [[ "$(stat -c %s "${TEMP_DIR}/source-read-response.bin")" == 68 && "${readback_sha256}" == "${upload_sha256}" ]] && source_byte_checksum_match=PASS || { source_byte_checksum_match=FAIL; set_gap source_byte_checksum_mismatch; }
  printf 'size_bytes=%s\nsha256=%s\n' "$(stat -c %s "${TEMP_DIR}/source-read-response.bin")" "$(sha256sum "${TEMP_DIR}/source-read-response.bin" | cut -d' ' -f1)" >"${OUTPUT_DIR}/source-read-identity.txt"
else
  source_byte_readback=FAIL
  set_gap "source_byte_readback_http_${source_image_http_status}"
fi
source_object_http_status="$(api_get "/api/projects/${project_id}/source-object" "${OUTPUT_DIR}/source-object-response.txt")"
printf '%s\n' "${source_object_http_status}" >"${OUTPUT_DIR}/source-object-http-status.txt"
if [[ "${source_object_http_status}" == 200 ]] && python3 - "${OUTPUT_DIR}/source-object-response.txt" <<'CHECK_METADATA'
import json, sys
d=json.load(open(sys.argv[1]))
assert d.get("storageProvider") == "filesystem"
assert d.get("binaryPersisted") is True
assert d.get("contentLength") == 68
assert d.get("contentType") == "image/png"
CHECK_METADATA
then source_object_metadata=PASS; else source_object_metadata=FAIL; set_gap source_object_metadata; fi
source_bucket="$(cut -f5 <<<"${source_row}")"; source_key="$(cut -f6 <<<"${source_row}")"
result_bucket="$(cut -f6 <<<"${result_row}")"; result_key="$(cut -f7 <<<"${result_row}")"
source_object_sha="$(kubectl -n "${NAMESPACE}" exec deployment/terraformers-backend -- sha256sum "/tmp/terraformers-object-store/${source_bucket}/${source_key}" | cut -d' ' -f1)"
result_object_sha="$(kubectl -n "${NAMESPACE}" exec deployment/terraformers-backend -- sha256sum "/tmp/terraformers-object-store/${result_bucket}/${result_key}" | cut -d' ' -f1)"
printf 'source_sha256=%s\nresult_sha256=%s\n' "${source_object_sha}" "${result_object_sha}" >"${OUTPUT_DIR}/filesystem-object-checksums.txt"
[[ "${source_object_sha}" == "${upload_sha256}" ]] || { source_byte_checksum_match=FAIL; set_gap source_filesystem_checksum_mismatch; }
[[ -n "${result_object_sha}" ]] && result_object_exists=PASS || { result_object_exists=FAIL; set_gap result_object_missing; }
[[ "${result_object_sha}" == "${db_checksum}" && "${result_object_sha}" == "${api_checksum}" ]] && result_object_checksum_match=PASS || { result_object_checksum_match=FAIL; set_gap result_object_checksum_mismatch; }
echo '[m2-object-byte-storage] filesystem persistence and read-back verified'
