package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonLockErrorResponse(
        String lockKey,
        long waitMillis,
        String message
) {
}
