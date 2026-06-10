package com.example.redisclusterlab.lock.redisson.dto;

public record RedissonReleaseResponse(
        String lockKey,
        String owner,
        boolean released,
        boolean knownLocalOwner,
        String releaseMode
) {
}
