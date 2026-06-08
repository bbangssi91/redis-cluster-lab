package com.example.redisclusterlab.cluster.dto;

public record ReplicationProbeRequest(String key, String value, int replicas, long timeoutMillis) {
    public ReplicationProbeRequest {
        if (replicas <= 0) {
            replicas = 1;
        }
        if (timeoutMillis <= 0) {
            timeoutMillis = 1000;
        }
    }
}
