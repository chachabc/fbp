package com.fbp.engine.metrics;

import java.util.List;

public record DomainMetricDefinition(
        String name,
        String sourceNodeId,
        String sourcePort,
        String field,
        List<String> windows
) {}
