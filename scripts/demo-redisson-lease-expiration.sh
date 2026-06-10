#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
lock_key="${2:-phase5:redisson:lease:$(date +%s)}"

echo "락 key: ${lock_key}"
echo
echo "== leaseTime보다 긴 작업 후 만료와 competitor 획득 가능 여부 관찰 =="
curl -s -X POST "${base_url}/locks/redisson/lease-expiration" \
  -H "Content-Type: application/json" \
  -d "{\"lockKey\":\"${lock_key}\",\"owner\":\"lease-demo\",\"leaseMillis\":1000,\"workMillis\":1500}"
echo
