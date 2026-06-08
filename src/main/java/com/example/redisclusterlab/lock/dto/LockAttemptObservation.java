package com.example.redisclusterlab.lock.dto;

import java.time.Instant;

// 경합 실험에서 worker 한 번의 락 획득/해제 시도를 기록한다.
public record LockAttemptObservation(
        String owner,
        String token,
        boolean acquired,
        // acquired=true인데 released=false이면 TTL 만료 등으로 owner의 늦은 해제가 실패했음을 보여준다.
        boolean released,
        Instant startedAt,
        Instant completedAt,
        String error
) {
}
