package com.fbp.engine.metrics;

import java.util.Map;

public record MetricPoint(
        String measurement,
        Map<String, String> tags,
        Map<String, Object> fields,
        long timestampNs
) {
    public static long nowNs() {
        return System.currentTimeMillis() * 1_000_000L;
    }
}
