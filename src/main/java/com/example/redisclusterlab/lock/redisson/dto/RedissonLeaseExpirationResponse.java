package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonLeaseExpirationResponse(
        String lockKey,
        String owner,
        boolean acquired,
        boolean existsAfterWork,
        long ttlMillisAfterWork,
        boolean competitorAcquired,
        boolean releaseAfterWork,
        long elapsedMillis
) {
}
