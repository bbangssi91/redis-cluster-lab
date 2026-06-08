package com.example.redisclusterlab.cluster.replication;

import com.example.redisclusterlab.cluster.topology.ClusterNode;

public record ReplicaRead(ClusterNode replica, String value, String error) {
}
