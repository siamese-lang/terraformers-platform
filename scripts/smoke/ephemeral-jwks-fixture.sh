#!/usr/bin/env bash
# Shared proven A7-7 JWT/JWKS fixture, also usable for isolated local browser validation.
set -euo pipefail
umask 077
: "${RUNNER_TEMP:?}"
b64url_file() {
  openssl base64 -A <"$1" | tr '+/' '-_' | tr -d '='
}
generate_key() {
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -pkeyopt rsa_keygen_pubexp:65537 -out "$private_key" 2>/dev/null
  modulus_hex="$(openssl rsa -in "$private_key" -noout -modulus 2>/dev/null | cut -d= -f2)"
  printf '%s' "$modulus_hex" | python3 -c 'import sys; sys.stdout.buffer.write(bytes.fromhex(sys.stdin.read()))' > "$modulus_bin"
  modulus="$(b64url_file "$modulus_bin")"
  printf '{"keys":[{"kid":"%s","kty":"RSA","alg":"RS256","use":"sig","n":"%s","e":"AQAB"}]}' \
    "$kid" "$modulus" > "$jwks"
}
sign_token() {
  openssl dgst -sha256 -sign "$private_key" -out "$signature" "$unsigned"
  printf '%s.%s' "$(cat "$unsigned")" "$(b64url_file "$signature")" > "$token_file"
  chmod 600 "$private_key" "$token_file" "$unsigned" "$signature"
}
local_browser_fixture() {
  # No kubectl, hosted IdP, credentials, or retained-runtime mutation in this mode.
  : "${ISSUER_URI:?}" "${CLIENT_ID:?}"
  private_key="${RUNNER_TEMP}/case-c-private.pem"
  modulus_bin="${RUNNER_TEMP}/case-c-modulus.bin"
  jwks="${RUNNER_TEMP}/case-c-jwks.json"
  kid="pt6-local-browser"
  generate_key
  python3 - "$RUNNER_TEMP" "$ISSUER_URI" "$CLIENT_ID" "$kid" <<'PY'
import base64, json, pathlib, sys, time
root, issuer, client, kid = sys.argv[1:]
def encode(value):
    return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).decode().rstrip('=')
now = int(time.time())
header = encode({'alg': 'RS256', 'kid': kid, 'typ': 'JWT'})
for owner in ('owner', 'other'):
    for use in ('access', 'id'):
        claims = {'iss': issuer, 'sub': 'pt6-browser-' + owner, 'iat': now, 'exp': now + 1200,
                  'email': owner + '@example.test', 'nickname': 'PT6 ' + owner, 'token_use': use}
        claims.update({'client_id': client, 'username': owner + '@example.test'} if use == 'access'
                      else {'aud': client, 'cognito:username': owner + '@example.test'})
        pathlib.Path(root, f'pt6-{owner}-{use}.unsigned').write_text(header + '.' + encode(claims))
PY
  for owner in owner other; do
    for use in access id; do
      unsigned="${RUNNER_TEMP}/pt6-${owner}-${use}.unsigned"
      signature="${RUNNER_TEMP}/pt6-${owner}-${use}.signature"
      token_file="${RUNNER_TEMP}/pt6-${owner}-${use}.token"
      sign_token
    done
  done
}
prepare_fixture() {
: "${OPERATION:?}" "${ISSUER_URI:?}" "${CLIENT_ID:?}"
previous_jwks="${RUNNER_TEMP}/previous-jwks.json"
private_key="${RUNNER_TEMP}/case-c-private.pem"
modulus_bin="${RUNNER_TEMP}/case-c-modulus.bin"
jwks="${RUNNER_TEMP}/case-c-jwks.json"
token_file="${RUNNER_TEMP}/case-c-access.token"
unsigned="${RUNNER_TEMP}/case-c-unsigned.token"
signature="${RUNNER_TEMP}/case-c-signature.bin"
kid="case-c-${GITHUB_RUN_ID}"
identity_run_id="${IDENTITY_RUN_ID:-${GITHUB_RUN_ID}}"
[[ "$identity_run_id" =~ ^[0-9]+$ ]] || {
  echo 'IDENTITY_RUN_ID must be a numeric GitHub run ID.' >&2
  exit 1
}
if [[ "$OPERATION" == pt2-realistic-continuation ]]; then
  [[ "$identity_run_id" == 37480519016 ]] || {
    echo 'PT-2 continuation requires the exact original fixture owner.' >&2
    exit 1
  }
elif [[ "$identity_run_id" != "$GITHUB_RUN_ID" ]]; then
  echo 'Fixture owner override is restricted to the bound PT-2 continuation operation.' >&2
  exit 1
fi

kubectl -n "$NAMESPACE" get configmap terraformers-jwks             -o jsonpath='{.data.jwks\.json}' > "$previous_jwks"
test -s "$previous_jwks"
jq -e '.keys == []' "$previous_jwks" >/dev/null || {
  echo 'Refusing to replace a non-placeholder JWKS configuration.' >&2
  exit 1
}

# Reuse the already-proven M2 portable-authenticated JWT/JWK construction.
generate_key

touch "${RUNNER_TEMP}/ephemeral-jwks-owned.marker"
kubectl -n "$NAMESPACE" create configmap terraformers-jwks             --from-file=jwks.json="$jwks"             --dry-run=client -o yaml             | kubectl apply -f -
kubectl -n "$NAMESPACE" rollout restart deployment/terraformers-jwks
kubectl -n "$NAMESPACE" rollout status deployment/terraformers-jwks --timeout=2m

served_jwks="$(
  kubectl -n "$NAMESPACE" exec deployment/terraformers-jwks --               wget -qO- http://127.0.0.1:8080/jwks.json
)"
jq -e --arg kid "$kid" --arg modulus "$modulus" '
  .keys | length == 1
  and .[0].kid == $kid
  and .[0].alg == "RS256"
  and .[0].n == $modulus
  and .[0].e == "AQAB"
