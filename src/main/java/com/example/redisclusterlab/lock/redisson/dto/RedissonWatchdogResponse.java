package com.example.redisclusterlab.lock.redisson.dto;

import java.util.List;

public record RedissonWatchdogResponse(
        String lockKey,
        String owner,
        boolean acquired,
        List<RedissonTtlSample> ttlSamples,
        boolean released,
        long elapsedMillis
) {
}
