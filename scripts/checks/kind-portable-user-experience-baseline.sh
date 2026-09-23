#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OVERLAY_DIR="${REPO_ROOT}/infra/kubernetes/overlays/portable-object-store"
OUTPUT_DIR="${REPO_ROOT}/artifacts/m2-user-experience-baseline"
NAMESPACE="${PORTABLE_NAMESPACE:-terraformers-portable}"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-terraformers-portable-user-experience}"
IMAGE_NAME="${PORTABLE_BACKEND_IMAGE:-terraformers-backend:m2-user-experience}"
BACKEND_PORT="${PORTABLE_BACKEND_PORT:-18080}"
ISSUER="https://identity.example.test/portable-authenticated"
CLIENT_ID="portable-runtime-client"
KEY_ID="terraformers-m2-ux-test-key"
TEMP_DIR=""; BACKEND_FORWARD_PID=""; first_confirmed_gap=none

checks=(runtime_ready project_upload analysis_terminal owned_project_list owner_private_get other_private_forbidden anonymous_private_forbidden terraform_read terraform_update terraform_update_readback other_terraform_update_forbidden anonymous_terraform_update_rejected project_tree private_comment_read_forbidden private_comment_write_forbidden other_visibility_forbidden anonymous_visibility_rejected owner_publish anonymous_public_get public_project_list frontend_public_project_list public_source_read public_terraform_read public_tree_read anonymous_comment_write_rejected canonical_comment_create canonical_comment_list compat_comment_create compat_comment_list comment_attribution project_delete)
for check in "${checks[@]}"; do printf -v "${check}" NOT_COVERED; done

require_command() { command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }; }
set_gap() { [[ "${first_confirmed_gap}" != none ]] || first_confirmed_gap="$1"; }
mark() { local name="$1" actual="$2" expected="$3"; if [[ "$actual" == "$expected" ]]; then printf -v "$name" PASS; else printf -v "$name" FAIL; set_gap "${name}_http_${actual}"; fi; }
request() { local method="$1" path="$2" output="$3" token="${4:-}" body="${5:-}"; local args=(-sS -o "$output" -w '%{http_code}' -X "$method"); [[ -z "$token" ]] || args+=(-H "Authorization: Bearer ${token}"); [[ -z "$body" ]] || args+=(-H 'Content-Type: application/json' --data "$body"); curl "${args[@]}" "http://127.0.0.1:${BACKEND_PORT}${path}"; }
db_query() { kubectl -n "$NAMESPACE" exec deployment/terraformers-mariadb -- sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" "$MARIADB_DATABASE" --batch --skip-column-names -e "$1"' -- "$1"; }
b64url_file() { openssl base64 -A <"$1" | tr '+/' '-_' | tr -d '='; }
make_token() { local sub="$1" email="$2" now header payload unsigned; now="$(date +%s)"; header="$(printf '{"alg":"RS256","kid":"%s","typ":"JWT"}' "$KEY_ID" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"; payload="$(printf '{"iss":"%s","sub":"%s","email":"%s","token_use":"access","client_id":"%s","iat":%s,"exp":%s}' "$ISSUER" "$sub" "$email" "$CLIENT_ID" "$now" "$((now+1200))" | openssl base64 -A | tr '+/' '-_' | tr -d '=')"; unsigned="${header}.${payload}"; printf %s "$unsigned" >"$TEMP_DIR/unsigned"; openssl dgst -sha256 -sign "$TEMP_DIR/private.pem" -out "$TEMP_DIR/signature" "$TEMP_DIR/unsigned"; printf '%s.%s' "$unsigned" "$(b64url_file "$TEMP_DIR/signature")"; }
jget() { python3 -c 'import json,sys; v=json.load(open(sys.argv[1])); print(v.get(sys.argv[2], ""))' "$1" "$2"; }
contains_project() { python3 - "$1" "$2" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); pid=int(sys.argv[2]); assert any(x.get("projectId")==pid for x in d)
PY
}
poll_job() { local job="$1" out="$2" status http; for _ in $(seq 1 60); do http="$(request GET "/api/analysis/jobs/$job" "$out" "$owner_token")"; if [[ "$http" == 200 ]]; then status="$(jget "$out" status)"; [[ "$status" == SUCCEEDED || "$status" == FAILED ]] && { [[ "$status" == SUCCEEDED ]]; return; }; fi; sleep 2; done; return 1; }
upload_project() { local name="$1" out="$2"; curl -sS -o "$out" -w '%{http_code}' -H "Authorization: Bearer ${owner_token}" -F "file=@${TEMP_DIR}/architecture.png;type=image/png" -F "projectName=$name" "http://127.0.0.1:${BACKEND_PORT}/api/upload"; }
collect() { mkdir -p "$OUTPUT_DIR"; kubectl -n "$NAMESPACE" get pods -o wide >"$OUTPUT_DIR/pods.txt" 2>&1 || true; kubectl -n "$NAMESPACE" logs deployment/terraformers-backend --tail=-1 >"$OUTPUT_DIR/backend.log" 2>&1 || true; { for check in "${checks[@]}"; do printf '%s=%s\n' "$check" "${!check}"; done; printf 'first_confirmed_gap=%s\ncloud_credentials_required=false\n' "$first_confirmed_gap"; } >"$OUTPUT_DIR/backend-summary.txt"; }
cleanup() { [[ -z "$BACKEND_FORWARD_PID" ]] || kill "$BACKEND_FORWARD_PID" >/dev/null 2>&1 || true; collect; [[ -z "$TEMP_DIR" ]] || rm -rf "$TEMP_DIR"; }
trap cleanup EXIT

