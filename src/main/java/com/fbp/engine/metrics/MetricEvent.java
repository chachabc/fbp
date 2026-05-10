package com.fbp.engine.metrics;

public sealed interface MetricEvent
        permits NodeMetricEvent, WireMetricEvent {}
