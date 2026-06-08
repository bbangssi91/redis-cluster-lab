package com.example.redisclusterlab.lock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// 락 획득 요청은 Redis key와 TTL을 필수로 받고, owner는 생략하면 앱 인스턴스 이름을 사용한다.
public record AcquireLockRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        @Positive(message = "ttlMillis는 0보다 커야 합니다.")
        long ttlMillis,
        String owner
) {
}
