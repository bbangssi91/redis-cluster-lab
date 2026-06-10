#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
lock_key="${2:-phase5:redisson:watchdog:$(date +%s)}"

echo "락 key: ${lock_key}"
echo
echo "== leaseTime 없이 RLock을 잡고 watchdog TTL sample 관찰 =="
curl -s -X POST "${base_url}/locks/redisson/watchdog" \
  -H "Content-Type: application/json" \
  -d "{\"lockKey\":\"${lock_key}\",\"owner\":\"watchdog-demo\",\"workMillis\":35000,\"sampleIntervalMillis\":5000}"
echo
