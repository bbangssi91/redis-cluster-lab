package com.example.redisclusterlab.cluster.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ClusterTopologyParser {

    public List<ClusterNode> parse(String rawClusterNodes) {
        return rawClusterNodes.lines()
                .filter(line -> !line.isBlank())
                .map(this::parseNode)
                .toList();
    }

    public Optional<ClusterNode> findMasterForSlot(List<ClusterNode> nodes, long slot) {
        return nodes.stream()
                .filter(ClusterNode::master)
                .filter(node -> node.owns(slot))
                .findFirst();
    }

    private ClusterNode parseNode(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 8) {
            throw new IllegalStateException("Unexpected CLUSTER NODES line: " + line);
        }

        String endpoint = parts[1].split("@")[0];
        String[] hostAndPort = endpoint.split(":");
        if (hostAndPort.length != 2) {
            throw new IllegalStateException("Unexpected Redis endpoint: " + endpoint);
        }

        Set<String> flags = Set.of(parts[2].split(","));
        return new ClusterNode(
                parts[0],
                endpoint,
                hostAndPort[0],
                Integer.parseInt(hostAndPort[1]),
                flags,
                flags.contains("master"),
                flags.contains("slave") || flags.contains("replica"),
                "-".equals(parts[3]) ? null : parts[3],
                "connected".equals(parts[7]),
                parseSlots(parts)
        );
    }

    private List<SlotRange> parseSlots(String[] parts) {
        List<SlotRange> slots = new ArrayList<>();
        for (int i = 8; i < parts.length; i++) {
            String token = parts[i];
            if (token.startsWith("[") || token.contains("->-") || token.contains("-<-")) {
                continue;
            }
            if (token.contains("-")) {
                String[] range = token.split("-");
                slots.add(new SlotRange(Long.parseLong(range[0]), Long.parseLong(range[1])));
            } else {
                long slot = Long.parseLong(token);
                slots.add(new SlotRange(slot, slot));
            }
        }
        return slots;
    }
}
