# Phase 1 Troubleshooting Notes

## Docker Desktop에서 Redis Cluster redirect가 실패하는 경우

Redis Cluster는 key가 속한 slot의 owner를 클라이언트에게 알려준다. 이때 노드가 announce한 주소가 클라이언트에서 접근 불가능하면 MOVED redirect 이후 연결이 실패한다.

이 랩에서는 Redis 노드가 Docker bridge 내부 고정 IP를 announce한다. 따라서 Spring Boot 앱 3개도 같은 Docker 네트워크에서 실행한다.

## 클러스터를 다시 만들 때 이미 노드가 구성되어 있다는 오류

Redis Cluster 구성 정보는 Docker volume의 `/data/nodes.conf`에 남는다. 완전히 초기화하려면 다음 명령을 사용한다.

```bash
./scripts/reset-cluster.sh
./scripts/create-cluster.sh
```

## Gradle native library 오류

로컬 Gradle이 `libnative-platform.dylib` 로딩 오류를 내는 경우 `GRADLE_USER_HOME`을 프로젝트 내부로 지정한다.

```bash
GRADLE_USER_HOME="${PWD}/.gradle" ./gradlew bootJar
```
