package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricEvent;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.WireMetricEvent;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * BlockingQueue 기반 로컬 Connection 구현체.
 *
 * <pre>
 * 동일 JVM 내에서 노드 간 메시지를 전달한다.
 * deliver()는 큐가 찰 때까지 블로킹(자연스러운 백프레셔).
 * poll()은 메시지가 도착할 때까지 블로킹.
 * </pre>
 */
public class LocalConnection implements Connection {

    static final int DEFAULT_CAPACITY = 100;

    private final String id;
    private final LinkedBlockingQueue<Message> buffer;
    private final MetricsCollector collector; // nullable
    private final String flowId;              // nullable

    public LocalConnection(String id) {
        this(id, DEFAULT_CAPACITY, null, null);
    }

    public LocalConnection(String id, int capacity) {
        this(id, capacity, null, null);
    }

    public LocalConnection(String id, MetricsCollector collector, String flowId) {
        this(id, DEFAULT_CAPACITY, collector, flowId);
    }

    LocalConnection(String id, int capacity, MetricsCollector collector, String flowId) {
        this.id        = id;
        this.buffer    = new LinkedBlockingQueue<>(capacity);
        this.collector = collector;
        this.flowId    = flowId;
    }

    @Override
    public void deliver(Message message) {
        if (collector != null) {
            collector.record(new WireMetricEvent(flowId, id, 0L, buffer.size(), false, message));
        }
        try {
            buffer.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Message poll() throws InterruptedException {
        return buffer.take();
    }

    @Override
    public int getBufferSize() {
        return buffer.size();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
