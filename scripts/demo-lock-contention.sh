#!/usr/bin/env bash
set -euo pipefail

lock_key="${1:-phase4:lock:contention:$(date +%s)}"
workers="${2:-6}"
attempts_per_worker="${3:-3}"
ttl_millis="${4:-1000}"
work_millis="${5:-250}"

apps=(
  "http://localhost:8081"
  "http://localhost:8082"
  "http://localhost:8083"
)

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

body="{\"lockKey\":\"${lock_key}\",\"workers\":${workers},\"attemptsPerWorker\":${attempts_per_worker},\"ttlMillis\":${ttl_millis},\"workMillis\":${work_millis}}"

echo "락 key: ${lock_key}"
echo "앱별 worker 수: ${workers}"
echo "worker별 시도 횟수: ${attempts_per_worker}"
echo "TTL 밀리초: ${ttl_millis}"
echo "작업 시간 밀리초: ${work_millis}"

for app in "${apps[@]}"; do
  name="$(basename "${app}")"
  curl -s -X POST "${app}/locks/lettuce/contend" \
    -H "Content-Type: application/json" \
    -d "${body}" > "${tmp_dir}/${name}.json" &
done

wait

for app in "${apps[@]}"; do
  name="$(basename "${app}")"
  echo
  echo "== ${app} 락 경합 결과 =="
  cat "${tmp_dir}/${name}.json"
  echo
done

echo
echo "== 최종 락 상태 =="
curl -s "${apps[0]}/locks/lettuce/state?lockKey=${lock_key}"
echo
