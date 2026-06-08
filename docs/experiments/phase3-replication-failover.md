# Phase 3 - Replication and Failover

## Goal

Redis Cluster의 master/replica 복제 관계를 확인하고, master process failure가 발생했을 때 replica promotion과 애플리케이션의 연결 회복을 관찰한다.

Phase 3에서는 distributed lock, network partition, hot key 실험을 구현하지 않는다. 장애 주입 범위는 단일 Redis process failure로 제한한다.

## Environment

* OS: macOS + Docker Desktop
* Redis: 7.2 Docker image
* Application: Java 17, Spring Boot 3.x
* Client: Lettuce
* Cluster topology: 3 masters + 3 replicas
* Observability: Redis Exporter, Prometheus, Grafana

## Scenario

1. Redis 노드 6개를 실행하고 클러스터를 구성한다.
2. Spring Boot 앱 3개 인스턴스를 실행한다.
3. `/cluster/topology`로 master, replica, slot range, 연결 상태를 조회한다.
4. `/cluster/replication/probe`로 master에 값을 쓰고 Redis `WAIT` ack 수를 확인한다.
5. 같은 key를 replica에 `READONLY` direct read로 조회해 복제 결과를 확인한다.
6. master 컨테이너 하나를 `docker stop`으로 중지한다.
7. `cluster_state:ok`, replica promotion, slot owner 변경을 확인한다.
8. failover 이후 앱 write/read가 성공하는지 확인한다.
9. 중지한 노드를 다시 시작하고 클러스터에 재합류하는지 확인한다.

## Commands

```bash
./scripts/create-cluster.sh
./scripts/start-apps.sh
docker compose --profile observability up -d
./scripts/verify-replication.sh http://localhost:8081
./scripts/demo-failover.sh redis-node-1 7001 http://localhost:8081
```

## API

Topology:

```bash
curl http://localhost:8081/cluster/topology
```

Replication probe:

```bash
curl -X POST http://localhost:8081/cluster/replication/probe \
  -H "Content-Type: application/json" \
  -d '{"key":"phase3:replication:probe","value":"hello","replicas":1,"timeoutMillis":1000}'
```

## Metrics

Redis metrics:

* `up{job="redis"}`
* `redis_up`
* `redis_connected_slaves`
* replication offset 계열 metric
* `redis_commands_processed_total`
* `redis_cluster_enabled`

Application metrics:

* `up{job="redis-cluster-lab-app"}`
* `http_server_requests_seconds_count`
* `http_server_requests_seconds_sum`

Manual command evidence:

* `cluster nodes`
* `cluster info`
* `WAIT` ack count
* failover 전후 slot owner
* failover 이후 app write/read 응답

## Expected Result

* `/cluster/topology`에서 master 3개와 replica 3개가 확인된다.
* replication probe의 `acknowledgedReplicas`가 `1` 이상이면 write가 적어도 하나의 replica에 ack 되었음을 의미한다.
* replica direct read 결과가 cluster read 값과 같으면 해당 replica에 복제된 값을 관찰한 것이다.
* master 컨테이너 중지 후 일정 시간 동안 cluster state가 흔들릴 수 있다.
* promotion 이후 `cluster_state:ok`가 회복되고, 기존 replica 중 하나가 master로 승격된다.
* Lettuce cluster client는 topology 갱신 후 failover된 slot owner로 요청을 라우팅한다.

## Result

실행 시 다음 항목을 기록한다.

* failover 대상 master endpoint
* failover 전 master/replica mapping
* `WAIT` ack count
* replica direct read 값
* master stop 시각
* `cluster_state:ok` 회복 시각
* promotion된 node id와 endpoint
* failover 이후 앱 write/read 결과

## Analysis

Redis replication은 비동기 복제이므로 일반 write 성공만으로 replica 반영 완료를 보장하지 않는다. Phase 3의 replication probe는 Redis `WAIT` 명령을 함께 사용해 지정한 replica 수가 write를 ack 했는지 확인한다. 단, `WAIT`는 강한 일관성 트랜잭션을 제공하는 명령이 아니라 복제 확인을 위한 관찰 도구로 사용한다.

Failover 실험은 process failure에 한정한다. master 컨테이너가 중지되면 Redis Cluster는 replica를 promotion candidate로 평가하고, quorum을 통해 새로운 master를 선출한다. 이 동안 클라이언트 요청은 일시적으로 실패하거나 지연될 수 있으며, promotion 이후 cluster-aware client는 갱신된 topology에 따라 요청을 재라우팅해야 한다.

## Conclusion

Phase 3 구성은 Redis Cluster의 복제 상태와 단일 master 장애 복구를 재현하기에 충분하다. 이후 phase에서는 이 기반 위에 lock consistency, replication lag가 lock 안전성에 미치는 영향, network partition, hot key 실험을 확장한다.
