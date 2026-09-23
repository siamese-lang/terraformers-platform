#!/usr/bin/env bash

# Shared by the portable authenticated verifier and its lightweight shell
# regression check. Callers provide BACKEND_PORT and OUTPUT_DIR.
http_status() {
  local method
  local path
  local token
  local body
  local -a args

  method="$1"
  path="$2"
  token="${3:-}"
  body="${4:-}"
  args=(-sS -o /dev/null -w '%{http_code}' -X "${method}")

  [[ -z "${token}" ]] || args+=(-H "Authorization: Bearer ${token}")
  [[ -z "${body}" ]] || args+=(-H 'Content-Type: application/json' --data "${body}")

  curl "${args[@]}" "http://127.0.0.1:${BACKEND_PORT}${path}"
}

assert_status() {
  local expected
  local method
  local path
  local actual

  expected="$1"
  method="$2"
  path="$3"
  shift 3
  actual="$(http_status "${method}" "${path}" "$@")"
  printf '%s %s=%s\n' "${method}" "${path}" "${actual}" >>"${OUTPUT_DIR}/http-status-summary.txt"
  [[ "${actual}" == "${expected}" ]]
}
