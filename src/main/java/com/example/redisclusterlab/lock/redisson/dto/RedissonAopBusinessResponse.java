package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonAopBusinessResponse(
        String couponId,
        String userId,
        boolean issuedNow,
        String result,
        String threadName
) {
}
