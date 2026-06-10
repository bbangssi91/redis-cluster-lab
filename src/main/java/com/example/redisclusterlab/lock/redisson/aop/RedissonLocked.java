package com.example.redisclusterlab.lock.redisson.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedissonLocked {

    String key();

    long waitMillis() default 0;

    long leaseMillis() default 0;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
