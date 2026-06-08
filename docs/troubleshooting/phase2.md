# Phase 2 Troubleshooting Notes

## Prometheus app target이 DOWN인 경우

앱 컨테이너가 `app` profile로 실행 중인지 확인한다.

```bash
docker compose --profile app ps
curl http://localhost:8081/actuator/health
```

Prometheus는 Docker network 내부 주소인 `app1:8080`, `app2:8080`, `app3:8080`을 scrape한다. 로컬 브라우저에서 접근하는 포트는 각각 `8081`, `8082`, `8083`이다.

## Redis Exporter target이 DOWN인 경우

Redis Cluster가 먼저 실행되어 있어야 한다.

```bash
docker compose ps
./scripts/verify-cluster.sh
curl http://localhost:9121/metrics
```

Redis Exporter는 Docker network 내부 Redis 주소 `172.28.0.11:7001`부터 `172.28.0.16:7006`까지 각각 연결한다.

## Grafana dashboard가 보이지 않는 경우

Grafana provisioning mount가 정상인지 확인한다.

```bash
docker compose --profile observability logs grafana
```

브라우저에서 `http://localhost:3000`에 접속한 뒤 `admin` / `admin`으로 로그인한다. Dashboard folder는 `Redis Cluster Lab`이다.

## Prometheus query 결과가 비어 있는 경우

scrape 직후에는 데이터가 아직 쌓이지 않았을 수 있다. 5초 이상 기다린 뒤 다시 조회한다.

```bash
curl --get http://localhost:9090/api/v1/query \
  --data-urlencode 'query=up{job="redis"}'
```

## Replication Lag를 바로 하나의 값으로 볼 수 없는 경우

Phase 2에서는 replication offset raw metric을 수집하는 것까지를 범위로 둔다. 실제 lag 계산식과 failover window 분석은 Phase 5에서 실험 시나리오와 함께 확정한다.
