# Redis Cluster Study Review

이 문서는 Phase 1부터 Phase 5까지 진행한 Redis Cluster Lab 내용을 다음 복습 때 빠르게 되살리기 위한 최종 정리 노트이다.

개별 phase 문서는 실험 절차와 명령을 자세히 남기는 용도이고, 이 문서는 개념을 다시 연결하는 용도이다. 따라서 명령어 전체보다 "무엇을 확인했고, 왜 중요한가"에 집중한다.

## 1. 학습 랩 전체 구조

관련 Phase: Phase 1, Phase 2, Phase 3, Phase 4, Phase 5

이 프로젝트는 Redis Cluster 환경에서 클러스터 라우팅, 복제, failover, 분산 락, 관측을 직접 실험하기 위한 학습 랩이다.

구성은 다음과 같다.

```text
Local macOS
├── Spring Boot App 3개
│   ├── app1: localhost:8081
│   ├── app2: localhost:8082
│   └── app3: localhost:8083
├── Redis Cluster
│   ├── master 3개
│   └── replica 3개
├── Observability
│   ├── Redis Exporter 6개
│   ├── Prometheus
│   └── Grafana
└── Client
    ├── Lettuce
    └── Redisson
```

Phase별 핵심 질문은 다음과 같다.

| Phase | 주제 | 핵심 질문 |
| --- | --- | --- |
| Phase 1 | Redis Cluster 기본 동작 | hash slot, MOVED redirect, cluster-aware client는 어떻게 동작하는가? |
| Phase 2 | Observability | Redis와 애플리케이션 상태를 어떻게 관측할 것인가? |
| Phase 3 | Replication / Failover | master 장애 시 replica promotion과 client recovery는 어떻게 보이는가? |
| Phase 4 | Lettuce Lock | `SET NX PX + Lua release`로 만든 락은 어떻게 동작하고 어디까지 안전한가? |
| Phase 5 | Redisson Lock | Redisson `RLock`, watchdog, lease time, multi lock은 Lettuce baseline과 무엇이 다른가? |

전체 흐름을 한 문장으로 요약하면 다음과 같다.

> Redis Cluster의 slot 기반 라우팅과 failover를 먼저 이해하고, 그 위에서 락을 사용할 때 발생하는 TTL, ownership, stale write, 관측 문제를 Lettuce와 Redisson으로 비교한다.

## 2. Redis Cluster 기본 동작

관련 Phase: Phase 1

Redis Cluster는 전체 key space를 `0`부터 `16383`까지 총 `16384`개의 hash slot으로 나눈다. 각 key는 CRC16 기반 계산을 통해 하나의 slot에 매핑되고, 각 slot은 특정 master node가 담당한다.

Phase 1에서 확인한 slot 분배 예시는 다음과 같다.

```text
Master 7001: 0-5460
Master 7002: 5461-10922
Master 7003: 10923-16383
```

즉, Redis Cluster에서 데이터가 분산되는 단위는 node가 아니라 hash slot이다. 클라이언트가 어떤 key를 읽거나 쓸 때는 먼저 해당 key가 어느 slot인지 계산하고, 그 slot을 담당하는 master에게 명령을 보내야 한다.

### Keyslot

예시:

```text
user:1 -> 10778
user:2 -> 6777
order:1 -> 14374
```

서로 다른 key는 보통 서로 다른 slot에 배치된다. 하지만 hash tag를 사용하면 여러 key를 같은 slot으로 강제로 묶을 수 있다.

```text
{account:1}:profile -> 10997
{account:1}:session -> 10997
```

중괄호 안의 값만 slot 계산에 사용되기 때문이다. Redis Cluster에서 multi-key command, Lua script, multi lock을 다룰 때 이 개념이 중요해진다.

### MOVED Redirect

클라이언트가 slot owner가 아닌 node에 key 명령을 보내면 Redis는 `MOVED` 응답을 반환할 수 있다.

예시:

```text
MOVED 7973 172.28.0.12:7002
```

