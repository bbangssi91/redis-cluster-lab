package com.example.redisclusterlab.cluster.common;

import java.util.List;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisClusterConnectionProvider {

    private final List<String> configuredNodes;
    private final RedisClusterClient redisClusterClient;
    private final StatefulRedisClusterConnection<String, String> clusterConnection;
    private final RedisAdvancedClusterCommands<String, String> clusterCommands;

    public RedisClusterConnectionProvider(@Value("${spring.data.redis.cluster.nodes}") List<String> configuredNodes) {
        this.configuredNodes = configuredNodes;
        this.redisClusterClient = RedisClusterClient.create(configuredNodes.stream()
                .map(this::redisUri)
                .toList());
        this.clusterConnection = redisClusterClient.connect();
        this.clusterCommands = clusterConnection.sync();
    }

    public List<String> configuredNodes() {
        return configuredNodes;
    }

    public StatefulRedisClusterConnection<String, String> connection() {
        return clusterConnection;
    }

    public RedisAdvancedClusterCommands<String, String> commands() {
        return clusterCommands;
    }

    @PreDestroy
    void close() {
        clusterConnection.close();
        redisClusterClient.shutdown();
    }

    private RedisURI redisUri(String node) {
        String[] hostAndPort = node.split(":");
        if (hostAndPort.length != 2) {
            throw new IllegalArgumentException("Invalid Redis node: " + node);
        }
        return RedisURI.create(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
    }
}
