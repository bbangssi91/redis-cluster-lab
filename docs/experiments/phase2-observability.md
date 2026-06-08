# Phase 2 - Observability

## Goal

Redis Cluster와 Spring Boot 애플리케이션의 상태를 Prometheus/Grafana로 관측할 수 있는 실험 기반을 만든다.

Phase 2에서는 lock, failover, hot key 실험을 구현하지 않는다. 이후 phase에서 사용할 공통 측정 기반만 구성한다.

## Environment

* OS: macOS + Docker Desktop
* Redis: 7.2 Docker image
* Application: Java 17, Spring Boot 3.x
* Client: Lettuce
* Cluster topology: 3 masters + 3 replicas
* Metrics:
  * Spring Actuator
  * Micrometer Prometheus registry
  * Redis Exporter
  * Prometheus
  * Grafana

## Scenario

1. Redis 노드 6개를 실행하고 클러스터를 구성한다.
2. Spring Boot 앱 3개 인스턴스를 실행한다.
3. Redis Exporter를 Redis 노드별로 6개 실행한다.
4. Prometheus가 Redis Exporter와 앱 인스턴스의 metrics endpoint를 scrape한다.
5. Grafana가 Prometheus datasource와 dashboard를 provisioning으로 등록한다.
6. Prometheus query와 Grafana dashboard로 Redis/Application metric 수집 여부를 확인한다.

## Commands

```bash
./scripts/create-cluster.sh
./scripts/start-apps.sh
docker compose --profile observability up -d
./scripts/verify-observability.sh
```

## Metrics

Redis metrics:

* `up{job="redis"}`
* `redis_memory_used_bytes`
* `redis_connected_clients`
* `redis_commands_processed_total`
* `redis_cluster_enabled`
* `replication offset 관련 metric`

Application metrics:

* `up{job="redis-cluster-lab-app"}`
* `http_server_requests_seconds_count`
* `http_server_requests_seconds_sum`
* `jvm_memory_used_bytes`
* `process_uptime_seconds`

## Expected Result

* Prometheus target에서 Redis Exporter 6개가 `UP` 상태로 표시된다.
* Prometheus target에서 앱 인스턴스 3개가 `UP` 상태로 표시된다.
* Grafana에 `Redis Cluster Lab Observability` dashboard가 자동 등록된다.
* Redis node별 memory, command rate, cluster enabled 상태를 확인할 수 있다.
* App instance별 HTTP request count, latency, status code별 rate를 확인할 수 있다.

## Result

초기 구성 결과는 다음 명령으로 검증한다.

```bash
./scripts/verify-observability.sh
```

검증 시 확인할 항목:

* `http://localhost:8081/actuator/health` 응답
* `http://localhost:8081/actuator/prometheus` metric 노출
* `http://localhost:9090/api/v1/targets` target 상태
* `up{job="redis"}` query 결과
* `up{job="redis-cluster-lab-app"}` query 결과
* `http://localhost:3000/api/health` 응답

## Analysis

Redis Cluster의 노드 상태는 각 Redis 노드에 연결된 Redis Exporter가 수집한다. Exporter를 노드별로 분리했기 때문에 이후 failover 실험에서 특정 master/replica의 상태 변화를 독립적으로 추적할 수 있다.

Spring Boot 애플리케이션은 Actuator와 Micrometer Prometheus registry를 통해 HTTP 요청 수, latency, JVM, process metric을 노출한다. Docker Compose에서 `APP_INSTANCE_NAME`을 인스턴스별로 주입해 Prometheus/Grafana에서 app1, app2, app3를 구분한다.

Replication lag는 Redis Exporter가 제공하는 master/replica replication offset 계열 metric의 차이를 기반으로 계산할 수 있다. 정확한 metric 이름은 사용 중인 Redis Exporter 버전의 `/metrics` 출력에서 확인한다. Failover time은 Phase 5에서 failover 시작 시각, cluster state 변화, target down/up, request failure window를 함께 관측해 측정한다.

## Conclusion

Phase 2 구성은 Redis Cluster와 Spring Boot 앱의 기본 상태를 Prometheus/Grafana에서 관측하기에 충분하다. 이후 Phase 3 이후 custom lock metric, Phase 5 failover metric, Phase 6 chaos/hot key metric은 이 기반 위에 추가한다.
