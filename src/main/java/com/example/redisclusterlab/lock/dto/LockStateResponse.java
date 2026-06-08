package com.example.redisclusterlab.lock.dto;

// 현재 Redis에 저장된 token과 남은 TTL을 조회한 결과다.
public record LockStateResponse(
        String lockKey,
        boolean exists,
        // 학습용 상태 조회라 token을 노출해 safe release 동작을 직접 확인
        String token,
        long ttlMillis
) {
}
