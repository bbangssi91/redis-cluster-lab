package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record RedissonLockRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        String owner,
        @PositiveOrZero(message = "waitMillis는 0 이상이어야 합니다.")
        long waitMillis,
        @Positive(message = "leaseMillis는 0보다 커야 합니다.")
        Long leaseMillis
) {
}
