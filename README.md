# Redis Cluster Lab

Redis Cluster 환경에서 발생하는 분산 시스템 이슈를 직접 재현하고 검증하기 위한 개인 학습 랩이다.

이 프로젝트는 Redis 사용법보다 Redis Cluster의 동작 원리, slot 분배, redirect, replication, failover, lock consistency, hot key 문제를 실험으로 설명 가능한 수준까지 이해하는 것을 목표로 한다.

## 현재 단계

현재 브랜치: `phase4-lock-with-lettuce`

Phase 4 목표:

* Lettuce 직접 사용 기반 `SET NX PX` 락 획득
* owner token 기반 safe release Lua script
* 락 상태, 락 경합, TTL 만료 실험 API
* 여러 앱 인스턴스 간 락 경합 관찰
* 락 획득/해제 Micrometer metric 확인

## 요구 사항

* macOS
* Docker Desktop
* Java 17 이상
* Gradle Wrapper 사용

## 빠른 시작

```bash
./scripts/create-cluster.sh
./scripts/verify-cluster.sh
./scripts/demo-moved.sh
./scripts/start-apps.sh
./scripts/verify-app.sh http://localhost:8081
docker compose --profile observability up -d
./scripts/verify-observability.sh
./scripts/verify-replication.sh http://localhost:8081
./scripts/demo-failover.sh redis-node-1 7001 http://localhost:8081
./scripts/verify-lettuce-lock.sh http://localhost:8081
./scripts/demo-lock-contention.sh
```

앱 인스턴스:

* App1: http://localhost:8081
* App2: http://localhost:8082
* App3: http://localhost:8083

관측 도구:

* Prometheus: http://localhost:9090
* Grafana: http://localhost:3000
  * 사용자: `admin`
  * 비밀번호: `admin`
* Redis Exporters:
  * redis-node-1: http://localhost:9121/metrics
  * redis-node-2: http://localhost:9122/metrics
  * redis-node-3: http://localhost:9123/metrics
  * redis-node-4: http://localhost:9124/metrics
  * redis-node-5: http://localhost:9125/metrics
  * redis-node-6: http://localhost:9126/metrics

## Spring Boot API

```bash
curl http://localhost:8081/cluster/configured-nodes
curl "http://localhost:8081/cluster/keyslot?key=user:1"
curl http://localhost:8081/cluster/nodes
curl http://localhost:8081/cluster/slots
curl http://localhost:8081/cluster/topology
curl -X POST http://localhost:8081/cluster/values \
  -H "Content-Type: application/json" \
  -d '{"key":"phase1:app-demo","value":"hello"}'
curl "http://localhost:8081/cluster/values?key=phase1:app-demo"
curl -X POST http://localhost:8081/cluster/replication/probe \
  -H "Content-Type: application/json" \
  -d '{"key":"phase3:replication:probe","value":"hello","replicas":1,"timeoutMillis":1000}'
curl -X POST http://localhost:8081/locks/lettuce/acquire \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:demo","ttlMillis":5000,"owner":"manual"}'
curl "http://localhost:8081/locks/lettuce/state?lockKey=phase4:lock:demo"
curl -X POST http://localhost:8081/locks/lettuce/contend \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:contention","workers":6,"attemptsPerWorker":3,"ttlMillis":1000,"workMillis":250}'
curl -X POST http://localhost:8081/locks/lettuce/ttl-expiration \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:ttl","ttlMillis":500,"waitMillis":700,"owner":"manual"}'
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus
```

## 초기화

Redis Cluster 구성 정보는 Docker volume에 남는다. 클러스터를 처음부터 다시 만들려면 다음 명령을 실행한다.

```bash
./scripts/reset-cluster.sh
./scripts/create-cluster.sh
```

## 문서

* [아키텍처 다이어그램](docs/architecture.md)
* [Phase 1 실험 보고서](docs/experiments/phase1-cluster.md)
* [Phase 2 실험 보고서](docs/experiments/phase2-observability.md)
* [Phase 3 실험 보고서](docs/experiments/phase3-replication-failover.md)
* [Phase 4 실험 보고서](docs/experiments/phase4-lock-with-lettuce.md)
* [Phase 1 문제 해결 기록](docs/troubleshooting/phase1.md)
* [Phase 2 문제 해결 기록](docs/troubleshooting/phase2.md)
* [Phase 3 문제 해결 기록](docs/troubleshooting/phase3.md)
