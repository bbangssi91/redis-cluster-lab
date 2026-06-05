# Phase 1 - Redis Cluster

## Goal

Redis Cluster를 직접 구성하고, 3 Master + 3 Replica topology, hash slot 분배, keyslot 계산, MOVED redirect를 검증한다.

## Environment

* OS: macOS + Docker Desktop
* Redis: 7.2 Docker image
* Application: Java 17, Spring Boot 3.x
* Client: Lettuce
* Cluster topology: 3 masters + 3 replicas

## Scenario

1. Redis 노드 6개를 개별 컨테이너로 실행한다.
2. `redis-cli --cluster create`로 클러스터를 직접 생성한다.
3. `cluster nodes`로 master/replica 구성을 확인한다.
4. `cluster slots`로 0~16383 slot 분배를 확인한다.
5. `cluster keyslot`으로 key가 어느 slot에 배치되는지 확인한다.
6. `redis-cli`의 cluster mode 여부에 따라 MOVED 응답을 관찰한다.
7. Spring Boot 앱 3개를 같은 Docker 네트워크에 실행하고 클러스터 API를 호출한다.

## Commands

```bash
./scripts/create-cluster.sh
./scripts/verify-cluster.sh
./scripts/demo-moved.sh
./scripts/start-apps.sh
./scripts/verify-app.sh http://localhost:8081
```

## Metrics

Phase 1에서는 Prometheus/Grafana를 아직 구성하지 않는다. 대신 다음 명령 결과를 실험 증거로 남긴다.

* `cluster info`
* `cluster nodes`
* `cluster slots`
* `cluster keyslot <key>`
* MOVED 응답
* Spring Boot API 응답

## Expected Result

* `cluster_state:ok`가 출력된다.
* master 3개와 replica 3개가 확인된다.
* slot 범위 `0-16383`이 3개 master에 분배된다.
* hash tag가 같은 key는 동일 slot으로 계산된다.
* cluster-aware client는 MOVED를 따라가고, cluster mode가 아닌 직접 요청은 MOVED를 반환할 수 있다.

## Result

2026-06-05 최초 실행 결과:

* `cluster_state:ok`
* `cluster_slots_assigned:16384`
* `cluster_slots_ok:16384`
* `cluster_known_nodes:6`
* `cluster_size:3`
* Slot allocation
  * Master 7001: `0-5460`
  * Master 7002: `5461-10922`
  * Master 7003: `10923-16383`
* Replica allocation
  * Replica 7005 -> Master 7001
  * Replica 7006 -> Master 7002
  * Replica 7004 -> Master 7003
* Keyslot samples
  * `user:1` -> `10778`
  * `user:2` -> `6777`
  * `order:1` -> `14374`
  * `{account:1}:profile` -> `10997`
  * `{account:1}:session` -> `10997`

MOVED 검증:

```text
phase1:moved-demo -> slot 7973
node 7001 -> MOVED 7973 172.28.0.12:7002
node 7002 -> hello-cluster
node 7003 -> MOVED 7973 172.28.0.12:7002
node 7004 -> MOVED 7973 172.28.0.12:7002
node 7005 -> MOVED 7973 172.28.0.12:7002
node 7006 -> MOVED 7973 172.28.0.12:7002
```

Spring Boot 앱 검증:

* app1: `http://localhost:8081`
* app2: `http://localhost:8082`
* app3: `http://localhost:8083`
* 세 인스턴스 모두 `user:1 -> slot 10778` 응답 확인
* 세 인스턴스 모두 `phase1:app-demo -> hello-from-spring` read/write 확인

## Analysis

Redis Cluster는 전체 key space를 16384개의 hash slot으로 나눈다. 클라이언트가 잘못된 노드에 key 명령을 보내면 해당 slot owner 정보를 포함한 MOVED 응답을 받는다. Lettuce cluster client는 이 redirect를 처리하고 topology를 갱신할 수 있다.

## Conclusion

Phase 1 구성은 Redis Cluster의 기본 동작을 직접 관찰하기에 충분하다. 6개 노드를 개별 컨테이너로 띄우고 `redis-cli --cluster create`로 구성했으며, 전체 16384 slot이 3개 master에 분배되는 것을 확인했다. cluster-aware client는 올바른 노드로 요청을 라우팅하고, cluster mode가 아닌 직접 요청은 slot owner가 아닌 노드에서 MOVED를 반환한다.
