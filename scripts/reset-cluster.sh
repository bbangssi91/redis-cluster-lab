#!/usr/bin/env bash
set -euo pipefail

docker compose --profile app down -v
echo "Redis Cluster containers and volumes removed."
