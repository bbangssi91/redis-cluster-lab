package com.example.redisclusterlab.cluster.replication;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.example.redisclusterlab.cluster.common.RedisClusterConnectionProvider;
import com.example.redisclusterlab.cluster.topology.ClusterNode;
import com.example.redisclusterlab.cluster.topology.ClusterTopologyParser;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// master write 이후 WAIT ack와 replica direct read를 비교해 Redis Cluster 복제 지연과 가시성을 관찰한다.
public class ReplicationProbeService {

    private final StringRedisTemplate redisTemplate;
    private final RedisClusterConnectionProvider connectionProvider;
    private final ClusterTopologyParser topologyParser;

    public ReplicationProbeResult probe(String key, String value, int replicas, long timeoutMillis) {
        List<ClusterNode> nodes = topologyParser.parse(connectionProvider.commands().clusterNodes());
        long slot = connectionProvider.commands().clusterKeyslot(key);
        ClusterNode master = topologyParser.findMasterForSlot(nodes, slot)
                .orElseThrow(() -> new IllegalStateException("No master owns slot " + slot));

        RedisCommands<String, String> masterCommands = nodeCommands(master);

        Instant startedAt = Instant.now();
        masterCommands.set(key, value);
        Long acknowledgedReplicas = masterCommands.waitForReplication(replicas, timeoutMillis);
        Instant completedAt = Instant.now();

        List<ReplicaRead> replicaReads = nodes.stream()
                .filter(node -> node.isReplicaOf(master.id()))
                .sorted(Comparator.comparing(ClusterNode::endpoint))
                .map(node -> readFromReplica(node, key))
                .toList();

        return new ReplicationProbeResult(
                key,
                value,
                slot,
                master,
                replicas,
                timeoutMillis,
                acknowledgedReplicas,
                redisTemplate.opsForValue().get(key),
                replicaReads,
                startedAt,
                completedAt
        );
    }

    private ReplicaRead readFromReplica(ClusterNode replica, String key) {
        try {
            RedisCommands<String, String> replicaCommands = nodeCommands(replica);
            replicaCommands.readOnly();
            return new ReplicaRead(replica, replicaCommands.get(key), null);
        } catch (RuntimeException ex) {
            return new ReplicaRead(replica, null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private RedisCommands<String, String> nodeCommands(ClusterNode node) {
        StatefulRedisConnection<String, String> nodeConnection =
                connectionProvider.connection().getConnection(node.host(), node.port());
        return nodeConnection.sync();
    }
}
