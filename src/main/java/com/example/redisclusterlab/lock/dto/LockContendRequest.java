package com.example.redisclusterlab.lock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// 여러 worker가 같은 Redis key를 두고 동시에 락을 시도하는 경합 실험 요청이다.
public record LockContendRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        // 동시에 출발시킬 worker 수
        @Positive(message = "workers는 0보다 커야 합니다.")
        @Max(value = 128, message = "workers는 128 이하여야 합니다.")
        int workers,
        @Positive(message = "attemptsPerWorker는 0보다 커야 합니다.")
        int attemptsPerWorker,
        @Positive(message = "ttlMillis는 0보다 커야 합니다.")
        long ttlMillis,
        // 실제 업무 시간을 흉내 내며, ttlMillis보다 길면 락 만료 한계를 관찰할 수 있다.
        @PositiveOrZero(message = "workMillis는 0 이상이어야 합니다.")
        long workMillis
) {
}
