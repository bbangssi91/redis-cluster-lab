package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonAopDemoResponse(
        String resourceId,
        long workMillis,
        String threadName
) {
}