for cmd in base64 curl docker kind kubectl openssl python3 sha256sum xxd; do require_command "$cmd"; done
rm -rf "$OUTPUT_DIR"; mkdir -p "$OUTPUT_DIR"; TEMP_DIR="$(mktemp -d)"; cd "$REPO_ROOT"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TEMP_DIR/private.pem" 2>/dev/null
openssl rsa -in "$TEMP_DIR/private.pem" -noout -modulus 2>/dev/null | cut -d= -f2 | xxd -r -p >"$TEMP_DIR/modulus.bin"
printf '{"keys":[{"kid":"%s","kty":"RSA","alg":"RS256","use":"sig","n":"%s","e":"AQAB"}]}' "$KEY_ID" "$(b64url_file "$TEMP_DIR/modulus.bin")" >"$TEMP_DIR/jwks.json"
printf %s 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=' | base64 -d >"$TEMP_DIR/architecture.png"
printf 'overlay=infra/kubernetes/overlays/portable-object-store\nobject_store=filesystem\nanalysis_provider=stub\nissuer=%s\ncloud_credentials_required=false\n' "$ISSUER" >"$OUTPUT_DIR/runtime-config-summary.txt"
kind get clusters | grep -Fxq "$CLUSTER_NAME" || kind create cluster --name "$CLUSTER_NAME"
kubectl config use-context "kind-$CLUSTER_NAME"; docker build -t "$IMAGE_NAME" backend; kind load docker-image "$IMAGE_NAME" --name "$CLUSTER_NAME"
kubectl delete namespace "$NAMESPACE" --ignore-not-found --wait=true; kubectl apply -k "$OVERLAY_DIR"
kubectl -n "$NAMESPACE" create configmap terraformers-jwks --from-file=jwks.json="$TEMP_DIR/jwks.json" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" rollout restart deployment/terraformers-jwks
kubectl -n "$NAMESPACE" rollout status deployment/terraformers-mariadb --timeout=240s; kubectl -n "$NAMESPACE" rollout status deployment/terraformers-jwks --timeout=180s; kubectl -n "$NAMESPACE" rollout status deployment/terraformers-backend --timeout=300s
kubectl -n "$NAMESPACE" port-forward service/terraformers-backend "$BACKEND_PORT:80" >"$OUTPUT_DIR/backend-port-forward.log" 2>&1 & BACKEND_FORWARD_PID=$!
for _ in $(seq 1 60); do curl -fsS "http://127.0.0.1:$BACKEND_PORT/actuator/health" >"$OUTPUT_DIR/backend-health.json" 2>/dev/null && break; sleep 2; done
grep -q '"status":"UP"' "$OUTPUT_DIR/backend-health.json"; runtime_ready=PASS
owner_token="$(make_token m2-ux-owner m2-ux-owner@example.test)"; other_token="$(make_token m2-ux-other m2-ux-other@example.test)"

