package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonTtlSample(
        long elapsedMillis,
        long ttlMillis
) {
}
