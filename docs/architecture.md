# Architecture Diagram

Phase 5 기준 아키텍처이다.

```mermaid
flowchart LR
    subgraph Docker["Docker Desktop - redis-cluster-net"]
        subgraph Apps["Spring Boot App Instances"]
            App1["App1 :8081 -> :8080"]
            App2["App2 :8082 -> :8080"]
            App3["App3 :8083 -> :8080"]
        end

        subgraph Redis["Redis Cluster"]
            M1["Master 7001"]
            M2["Master 7002"]
            M3["Master 7003"]
            R4["Replica Node 7004"]
            R5["Replica Node 7005"]
            R6["Replica Node 7006"]
        end

        subgraph Observability["Observability"]
            E1["Redis Exporter 7001 :9121"]
            E2["Redis Exporter 7002 :9121"]
            E3["Redis Exporter 7003 :9121"]
            E4["Redis Exporter 7004 :9121"]
            E5["Redis Exporter 7005 :9121"]
            E6["Redis Exporter 7006 :9121"]
            Prom["Prometheus :9090"]
            Grafana["Grafana :3000"]
        end

        App1 --> M1
        App1 --> M2
        App1 --> M3
        App1 -. Redisson RLock .-> Redis
        App2 --> M1
        App2 --> M2
        App2 --> M3
        App2 -. Redisson RLock .-> Redis
        App3 --> M1
        App3 --> M2
        App3 --> M3
        App3 -. Redisson RLock .-> Redis

        M1 -. replica assignment .-> R5
        M2 -. replica assignment .-> R6
        M3 -. replica assignment .-> R4

        R5 -. promotion candidate .-> M1
        R6 -. promotion candidate .-> M2
        R4 -. promotion candidate .-> M3

        E1 --> M1
        E2 --> M2
        E3 --> M3
        E4 --> R4
        E5 --> R5
        E6 --> R6

        Prom --> E1
        Prom --> E2
        Prom --> E3
        Prom --> E4
        Prom --> E5
        Prom --> E6
        Prom --> App1
        Prom --> App2
        Prom --> App3

        Grafana --> Prom
    end

    User["Local macOS"] --> App1
    User --> App2
    User --> App3
    User --> Redis
    User --> Prom
    User --> Grafana
```

Redis 노드는 `redis-cli --cluster create`로 직접 구성한다. 자동 클러스터 구성 이미지는 사용하지 않는다.

Prometheus는 Redis Exporter 6개와 Spring Boot Actuator endpoint를 scrape한다. Grafana는 provisioning 설정으로 Prometheus datasource와 `Redis Cluster Lab Observability` dashboard를 자동 등록한다.

Phase 3에서는 앱의 `/cluster/topology` API로 master/replica 관계와 slot owner를 조회하고, `/cluster/replication/probe` API로 master write 이후 `WAIT` ack와 replica direct read 결과를 확인한다. Process failure는 `scripts/demo-failover.sh`에서 특정 Redis master 컨테이너를 `docker stop`으로 중지해 주입한다.

Phase 4에서는 Lettuce로 `SET NX PX`와 token-safe Lua release 기반 락을 직접 구현한다. Phase 5에서는 같은 Redis Cluster 위에 Redisson client를 추가해 `RLock`, watchdog, lease time, multi lock을 Lettuce baseline과 비교한다.

Replica assignment는 Phase 1 최초 실행 결과 기준이다. 클러스터를 초기화한 뒤 다시 생성하면 `cluster nodes` 결과를 기준으로 확인한다.

## Java Package Layout

```text
com.example.redisclusterlab.cluster
├── api
│   └── REST endpoint
├── application
│   └── API use-case facade
├── common
│   └── Redis Cluster client lifecycle
├── dto
│   └── Request/response records
├── replication
│   └── Replication experiment service and result records
└── topology
    └── Cluster node/slot model and parser

com.example.redisclusterlab.lock
├── api
│   └── Lettuce lock REST endpoint
├── application
│   └── Lettuce lock use-cases and experiment executor
├── dto
│   └── Lettuce lock request/response records
├── lettuce
│   └── SET NX PX and Lua release client
├── metrics
│   └── Lettuce lock Micrometer metrics
└── redisson
    ├── api
    │   └── Redisson lock REST endpoint
    ├── application
    │   └── RLock/watchdog/lease/multi-lock experiments
    ├── config
    │   └── Redisson Cluster client bean
    ├── dto
    │   └── Redisson request/response records
    └── metrics
        └── Redisson lock Micrometer metrics
```
