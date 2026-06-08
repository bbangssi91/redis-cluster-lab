package com.example.redisclusterlab.cluster.api;

import com.example.redisclusterlab.cluster.application.ClusterCommandService;
import com.example.redisclusterlab.cluster.dto.ClusterSlotsResponse;
import com.example.redisclusterlab.cluster.dto.ClusterTopologyResponse;
import com.example.redisclusterlab.cluster.dto.ConfiguredNodesResponse;
import com.example.redisclusterlab.cluster.dto.KeySlotResponse;
import com.example.redisclusterlab.cluster.dto.KeyValueRequest;
import com.example.redisclusterlab.cluster.dto.KeyValueResponse;
import com.example.redisclusterlab.cluster.dto.RawClusterCommandResponse;
import com.example.redisclusterlab.cluster.dto.ReplicationProbeRequest;
import com.example.redisclusterlab.cluster.replication.ReplicationProbeResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cluster")
public class ClusterController {

    private final ClusterCommandService clusterCommandService;

    public ClusterController(ClusterCommandService clusterCommandService) {
        this.clusterCommandService = clusterCommandService;
    }

    @GetMapping("/configured-nodes")
    public ConfiguredNodesResponse configuredNodes() {
        return new ConfiguredNodesResponse(clusterCommandService.configuredNodes());
    }

    @GetMapping("/nodes")
    public RawClusterCommandResponse nodes() {
        return new RawClusterCommandResponse(clusterCommandService.clusterNodes());
    }

    @GetMapping("/slots")
    public ClusterSlotsResponse slots() {
        return new ClusterSlotsResponse(clusterCommandService.clusterSlots());
    }

    @GetMapping("/topology")
    public ClusterTopologyResponse topology() {
        return new ClusterTopologyResponse(clusterCommandService.topology());
    }

    @GetMapping("/keyslot")
    public KeySlotResponse keySlot(@RequestParam String key) {
        return new KeySlotResponse(key, clusterCommandService.keySlot(key));
    }

    @PostMapping("/values")
    public ResponseEntity<Void> setValue(@RequestBody KeyValueRequest request) {
        clusterCommandService.setValue(request.key(), request.value());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/values")
    public KeyValueResponse getValue(@RequestParam String key) {
        return new KeyValueResponse(key, clusterCommandService.getValue(key));
    }

    @PostMapping("/replication/probe")
    public ReplicationProbeResult replicationProbe(@RequestBody ReplicationProbeRequest request) {
        return clusterCommandService.replicationProbe(
                request.key(),
                request.value(),
                request.replicas(),
                request.timeoutMillis()
        );
    }
}
