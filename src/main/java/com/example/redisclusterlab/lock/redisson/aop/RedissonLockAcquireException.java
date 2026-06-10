package com.example.redisclusterlab.lock.redisson.aop;

public class RedissonLockAcquireException extends RuntimeException {

    private final String lockKey;
    private final long waitMillis;

    public RedissonLockAcquireException(String lockKey, long waitMillis) {
        super("Redisson 락을 획득하지 못했습니다. lockKey=" + lockKey + ", waitMillis=" + waitMillis);
        this.lockKey = lockKey;
        this.waitMillis = waitMillis;
    }

    public String lockKey() {
        return lockKey;
    }

    public long waitMillis() {
        return waitMillis;
    }
}
