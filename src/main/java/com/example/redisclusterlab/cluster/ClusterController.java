package com.example.redisclusterlab.cluster;

import java.util.List;
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

    public record ConfiguredNodesResponse(List<String> nodes) {
    }

    public record RawClusterCommandResponse(String result) {
    }

    public record ClusterSlotsResponse(List<Object> slots) {
    }

    public record KeySlotResponse(String key, Long slot) {
    }

    public record KeyValueRequest(String key, String value) {
    }

    public record KeyValueResponse(String key, String value) {
    }
}
