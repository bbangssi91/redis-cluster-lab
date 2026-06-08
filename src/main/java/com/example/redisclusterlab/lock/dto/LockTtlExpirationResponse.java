package com.example.redisclusterlab.lock.dto;

import java.time.Instant;

// TTL이 지난 뒤 key가 사라졌는지, 늦은 release가 실패하는지 보여준다.
public record LockTtlExpirationResponse(
        String lockKey,
        String owner,
        String token,
        boolean acquired,
        long ttlMillis,
        long waitMillis,
        boolean existsAfterWait,
        long ttlMillisAfterWait,
        // TTL 만료 뒤 같은 token으로 release했을 때 false가 되어야 safe release가 지켜진다.
        boolean releaseAfterWait,
        Instant acquiredAt,
        Instant inspectedAt
) {
}