이 의미는 다음과 같다.

> 이 key가 속한 slot `7973`은 지금 이 node가 아니라 `172.28.0.12:7002`가 담당한다.

cluster-aware client인 Lettuce는 이 응답을 보고 올바른 node로 요청을 다시 보내거나 topology 정보를 갱신한다. 반대로 cluster mode가 아닌 단순 client나 `redis-cli` 직접 요청은 MOVED 응답을 그대로 보게 된다.

### 복습 포인트

Redis Cluster를 이해할 때 가장 먼저 떠올릴 것은 node가 아니라 slot이다.

```text
key -> hash slot -> slot owner master -> command routing
```

이 흐름이 이해되면 MOVED, hash tag, multi-key 제약, failover 후 topology refresh까지 자연스럽게 이어진다.

## 3. Sentinel vs Cluster

관련 Phase: 개념 비교

이 프로젝트에서는 Sentinel 환경을 직접 구현하지 않았지만, Cluster를 복습할 때 Sentinel과의 차이를 함께 정리해두는 것이 중요하다.

| 구분 | Sentinel | Cluster |
| --- | --- | --- |
| 주 목적 | 고가용성 | 샤딩 + 고가용성 |
| 데이터 분산 | 없음 | 있음 |
| key slot | 없음 | 있음, 총 16384개 |
| write master | 보통 하나 | 여러 master |
| failover | Sentinel이 master 장애 감지 후 replica 승격 | Cluster node들이 replica promotion 수행 |
| 클라이언트 역할 | 현재 master 주소를 찾아야 함 | slot owner로 라우팅해야 함 |
| multi-key 제약 | Cluster보다 단순 | 같은 slot key만 안전하게 처리 가능 |

Sentinel은 기본적으로 단일 master 구조의 고가용성을 관리한다. master가 죽으면 Sentinel이 장애를 감지하고 replica를 master로 승격한다. 하지만 key를 여러 master에 나누어 저장하지는 않는다.

Cluster는 데이터를 여러 master에 나누어 저장한다. 이때 분산 단위가 hash slot이다. 따라서 Cluster에서는 고가용성뿐 아니라 라우팅, slot 이동, multi-key 제약까지 함께 고려해야 한다.

정리하면 다음과 같다.

```text
Sentinel = master 장애 대응 중심
Cluster  = 데이터 샤딩 + master 장애 대응
```

실무 판단에서는 다음 질문을 해볼 수 있다.

| 질문 | Sentinel 쪽 | Cluster 쪽 |
| --- | --- | --- |
| 데이터가 한 master에 들어가도 충분한가? | 적합할 수 있음 | 과할 수 있음 |
| write/read 부하를 여러 master로 분산해야 하는가? | 부적합 | 적합 |
| multi-key transaction/script를 많이 쓰는가? | 상대적으로 단순 | hash tag 설계 필요 |
| 운영 복잡도를 낮추고 싶은가? | 상대적으로 단순 | 더 복잡 |

## 4. 클러스터 환경에서 Hash Slot 기반 동작 방식

관련 Phase: Phase 1, Phase 3, Phase 5

Redis Cluster의 모든 핵심 동작은 hash slot을 중심으로 돌아간다.

### 기본 라우팅

쓰기 예시:

```text
SET user:1 moon
```

동작 흐름:

```text
1. client가 user:1의 slot을 계산한다.
2. slot owner master를 찾는다.
3. 해당 master로 SET 명령을 보낸다.
4. 잘못된 node로 보냈다면 MOVED를 받는다.
5. client는 topology를 갱신하고 올바른 node로 재시도한다.
```

### Failover와 Slot Ownership

Cluster에서 master가 죽으면 그 master가 담당하던 slot이 사라지는 것이 아니다. 해당 master의 replica가 승격되면 slot ownership이 새 master로 이동한다.

Phase 3에서 관찰한 핵심은 다음이다.

