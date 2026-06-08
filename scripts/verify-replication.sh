#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"
key="${2:-phase3:replication:probe}"
value="${3:-$(date +%s)}"

echo "== CLUSTER TOPOLOGY =="
curl -s "${base_url}/cluster/topology"

echo
echo
echo "== REPLICATION PROBE =="
curl -s -X POST "${base_url}/cluster/replication/probe" \
  -H "Content-Type: application/json" \
  -d "{\"key\":\"${key}\",\"value\":\"${value}\",\"replicas\":1,\"timeoutMillis\":1000}"
echo
