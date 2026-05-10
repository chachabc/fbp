package com.fbp.engine.metrics;

@FunctionalInterface
public interface PointSink {
    void accept(MetricPoint point);
}
