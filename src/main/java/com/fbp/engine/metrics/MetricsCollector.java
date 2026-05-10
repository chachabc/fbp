package com.fbp.engine.metrics;

import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 다층 메트릭 수집기.
 *
 * <pre>
 * Hot path:  record()가 work queue에 Runnable을 offer()하고 즉시 반환 (블로킹 없음)
 * Cold path: drainThread가 queue를 소비하며 NodeStats / WireStats / FlowStats 갱신
 *            scheduler가 10초마다 tick()을 enqueue → drain thread가 스냅샷 후 PointSink 발행
 *            scheduler가 1m/1h/1d마다 windowClose를 enqueue → drain thread가 도메인 집계 발행
 * </pre>
 *
 * 모든 내부 집계 자료구조는 drainThread 단독 접근이므로 별도 동기화 없음.
 * queue가 가득 차면 이벤트를 드롭하고 droppedEventCount를 증가시킨다.
 */
public class MetricsCollector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    private static final int WORK_QUEUE_CAPACITY = 65536;
    private static final long MAX_PROC_TIME_NS = TimeUnit.SECONDS.toNanos(10);

    // ── 큐 (hot path) ─────────────────────────────────────────────────────────
    private final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY);
    private final LongAdder droppedEventCount = new LongAdder();

    // ── 집계 자료구조 (drain thread 전용) ──────────────────────────────────────
    private final Map<String, NodeStats>  nodeStatsMap  = new HashMap<>(); // key: flowId|nodeId
    private final Map<String, WireStats>  wireStatsMap  = new HashMap<>(); // key: wireId
    private final Map<String, FlowStats>  flowStatsMap  = new HashMap<>(); // key: flowId
    private final Map<String, WireSource> wireSourceMap = new HashMap<>(); // wireId → source info
    private final Map<String, List<DomainMetricDefinition>> domainDefsByFlow = new HashMap<>();
    private final Map<String, TumblingBucket> buckets   = new HashMap<>(); // flowId|name|window

    // ── 출력 / 생명주기 ────────────────────────────────────────────────────────
    private final PointSink sink;
    private final long tickIntervalMs;
    private volatile boolean running = true;
    private Thread drainThread;
    private ScheduledExecutorService scheduler;

    /** 마지막 tick 시점의 스냅샷 — drain thread가 쓰고, 외부(CLI 등)가 읽는다. */
    private volatile MetricsSnapshot lastSnapshot = MetricsSnapshot.EMPTY;

    // ── 생성자 ──────────────────────────────────────────────────────────────────

    public MetricsCollector(PointSink sink) {
        this(sink, 10_000L);
    }

    /** 테스트용: tick 주기를 짧게 설정할 수 있다. */
    MetricsCollector(PointSink sink, long tickIntervalMs) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.tickIntervalMs = tickIntervalMs;
    }

    // ── 생명주기 ─────────────────────────────────────────────────────────────────

    public void start() {
        drainThread = Thread.ofVirtual()
                .name("fbp-metrics-drain")
                .start(this::drainLoop);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fbp-metrics-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                () -> enqueue(this::tick),
                tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> enqueue(() -> closeWindows(60_000L, "1m")),
                60_000, 60_000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> enqueue(() -> closeWindows(3_600_000L, "1h")),
                3_600_000, 3_600_000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> enqueue(() -> closeWindows(86_400_000L, "1d")),
                86_400_000, 86_400_000, TimeUnit.MILLISECONDS);

        log.info("MetricsCollector 시작 (tick={}ms)", tickIntervalMs);
    }

    @Override
    public void close() {
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        if (drainThread != null) {
            drainThread.interrupt();
            try { drainThread.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // ── 공개 API (hot path) ──────────────────────────────────────────────────

    public void record(MetricEvent event) {
        enqueue(() -> dispatch(event));
    }

    public void registerWire(String wireId, String flowId, String sourceNodeId, String sourcePort) {
        enqueue(() -> wireSourceMap.put(wireId, new WireSource(flowId, sourceNodeId, sourcePort)));
    }

    public void registerFlow(String flowId, List<DomainMetricDefinition> defs) {
        if (defs == null || defs.isEmpty()) return;
        List<DomainMetricDefinition> copy = List.copyOf(defs);
        enqueue(() -> {
            domainDefsByFlow.put(flowId, copy);
            for (DomainMetricDefinition def : copy) {
                for (String window : def.windows()) {
                    String key = flowId + "|" + def.name() + "|" + window;
                    buckets.computeIfAbsent(key, k -> new TumblingBucket(windowMs(window)));
                }
            }
        });
    }

    public void unregisterFlow(String flowId) {
        enqueue(() -> {
            domainDefsByFlow.remove(flowId);
            wireSourceMap.entrySet().removeIf(e -> e.getValue().flowId().equals(flowId));
            nodeStatsMap.entrySet().removeIf(e -> e.getKey().startsWith(flowId + "|"));
            wireStatsMap.entrySet().removeIf(e -> {
                WireSource src = wireSourceMap.get(e.getKey());
                return src != null && src.flowId().equals(flowId);
            });
            flowStatsMap.remove(flowId);
            buckets.entrySet().removeIf(e -> e.getKey().startsWith(flowId + "|"));
        });
    }

    public long getDroppedEventCount() {
        return droppedEventCount.sum();
    }

    /** 마지막 tick에서 캡처된 메트릭 스냅샷을 반환한다. tick 주기가 경과하기 전에는 EMPTY일 수 있다. */
    public MetricsSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    /** 등록된 도메인 메트릭 정의를 반환한다. key = flowId. */
    public Map<String, List<DomainMetricDefinition>> getDomainDefs() {
        return Map.copyOf(domainDefsByFlow);
    }

    // ── 테스트 지원 ──────────────────────────────────────────────────────────────

    /**
     * 큐의 모든 대기 항목을 동기적으로 처리한 후 tick을 수행한다.
     * 테스트 전용 — 운영 환경에서는 drainThread와 scheduler가 담당한다.
     */
    void processAllPending() {
        Runnable work;
        while ((work = workQueue.poll()) != null) {
            work.run();
        }
        tick();
    }

    // ── 내부 — 큐 처리 ──────────────────────────────────────────────────────────

    private void enqueue(Runnable work) {
        if (!workQueue.offer(work)) {
            droppedEventCount.increment();
        }
    }

    private void drainLoop() {
        while (running) {
            try {
                Runnable work = workQueue.poll(100, TimeUnit.MILLISECONDS);
                if (work != null) work.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Runnable work;
        while ((work = workQueue.poll()) != null) {
            work.run();
        }
    }

    private void dispatch(MetricEvent event) {
        switch (event) {
            case NodeMetricEvent e -> processNode(e);
            case WireMetricEvent e -> processWire(e);
        }
    }

    // ── 내부 — 이벤트 처리 (drain thread) ────────────────────────────────────────

    private void processNode(NodeMetricEvent e) {
        NodeStats ns = nodeStatsMap.computeIfAbsent(e.flowId() + "|" + e.nodeId(), k -> new NodeStats());
        ns.inCount.increment();
        if (e.hadError()) ns.errors.increment();
        ns.histogram.recordValue(Math.min(e.processingTimeNs(), MAX_PROC_TIME_NS));

        FlowStats fs = flowStatsMap.computeIfAbsent(e.flowId(), k -> new FlowStats());
        fs.processed.increment();
        if (e.hadError()) fs.errors.increment();
    }

    private void processWire(WireMetricEvent e) {
        WireStats ws = wireStatsMap.computeIfAbsent(e.wireId(), k -> new WireStats());
        ws.delivered.increment();
        ws.payloadBytes.add(e.payloadBytes());
        ws.queueSize.set(e.queueSize());
        if (e.dropped()) ws.dropped.increment();

        WireSource src = wireSourceMap.get(e.wireId());
        if (src != null) {
            NodeStats ns = nodeStatsMap.computeIfAbsent(src.flowId() + "|" + src.sourceNodeId(), k -> new NodeStats());
            ns.outCount.increment();

            if (e.message() != null) {
                extractDomainMetrics(src, e.message());
            }
        }
    }

    private void extractDomainMetrics(WireSource src, com.fbp.engine.message.Message message) {
        List<DomainMetricDefinition> defs = domainDefsByFlow.get(src.flowId());
        if (defs == null) return;
        for (DomainMetricDefinition def : defs) {
            if (!def.sourceNodeId().equals(src.sourceNodeId())) continue;
            if (!def.sourcePort().equals(src.sourcePort())) continue;
            Object raw = message.get(def.field());
            if (!(raw instanceof Number num)) continue;
            double value = num.doubleValue();
            for (String window : def.windows()) {
                String key = src.flowId() + "|" + def.name() + "|" + window;
                TumblingBucket bucket = buckets.computeIfAbsent(key, k -> new TumblingBucket(windowMs(window)));
                bucket.record(value);
            }
        }
    }

    // ── 내부 — tick / 윈도우 (drain thread) ──────────────────────────────────────

    private void tick() {
        long tsNs = MetricPoint.nowNs();

        nodeStatsMap.forEach((key, ns) -> {
            String[] parts = key.split("\\|", 2);
            String flowId = parts[0], nodeId = parts[1];
            long p99Ns  = ns.histogram.getTotalCount() > 0 ? ns.histogram.getValueAtPercentile(99.0) : 0L;
            double avgMs = ns.histogram.getTotalCount() > 0 ? ns.histogram.getMean() / 1_000_000.0 : 0.0;
            ns.histogram.reset();
            ns.lastAvgMs = avgMs;
            ns.lastP99Ms = p99Ns / 1_000_000.0;

            sink.accept(new MetricPoint(
                    "node_stats",
                    Map.of("flow_id", flowId, "node_id", nodeId),
                    Map.of(
                            "in_count",    ns.inCount.sum(),
                            "out_count",   ns.outCount.sum(),
                            "errors",      ns.errors.sum(),
                            "avg_time_ms", avgMs,
                            "p99_time_ms", p99Ns / 1_000_000.0
                    ),
                    tsNs
            ));
        });

        wireStatsMap.forEach((wireId, ws) -> {
            WireSource src = wireSourceMap.get(wireId);
            Map<String, String> tags = src != null
                    ? Map.of("flow_id", src.flowId(), "wire_id", wireId)
                    : Map.of("wire_id", wireId);
            sink.accept(new MetricPoint(
                    "wire_stats",
                    tags,
                    Map.of(
                            "delivered",     ws.delivered.sum(),
                            "payload_bytes", ws.payloadBytes.sum(),
                            "queue_size",    ws.queueSize.get(),
                            "dropped",       ws.dropped.sum()
                    ),
                    tsNs
            ));
        });

        flowStatsMap.forEach((flowId, fs) -> sink.accept(new MetricPoint(
                "flow_stats",
                Map.of("flow_id", flowId),
                Map.of("processed", fs.processed.sum(), "errors", fs.errors.sum()),
                tsNs
        )));

        log.debug("Metrics tick — nodes:{}, wires:{}, dropped:{}", nodeStatsMap.size(), wireStatsMap.size(), droppedEventCount.sum());

        // CLI가 읽을 수 있도록 스냅샷 갱신 (drain thread에서만 호출되므로 동기화 불필요)
        Map<String, MetricsSnapshot.NodeStatsSnapshot> nodeSnap = new HashMap<>();
        nodeStatsMap.forEach((k, ns) -> nodeSnap.put(k, new MetricsSnapshot.NodeStatsSnapshot(
                ns.inCount.sum(), ns.outCount.sum(), ns.errors.sum(),
                ns.lastAvgMs, ns.lastP99Ms)));

        Map<String, MetricsSnapshot.WireStatsSnapshot> wireSnap = new HashMap<>();
        wireStatsMap.forEach((k, ws) -> wireSnap.put(k, new MetricsSnapshot.WireStatsSnapshot(
                ws.delivered.sum(), ws.payloadBytes.sum(), ws.queueSize.get(), ws.dropped.sum())));

        Map<String, MetricsSnapshot.FlowStatsSnapshot> flowSnap = new HashMap<>();
        flowStatsMap.forEach((k, fs) -> flowSnap.put(k, new MetricsSnapshot.FlowStatsSnapshot(
                fs.processed.sum(), fs.errors.sum())));

        lastSnapshot = new MetricsSnapshot(
                Collections.unmodifiableMap(nodeSnap),
                Collections.unmodifiableMap(wireSnap),
                Collections.unmodifiableMap(flowSnap));
    }

    private void closeWindows(long windowDurationMs, String windowLabel) {
        long now = System.currentTimeMillis();
        buckets.forEach((key, bucket) -> {
            if (bucket.windowDurationMs() != windowDurationMs) return;
            if (!bucket.shouldClose(now)) return;
            String[] parts = key.split("\\|", 3);
            String flowId = parts[0], sensorName = parts[1];
            MetricPoint point = bucket.closeAndReset(
                    "sensor_stats_" + windowLabel,
                    Map.of("flow_id", flowId, "sensor_name", sensorName)
            );
            if (point != null) sink.accept(point);
        });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private static long windowMs(String window) {
        return switch (window) {
            case "1m" -> 60_000L;
            case "1h" -> 3_600_000L;
            case "1d" -> 86_400_000L;
            default -> throw new IllegalArgumentException("알 수 없는 윈도우: " + window);
        };
    }

    // ── 내부 자료구조 ──────────────────────────────────────────────────────────────

    record WireSource(String flowId, String sourceNodeId, String sourcePort) {}

    static class NodeStats {
        final LongAdder inCount  = new LongAdder();
        final LongAdder outCount = new LongAdder();
        final LongAdder errors   = new LongAdder();
        final Histogram histogram = new Histogram(MAX_PROC_TIME_NS, 3);
        double lastAvgMs = 0.0;
        double lastP99Ms = 0.0;
    }

    static class WireStats {
        final LongAdder  delivered    = new LongAdder();
        final LongAdder  payloadBytes = new LongAdder();
        final AtomicLong queueSize    = new AtomicLong();
        final LongAdder  dropped      = new LongAdder();
    }

    static class FlowStats {
        final LongAdder processed = new LongAdder();
        final LongAdder errors    = new LongAdder();
    }

    static class TumblingBucket {
        private double sum  = 0.0;
        private double min  = Double.MAX_VALUE;
        private double max  = -Double.MAX_VALUE;
        private long   count = 0;
        private final long durationMs;
        private long windowStartMs = System.currentTimeMillis();

        TumblingBucket(long durationMs) { this.durationMs = durationMs; }

        void record(double value) {
            sum += value;
            if (value < min) min = value;
            if (value > max) max = value;
            count++;
        }

        boolean shouldClose(long nowMs) { return nowMs >= windowStartMs + durationMs; }
        long windowDurationMs()         { return durationMs; }

        MetricPoint closeAndReset(String measurement, Map<String, String> tags) {
            MetricPoint point = null;
            if (count > 0) {
                point = new MetricPoint(
                        measurement,
                        tags,
                        Map.of("avg", sum / count, "min", min, "max", max, "count", count),
                        MetricPoint.nowNs()
                );
            }
            sum = 0.0; min = Double.MAX_VALUE; max = -Double.MAX_VALUE; count = 0;
            windowStartMs = System.currentTimeMillis();
            return point;
        }
    }
}