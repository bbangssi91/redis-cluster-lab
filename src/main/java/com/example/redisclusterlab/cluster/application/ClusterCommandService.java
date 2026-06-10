package com.example.redisclusterlab.cluster.application;

import java.util.List;
import com.example.redisclusterlab.cluster.common.RedisClusterConnectionProvider;
import com.example.redisclusterlab.cluster.replication.ReplicationProbeResult;
import com.example.redisclusterlab.cluster.replication.ReplicationProbeService;
import com.example.redisclusterlab.cluster.topology.ClusterNode;
import com.example.redisclusterlab.cluster.topology.ClusterTopologyParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Redis Cluster 기본 동작을 REST API로 노출해 slot, topology, redirect, 단순 read/write 실험의 진입점으로 사용한다.
public class ClusterCommandService {

    private final StringRedisTemplate redisTemplate;
    private final RedisClusterConnectionProvider connectionProvider;
    private final ClusterTopologyParser topologyParser;
    private final ReplicationProbeService replicationProbeService;

    public List<String> configuredNodes() {
        return connectionProvider.configuredNodes();
    }

    public String clusterNodes() {
        return connectionProvider.commands().clusterNodes();
    }

    public List<Object> clusterSlots() {
        return connectionProvider.commands().clusterSlots().stream()
                .toList();
    }

    public List<ClusterNode> topology() {
        return topologyParser.parse(clusterNodes());
    }

    public Long keySlot(String key) {
        return connectionProvider.commands().clusterKeyslot(key);
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public ReplicationProbeResult replicationProbe(String key, String value, int replicas, long timeoutMillis) {
        return replicationProbeService.probe(key, value, replicas, timeoutMillis);
    }
}
