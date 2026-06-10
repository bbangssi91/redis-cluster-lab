package com.example.redisclusterlab.lock.redisson.application;

import com.example.redisclusterlab.lock.redisson.aop.RedissonLocked;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopBusinessResponse;
import com.example.redisclusterlab.lock.redisson.dto.RedissonAopDemoResponse;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
// 실제 비즈니스 메서드가 @RedissonLocked 하나로 보호되는 사용 방식을 보여주는 AOP 기반 락 데모 서비스다.
public class RedissonAopDemoService {

    private final Set<String> issuedCoupons = ConcurrentHashMap.newKeySet();

    @RedissonLocked(
            key = "'phase5:redisson:aop:' + #resourceId",
            waitMillis = 200,
            leaseMillis = 2000
    )
    public RedissonAopDemoResponse runLocked(String resourceId, long workMillis) {
        sleep(workMillis);
        return new RedissonAopDemoResponse(resourceId, workMillis, Thread.currentThread().getName());
    }

    @RedissonLocked(
            key = "'phase5:redisson:coupon:' + #couponId + ':' + #userId",
            waitMillis = 1000,
            leaseMillis = 5000
    )
    public RedissonAopBusinessResponse issueCoupon(String couponId, String userId) {
        String issueKey = couponId + ":" + userId;

        // 실무에서는 이 영역에 DB unique constraint, 재고 차감, 발급 이력 저장 같은 critical section이 들어간다.
        boolean issuedNow = issuedCoupons.add(issueKey);

        return new RedissonAopBusinessResponse(
                couponId,
                userId,
                issuedNow,
                issuedNow ? "ISSUED" : "ALREADY_ISSUED",
                Thread.currentThread().getName()
        );
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AOP demo 작업 중 인터럽트가 발생했습니다.", ex);
        }
    }
}
