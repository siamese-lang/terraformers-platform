#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_PORT=18080
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "${OUTPUT_DIR}"' EXIT

# shellcheck source=lib/http-status.sh
source "${REPO_ROOT}/scripts/checks/lib/http-status.sh"

curl() {
  printf '401'
}

[[ "$(http_status GET /api/projects)" == 401 ]]
assert_status 401 GET /api/projects
grep -Fxq 'GET /api/projects=401' "${OUTPUT_DIR}/http-status-summary.txt"

echo 'http status helper verification passed'
