package com.example.redisclusterlab.lock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// TTL 만료 이후 락 상태와 늦은 release 결과를 관찰하기 위한 요청이다.
public record LockTtlExpirationRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        @Positive(message = "ttlMillis는 0보다 커야 합니다.")
        long ttlMillis,
        // 생략하면 ttlMillis보다 조금 더 기다려 만료 이후 상태를 기본으로 관찰
        @Positive(message = "waitMillis는 0보다 커야 합니다.")
        Long waitMillis,
        String owner
) {
}
