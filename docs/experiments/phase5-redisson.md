# Phase 5 - Redisson Distributed Lock

Phase 5는 Phase 4의 Lettuce `SET NX PX + Lua release` 직접 구현을 baseline으로 두고, Redisson `RLock`, watchdog, lease time, multi lock을 실험한다.

## 검증 질문

* Redisson `RLock`은 직접 구현한 Redis lock보다 어떤 추상화를 제공하는가?
* lease time을 지정하지 않은 lock은 watchdog에 의해 TTL이 연장되는가?
* lease time을 지정하면 watchdog 없이 지정된 시간이 지난 뒤 lock이 만료되는가?
* 여러 lock key를 동시에 잡는 multi lock은 성공/실패와 release를 어떻게 처리하는가?
* Redisson을 사용해도 stale owner write, process stop, GC pause, network partition 문제는 왜 남는가?

## API

```http
POST /locks/redisson/acquire
POST /locks/redisson/try-acquire
POST /locks/redisson/release
GET  /locks/redisson/state?lockKey=...
POST /locks/redisson/watchdog
POST /locks/redisson/lease-expiration
POST /locks/redisson/multi-lock
POST /locks/redisson/aop-demo
POST /locks/redisson/aop-business-demo
```

`/acquire`와 `/try-acquire`는 REST 실험 편의를 위해 app-local owner registry를 기록한다. Redisson `RLock.unlock()`은 같은 Java thread owner를 요구하므로, 별도 HTTP 요청에서 일반 `unlock()`으로 release하는 모델과 잘 맞지 않는다. 이 프로젝트의 `/release`는 owner registry가 일치할 때만 `forceUnlock()`을 호출한다. 실제 업무 코드에서는 lock 획득과 release를 같은 실행 흐름의 `try/finally` 안에 두는 편이 Redisson 모델에 맞다.

## AOP 기반 공통 락

실무 코드에서는 `RedissonClient.getLock()`을 서비스마다 직접 호출하지 않고, 보호해야 하는 메서드에 `@RedissonLocked`를 붙인다.

경합 재현용 demo:

```java
@RedissonLocked(
        key = "'phase5:redisson:aop:' + #resourceId",
        waitMillis = 200,
        leaseMillis = 2000
)
public RedissonAopDemoResponse runLocked(String resourceId, long workMillis) {
    sleep(workMillis);
}
```

실무형 패턴 demo:

```java
@RedissonLocked(
        key = "'phase5:redisson:coupon:' + #couponId + ':' + #userId",
        waitMillis = 1000,
        leaseMillis = 5000
)
public RedissonAopBusinessResponse issueCoupon(String couponId, String userId) {
    // 실무에서는 DB unique constraint, 재고 차감, 발급 이력 저장 같은 critical section이 들어간다.
}
```

요청 예시:

```bash
curl -X POST http://localhost:8081/locks/redisson/aop-business-demo \
  -H "Content-Type: application/json" \
  -d '{"couponId":"coupon-1","userId":"user-1"}'
```

## Watchdog 실험

```bash
./scripts/demo-redisson-watchdog.sh http://localhost:8081
```

요청 예시:

```bash
curl -X POST http://localhost:8081/locks/redisson/watchdog \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase5:redisson:watchdog","owner":"manual","workMillis":35000,"sampleIntervalMillis":5000}'
```

lease time 없이 `tryLock(waitTime, TimeUnit)`으로 lock을 잡으면 Redisson watchdog이 기본 TTL을 주기적으로 연장한다. 응답의 `ttlSamples`와 `redis.lock.watchdog.ttl` metric으로 PTTL 변화를 확인한다.

## Lease Time 실험

```bash
./scripts/demo-redisson-lease-expiration.sh http://localhost:8081
```

요청 예시:

```bash
curl -X POST http://localhost:8081/locks/redisson/lease-expiration \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase5:redisson:lease","owner":"manual","leaseMillis":1000,"workMillis":1500}'
```

`tryLock(waitTime, leaseTime, TimeUnit)`으로 lock을 잡으면 명시한 lease time 이후 자동 만료된다. `workMillis > leaseMillis`이면 기존 owner가 아직 작업 중이어도 competitor가 lock을 획득할 수 있다. 이것은 Redisson도 stale write를 자동으로 막지 못한다는 점을 보여준다.

## Multi Lock 실험

```bash
./scripts/demo-redisson-multi-lock.sh http://localhost:8081
```

요청 예시:

```bash
curl -X POST http://localhost:8081/locks/redisson/multi-lock \
  -H "Content-Type: application/json" \
  -d '{"lockKeys":["phase5:{multi}:a","phase5:{multi}:b"],"owner":"manual","waitMillis":500,"leaseMillis":5000,"workMillis":200}'
```

Redisson multi lock은 여러 `RLock`을 하나의 lock처럼 다룬다. 모든 lock을 잡아야 성공으로 본다. Redis Cluster에서는 hash tag를 사용해 같은 slot에 묶은 key와 서로 다른 slot key를 모두 실험해본다. Redisson은 개별 key에 대한 command를 조합하지만, 장애 상황에서 partial acquire/release가 어떻게 보일 수 있는지 반드시 관찰해야 한다.

## Lettuce Baseline과 비교

| 항목 | Lettuce 직접 구현 | Redisson |
| --- | --- | --- |
| 기본 획득 | `SET NX PX` 직접 호출 | `RLock.tryLock()` |
| 안전 해제 | token 비교 Lua script | Java thread ownership 기반 `unlock()` |
| TTL 연장 | 직접 구현 필요 | lease time 미지정 시 watchdog |
| REST release | token만 있으면 가능 | 일반 `unlock()`은 같은 Java thread 필요 |
| 여러 lock | 직접 설계 필요 | `getMultiLock()` |
| stale write 방어 | 별도 fencing token 필요 | 별도 fencing token 필요 |

## Metrics

Prometheus metric:

```text
redis_lock_acquire_total{client="redisson",mode="rlock|multi",result="success|failure"}
redis_lock_release_total{client="redisson",mode="rlock|multi",result="success|failure"}
redis_lock_acquire_duration_seconds{client="redisson",mode="rlock|multi"}
redis_lock_watchdog_ttl_max{client="redisson",sample="pttl"}
redis_lock_watchdog_ttl_sum{client="redisson",sample="pttl"}
redis_lock_watchdog_ttl_count{client="redisson",sample="pttl"}
redis_lock_contention_attempts_total{client="redisson",mode="rlock|multi"}
```

Grafana dashboard에는 `Phase 5 Redisson Lock` row를 추가한다.

## 한계

Watchdog은 lock holder client가 살아 있고 갱신을 수행한다는 신호다. 긴 GC pause, process stop, network partition, Redis failover 중에는 lock safety를 완전히 보장하지 않는다. stale owner가 오래 걸린 작업을 뒤늦게 write하는 문제는 fencing token 또는 idempotency 설계로 별도 방어해야 한다.
