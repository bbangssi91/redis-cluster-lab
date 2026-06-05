#!/usr/bin/env bash
set -euo pipefail

docker compose up -d redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5 redis-node-6

echo "Waiting for Redis nodes..."
for port in 7001 7002 7003 7004 7005 7006; do
  until docker exec "redis-node-$((port - 7000))" redis-cli -p "${port}" ping >/dev/null 2>&1; do
    sleep 1
  done
done

echo "Creating Redis Cluster: 3 masters + 3 replicas"
docker exec redis-node-1 redis-cli --cluster create \
  172.28.0.11:7001 \
  172.28.0.12:7002 \
  172.28.0.13:7003 \
  172.28.0.14:7004 \
  172.28.0.15:7005 \
  172.28.0.16:7006 \
  --cluster-replicas 1 \
  --cluster-yes

echo "Cluster created."
