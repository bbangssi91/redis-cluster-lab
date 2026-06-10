package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;

public record RedissonAopBusinessRequest(
        @NotBlank(message = "couponId는 필수입니다.")
        String couponId,
        @NotBlank(message = "userId는 필수입니다.")
        String userId
) {
}
