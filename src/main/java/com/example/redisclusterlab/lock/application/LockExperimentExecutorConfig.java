package com.example.redisclusterlab.lock.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LockExperimentExecutorConfig {

    // 락 경합 실험은 요청마다 스레드풀을 만들지 않고, 공유 executor에서 worker 작업을 실행한다.
    @Bean(name = "lockExperimentExecutor")
    public AsyncTaskExecutor lockExperimentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("lock-experiment-");
        executor.setCorePoolSize(32);
        executor.setMaxPoolSize(128);
        executor.setQueueCapacity(512);
        executor.initialize();
        return executor;
    }
}
