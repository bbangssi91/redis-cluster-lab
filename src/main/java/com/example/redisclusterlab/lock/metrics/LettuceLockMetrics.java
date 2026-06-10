package com.example.redisclusterlab.lock.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class LettuceLockMetrics {

    private static final String CLIENT = "lettuce";
    private static final String MODE = "single";

    private final MeterRegistry meterRegistry;
    private final Timer acquireDuration;
    private final Counter contentionAttempts;

    public LettuceLockMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.acquireDuration = Timer.builder("redis.lock.acquire.duration")
                .tags(Tags.of("client", CLIENT, "mode", MODE))
                .description("Lettuce Redis lock acquire latency")
                .register(meterRegistry);
        this.contentionAttempts = Counter.builder("redis.lock.contention.attempts")
                .tags(Tags.of("client", CLIENT, "mode", MODE))
                .description("Lettuce Redis lock contention attempts")
                .register(meterRegistry);
    }

    // acquire 성공/실패 카운터와 지연 시간을 함께 기록해 경합 상황을 Prometheus에서 볼 수 있게 한다.
    public void recordAcquire(boolean acquired, long durationNanos) {
        Counter.builder("redis.lock.acquire")
                .tags(Tags.of("client", CLIENT, "mode", MODE, "result", acquired ? "success" : "failure"))
                .register(meterRegistry)
                .increment();
        acquireDuration.record(Duration.ofNanos(durationNanos));
    }

    // 잘못된 token release도 failure metric으로 남겨 safe release 동작을 관찰한다.
    public void recordRelease(boolean released) {
        Counter.builder("redis.lock.release")
                .tags(Tags.of("client", CLIENT, "mode", MODE, "result", released ? "success" : "failure"))
                .register(meterRegistry)
                .increment();
    }

    // contend API가 만든 전체 락 획득 시도 수를 기록한다.
    public void incrementContentionAttempt() {
        contentionAttempts.increment();
    }
}
