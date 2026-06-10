package com.example.redisclusterlab.lock.redisson.application;

import com.example.redisclusterlab.cluster.common.RedisClusterConnectionProvider;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLockStateResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonReleaseRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonReleaseResponse;
import com.example.redisclusterlab.lock.redisson.metrics.RedissonLockMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Redisson RLock 기본 acquire/release/state를 REST 실험으로 관찰하기 위한 서비스다.
// HTTP 요청이 thread ownership을 유지하지 못하므로 release는 app-local owner 확인 후 forceUnlock으로 제한한다.
public class RedissonLockService {

    private static final String MODE_RLOCK = "rlock";

    private final RedissonClient redissonClient;
    private final RedisClusterConnectionProvider connectionProvider;
    private final RedissonLockMetrics lockMetrics;
    @Value("${APP_INSTANCE_NAME:local}")
    private final String defaultOwner;
    private final Map<String, HeldLock> localOwners = new ConcurrentHashMap<>();

    /**
     *  분산 Lock 실패시, 기다리지 않는 즉시 획득 시도
     *
     */
    public RedissonLockResponse acquire(RedissonLockRequest request) {
        return doAcquire(request, false);
    }

    /**
     *  분산 Lock 실패시, waitMillis만큼 기다리는 획득 시도
     *  실무에서는 finally 문에 lock 해제를 해줘야함.
     *  AOP 기반으로 분산락을 구현하여, 개발자는 구현체 Service메서드에 비즈니스 로직을 구성하도록한다.
     */
    public RedissonLockResponse tryAcquire(RedissonLockRequest request) {
        return doAcquire(request, true);
    }

    public RedissonReleaseResponse release(RedissonReleaseRequest request) {
        String owner = resolveOwner(request.owner());
        HeldLock heldLock = localOwners.get(request.lockKey());
        boolean knownLocalOwner = heldLock != null && owner.equals(heldLock.owner()) && !heldLock.leaseExpired();
        boolean released = false;

        if (heldLock != null && owner.equals(heldLock.owner()) && heldLock.leaseExpired()) {
            localOwners.remove(request.lockKey());
        }

        if (knownLocalOwner) {
            released = redissonClient.getLock(request.lockKey()).forceUnlock();
            if (released) {
                localOwners.remove(request.lockKey());
            }
        }

        lockMetrics.recordRelease(MODE_RLOCK, released);
        return new RedissonReleaseResponse(
                request.lockKey(),
                owner,
                released,
                knownLocalOwner,
                "forceUnlock-after-local-owner-check"
        );
    }

    public RedissonLockStateResponse state(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        long ttlMillis = pttl(lockKey);
        if (ttlMillis == -2) {
            localOwners.remove(lockKey);
        }
        HeldLock heldLock = localOwners.get(lockKey);
        return new RedissonLockStateResponse(
                lockKey,
                lock.isLocked(),
                lock.isHeldByCurrentThread(),
                ttlMillis,
                heldLock == null ? null : heldLock.owner()
        );
    }

    private RedissonLockResponse doAcquire(RedissonLockRequest request, boolean honorWaitTime) {
        String owner = resolveOwner(request.owner());
        RLock lock = redissonClient.getLock(request.lockKey());
        long startedNanos = System.nanoTime();
        boolean acquired = false;

        try {
            if (request.leaseMillis() == null) {
                acquired = lock.tryLock(honorWaitTime ? request.waitMillis() : 0, TimeUnit.MILLISECONDS);
            } else {
                acquired = lock.tryLock(
                        honorWaitTime ? request.waitMillis() : 0,
                        request.leaseMillis(),
                        TimeUnit.MILLISECONDS
                );
            }

            if (acquired) {
                localOwners.put(request.lockKey(), new HeldLock(owner, request.leaseMillis(), System.nanoTime()));
            }
            return response(request, owner, acquired, startedNanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redisson 락 획득 중 인터럽트가 발생했습니다.", ex);
        } finally {
            lockMetrics.recordAcquire(MODE_RLOCK, acquired, System.nanoTime() - startedNanos);
        }
    }

    String resolveOwner(String requestedOwner) {
        if (requestedOwner == null || requestedOwner.isBlank()) {
            return defaultOwner;
        }
        return requestedOwner;
    }

    long pttl(String lockKey) {
        Long ttlMillis = connectionProvider.commands().pttl(lockKey);
        return ttlMillis == null ? -2 : ttlMillis;
    }

    private RedissonLockResponse response(
            RedissonLockRequest request,
            String owner,
            boolean acquired,
            long startedNanos
    ) {
        return new RedissonLockResponse(
                request.lockKey(),
                owner,
                acquired,
                request.waitMillis(),
                request.leaseMillis(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                Thread.currentThread().getName()
        );
    }

    private record HeldLock(String owner, Long leaseMillis, long acquiredNanos) {

        boolean leaseExpired() {
            if (leaseMillis == null) {
                return false;
            }
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - acquiredNanos) >= leaseMillis;
        }
    }
}