status="$(upload_project 'M2 UX Parity' "$OUTPUT_DIR/upload-response.json")"; mark project_upload "$status" 201; [[ "$project_upload" == PASS ]] || exit 0
project_id="$(jget "$OUTPUT_DIR/upload-response.json" projectId)"; source_file_id="$(jget "$OUTPUT_DIR/upload-response.json" sourceFileId)"; job_id="$(jget "$OUTPUT_DIR/upload-response.json" analysisJobId)"
if poll_job "$job_id" "$OUTPUT_DIR/analysis-response.json"; then analysis_terminal=PASS; else analysis_terminal=FAIL; set_gap analysis_terminal; exit 0; fi

status="$(request GET /api/projects "$OUTPUT_DIR/owned-projects.json" "$owner_token")"; if [[ "$status" == 200 ]] && contains_project "$OUTPUT_DIR/owned-projects.json" "$project_id" && python3 - "$OUTPUT_DIR/owned-projects.json" "$project_id" "$source_file_id" <<'PY'
import json,sys
p=next(x for x in json.load(open(sys.argv[1])) if x["projectId"]==int(sys.argv[2]))
assert p["displayName"]=="M2 UX Parity" and p["visibility"]=="PRIVATE"
assert p["sourceFileId"]==int(sys.argv[3]) and p["latestResultFileId"] and p["latestAnalysisJobId"] and p["analysisStatus"]=="SUCCEEDED"
PY
then owned_project_list=PASS; else owned_project_list=FAIL; set_gap owned_project_list; fi
mark owner_private_get "$(request GET "/api/projects/$project_id" "$OUTPUT_DIR/owner-private.json" "$owner_token")" 200
mark other_private_forbidden "$(request GET "/api/projects/$project_id" "$OUTPUT_DIR/other-private.txt" "$other_token")" 403
mark anonymous_private_forbidden "$(request GET "/api/projects/$project_id" "$OUTPUT_DIR/anonymous-private.txt")" 403
mark terraform_read "$(request GET "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/terraform-before.json" "$owner_token")" 200
updated_content=$'resource "terraform_data" "m2_ux" {\n  input = "updated"\n}\n'; update_body="$(python3 -c 'import json,sys; print(json.dumps({"content":sys.argv[1]}))' "$updated_content")"
mark terraform_update "$(request PUT "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/terraform-update.json" "$owner_token" "$update_body")" 200
read_status="$(request GET "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/terraform-after.json" "$owner_token")"; if [[ "$read_status" == 200 ]] && python3 - "$OUTPUT_DIR/terraform-after.json" "$updated_content" <<'PY'
import json,sys
assert json.load(open(sys.argv[1]))["content"] == sys.argv[2]
PY
then terraform_update_readback=PASS; else terraform_update_readback=FAIL; set_gap terraform_update_readback; fi
result_file_id="$(jget "$OUTPUT_DIR/owner-private.json" latestResultFileId)"; db_row="$(db_query "SELECT checksum,SHA2(inline_content,256),s3_bucket,s3_key FROM project_files WHERE file_id=$result_file_id;")"; db_checksum="$(cut -f1 <<<"$db_row")"; db_inline_sha="$(cut -f2 <<<"$db_row")"; expected_sha="$(printf %s "$updated_content" | sha256sum | cut -d' ' -f1)"; object_sha="$(kubectl -n "$NAMESPACE" exec deployment/terraformers-backend -- sha256sum "/tmp/terraformers-object-store/$(cut -f3 <<<"$db_row")/$(cut -f4 <<<"$db_row")" | cut -d' ' -f1)"; printf 'result_file_id=%s\nexpected_sha256=%s\ndatabase_checksum=%s\ndatabase_inline_sha256=%s\nfilesystem_sha256=%s\n' "$result_file_id" "$expected_sha" "$db_checksum" "$db_inline_sha" "$object_sha" >"$OUTPUT_DIR/terraform-update-storage-evidence.txt"; [[ "$expected_sha" == "$db_checksum" && "$expected_sha" == "$db_inline_sha" && "$expected_sha" == "$object_sha" ]] || { terraform_update_readback=FAIL; set_gap terraform_update_object_checksum; }
mark other_terraform_update_forbidden "$(request PUT "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/other-update.txt" "$other_token" "$update_body")" 403
mark anonymous_terraform_update_rejected "$(request PUT "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/anonymous-update.txt" '' "$update_body")" 401
tree_status="$(request GET "/api/project-tree/$project_id" "$OUTPUT_DIR/private-tree.json" "$owner_token")"; if [[ "$tree_status" == 200 ]] && python3 - "$OUTPUT_DIR/private-tree.json" "$source_file_id" "$result_file_id" "$job_id" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); s=json.dumps(d)
assert d["visibility"]=="PRIVATE" and all(x in s for x in sys.argv[2:]) and "main.tf" in s and "source" in s.lower() and "terraform" in s.lower()
PY
then project_tree=PASS; else project_tree=FAIL; set_gap project_tree; fi
mark private_comment_read_forbidden "$(request GET "/api/projects/$project_id/comments" "$OUTPUT_DIR/private-comments.txt")" 403
mark private_comment_write_forbidden "$(request POST "/api/projects/$project_id/comments" "$OUTPUT_DIR/private-comment-write.txt" "$other_token" '{"content":"private"}')" 403
visibility_body='{"visibility":"PUBLIC"}'
mark other_visibility_forbidden "$(request PATCH "/api/projects/$project_id/visibility" "$OUTPUT_DIR/other-visibility.txt" "$other_token" "$visibility_body")" 403
mark anonymous_visibility_rejected "$(request PATCH "/api/projects/$project_id/visibility" "$OUTPUT_DIR/anonymous-visibility.txt" '' "$visibility_body")" 401
publish_status="$(request PATCH "/api/projects/$project_id/visibility" "$OUTPUT_DIR/publish.json" "$owner_token" "$visibility_body")"; if [[ "$publish_status" == 200 && "$(jget "$OUTPUT_DIR/publish.json" visibility)" == PUBLIC ]]; then owner_publish=PASS; else owner_publish=FAIL; set_gap owner_publish; fi
mark anonymous_public_get "$(request GET "/api/projects/$project_id" "$OUTPUT_DIR/public-project.json")" 200
status="$(request GET /api/projects/public "$OUTPUT_DIR/public-projects.json")"; if [[ "$status" == 200 ]] && contains_project "$OUTPUT_DIR/public-projects.json" "$project_id"; then public_project_list=PASS; else public_project_list=FAIL; set_gap public_project_list; fi
status="$(request GET /api/public-projects "$OUTPUT_DIR/frontend-public-projects.json")"; if [[ "$status" == 200 ]] && contains_project "$OUTPUT_DIR/frontend-public-projects.json" "$project_id" && python3 - "$OUTPUT_DIR/frontend-public-projects.json" "$project_id" <<'PY'
import json,sys
p=next(x for x in json.load(open(sys.argv[1])) if x["projectId"]==int(sys.argv[2])); assert p["imageUrl"]==f"/api/projects/{sys.argv[2]}/source-image"
PY
then frontend_public_project_list=PASS; else frontend_public_project_list=FAIL; set_gap frontend_public_project_list; fi
mark public_source_read "$(request GET "/api/projects/$project_id/source-image" "$OUTPUT_DIR/public-source.png")" 200
mark public_terraform_read "$(request GET "/api/projects/$project_id/terraform/main.tf" "$OUTPUT_DIR/public-terraform.json")" 200
mark public_tree_read "$(request GET "/api/project-tree/$project_id" "$OUTPUT_DIR/public-tree.json")" 200
status="$(request GET "/api/projects/$project_id/comments" "$OUTPUT_DIR/comments-initial.json")"; [[ "$status" == 200 && "$(cat "$OUTPUT_DIR/comments-initial.json")" == '[]' ]] || { canonical_comment_list=FAIL; set_gap initial_comments; }
mark anonymous_comment_write_rejected "$(request POST "/api/projects/$project_id/comments" "$OUTPUT_DIR/anonymous-comment.txt" '' '{"content":"anonymous"}')" 401
canonical_body='{"content":"M2 canonical comment","author":"spoofed","userEmail":"spoofed@example.test"}'
mark canonical_comment_create "$(request POST "/api/projects/$project_id/comments" "$OUTPUT_DIR/canonical-comment.json" "$other_token" "$canonical_body")" 201
status="$(request GET "/api/projects/$project_id/comments" "$OUTPUT_DIR/canonical-comments.json")"; if [[ "$status" == 200 ]] && python3 - "$OUTPUT_DIR/canonical-comments.json" <<'PY'
import json,sys
assert any(x["content"]=="M2 canonical comment" for x in json.load(open(sys.argv[1])))
PY
then canonical_comment_list=PASS; else canonical_comment_list=FAIL; set_gap canonical_comment_list; fi
compat_body="$(printf '{\"projectId\":%s,\"content\":\"M2 compatibility comment\",\"userEmail\":\"spoofed@example.test\"}' "$project_id")"
mark compat_comment_create "$(request POST /api/addProjectComment "$OUTPUT_DIR/compat-comment.json" "$owner_token" "$compat_body")" 200
status="$(request GET "/api/getProjectComments/$project_id" "$OUTPUT_DIR/compat-comments.json")"; if [[ "$status" == 200 ]] && python3 - "$OUTPUT_DIR/compat-comments.json" <<'PY'
import json,sys
c=json.load(open(sys.argv[1])); assert {x["content"] for x in c} >= {"M2 canonical comment","M2 compatibility comment"}
PY
then compat_comment_list=PASS; else compat_comment_list=FAIL; set_gap compat_comment_list; fi
attribution="$(db_query "SELECT u.external_identity_subject,COUNT(*) FROM comments c JOIN users u ON u.user_id=c.writer_user_id WHERE c.board_id=(SELECT board_id FROM boards WHERE project_id=$project_id) GROUP BY u.external_identity_subject ORDER BY u.external_identity_subject;")"; printf '%s\n' "$attribution" >"$OUTPUT_DIR/comment-attribution.txt"; if grep -q $'m2-ux-other\t1' <<<"$attribution" && grep -q $'m2-ux-owner\t1' <<<"$attribution" && ! grep -q spoofed <<<"$attribution"; then comment_attribution=PASS; else comment_attribution=FAIL; set_gap comment_attribution; fi

