package com.example.redisclusterlab.lock.dto;

import java.time.Instant;

// 락 획득 결과와 생성된 owner token을 함께 반환해 후속 release 실험에 사용한다.
public record AcquireLockResponse(
        String lockKey,
        // release 시 저장된 token과 비교하는 소유권 증명 값
        String token,
        boolean acquired,
        long ttlMillis,
        String owner,
        Instant acquiredAt
) {
}
