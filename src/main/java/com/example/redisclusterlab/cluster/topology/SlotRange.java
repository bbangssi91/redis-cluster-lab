package com.example.redisclusterlab.cluster.topology;

public record SlotRange(long start, long end) {
    boolean contains(long slot) {
        return start <= slot && slot <= end;
    }
}
