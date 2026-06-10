# Phase 5 Troubleshooting

## Gradle dependency download fails

증상:

```text
Could not resolve org.redisson:redisson
java.net.SocketException: Operation not permitted
Connection reset
```

원인:

Codex sandbox 또는 로컬 네트워크가 Gradle의 Maven Central 연결을 막으면 Redisson 의존성 다운로드가 실패한다.

대응:

```bash
GRADLE_USER_HOME="${PWD}/.gradle" ./gradlew test
```

Codex 환경에서 socket 제한이 발생하면 승인된 실행으로 다시 시도한다. `Connection reset`이 반복되면 네트워크가 회복된 뒤 재실행한다.

## Redisson release fails after acquire

증상:

```json
{"released":false,"knownLocalOwner":false}
```

원인:

`/locks/redisson/release`는 app-local owner registry가 일치할 때만 `forceUnlock()`을 호출한다. acquire와 release를 서로 다른 앱 인스턴스에 보내거나 앱이 재시작되면 registry가 없다.

대응:

같은 앱 인스턴스 URL로 acquire/release를 호출한다. 실제 업무 코드에서는 HTTP 요청을 나누지 말고 같은 Java 실행 흐름에서 `tryLock()`과 `unlock()`을 `try/finally`로 묶는다.

## IllegalMonitorStateException

원인:

Redisson `RLock.unlock()`은 lock을 획득한 같은 Java thread에서 호출되어야 한다. 다른 thread에서 호출하면 owner mismatch가 발생한다.

대응:

실험 API 중 watchdog, lease expiration, multi lock은 한 요청 안에서 획득과 해제를 수행한다. 별도 release endpoint는 이 제약을 관찰하기 위해 app-local owner check와 `forceUnlock()`을 사용한다.

## Watchdog TTL sample does not increase

확인할 점:

* request에 `leaseMillis`를 넣지 않았는지 확인한다.
* `workMillis`가 기본 watchdog timeout보다 충분히 긴지 확인한다.
* Redis key가 이미 다른 owner에게 잡혀 `acquired=false`가 아닌지 확인한다.

Redisson은 lease time을 명시하면 watchdog 갱신을 사용하지 않는다.

## Multi lock with cross-slot keys

Redis Cluster에서 여러 key가 서로 다른 hash slot에 놓일 수 있다. Redisson multi lock은 여러 `RLock`을 조합하지만, 장애 상황에서 partial acquire나 release를 이해해야 한다. 같은 slot 실험은 hash tag를 사용한다.

```text
phase5:{multi}:a
phase5:{multi}:b
```
