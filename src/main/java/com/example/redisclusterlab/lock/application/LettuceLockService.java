package com.example.redisclusterlab.lock.application;

import com.example.redisclusterlab.lock.dto.AcquireLockRequest;
import com.example.redisclusterlab.lock.dto.AcquireLockResponse;
import com.example.redisclusterlab.lock.dto.LockStateResponse;
import com.example.redisclusterlab.lock.dto.ReleaseLockRequest;
import com.example.redisclusterlab.lock.dto.ReleaseLockResponse;
import com.example.redisclusterlab.lock.lettuce.LettuceLockClient;
import com.example.redisclusterlab.lock.metrics.LettuceLockMetrics;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Lettuce로 SET NX PX와 token-safe Lua release를 직접 구현해 분산락의 최소 원리를 검증하는 baseline 서비스다.
public class LettuceLockService {

    private final LettuceLockClient lockClient;
    private final LettuceLockMetrics lockMetrics;
    @Value("${APP_INSTANCE_NAME:local}")
    private final String defaultOwner;

    // SET NX PX는 Redis 단일 명령이라 락 획득과 TTL 설정이 원자적으로 처리된다.
    public AcquireLockResponse acquire(AcquireLockRequest request) {
        String owner = resolveOwner(request.owner());
        String token = token(owner);
        Instant acquiredAt = Instant.now();
        long startedNanos = System.nanoTime();

        try {
            boolean acquired = lockClient.acquire(request.lockKey(), token, request.ttlMillis());
            lockMetrics.recordAcquire(acquired, System.nanoTime() - startedNanos);
            return new AcquireLockResponse(
                    request.lockKey(),
                    token,
                    acquired,
                    request.ttlMillis(),
                    owner,
                    acquiredAt
            );
        } catch (RuntimeException ex) {
            lockMetrics.recordAcquire(false, System.nanoTime() - startedNanos);
            throw ex;
        }
    }

    // release는 token 비교 Lua script를 통해 현재 owner의 락만 삭제한다.
    public ReleaseLockResponse release(ReleaseLockRequest request) {
        boolean released = lockClient.release(request.lockKey(), request.token());
        lockMetrics.recordRelease(released);
        return new ReleaseLockResponse(request.lockKey(), released);
    }

    // 상태 조회는 실험 관찰용이므로 token과 남은 TTL을 그대로 노출한다.
    public LockStateResponse state(String lockKey) {
        String token = lockClient.get(lockKey);
        long ttlMillis = lockClient.pttl(lockKey);
        return new LockStateResponse(lockKey, token != null, token, ttlMillis);
    }

    // owner가 비어 있으면 컨테이너/app 인스턴스 이름을 사용해 어느 인스턴스가 락을 잡았는지 드러낸다.
    private String resolveOwner(String requestedOwner) {
        if (requestedOwner == null || requestedOwner.isBlank()) {
            return defaultOwner;
        }
        return requestedOwner;
    }

    // token은 owner와 UUID를 함께 담아 release 시점에 실제 소유자인지 검증하는 값이다.
    private String token(String owner) {
        return owner + ":" + UUID.randomUUID();
    }
}