```text
master down
-> replica promotion
-> slot owner 변경
-> cluster_state:ok 회복
-> client topology refresh
-> app read/write 회복
```

이 과정에서 짧은 실패나 지연이 발생할 수 있다. 따라서 Cluster client는 topology refresh와 redirect 처리를 잘해야 한다.

### Multi-key 제약

Redis Cluster에서 여러 key를 한 번에 다루는 명령은 모든 key가 같은 slot에 있어야 안전하다.

가능한 예:

```text
phase5:{multi}:a
phase5:{multi}:b
```

두 key 모두 `{multi}`를 기준으로 slot이 계산되므로 같은 slot에 배치된다.

주의할 예:

```text
phase5:multi:a
phase5:multi:b
```

겉보기에는 비슷해도 hash tag가 없으므로 서로 다른 slot에 배치될 수 있다.

이 차이는 Lua script, transaction, multi lock, 여러 resource를 한 번에 다루는 도메인 로직에서 중요하다.

## 5. Replication과 Failover

관련 Phase: Phase 3

Redis Cluster의 각 master는 replica를 가질 수 있다. replica는 master의 데이터를 복제하고, master 장애 시 promotion candidate가 된다.

Phase 1에서 확인한 replica mapping 예시는 다음과 같다.

```text
Replica 7005 -> Master 7001
Replica 7006 -> Master 7002
Replica 7004 -> Master 7003
```

### 비동기 Replication

Redis replication은 기본적으로 비동기다.

즉, master에 write가 성공했다고 해서 replica에 즉시 반영되었다고 보장할 수 없다. 이 때문에 Phase 3에서는 `WAIT` 명령을 함께 사용해 replica ack를 관찰했다.

`WAIT`의 의미:

```text
지정한 replica 수가 현재 write를 ack 했는지 기다린다.
```

하지만 `WAIT`는 강한 일관성 트랜잭션을 만들어주는 명령이 아니다. 복제 반영을 관찰하고 지연을 줄이는 데 도움을 줄 뿐이다.

### Replica Direct Read

Cluster replica에서 직접 읽으려면 `READONLY` 모드가 필요하다. Phase 3에서는 master에 값을 쓴 뒤 replica direct read로 복제 결과를 확인했다.

이 실험의 의미는 다음과 같다.

```text
master write
-> WAIT ack 확인
-> replica direct read
-> 복제된 값을 실제로 읽을 수 있는지 확인
```

### Failover

master process failure를 재현하면 다음 흐름이 발생한다.

```text
1. master container stop
2. cluster가 master 장애 감지
3. replica 중 하나가 promotion
4. 새 master가 기존 slot range를 담당
5. cluster_state:ok 회복
6. app client가 topology를 갱신하고 read/write 재개
```

복습할 때 중요한 점은 failover가 무중단을 항상 보장한다는 뜻이 아니라는 것이다. failover window 동안 일부 요청은 실패하거나 지연될 수 있다.

## 6. Observability

관련 Phase: Phase 2, Phase 4, Phase 5

Phase 2에서는 이후 실험을 위한 관측 기반을 만들었다. Redis Cluster 실험은 장애와 경합을 다루기 때문에 "동작했다"보다 "어디서 어떻게 흔들렸는지 볼 수 있는가"가 중요하다.

구성:

```text
Redis node 6개
-> Redis Exporter 6개
-> Prometheus
-> Grafana

Spring Boot app 3개
-> Actuator / Prometheus endpoint
-> Prometheus
-> Grafana
```

### Redis Metric

복습할 metric 예시:

```text
up{job="redis"}
redis_memory_used_bytes
redis_connected_clients
redis_commands_processed_total
redis_cluster_enabled
replication offset 계열 metric
```

노드별 Redis Exporter를 둔 이유는 failover 실험에서 특정 master/replica 상태를 독립적으로 추적하기 위해서다.

### Application Metric

복습할 metric 예시:

```text
up{job="redis-cluster-lab-app"}
http_server_requests_seconds_count
http_server_requests_seconds_sum
jvm_memory_used_bytes
process_uptime_seconds
```

