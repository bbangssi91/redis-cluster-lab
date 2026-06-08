#!/usr/bin/env bash
set -euo pipefail

target_container="${1:-redis-node-1}"
target_port="${2:-7001}"
base_url="${3:-http://localhost:8081}"

target_node_id="$(docker exec "${target_container}" redis-cli -p "${target_port}" cluster myid)"
observer_container=""
observer_port=""

for candidate_port in 7001 7002 7003 7004 7005 7006; do
  candidate_container="redis-node-$((candidate_port - 7000))"
  if [[ "${candidate_container}" != "${target_container}" ]] &&
    docker exec "${candidate_container}" redis-cli -p "${candidate_port}" ping >/dev/null 2>&1; then
    observer_container="${candidate_container}"
    observer_port="${candidate_port}"
    break
  fi
done

if [[ -z "${observer_container}" ]]; then
  echo "No observer Redis node is available."
  exit 1
fi

replica_node_ids="$(
  docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes |
    awk -v master_id="${target_node_id}" '$4 == master_id { print $1 }'
)"

target_line="$(
  docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes |
    awk -v target_id="${target_node_id}" '$1 == target_id { print; exit }'
)"

if ! awk '$3 ~ /master/ && NF >= 9 { found = 1 } END { exit found ? 0 : 1 }' <<< "${target_line}"; then
  echo "${target_container} is not an active master with slot ownership."
  echo "${target_line}"
  exit 1
fi

if [[ -z "${replica_node_ids}" ]]; then
  echo "No replica is assigned to target master ${target_node_id}."
  exit 1
fi

echo "== BEFORE FAILOVER =="
docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes

echo
echo "Stopping ${target_container} on port ${target_port}"
docker stop "${target_container}" >/dev/null

echo
echo "Waiting for replica promotion..."
promoted_line=""
for _ in {1..90}; do
  cluster_nodes="$(docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes)"
  promoted_line="$(
    awk -v replicas="${replica_node_ids}" '
      BEGIN {
        split(replicas, ids, "\n")
        for (i in ids) {
          if (ids[i] != "") {
            replica_ids[ids[i]] = 1
          }
        }
      }
      $1 in replica_ids && $3 ~ /master/ && NF >= 9 { print; exit }
    ' <<< "${cluster_nodes}"
  )"
  if [[ -n "${promoted_line}" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "${promoted_line}" ]]; then
  echo "Replica promotion was not observed within timeout."
  docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes
  echo
  echo "Restarting ${target_container}"
  docker compose up -d "${target_container}" >/dev/null
  exit 1
fi

echo
echo "== AFTER FAILOVER =="
docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster info
echo
docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes
echo
echo "Promoted replica:"
echo "${promoted_line}"

echo
echo "== APP WRITE/READ AFTER FAILOVER =="
curl -s -X POST "${base_url}/cluster/values" \
  -H "Content-Type: application/json" \
  -d '{"key":"phase3:failover:probe","value":"available-after-failover"}' \
  -i
echo
curl -s "${base_url}/cluster/values?key=phase3:failover:probe"
echo

echo
echo "Restarting ${target_container}"
docker compose up -d "${target_container}" >/dev/null

echo
echo "Waiting for ${target_container} to rejoin as replica..."
rejoined_line=""
for _ in {1..60}; do
  if docker exec "${target_container}" redis-cli -p "${target_port}" ping >/dev/null 2>&1; then
    cluster_nodes="$(docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes)"
    rejoined_line="$(
      awk -v target_id="${target_node_id}" '
        $1 == target_id && $3 ~ /slave/ && $3 !~ /fail/ { print; exit }
      ' <<< "${cluster_nodes}"
    )"
  fi
  if [[ -n "${rejoined_line}" ]]; then
    break
  fi
  sleep 1
done

echo
echo "== AFTER RECOVERY =="
docker exec "${observer_container}" redis-cli -p "${observer_port}" cluster nodes
if [[ -n "${rejoined_line}" ]]; then
  echo
  echo "Recovered former master:"
  echo "${rejoined_line}"
fi
