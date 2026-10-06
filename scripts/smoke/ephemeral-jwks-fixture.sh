#!/usr/bin/env bash
# Shared proven A7-7 JWT/JWKS fixture; only protected retained-runtime workflows call it.
set -euo pipefail
umask 077
: "${RUNNER_TEMP:?}" "${NAMESPACE:?}"
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

kubectl -n "$NAMESPACE" get configmap terraformers-jwks             -o jsonpath='{.data.jwks\.json}' > "$previous_jwks"
test -s "$previous_jwks"
jq -e '.keys == []' "$previous_jwks" >/dev/null || {
  echo 'Refusing to replace a non-placeholder JWKS configuration.' >&2
  exit 1
}

# Reuse the already-proven M2 portable-authenticated JWT/JWK construction.
b64url_file() {
  openssl base64 -A <"$1" | tr '+/' '-_' | tr -d '='
}

openssl genpkey             -algorithm RSA             -pkeyopt rsa_keygen_bits:2048             -pkeyopt rsa_keygen_pubexp:65537             -out "$private_key" 2>/dev/null

modulus_hex="$(
  openssl rsa -in "$private_key" -noout -modulus 2>/dev/null | cut -d= -f2
)"
printf '%s' "$modulus_hex" | xxd -r -p > "$modulus_bin"
modulus="$(b64url_file "$modulus_bin")"

printf '{"keys":[{"kid":"%s","kty":"RSA","alg":"RS256","use":"sig","n":"%s","e":"AQAB"}]}'             "$kid" "$modulus" > "$jwks"

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
if [[ "$OPERATION" == backend-live-validation-c2-stability || "$OPERATION" == a7-7-broad-v4-live-proof || "$OPERATION" == pt2-realistic-baseline ]]; then
  token_ttl=10800
fi
payload="$(
  printf '{"iss":"%s","sub":"%s","email":"%s","token_use":"access","client_id":"%s","iat":%s,"exp":%s}'               "$ISSUER_URI"               "case-c-${GITHUB_RUN_ID}"               "case-c-${GITHUB_RUN_ID}@example.test"               "$CLIENT_ID"               "$now"               "$((now + token_ttl))"               | openssl base64 -A | tr '+/' '-_' | tr -d '='
)"
printf '%s.%s' "$header" "$payload" > "$unsigned"
openssl dgst -sha256 -sign "$private_key" -out "$signature" "$unsigned"
printf '%s.%s' "$(cat "$unsigned")" "$(b64url_file "$signature")" > "$token_file"

chmod 600 "$private_key" "$token_file" "$unsigned" "$signature"

}
restore_fixture() {
previous_jwks="${RUNNER_TEMP}/previous-jwks.json"
if [[ -s "$previous_jwks" && -f "${RUNNER_TEMP}/ephemeral-jwks-owned.marker" ]]; then
  jq -e ' .keys == [] ' "$previous_jwks" >/dev/null
  kubectl -n "$NAMESPACE" create configmap terraformers-jwks               --from-file=jwks.json="$previous_jwks"               --dry-run=client -o yaml               | kubectl apply -f -
  kubectl -n "$NAMESPACE" rollout restart deployment/terraformers-jwks
  kubectl -n "$NAMESPACE" rollout status deployment/terraformers-jwks --timeout=2m
fi

rm -f "${RUNNER_TEMP}/ephemeral-jwks-owned.marker" "${RUNNER_TEMP}/case-c-private.pem" "${RUNNER_TEMP}/case-c-access.token" "${RUNNER_TEMP}/case-c-unsigned.token" "${RUNNER_TEMP}/case-c-signature.bin" "${RUNNER_TEMP}/case-c-modulus.bin"

}
case "${1:-}" in
  prepare) prepare_fixture ;;
  restore) restore_fixture ;;
  *) echo 'Usage: ephemeral-jwks-fixture.sh prepare|restore' >&2; exit 2 ;;
esac
