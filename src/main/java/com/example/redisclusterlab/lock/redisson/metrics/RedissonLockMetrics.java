package com.example.redisclusterlab.lock.redisson.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RedissonLockMetrics {

    private static final String CLIENT = "redisson";

    private final MeterRegistry meterRegistry;
    private final Timer rlockAcquireDuration;
    private final Timer multiAcquireDuration;
    private final Timer aopAcquireDuration;
    private final DistributionSummary watchdogTtlSamples;
    private final Counter rlockContentionAttempts;
    private final Counter multiContentionAttempts;
    private final Counter aopContentionAttempts;

    public RedissonLockMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.rlockAcquireDuration = acquireTimer("rlock");
        this.multiAcquireDuration = acquireTimer("multi");
        this.aopAcquireDuration = acquireTimer("aop");
        this.watchdogTtlSamples = DistributionSummary.builder("redis.lock.watchdog.ttl")
                .tags(Tags.of("client", CLIENT, "sample", "pttl"))
                .description("Redisson watchdog lock TTL samples")
                .register(meterRegistry);
        this.rlockContentionAttempts = contentionCounter("rlock");
        this.multiContentionAttempts = contentionCounter("multi");
        this.aopContentionAttempts = contentionCounter("aop");
    }

    public void recordAcquire(String mode, boolean acquired, long durationNanos) {
        Counter.builder("redis.lock.acquire")
                .tags(Tags.of("client", CLIENT, "mode", mode, "result", acquired ? "success" : "failure"))
                .register(meterRegistry)
                .increment();
        timer(mode).record(Duration.ofNanos(durationNanos));
    }

    public void recordRelease(String mode, boolean released) {
        Counter.builder("redis.lock.release")
                .tags(Tags.of("client", CLIENT, "mode", mode, "result", released ? "success" : "failure"))
                .register(meterRegistry)
                .increment();
    }

    public void recordWatchdogTtl(long ttlMillis) {
        if (ttlMillis >= 0) {
            watchdogTtlSamples.record(ttlMillis);
        }
    }

    public void incrementContentionAttempt(String mode) {
        if ("multi".equals(mode)) {
            multiContentionAttempts.increment();
        } else if ("aop".equals(mode)) {
            aopContentionAttempts.increment();
        } else {
            rlockContentionAttempts.increment();
        }
    }

    private Timer acquireTimer(String mode) {
        return Timer.builder("redis.lock.acquire.duration")
                .tags(Tags.of("client", CLIENT, "mode", mode))
                .description("Redisson Redis lock acquire latency")
                .register(meterRegistry);
    }

    private Counter contentionCounter(String mode) {
        return Counter.builder("redis.lock.contention.attempts")
                .tags(Tags.of("client", CLIENT, "mode", mode))
                .description("Redisson Redis lock contention attempts")
                .register(meterRegistry);
    }

    private Timer timer(String mode) {
        if ("multi".equals(mode)) {
            return multiAcquireDuration;
        }
        if ("aop".equals(mode)) {
            return aopAcquireDuration;
        }
        return rlockAcquireDuration;
    }
}
