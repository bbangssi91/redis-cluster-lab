package com.example.redisclusterlab.lock.application;

import com.example.redisclusterlab.lock.dto.AcquireLockRequest;
import com.example.redisclusterlab.lock.dto.AcquireLockResponse;
import com.example.redisclusterlab.lock.dto.LockAttemptObservation;
import com.example.redisclusterlab.lock.dto.LockContendRequest;
import com.example.redisclusterlab.lock.dto.LockContendResponse;
import com.example.redisclusterlab.lock.dto.LockStateResponse;
import com.example.redisclusterlab.lock.dto.LockTtlExpirationRequest;
import com.example.redisclusterlab.lock.dto.LockTtlExpirationResponse;
import com.example.redisclusterlab.lock.dto.ReleaseLockRequest;
import com.example.redisclusterlab.lock.dto.ReleaseLockResponse;
import com.example.redisclusterlab.lock.metrics.LettuceLockMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class LettuceLockExperimentService {

    private final LettuceLockService lockService;
    private final LettuceLockMetrics lockMetrics;
    private final AsyncTaskExecutor lockExperimentExecutor;
    private final String defaultOwner;

    public LettuceLockExperimentService(
            LettuceLockService lockService,
            LettuceLockMetrics lockMetrics,
            @Qualifier("lockExperimentExecutor") AsyncTaskExecutor lockExperimentExecutor,
            @Value("${APP_INSTANCE_NAME:local}") String defaultOwner
    ) {
        this.lockService = lockService;
        this.lockMetrics = lockMetrics;
        this.lockExperimentExecutor = lockExperimentExecutor;
        this.defaultOwner = defaultOwner;
    }

    // 모든 worker를 대기시킨 뒤 동시에 출발시켜 같은 key에 대한 경합을 재현한다.
    public LockContendResponse contend(LockContendRequest request) {
        CountDownLatch startGate = new CountDownLatch(1);
        Instant startedAt = Instant.now();

        try {
            List<Future<LockAttemptObservation>> futures = submitAttempts(request, startGate);
            startGate.countDown();
            List<LockAttemptObservation> attempts = collectAttempts(futures);
            attempts.sort(Comparator.comparing(LockAttemptObservation::startedAt));
            return toContendResponse(request, attempts, startedAt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 경합 실험 중 인터럽트가 발생했습니다.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("락 경합 실험을 실행하지 못했습니다.", ex);
        }
    }

    // TTL보다 오래 기다린 뒤 release를 시도해, 만료된 락의 늦은 해제가 실패하는지 관찰한다.
    public LockTtlExpirationResponse ttlExpiration(LockTtlExpirationRequest request) {
        long waitMillis = request.waitMillis() == null ? request.ttlMillis() + 100 : request.waitMillis();
        AcquireLockResponse acquired = lockService.acquire(new AcquireLockRequest(
                request.lockKey(),
                request.ttlMillis(),
                request.owner()
        ));

        sleep(waitMillis);
        Instant inspectedAt = Instant.now();
        LockStateResponse stateAfterWait = lockService.state(request.lockKey());
        ReleaseLockResponse releaseAfterWait =
                lockService.release(new ReleaseLockRequest(request.lockKey(), acquired.token()));

        return new LockTtlExpirationResponse(
                request.lockKey(),
                acquired.owner(),
                acquired.token(),
                acquired.acquired(),
                request.ttlMillis(),
                waitMillis,
                stateAfterWait.exists(),
                stateAfterWait.ttlMillis(),
                releaseAfterWait.released(),
                acquired.acquiredAt(),
                inspectedAt
        );
    }

    private List<Future<LockAttemptObservation>> submitAttempts(
            LockContendRequest request,
            CountDownLatch startGate
    ) {
        List<Future<LockAttemptObservation>> futures = new ArrayList<>();
        for (int worker = 1; worker <= request.workers(); worker++) {
            for (int attempt = 1; attempt <= request.attemptsPerWorker(); attempt++) {
                String owner = defaultOwner + "-worker-" + worker + "-attempt-" + attempt;
                futures.add(lockExperimentExecutor.submit(lockAttempt(request, owner, startGate)));
            }
        }
        return futures;
    }

    // Future 수집을 한 곳에 모아 contend 흐름에서 동시성 준비/수집/집계를 분리한다.
    private List<LockAttemptObservation> collectAttempts(List<Future<LockAttemptObservation>> futures)
            throws Exception {
        List<LockAttemptObservation> attempts = new ArrayList<>();
        for (Future<LockAttemptObservation> future : futures) {
            attempts.add(future.get());
        }
        return attempts;
    }

    // 개별 worker는 acquire 성공 시에만 workMillis만큼 작업을 흉내 낸 뒤 release를 시도한다.
    private Callable<LockAttemptObservation> lockAttempt(
            LockContendRequest request,
            String owner,
            CountDownLatch startGate
    ) {
        return () -> {
            startGate.await();
            lockMetrics.incrementContentionAttempt();
            Instant startedAt = Instant.now();
            AcquireLockResponse acquire = null;
            try {
                acquire = lockService.acquire(new AcquireLockRequest(request.lockKey(), request.ttlMillis(), owner));
                boolean released = releaseAfterWork(request, acquire);
                return observation(owner, acquire, released, startedAt, null);
            } catch (RuntimeException ex) {
                return observation(owner, acquire, false, startedAt,
                        ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        };
    }

    // TTL보다 workMillis가 길면 release가 false가 될 수 있고, 이 값이 락 한계를 보여준다.
    private boolean releaseAfterWork(LockContendRequest request, AcquireLockResponse acquire) {
        if (!acquire.acquired()) {
            return false;
        }
        sleep(request.workMillis());
        return lockService.release(new ReleaseLockRequest(request.lockKey(), acquire.token())).released();
    }

    // 성공/실패를 같은 응답 형태로 남겨 경합 실험 결과를 한 번에 비교할 수 있게 한다.
    private LockAttemptObservation observation(
            String owner,
            AcquireLockResponse acquire,
            boolean released,
            Instant startedAt,
            String error
    ) {
        return new LockAttemptObservation(
                owner,
                acquire == null ? null : acquire.token(),
                acquire != null && acquire.acquired(),
                released,
                startedAt,
                Instant.now(),
                error
        );
    }

    // 관찰 기록을 집계해 성공/실패 수와 실제 owner token 목록을 만든다.
    private LockContendResponse toContendResponse(
            LockContendRequest request,
            List<LockAttemptObservation> attempts,
            Instant startedAt
    ) {
        long successCount = attempts.stream().filter(LockAttemptObservation::acquired).count();
        long releaseSuccessCount = attempts.stream().filter(LockAttemptObservation::released).count();
        long releaseFailureCount = attempts.stream()
                .filter(LockAttemptObservation::acquired)
                .filter(attempt -> !attempt.released())
                .count();
        List<String> observedOwners = attempts.stream()
                .filter(LockAttemptObservation::acquired)
                .map(LockAttemptObservation::owner)
                .distinct()
                .toList();
        List<String> observedTokens = attempts.stream()
                .filter(LockAttemptObservation::acquired)
                .map(LockAttemptObservation::token)
                .distinct()
                .toList();

        return new LockContendResponse(
                request.lockKey(),
                request.workers(),
                request.attemptsPerWorker(),
                request.workers() * request.attemptsPerWorker(),
                successCount,
                attempts.size() - successCount,
                releaseSuccessCount,
                releaseFailureCount,
                observedOwners,
                observedTokens,
                Duration.between(startedAt, Instant.now()).toMillis(),
                attempts
        );
    }

    // Thread.sleep은 실험용 작업 시간과 TTL 만료 대기를 명시적으로 재현하기 위해 사용한다.
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대기 중 인터럽트가 발생했습니다.", ex);
        }
    }
}
