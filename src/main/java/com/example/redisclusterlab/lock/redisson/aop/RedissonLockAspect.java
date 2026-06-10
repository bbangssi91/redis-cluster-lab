package com.example.redisclusterlab.lock.redisson.aop;

import com.example.redisclusterlab.lock.redisson.metrics.RedissonLockMetrics;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
// @RedissonLocked가 붙은 비즈니스 메서드 앞뒤로 RLock 획득과 해제를 공통 적용하는 AOP 락 컴포넌트다.
public class RedissonLockAspect {

    private static final String MODE_AOP = "aop";

    private final RedissonClient redissonClient;
    private final RedissonLockKeyResolver keyResolver;
    private final RedissonLockMetrics lockMetrics;

    @Around("@annotation(com.example.redisclusterlab.lock.redisson.aop.RedissonLocked)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        RedissonLocked redissonLocked = redissonLocked(joinPoint);
        String lockKey = keyResolver.resolve(joinPoint, redissonLocked.key());
        RLock lock = redissonClient.getLock(lockKey);
        long startedNanos = System.nanoTime();
        boolean acquired = false;

        try {
            lockMetrics.incrementContentionAttempt(MODE_AOP);
            acquired = tryLock(lock, redissonLocked);
            lockMetrics.recordAcquire(MODE_AOP, acquired, System.nanoTime() - startedNanos);
            if (!acquired) {
                throw new RedissonLockAcquireException(lockKey, redissonLocked.waitMillis());
            }
            return joinPoint.proceed();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lockMetrics.recordAcquire(MODE_AOP, false, System.nanoTime() - startedNanos);
            throw new IllegalStateException("Redisson AOP 락 획득 중 인터럽트가 발생했습니다.", ex);
        } finally {
            if (acquired) {
                boolean released = unlockIfHeld(lock);
                lockMetrics.recordRelease(MODE_AOP, released);
            }
        }
    }

    private boolean tryLock(RLock lock, RedissonLocked redissonLocked) throws InterruptedException {
        TimeUnit timeUnit = redissonLocked.timeUnit();
        if (redissonLocked.leaseMillis() > 0) {
            return lock.tryLock(redissonLocked.waitMillis(), redissonLocked.leaseMillis(), timeUnit);
        }
        return lock.tryLock(redissonLocked.waitMillis(), timeUnit);
    }

    private boolean unlockIfHeld(RLock lock) {
        if (!lock.isHeldByCurrentThread()) {
            return false;
        }
        lock.unlock();
        return true;
    }

    private RedissonLocked redissonLocked(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RedissonLocked annotation = AnnotationUtils.findAnnotation(method, RedissonLocked.class);
        if (annotation == null) {
            throw new IllegalStateException("RedissonLocked annotation not found on method: " + method);
        }
        return annotation;
    }
}
