# Phase 4 - Lettuce 기반 락

## 목표

Redis Cluster에서 Lettuce를 직접 사용해 `SET lockKey token NX PX ttlMillis` 기반 분산 락을 구현하고, 여러 애플리케이션 인스턴스가 동시에 같은 락을 획득하려는 상황과 TTL 만료 상황에서 락의 동작 방식과 한계를 관찰한다.

Phase 4에서는 Redisson, RedLock, 공정 락, 재진입 락, 멀티 락, 네트워크 파티션 장애 주입, hot key 실험은 구현하지 않는다.

## 실행 환경

* OS: macOS + Docker Desktop
* Redis: 7.2 Docker 이미지
* 애플리케이션: Java 17, Spring Boot 3.x
* 클라이언트: Lettuce
* 클러스터 토폴로지: master 3개 + replica 3개
* 앱 인스턴스: `http://localhost:8081`, `http://localhost:8082`, `http://localhost:8083`
* 관측 도구: Redis Exporter, Prometheus, Grafana

## API

락 획득:

```bash
curl -X POST http://localhost:8081/locks/lettuce/acquire \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:demo","ttlMillis":5000,"owner":"manual"}'
```

락 해제:

```bash
curl -X POST http://localhost:8081/locks/lettuce/release \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:demo","token":"<token>"}'
```

락 상태 조회:

```bash
curl "http://localhost:8081/locks/lettuce/state?lockKey=phase4:lock:demo"
```

락 경합 실험:

```bash
curl -X POST http://localhost:8081/locks/lettuce/contend \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:contention","workers":6,"attemptsPerWorker":3,"ttlMillis":1000,"workMillis":250}'
```

TTL 만료 실험:

```bash
curl -X POST http://localhost:8081/locks/lettuce/ttl-expiration \
  -H "Content-Type: application/json" \
  -d '{"lockKey":"phase4:lock:ttl","ttlMillis":500,"waitMillis":700,"owner":"manual"}'
```

## 스크립트

```bash
./scripts/create-cluster.sh
./scripts/start-apps.sh
./scripts/verify-lettuce-lock.sh http://localhost:8081
./scripts/demo-lock-contention.sh
```

`verify-lettuce-lock.sh`는 다음 흐름을 검증한다.

* token A로 락 획득이 성공한다.
* token A의 락이 살아 있는 동안 두 번째 락 획득은 실패한다.
* 잘못된 token으로 락 해제를 시도하면 실패한다.
* token A로 락 해제를 시도하면 성공한다.
* 락 해제 후 다시 락 획득이 성공한다.

`demo-lock-contention.sh`는 app1, app2, app3에 같은 락 경합 요청을 동시에 보낸다. 같은 Redis key는 한 시점에 하나의 owner만 보유할 수 있고, 실패한 획득 시도는 각 응답과 메트릭에서 확인할 수 있다.

## 메트릭

애플리케이션 메트릭:

* `redis_lock_acquire_total{client="lettuce",mode="single",result="success"}`
* `redis_lock_acquire_total{client="lettuce",mode="single",result="failure"}`
* `redis_lock_release_total{client="lettuce",mode="single",result="success"}`
* `redis_lock_release_total{client="lettuce",mode="single",result="failure"}`
* `redis_lock_acquire_duration_seconds_count{client="lettuce",mode="single"}`
* `redis_lock_acquire_duration_seconds_sum{client="lettuce",mode="single"}`
* `redis_lock_contention_attempts_total{client="lettuce",mode="single"}`

Prometheus 질의 예시:

```promql
sum by (result, instance) (rate(redis_lock_acquire_total[1m]))
```

Grafana dashboard에는 `Phase 4 Lettuce Lock` 섹션을 추가한다. 이 섹션에서 다음 항목을 확인한다.

* 락 획득 누적 횟수
* 락 해제 누적 횟수
* 락 경합 시도 누적 횟수
* 락 획득 성공/실패 추이
* 락 해제 성공/실패 추이
* 락 획득 평균 지연 시간
* 락 경합 시도 추이
* 락 API 요청 상태

개별 `lockKey`가 현재 존재하는지 여부는 `/locks/lettuce/state` API 응답으로 확인한다. `lockKey`별 상태를 Prometheus label로 수집하면 key 수만큼 metric cardinality가 늘어날 수 있으므로 Phase 4 dashboard에는 추가하지 않는다.

## 기대 결과

* 첫 번째 락 획득 요청은 `acquired: true`와 생성된 owner token을 반환한다.
* 같은 key에 대한 두 번째 락 획득 요청은 TTL이 살아 있는 동안 `acquired: false`를 반환한다.
* 잘못된 token으로 락 해제를 요청하면 `released: false`를 반환하고 기존 락을 삭제하지 않는다.
* owner token으로 락 해제를 요청하면 `released: true`를 반환한다.
* 락 해제 또는 TTL 만료 후에는 새로운 락 획득이 성공할 수 있다.
* 락 경합 상황에서는 성공 횟수가 전체 시도 횟수보다 작고, 실제로 락을 보유한 worker는 observed owner/token 목록으로 확인할 수 있다.

## 분석

`SET NX PX`는 단일 Redis key에 대해 원자적인 락 획득 연산을 제공한다. Redis는 key가 없을 때만 새 값을 만들고, 같은 명령 안에서 만료 시간을 설정한다. 생성된 token은 락 소유권을 나타내며, Lua 해제 스크립트는 저장된 token이 호출자의 token과 일치할 때만 key를 삭제한다.

이 방식은 보호 대상 작업이 TTL 안에 끝나고 클라이언트가 자기 token으로만 해제를 시도하는 일반적인 단일 key Redis Cluster 동작에서는 충분히 안전하게 사용할 수 있다. 중요한 한계는 TTL이 시간 기반이라는 점이다. owner가 TTL 만료 이후에도 작업을 계속하면 다른 owner가 같은 락을 획득할 수 있고, 이전 owner의 늦은 해제 요청은 새 owner의 락을 삭제하지 못하고 실패해야 한다. Phase 4는 TTL 만료 API와 락 경합 응답을 통해 이 동작을 드러낸다.

## 결론

Phase 4는 Redis Cluster에서 Lettuce 기반 Redis 락을 설명하기 위한 반복 가능한 기준 실험을 제공한다. 이후 phase에서는 이 기준 위에서 Redisson, RedLock, replication lag가 락 안전성에 미치는 영향, 네트워크 파티션, hot key 압력을 비교 실험으로 확장할 수 있다.