앱 인스턴스는 `APP_INSTANCE_NAME`으로 구분한다. app1, app2, app3가 같은 Redis Cluster를 바라보며 동시 경합 실험에 참여한다.

### Lock Metric

Phase 4와 Phase 5에서 lock 관련 custom metric을 추가했다.

Lettuce:

```text
redis_lock_acquire_total{client="lettuce",mode="single",result="success|failure"}
redis_lock_release_total{client="lettuce",mode="single",result="success|failure"}
redis_lock_acquire_duration_seconds{client="lettuce",mode="single"}
redis_lock_contention_attempts_total{client="lettuce",mode="single"}
```

Redisson:

```text
redis_lock_acquire_total{client="redisson",mode="rlock|multi",result="success|failure"}
redis_lock_release_total{client="redisson",mode="rlock|multi",result="success|failure"}
redis_lock_acquire_duration_seconds{client="redisson",mode="rlock|multi"}
redis_lock_watchdog_ttl_*{client="redisson",sample="pttl"}
redis_lock_contention_attempts_total{client="redisson",mode="rlock|multi"}
```

중요한 판단:

> lockKey를 metric label로 넣으면 key 수만큼 cardinality가 증가한다. 그래서 개별 lock 상태는 API로 보고, metric은 성공/실패/latency/contention 추이를 보는 식으로 분리했다.

## 7. Lettuce 기반 분산 락

관련 Phase: Phase 4

Phase 4에서는 Redisson 없이 Lettuce를 직접 사용해 Redis lock의 기본 원리를 구현했다.

락 획득:

```text
SET lockKey token NX PX ttlMillis
```

의미:

| 옵션 | 의미 |
| --- | --- |
| `NX` | key가 없을 때만 set |
| `PX` | millisecond TTL 설정 |
| `token` | lock owner를 구분하는 값 |

이 명령은 단일 key에 대해 원자적이다. 즉, 여러 app instance가 같은 key로 동시에 시도해도 한 시점에 하나만 성공한다.

### Safe Release

락 해제는 단순 `DEL lockKey`로 하면 위험하다.

예를 들어 owner A의 TTL이 만료된 뒤 owner B가 새로 락을 잡았는데, owner A가 뒤늦게 `DEL`을 보내면 owner B의 락을 지워버릴 수 있다.

그래서 Lua script로 token이 일치할 때만 삭제한다.

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
else
  return 0
end
```

이 방식의 핵심은 다음이다.

```text
lockKey의 현재 token == 내가 가진 token
-> 내가 owner이므로 삭제 가능

