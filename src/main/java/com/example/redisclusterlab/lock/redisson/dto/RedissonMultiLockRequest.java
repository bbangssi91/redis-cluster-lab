package com.example.redisclusterlab.lock.redisson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record RedissonMultiLockRequest(
        @NotEmpty(message = "lockKeys는 비어 있을 수 없습니다.")
        List<@NotBlank(message = "lockKey는 비어 있을 수 없습니다.") String> lockKeys,
        String owner,
        @PositiveOrZero(message = "waitMillis는 0 이상이어야 합니다.")
        long waitMillis,
        @Positive(message = "leaseMillis는 0보다 커야 합니다.")
        Long leaseMillis,
        @PositiveOrZero(message = "workMillis는 0 이상이어야 합니다.")
        long workMillis
) {
}
