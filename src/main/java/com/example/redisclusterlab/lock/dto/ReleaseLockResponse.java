package com.example.redisclusterlab.lock.dto;

// Lua safe release가 실제로 key를 삭제했는지 알려준다.
public record ReleaseLockResponse(
        String lockKey,
        boolean released
) {
}