lockKey의 현재 token != 내가 가진 token
-> 이미 다른 owner의 lock이므로 삭제하면 안 됨
```

### TTL Expiration

TTL은 lock이 영원히 남는 것을 막아준다. 하지만 TTL은 동시에 위험의 원인이기도 하다.

문제 상황:

```text
1. owner A가 ttl=500ms로 lock 획득
2. A의 작업이 1000ms 걸림
3. 500ms 뒤 lock 만료
4. owner B가 같은 lock 획득
5. A와 B가 동시에 critical section을 수행할 수 있음
```

이때 Lua safe release는 B의 lock을 A가 지우는 문제는 막아준다. 하지만 A가 이미 critical section에서 늦은 write를 하는 문제까지 막아주지는 않는다.

즉, Redis lock은 "락 key 삭제 안전성"과 "비즈니스 write 안전성"을 구분해서 봐야 한다.

### Lettuce Lock의 한계

Lettuce 직접 구현은 원리를 이해하기 좋고 단순하다. 하지만 다음 기능은 직접 설계해야 한다.

```text
TTL 연장
재진입 락
공정 락
multi lock
fencing token
stale write 방어
장애 상황 보정
```

Phase 4의 결론은 다음이다.

> `SET NX PX + token-safe Lua release`는 Redis 기반 단일 key lock의 기본형으로 충분히 중요하지만, TTL 이후에도 작업이 계속되는 stale owner 문제는 별도 설계가 필요하다.

## 8. Redisson 기반 분산 락

관련 Phase: Phase 5

Phase 5에서는 Phase 4의 Lettuce 직접 구현을 baseline으로 두고 Redisson을 비교했다.

Redisson은 Redis 기반 lock을 Java의 `Lock`에 가까운 API로 사용할 수 있게 해준다.

대표 API:

```java
RLock lock = redissonClient.getLock(lockKey);
boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
lock.unlock();
```

### RLock

`RLock`은 Redisson의 기본 분산 락이다.

관찰한 핵심:

```text
tryLock()으로 획득 시도
wait time 동안 대기 가능
lease time 지정 가능
unlock()은 Redisson의 owner thread 모델을 따름
```

Redisson의 일반 `unlock()`은 같은 Java thread가 lock owner라는 전제에 맞춰져 있다. 이 프로젝트의 REST 실험에서는 acquire와 release가 서로 다른 HTTP 요청, 다른 Java thread에서 실행될 수 있으므로 일반적인 `unlock()` 모델과 잘 맞지 않는다.

그래서 실험용 `/release`는 app-local owner registry를 확인한 뒤 `forceUnlock()`을 사용했다.

중요한 복습 포인트:

> 실무 코드에서는 Redisson lock 획득과 release를 같은 실행 흐름의 `try/finally` 안에 두는 편이 Redisson 모델에 맞다.

### Watchdog

lease time을 지정하지 않고 lock을 잡으면 Redisson watchdog이 TTL을 자동으로 연장한다.

흐름:

```text
1. lock 획득
2. Redisson이 lock key에 TTL 설정
3. client가 살아 있는 동안 watchdog이 TTL 갱신
4. 작업이 길어져도 lock이 중간에 만료되지 않음
5. release하면 lock 삭제
```

watchdog 실험에서는 Redis key의 `PTTL`을 주기적으로 샘플링해 TTL이 연장되는 것을 확인했다.

하지만 watchdog을 "안전 보장 장치"로 과신하면 안 된다.

watchdog이 의미하는 것은 다음에 가깝다.

```text
Redisson client가 아직 살아 있고 TTL 갱신을 수행하고 있다.
```

다음을 완전히 보장하지는 않는다.

```text
비즈니스 작업이 실제로 안전하게 진행 중이다.
GC pause가 절대 길어지지 않는다.
네트워크 파티션이 없다.
Redis failover 중에도 lock safety가 완전하다.
stale owner write가 절대 발생하지 않는다.
```

### Lease Time

`tryLock(waitTime, leaseTime, TimeUnit)`처럼 lease time을 명시하면 지정한 시간이 지난 뒤 lock이 자동 만료된다. 이 경우 watchdog은 동작하지 않는다.

문제 상황:

```text
1. owner A가 leaseMillis=1000으로 lock 획득
2. A의 workMillis=1500
3. 1000ms 뒤 lock 만료
4. owner B가 lock 획득 가능
5. A는 여전히 작업 중일 수 있음
```

이 실험은 Redisson을 사용해도 stale owner 문제가 사라지지 않는다는 점을 보여준다.

### Multi Lock

Redisson multi lock은 여러 `RLock`을 하나의 lock처럼 다룬다.

```java
RLock a = redissonClient.getLock("phase5:{multi}:a");
RLock b = redissonClient.getLock("phase5:{multi}:b");
RLock multiLock = redissonClient.getMultiLock(a, b);
```

핵심 의미:

```text
여러 lock을 모두 획득해야 성공
일부 실패 시 이미 잡은 lock 정리가 필요
여러 resource를 함께 보호할 때 사용 가능
```

Redis Cluster에서는 key slot을 반드시 의식해야 한다. hash tag를 사용하면 관련 key를 같은 slot에 묶을 수 있다.

```text
phase5:{multi}:a
phase5:{multi}:b
```

다만 multi lock이 있다고 해서 장애 상황의 partial acquire/release, network partition, stale write 문제가 자동으로 사라지는 것은 아니다.

## 9. Lettuce vs Redisson 비교

관련 Phase: Phase 4, Phase 5

| 항목 | Lettuce 직접 구현 | Redisson |
| --- | --- | --- |
| 기본 획득 | `SET NX PX` 직접 호출 | `RLock.tryLock()` |
| 해제 방식 | token 비교 Lua script | thread ownership 기반 `unlock()` |
| REST release 실험 | token만 있으면 가능 | 일반 `unlock()`은 같은 Java thread 필요 |
| TTL 연장 | 직접 구현 필요 | lease time 미지정 시 watchdog 제공 |
| lease time | 직접 TTL로 관리 | API에서 지원 |
| multi lock | 직접 설계 필요 | `getMultiLock()` 제공 |
| 재진입 락 | 직접 설계 필요 | `RLock`이 재진입 모델 제공 |
| metric | 직접 설계 | 직접 설계 필요 |
| stale write 방어 | 별도 fencing token 필요 | 별도 fencing token 필요 |

Lettuce는 Redis 명령에 가까운 저수준 client다. 그래서 Redis lock의 원리를 직접 이해하고 제어하기 좋다.

Redisson은 lock, watchdog, multi lock 같은 고수준 추상화를 제공한다. 실무 코드에서는 편의성이 크지만, 내부 모델을 이해하지 못하면 REST release, thread ownership, lease time, watchdog 동작을 오해하기 쉽다.

핵심 차이는 다음과 같다.

```text
Lettuce  = Redis command를 직접 조합해 lock을 만든다.
Redisson = Redis 기반 lock 알고리즘을 Java Lock 추상화로 제공한다.
```

하지만 둘의 공통 한계도 중요하다.

```text
lock을 잡았다는 사실만으로 stale write가 자동 방어되지는 않는다.
```

## 10. Redis Cluster에서 Lock을 사용할 때의 핵심 위험

관련 Phase: Phase 4, Phase 5, 후속 Phase 후보

Redis lock을 사용할 때 복습해야 할 가장 중요한 질문은 다음이다.

> lock key는 안전한가?
> 그리고 lock으로 보호하려는 실제 비즈니스 write도 안전한가?

이 둘은 다르다.

### 위험 1. TTL보다 작업이 오래 걸림

```text
lock ttl = 1초
작업 시간 = 3초
```

이 경우 lock은 중간에 만료되고 다른 owner가 들어올 수 있다. 이전 owner가 뒤늦게 write하면 stale write가 된다.

### 위험 2. GC Pause / Process Stop

lock owner app이 긴 GC pause에 빠지거나 process가 멈추면 TTL 연장이나 release가 제때 수행되지 않을 수 있다.

Redisson watchdog도 client가 정상적으로 갱신할 수 있을 때 의미가 있다.

### 위험 3. Network Partition

app과 Redis 사이 또는 Redis node 사이 네트워크가 갈라지면 lock holder가 자신이 아직 안전하다고 착각할 수 있다.

Phase 5에서는 정식 network partition 실험을 하지 않았고, 후속 Phase 후보로 남겼다.

### 위험 4. Failover와 Replication Lag

Redis replication은 비동기다. master failover가 lock write 직후 발생하면 어떤 write가 replica에 반영되었는지, 새 master가 어떤 상태를 갖는지 주의 깊게 봐야 한다.

이 프로젝트에서는 Phase 3에서 replication/failover 기반을 만들었고, Phase 4/5에서 lock을 다뤘다. 두 주제를 합친 "failover 중 lock safety"는 후속 실험으로 확장할 수 있다.

### 위험 5. Lock만 있고 Fencing Token이 없음

stale owner write를 막으려면 lock 외부의 보호 장치가 필요할 수 있다.

대표적인 보완책:

```text
fencing token
DB unique constraint
idempotency key
version check
optimistic locking
business-level state transition guard
```

예를 들어 fencing token은 lock 획득 시 단조 증가하는 token을 발급하고, 실제 write 대상 시스템이 더 낮은 token의 write를 거부하게 만드는 방식이다.

정리:

```text
Redis lock = critical section 진입을 줄이는 장치
fencing/idempotency/version = 늦은 write를 거부하는 장치
```

## 11. 복습용 실험 시나리오

관련 Phase: Phase 1, Phase 2, Phase 3, Phase 4, Phase 5

다음 순서로 다시 실행하면 전체 흐름을 빠르게 복습할 수 있다.

### 1. Cluster 생성과 기본 검증

```bash
./scripts/create-cluster.sh
./scripts/verify-cluster.sh
./scripts/demo-moved.sh
```

확인할 것:

```text
cluster_state:ok
cluster_slots_assigned:16384
master 3개 + replica 3개
slot range
MOVED redirect
```

### 2. App 실행과 Cluster API

```bash
./scripts/start-apps.sh
./scripts/verify-app.sh http://localhost:8081
```

확인할 것:

```text
app1/app2/app3가 같은 Redis Cluster를 바라보는지
keyslot API가 예상대로 동작하는지
value read/write가 성공하는지
```

### 3. Observability

```bash
docker compose --profile observability up -d
./scripts/verify-observability.sh
```

확인할 것:

```text
Prometheus target UP
Redis exporter 6개 UP
app actuator metric 수집
Grafana dashboard provisioning
```

### 4. Replication / Failover

```bash
./scripts/verify-replication.sh http://localhost:8081
./scripts/demo-failover.sh redis-node-1 7001 http://localhost:8081
```

확인할 것:

```text
WAIT ack
replica direct read
master stop
replica promotion
cluster_state:ok 회복
failover 이후 app read/write 성공
```

### 5. Lettuce Lock

```bash
./scripts/verify-lettuce-lock.sh http://localhost:8081
./scripts/demo-lock-contention.sh
```

확인할 것:

```text
첫 acquire 성공
동일 lock의 두 번째 acquire 실패
잘못된 token release 실패
정상 token release 성공
경합 중 한 시점에 owner 하나만 성공
TTL 만료 후 새 owner 획득 가능
```

### 6. Redisson Lock

```bash
./scripts/verify-redisson-lock.sh http://localhost:8081
./scripts/demo-redisson-watchdog.sh http://localhost:8081
./scripts/demo-redisson-lease-expiration.sh http://localhost:8081
./scripts/demo-redisson-multi-lock.sh http://localhost:8081
```

확인할 것:

```text
RLock acquire / release
watchdog TTL 자동 연장
lease time 지정 시 자동 만료
lease 만료 후 competitor 획득 가능
multi lock 성공/실패와 release
```

## 12. 다음 복습 때 꼭 떠올릴 문장

Redis Cluster:

> Cluster는 node가 아니라 slot을 기준으로 생각한다.

MOVED:

> MOVED는 "이 key의 slot owner는 다른 node"라는 라우팅 힌트다.

Replication:

> Redis replication은 기본적으로 비동기이고, `WAIT`는 관찰 도구이지 강한 일관성 보장이 아니다.

Failover:

> failover는 회복 메커니즘이지 모든 요청의 무중단 성공을 보장하는 것은 아니다.

Lettuce Lock:

> `SET NX PX + token-safe Lua release`는 Redis lock의 기본 원리를 보여주지만, TTL 이후 stale owner write는 막지 못한다.

Redisson:

> Redisson은 lock 사용성을 높여주지만, watchdog이 비즈니스 write 안전성까지 보장하지는 않는다.

Multi Lock:

> 여러 key를 함께 다룰수록 Redis Cluster에서는 hash slot과 partial failure를 같이 생각해야 한다.

실무 안전성:

> Redis lock은 critical section 진입을 줄이는 장치이고, stale write 방어는 fencing token, idempotency, version check 같은 별도 설계가 필요하다.