status="$(upload_project 'M2 UX Delete Fixture' "$OUTPUT_DIR/delete-upload.json")"; if [[ "$status" == 201 ]]; then delete_id="$(jget "$OUTPUT_DIR/delete-upload.json" projectId)"; delete_job="$(jget "$OUTPUT_DIR/delete-upload.json" analysisJobId)"; poll_job "$delete_job" "$OUTPUT_DIR/delete-analysis.json" || true; other_delete="$(request DELETE "/api/projects/$delete_id" "$OUTPUT_DIR/other-delete.txt" "$other_token")"; owner_delete="$(request DELETE "/api/projects/$delete_id" "$OUTPUT_DIR/owner-delete.txt" "$owner_token")"; deleted_get="$(request GET "/api/projects/$delete_id" "$OUTPUT_DIR/deleted-get.txt" "$owner_token")"; request GET /api/projects "$OUTPUT_DIR/owned-after-delete.json" "$owner_token" >/dev/null; if [[ "$other_delete" == 403 && "$owner_delete" == 204 && "$deleted_get" == 404 ]] && ! contains_project "$OUTPUT_DIR/owned-after-delete.json" "$delete_id" 2>/dev/null; then project_delete=PASS; else project_delete=FAIL; set_gap project_delete; fi; fi
echo '[m2-user-experience] baseline collection completed'
