package com.fbp.engine.metrics;

public record NodeMetricEvent(
        String flowId,
        String nodeId,
        long processingTimeNs,
        boolean hadError
) implements MetricEvent {}
