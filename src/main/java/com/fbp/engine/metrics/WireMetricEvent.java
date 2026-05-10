package com.fbp.engine.metrics;

import com.fbp.engine.message.Message;

public record WireMetricEvent(
        String flowId,
        String wireId,
        long payloadBytes,
        int queueSize,
        boolean dropped,
        Message message
) implements MetricEvent {}
