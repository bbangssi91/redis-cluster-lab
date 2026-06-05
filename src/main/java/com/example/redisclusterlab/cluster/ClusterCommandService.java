package com.example.redisclusterlab.cluster;

import java.util.List;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClusterCommandService {

    private final StringRedisTemplate redisTemplate;
    private final List<String> clusterNodes;
    private final RedisClusterClient redisClusterClient;
    private final StatefulRedisClusterConnection<String, String> clusterConnection;
    private final RedisAdvancedClusterCommands<String, String> clusterCommands;

    public ClusterCommandService(
            StringRedisTemplate redisTemplate,
            @Value("${spring.data.redis.cluster.nodes}") List<String> clusterNodes
    ) {
        this.redisTemplate = redisTemplate;
        this.clusterNodes = clusterNodes;
        this.redisClusterClient = RedisClusterClient.create(clusterNodes.stream()
                .map(this::redisUri)
                .toList());
        this.clusterConnection = redisClusterClient.connect();
        this.clusterCommands = clusterConnection.sync();
    }

    public List<String> configuredNodes() {
        return clusterNodes;
    }

    public String clusterNodes() {
        return clusterCommands.clusterNodes();
    }

    public List<Object> clusterSlots() {
        return clusterCommands.clusterSlots().stream()
                .map(Object.class::cast)
                .toList();
    }

    public Long keySlot(String key) {
        return clusterCommands.clusterKeyslot(key);
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
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
