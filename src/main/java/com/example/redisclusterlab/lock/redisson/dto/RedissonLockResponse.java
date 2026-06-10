package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonLockResponse(
        String lockKey,
        String owner,
        boolean acquired,
        long waitMillis,
        Long leaseMillis,
        long elapsedMillis,
        String threadName
) {
}
