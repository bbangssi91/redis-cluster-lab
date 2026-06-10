package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record RedissonWatchdogRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        String owner,
        @Positive(message = "workMillis는 0보다 커야 합니다.")
        long workMillis,
        @Positive(message = "sampleIntervalMillis는 0보다 커야 합니다.")
        long sampleIntervalMillis
) {
}
