#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
suffix="${2:-$(date +%s)}"

echo "suffix: ${suffix}"
echo
echo "== hash tag로 같은 slot에 묶은 여러 lock 동시 획득 =="
curl -s -X POST "${base_url}/locks/redisson/multi-lock" \
  -H "Content-Type: application/json" \
  -d "{\"lockKeys\":[\"phase5:{multi:${suffix}}:a\",\"phase5:{multi:${suffix}}:b\"],\"owner\":\"multi-demo\",\"waitMillis\":500,\"leaseMillis\":5000,\"workMillis\":200}"
echo

echo
echo "== 서로 다른 hash slot에 놓인 여러 lock 동시 획득 =="
curl -s -X POST "${base_url}/locks/redisson/multi-lock" \
  -H "Content-Type: application/json" \
  -d "{\"lockKeys\":[\"phase5:multi:${suffix}:a\",\"phase5:multi:${suffix}:b\"],\"owner\":\"multi-demo-cross-slot\",\"waitMillis\":500,\"leaseMillis\":5000,\"workMillis\":200}"
echo
