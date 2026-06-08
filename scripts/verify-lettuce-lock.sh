#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
lock_key="${2:-phase4:lock:verify:$(date +%s)}"

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

extract_token() {
  sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

echo "락 key: ${lock_key}"

echo
echo "== 1. token A로 락 획득 성공 =="
acquire_a="$(json_post "/locks/lettuce/acquire" "{\"lockKey\":\"${lock_key}\",\"ttlMillis\":10000,\"owner\":\"verify-a\"}")"
echo "${acquire_a}"
require_json_value "${acquire_a}" '"acquired":true'
token_a="$(printf '%s' "${acquire_a}" | extract_token)"

echo
echo "== 2. token A의 락이 살아 있는 동안 두 번째 락 획득 실패 =="
acquire_b="$(json_post "/locks/lettuce/acquire" "{\"lockKey\":\"${lock_key}\",\"ttlMillis\":10000,\"owner\":\"verify-b\"}")"
echo "${acquire_b}"
require_json_value "${acquire_b}" '"acquired":false'

echo
echo "== 3. 잘못된 token으로 락 해제 실패 =="
wrong_release="$(json_post "/locks/lettuce/release" "{\"lockKey\":\"${lock_key}\",\"token\":\"wrong-token\"}")"
echo "${wrong_release}"
require_json_value "${wrong_release}" '"released":false'

echo
echo "== 4. token A로 락 해제 성공 =="
release_a="$(json_post "/locks/lettuce/release" "{\"lockKey\":\"${lock_key}\",\"token\":\"${token_a}\"}")"
echo "${release_a}"
require_json_value "${release_a}" '"released":true'

echo
echo "== 5. 락 해제 후 다시 락 획득 성공 =="
acquire_c="$(json_post "/locks/lettuce/acquire" "{\"lockKey\":\"${lock_key}\",\"ttlMillis\":10000,\"owner\":\"verify-c\"}")"
echo "${acquire_c}"
require_json_value "${acquire_c}" '"acquired":true'
token_c="$(printf '%s' "${acquire_c}" | extract_token)"

echo
echo "== 정리 =="
json_post "/locks/lettuce/release" "{\"lockKey\":\"${lock_key}\",\"token\":\"${token_c}\"}"
echo
