package com.example.redisclusterlab.lock.redisson.application;

import com.example.redisclusterlab.lock.redisson.dto.RedissonLeaseExpirationRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonLeaseExpirationResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonMultiLockRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonMultiLockResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonTtlSample;
import com.example.redisclusterlab.lock.redisson.dto.RedissonWatchdogRequest;
import com.example.redisclusterlab.lock.redisson.dto.RedissonWatchdogResponse;
import com.example.redisclusterlab.lock.redisson.metrics.RedissonLockMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Redisson의 watchdog, 명시 lease time, multi lock 동작을 한 요청 안에서 재현해 내부 TTL과 경쟁 결과를 관찰한다.
public class RedissonLockExperimentService {

    private static final String MODE_RLOCK = "rlock";
    private static final String MODE_MULTI = "multi";

    private final RedissonClient redissonClient;
    private final RedissonLockService lockService;
    private final RedissonLockMetrics lockMetrics;
    @Qualifier("lockExperimentExecutor")
    private final AsyncTaskExecutor lockExperimentExecutor;

    public RedissonWatchdogResponse watchdog(RedissonWatchdogRequest request) {
        String owner = lockService.resolveOwner(request.owner());
        RLock lock = redissonClient.getLock(request.lockKey());
        long startedNanos = System.nanoTime();
        long startedMillis = System.currentTimeMillis();
        boolean acquired = false;
        boolean released = false;
        List<RedissonTtlSample> samples = new ArrayList<>();

        try {
            /**
                * tryLock(waitTime, ttl, timeUnit) 메서드
                - ttl을 설정하지 않으면 watchdog 이 작업시간이 길어질 때, ttl을 자동으로 연장
            **/
            acquired = lock.tryLock(0, TimeUnit.MILLISECONDS);
            lockMetrics.recordAcquire(MODE_RLOCK, acquired, System.nanoTime() - startedNanos);
            if (!acquired) {
                return watchdogResponse(request.lockKey(), owner, false, samples, false, startedNanos);
            }

            while (System.currentTimeMillis() - startedMillis < request.workMillis()) {
                sampleTtl(request.lockKey(), startedMillis, samples);
                sleep(Math.min(request.sampleIntervalMillis(), remainingMillis(request.workMillis(), startedMillis)));
            }
            sampleTtl(request.lockKey(), startedMillis, samples);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redisson watchdog 실험 중 인터럽트가 발생했습니다.", ex);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                released = true;
                lockMetrics.recordRelease(MODE_RLOCK, true);
            } else if (acquired) {
                lockMetrics.recordRelease(MODE_RLOCK, false);
            }
        }

        return watchdogResponse(request.lockKey(), owner, acquired, samples, released, startedNanos);
    }

    /**
     * lease time보다 작업이 오래 걸리면 기존 owner가 아직 작업 중이어도 다른 요청이 락을 잡을 수 있다
     */
    public RedissonLeaseExpirationResponse leaseExpiration(RedissonLeaseExpirationRequest request) {
        String owner = lockService.resolveOwner(request.owner());
        RLock lock = redissonClient.getLock(request.lockKey());
        long startedNanos = System.nanoTime();
        boolean acquired = false;
        boolean competitorAcquired = false;
        boolean releaseAfterWork = false;

        try {
            acquired = lock.tryLock(0, request.leaseMillis(), TimeUnit.MILLISECONDS);
            lockMetrics.recordAcquire(MODE_RLOCK, acquired, System.nanoTime() - startedNanos);
            if (!acquired) {
                return leaseResponse(request.lockKey(), owner, false, false, -2, false, false, startedNanos);
            }

            sleep(request.workMillis());
            long ttlAfterWork = lockService.pttl(request.lockKey());
            boolean existsAfterWork = ttlAfterWork != -2;
            competitorAcquired = tryAcquireAsCompetitor(request.lockKey());

            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                releaseAfterWork = true;
            }
            lockMetrics.recordRelease(MODE_RLOCK, releaseAfterWork);
            return leaseResponse(
                    request.lockKey(),
                    owner,
                    true,
                    existsAfterWork,
                    ttlAfterWork,
                    competitorAcquired,
                    releaseAfterWork,
                    startedNanos
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redisson lease expiration 실험 중 인터럽트가 발생했습니다.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Redisson competitor 락 획득 실험을 실행하지 못했습니다.", ex);
        }
    }

    public RedissonMultiLockResponse multiLock(RedissonMultiLockRequest request) {
        String owner = lockService.resolveOwner(request.owner());
        RLock[] locks = request.lockKeys().stream()
                .map(redissonClient::getLock)
                .toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(locks);
        long startedNanos = System.nanoTime();
        boolean acquired = false;
        boolean released = false;

        try {
            lockMetrics.incrementContentionAttempt(MODE_MULTI);
            if (request.leaseMillis() == null) {
                acquired = multiLock.tryLock(request.waitMillis(), TimeUnit.MILLISECONDS);
            } else {
                acquired = multiLock.tryLock(request.waitMillis(), request.leaseMillis(), TimeUnit.MILLISECONDS);
            }
            lockMetrics.recordAcquire(MODE_MULTI, acquired, System.nanoTime() - startedNanos);
            List<Long> ttlSamples = request.lockKeys().stream()
                    .map(lockService::pttl)
                    .toList();

            if (acquired) {
                sleep(request.workMillis());
                if (multiLock.isHeldByCurrentThread()) {
                    multiLock.unlock();
                    released = true;
                }
            }
            lockMetrics.recordRelease(MODE_MULTI, released);
            return new RedissonMultiLockResponse(
                    request.lockKeys(),
                    owner,
                    acquired,
                    released,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos),
                    ttlSamples
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redisson multi lock 실험 중 인터럽트가 발생했습니다.", ex);
        }
    }

    private void sampleTtl(String lockKey, long startedMillis, List<RedissonTtlSample> samples) {
        long ttlMillis = lockService.pttl(lockKey);
        lockMetrics.recordWatchdogTtl(ttlMillis);
        samples.add(new RedissonTtlSample(System.currentTimeMillis() - startedMillis, ttlMillis));
    }

    private long remainingMillis(long workMillis, long startedMillis) {
        return Math.max(0, workMillis - (System.currentTimeMillis() - startedMillis));
    }

    private RedissonWatchdogResponse watchdogResponse(
            String lockKey,
            String owner,
            boolean acquired,
            List<RedissonTtlSample> samples,
            boolean released,
            long startedNanos
    ) {
        return new RedissonWatchdogResponse(
                lockKey,
                owner,
                acquired,
                samples,
                released,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
        );
    }

    private RedissonLeaseExpirationResponse leaseResponse(
            String lockKey,
            String owner,
            boolean acquired,
            boolean existsAfterWork,
            long ttlMillisAfterWork,
            boolean competitorAcquired,
            boolean releaseAfterWork,
            long startedNanos
    ) {
        return new RedissonLeaseExpirationResponse(
                lockKey,
                owner,
                acquired,
                existsAfterWork,
                ttlMillisAfterWork,
                competitorAcquired,
                releaseAfterWork,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
        );
    }

    private void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    private boolean tryAcquireAsCompetitor(String lockKey) throws InterruptedException, ExecutionException {
        Future<Boolean> future = lockExperimentExecutor.submit(() -> {
            RLock competitor = redissonClient.getLock(lockKey);
            boolean acquired = competitor.tryLock(0, 500, TimeUnit.MILLISECONDS);
            if (acquired) {
                competitor.unlock();
            }
            return acquired;
        });
        return future.get();
    }
}
