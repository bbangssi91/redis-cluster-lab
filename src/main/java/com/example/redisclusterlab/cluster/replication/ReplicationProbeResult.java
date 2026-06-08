package com.example.redisclusterlab.cluster.replication;

import java.time.Instant;
import java.util.List;
import com.example.redisclusterlab.cluster.topology.ClusterNode;

public record ReplicationProbeResult(
        String key,
        String value,
        long slot,
        ClusterNode master,
        int requestedReplicas,
        long timeoutMillis,
        Long acknowledgedReplicas,
        String clusterReadValue,
        List<ReplicaRead> replicaReads,
        Instant startedAt,
        Instant completedAt
) {
}
