#!/usr/bin/env bash
set -euo pipefail

base_url="${1:-http://localhost:8081}"

echo "== CONFIGURED NODES =="
curl -s "${base_url}/cluster/configured-nodes"

echo
echo
echo "== KEYSLOT =="
curl -s "${base_url}/cluster/keyslot?key=user:1"

echo
echo
echo "== WRITE =="
curl -s -X POST "${base_url}/cluster/values" \
  -H "Content-Type: application/json" \
  -d '{"key":"phase1:app-demo","value":"hello-from-spring"}' \
  -i

echo
echo "== READ =="
curl -s "${base_url}/cluster/values?key=phase1:app-demo"
echo
