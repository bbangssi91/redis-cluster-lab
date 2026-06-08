package com.example.redisclusterlab.cluster.dto;

import java.util.List;
import com.example.redisclusterlab.cluster.topology.ClusterNode;

public record ClusterTopologyResponse(List<ClusterNode> nodes) {
}
