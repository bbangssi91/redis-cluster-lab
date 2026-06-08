# Phase 3 Troubleshooting

## `acknowledgedReplicas`가 0으로 나온다

`WAIT`는 지정한 timeout 안에 replica ack를 받지 못하면 0을 반환할 수 있다. 클러스터 상태와 replica 연결을 먼저 확인한다.

```bash
docker exec redis-node-1 redis-cli -p 7001 cluster nodes
docker exec redis-node-1 redis-cli -p 7001 info replication
```

timeout을 늘려 다시 실행한다.

```bash
./scripts/verify-replication.sh http://localhost:8081 phase3:replication:probe retry
```

## Failover 후 앱 요청이 잠시 실패한다

master 중지 직후에는 slot owner 변경과 Lettuce topology refresh 사이에 짧은 실패 구간이 생길 수 있다. `cluster_state:ok` 회복 후 다시 요청한다.

```bash
curl http://localhost:8081/cluster/topology
curl "http://localhost:8081/cluster/values?key=phase3:failover:probe"
```

## 중지한 노드가 기존 master로 돌아오지 않는다

Redis Cluster failover 이후 재시작된 기존 master는 보통 새 master의 replica로 재합류한다. 이는 정상 동작이다. 원래 topology로 되돌리고 싶다면 클러스터를 초기화한다.

```bash
./scripts/reset-cluster.sh
./scripts/create-cluster.sh
```
