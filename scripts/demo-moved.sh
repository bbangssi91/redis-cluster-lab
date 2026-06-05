#!/usr/bin/env bash
set -euo pipefail

key="${1:-phase1:moved-demo}"
value="${2:-hello-cluster}"

echo "Writing through cluster-aware redis-cli: ${key}=${value}"
docker exec redis-node-1 redis-cli -c -p 7001 set "${key}" "${value}"

slot=$(docker exec redis-node-1 redis-cli -p 7001 cluster keyslot "${key}")
echo "Key slot: ${slot}"

echo
echo "Reading without -c from every node. A wrong owner should return MOVED."
for port in 7001 7002 7003 7004 7005 7006; do
  printf "node %s -> " "${port}"
  docker exec "redis-node-$((port - 7000))" redis-cli -p "${port}" get "${key}" || true
done
