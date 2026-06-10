package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record RedissonAopDemoRequest(
        @NotBlank(message = "resourceId는 필수입니다.")
        String resourceId,
        @PositiveOrZero(message = "workMillis는 0 이상이어야 합니다.")
        long workMillis
) {
}
