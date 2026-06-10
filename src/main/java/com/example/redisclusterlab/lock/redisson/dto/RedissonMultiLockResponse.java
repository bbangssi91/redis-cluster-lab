package com.example.redisclusterlab.lock.redisson.dto;

import java.util.List;

public record RedissonMultiLockResponse(
        List<String> lockKeys,
        String owner,
        boolean acquired,
        boolean released,
        long elapsedMillis,
        List<Long> ttlMillisAfterAcquire
) {
}
