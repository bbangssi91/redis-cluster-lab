#!/usr/bin/env bash
set -euo pipefail

echo "== CLUSTER INFO =="
docker exec redis-node-1 redis-cli -p 7001 cluster info

echo
echo "== CLUSTER NODES =="
docker exec redis-node-1 redis-cli -p 7001 cluster nodes

echo
echo "== CLUSTER SLOTS =="
docker exec redis-node-1 redis-cli -p 7001 cluster slots

echo
echo "== KEYSLOT SAMPLES =="
for key in user:1 user:2 order:1 "{account:1}:profile" "{account:1}:session"; do
  slot=$(docker exec redis-node-1 redis-cli -p 7001 cluster keyslot "${key}")
  echo "${key} -> slot ${slot}"
done
