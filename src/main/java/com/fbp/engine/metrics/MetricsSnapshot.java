package com.fbp.engine.metrics;

import java.util.Map;

/**
 * MetricsCollector가 매 tick마다 생성하는 불변 스냅샷.
 * CLI 등 외부 컴포넌트가 스레드-안전하게 읽을 수 있다.
 *
 * key 형식:
 *   nodes — "flowId|nodeId"
 *   wires — wireId (e.g. "src.out-sink.in")
 *   flows — flowId
 */
public record MetricsSnapshot(
        Map<String, NodeStatsSnapshot> nodes,
        Map<String, WireStatsSnapshot> wires,
        Map<String, FlowStatsSnapshot> flows
) {
    public static final MetricsSnapshot EMPTY =
            new MetricsSnapshot(Map.of(), Map.of(), Map.of());

    public record NodeStatsSnapshot(
            long inCount, long outCount, long errors,
            double avgMs, double p99Ms) {}

    public record WireStatsSnapshot(
            long delivered, long payloadBytes,
            long queueSize, long dropped) {}

    public record FlowStatsSnapshot(long processed, long errors) {}
}
