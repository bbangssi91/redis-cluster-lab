package com.example.redisclusterlab.lock.dto;

import jakarta.validation.constraints.NotBlank;

// 락 해제 요청은 잘못된 owner가 다른 락을 지우지 못하도록 token을 필수로 받는다.
public record ReleaseLockRequest(
        @NotBlank(message = "lockKey는 필수입니다.")
        String lockKey,
        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
