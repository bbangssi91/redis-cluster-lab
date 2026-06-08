package com.example.redisclusterlab.lock.dto;

import java.util.List;

// 전체 경합 실험의 집계와 개별 시도 기록을 함께 반환한다.
public record LockContendResponse(
        String lockKey,
        int workers,
        int attemptsPerWorker,
        int totalAttempts,
        long successCount,
        long failureCount,
        long releaseSuccessCount,
        long releaseFailureCount,
        // 실제로 락을 잡은 owner 목록만 모아 경합 승자를 빠르게 확인
        List<String> observedOwners,
        List<String> observedTokens,
        long elapsedMillis,
        // 학습용 실험이라 요약뿐 아니라 개별 시도 기록도 모두 반환
        List<LockAttemptObservation> attempts
) {
}
