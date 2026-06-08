#!/usr/bin/env bash
set -euo pipefail

prometheus_url="${1:-http://localhost:9090}"
grafana_url="${2:-http://localhost:3000}"
app_url="${3:-http://localhost:8081}"

echo "== APP ACTUATOR HEALTH =="
curl -fsS "${app_url}/actuator/health"

echo
echo
echo "== APP PROMETHEUS METRICS SAMPLE =="
curl -fsS "${app_url}/actuator/prometheus" \
  | awk '/^(http_server_requests_seconds_count|jvm_memory_used_bytes|process_uptime_seconds)/ { print; count++; if (count == 10) exit }'

echo
echo
echo "== PROMETHEUS TARGETS =="
curl -fsS "${prometheus_url}/api/v1/targets" \
  | awk '/"job":"(redis|redis-cluster-lab-app|prometheus)"/ { print; count++; if (count == 20) exit }'

echo
echo
echo "== PROMETHEUS REDIS QUERY =="
curl -fsS --get "${prometheus_url}/api/v1/query" \
  --data-urlencode 'query=up{job="redis"}'

echo
echo
echo "== PROMETHEUS APP QUERY =="
curl -fsS --get "${prometheus_url}/api/v1/query" \
  --data-urlencode 'query=up{job="redis-cluster-lab-app"}'

echo
echo
echo "== GRAFANA HEALTH =="
curl -fsS "${grafana_url}/api/health"

echo
