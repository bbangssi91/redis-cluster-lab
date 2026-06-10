package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;

public record RedissonReleaseRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        String owner
) {
}
