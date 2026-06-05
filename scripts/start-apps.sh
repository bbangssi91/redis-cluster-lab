#!/usr/bin/env bash
set -euo pipefail

GRADLE_USER_HOME="${PWD}/.gradle" ./gradlew bootJar
docker compose --profile app up -d --build app1 app2 app3

echo "App instances are available:"
echo "app1: http://localhost:8081"
echo "app2: http://localhost:8082"
echo "app3: http://localhost:8083"
