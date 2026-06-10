package com.example.redisclusterlab.lock.redisson.config;

import java.util.List;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonClusterConfig {

    private static final String REDIS_SCHEME = "redis://";

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.cluster.nodes}") List<String> configuredNodes) {
        Config config = new Config();
        config.useClusterServers()
                .addNodeAddress(configuredNodes.stream()
                        .map(node -> REDIS_SCHEME + node)
                        .toArray(String[]::new));
        return Redisson.create(config);
    }
}
