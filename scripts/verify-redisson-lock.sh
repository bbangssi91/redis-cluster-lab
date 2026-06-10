#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
lock_key="${2:-phase5:redisson:verify:$(date +%s)}"

json_post() {
  local path="$1"
  local body="$2"
  curl -s -X POST "${base_url}${path}" \
    -H "Content-Type: application/json" \
    -d "${body}"
}

require_json_value() {
  local response="$1"
  local expected="$2"
  if [[ "${response}" != *"${expected}"* ]]; then
    echo "응답에 ${expected} 값이 포함되어야 합니다." >&2
    echo "${response}" >&2
    exit 1
  fi
}

echo "락 key: ${lock_key}"

echo
echo "== 1. Redisson RLock 획득 성공 =="
acquire_a="$(json_post "/locks/redisson/try-acquire" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-a\",\"waitMillis\":0,\"leaseMillis\":10000}")"
echo "${acquire_a}"
require_json_value "${acquire_a}" '"acquired":true'

echo
echo "== 2. 같은 key에 대한 다른 owner 획득 실패 =="
acquire_b="$(json_post "/locks/redisson/try-acquire" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-b\",\"waitMillis\":100,\"leaseMillis\":10000}")"
echo "${acquire_b}"
require_json_value "${acquire_b}" '"acquired":false'

echo
echo "== 3. 다른 owner release 실패 =="
wrong_release="$(json_post "/locks/redisson/release" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-b\"}")"
echo "${wrong_release}"
require_json_value "${wrong_release}" '"released":false'

echo
echo "== 4. app-local owner 확인 후 forceUnlock release 성공 =="
release_a="$(json_post "/locks/redisson/release" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-a\"}")"
echo "${release_a}"
require_json_value "${release_a}" '"released":true'

echo
echo "== 5. release 이후 다시 획득 성공 =="
acquire_c="$(json_post "/locks/redisson/try-acquire" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-c\",\"waitMillis\":0,\"leaseMillis\":10000}")"
echo "${acquire_c}"
require_json_value "${acquire_c}" '"acquired":true'

echo
echo "== 정리 =="
json_post "/locks/redisson/release" "{\"lockKey\":\"${lock_key}\",\"owner\":\"verify-c\"}"
echo
