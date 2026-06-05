# Redis Cluster Lab

Redis Cluster 환경에서 발생하는 분산 시스템 이슈를 직접 재현하고 검증하기 위한 개인 학습 랩이다.

이 프로젝트는 Redis 사용법보다 Redis Cluster의 동작 원리, slot 분배, redirect, replication, failover, lock consistency, hot key 문제를 실험으로 설명 가능한 수준까지 이해하는 것을 목표로 한다.

## Current Phase

현재 브랜치: `phase1-cluster`

Phase 1 목표:

* Redis 7.x Cluster 직접 구축
* 3 Master + 3 Replica 구성 검증
* Hash Slot 분배 확인
* `MOVED` redirect 관찰
* Spring Boot 앱 3개 인스턴스에서 cluster client 연결 확인

## Requirements

* macOS
* Docker Desktop
* Java 17 이상
* Gradle Wrapper 사용

## Quick Start

```bash
./scripts/create-cluster.sh
./scripts/verify-cluster.sh
./scripts/demo-moved.sh
./scripts/start-apps.sh
./scripts/verify-app.sh http://localhost:8081
```

앱 인스턴스:

* App1: http://localhost:8081
* App2: http://localhost:8082
* App3: http://localhost:8083

## Spring Boot API

```bash
curl http://localhost:8081/cluster/configured-nodes
curl "http://localhost:8081/cluster/keyslot?key=user:1"
curl http://localhost:8081/cluster/nodes
curl http://localhost:8081/cluster/slots
curl -X POST http://localhost:8081/cluster/values \
  -H "Content-Type: application/json" \
  -d '{"key":"phase1:app-demo","value":"hello"}'
curl "http://localhost:8081/cluster/values?key=phase1:app-demo"
```

## Reset

Redis Cluster 구성 정보는 Docker volume에 남는다. 클러스터를 처음부터 다시 만들려면 다음 명령을 실행한다.

```bash
./scripts/reset-cluster.sh
./scripts/create-cluster.sh
```

## Documents

* [Architecture Diagram](docs/architecture.md)
* [Phase 1 Experiment Report](docs/experiments/phase1-cluster.md)
* [Phase 1 Troubleshooting Notes](docs/troubleshooting/phase1.md)
