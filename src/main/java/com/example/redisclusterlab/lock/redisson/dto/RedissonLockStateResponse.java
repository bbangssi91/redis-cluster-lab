package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonLockStateResponse(
        String lockKey,
        boolean locked,
        boolean heldByCurrentThread,
        long ttlMillis,
        String localOwner
) {
}