' <<< "$served_jwks" >/dev/null

service_visible=false
for _ in $(seq 1 30); do
  backend_seen_jwks="$(
    kubectl -n "$NAMESPACE" exec deployment/terraformers-backend                 -c backend --                 curl -fsS http://terraformers-jwks:8080/jwks.json 2>/dev/null || true
  )"
  if jq -e --arg kid "$kid" --arg modulus "$modulus" '
    .keys | length == 1
    and .[0].kid == $kid
    and .[0].alg == "RS256"
    and .[0].n == $modulus
    and .[0].e == "AQAB"
  ' <<< "$backend_seen_jwks" >/dev/null 2>&1; then
    service_visible=true
    break
  fi
  sleep 1
done
[[ "$service_visible" == true ]] || {
  echo 'Backend pod did not observe the new JWKS through the Service path.' >&2
  exit 1
}

now="$(date +%s)"
header="$(
  printf '{"alg":"RS256","kid":"%s","typ":"JWT"}' "$kid"               | openssl base64 -A | tr '+/' '-_' | tr -d '='
)"
token_ttl=900
if [[ "$OPERATION" == backend-live-validation-c2-stability || "$OPERATION" == a7-7-broad-v4-live-proof || "$OPERATION" == pt2-realistic-baseline || "$OPERATION" == pt2-realistic-continuation ]]; then
  token_ttl=10800
fi
payload="$(
  printf '{"iss":"%s","sub":"%s","email":"%s","token_use":"access","client_id":"%s","iat":%s,"exp":%s}'               "$ISSUER_URI"               "case-c-${identity_run_id}"               "case-c-${identity_run_id}@example.test"               "$CLIENT_ID"               "$now"               "$((now + token_ttl))"               | openssl base64 -A | tr '+/' '-_' | tr -d '='
)"
printf '%s.%s' "$header" "$payload" > "$unsigned"
sign_token

}
restore_fixture() {
previous_jwks="${RUNNER_TEMP}/previous-jwks.json"
# Even failed restoration must remove this run's private key/token/header material.
# Retain the ownership marker on failure so the un-restored fixture is never claimed as clean.
trap 'rm -f "${RUNNER_TEMP}/case-c-private.pem" "${RUNNER_TEMP}/case-c-access.token" "${RUNNER_TEMP}/case-c-unsigned.token" "${RUNNER_TEMP}/case-c-signature.bin" "${RUNNER_TEMP}/case-c-modulus.bin" "${RUNNER_TEMP}"/pt2-auth-*' EXIT
if [[ -s "$previous_jwks" && -f "${RUNNER_TEMP}/ephemeral-jwks-owned.marker" ]]; then
  jq -e ' .keys == [] ' "$previous_jwks" >/dev/null
  kubectl -n "$NAMESPACE" create configmap terraformers-jwks               --from-file=jwks.json="$previous_jwks"               --dry-run=client -o yaml               | kubectl apply -f -
  kubectl -n "$NAMESPACE" rollout restart deployment/terraformers-jwks
  kubectl -n "$NAMESPACE" rollout status deployment/terraformers-jwks --timeout=2m
fi

rm -f "${RUNNER_TEMP}/ephemeral-jwks-owned.marker"

}
case "${1:-}" in
  prepare) : "${NAMESPACE:?}"; prepare_fixture ;;
  restore) : "${NAMESPACE:?}"; restore_fixture ;;
  local-browser) local_browser_fixture ;;
  *) echo 'Usage: ephemeral-jwks-fixture.sh prepare|restore|local-browser' >&2; exit 2 ;;
esac
