package com.example.redisclusterlab.cluster.topology;

import java.util.List;
import java.util.Set;

public record ClusterNode(
        String id,
        String endpoint,
        String host,
        int port,
        Set<String> flags,
        boolean master,
        boolean replica,
        String masterId,
        boolean connected,
        List<SlotRange> slots
) {
    public boolean isReplicaOf(String nodeId) {
        return replica && nodeId.equals(masterId);
    }

    boolean owns(long slot) {
        return slots.stream().anyMatch(range -> range.contains(slot));
    }
}
