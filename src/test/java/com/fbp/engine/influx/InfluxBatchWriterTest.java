package com.fbp.engine.influx;

import com.fbp.engine.metrics.MetricPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class InfluxBatchWriterTest {

    // ── 로컬 버퍼 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("InfluxDB에 도달할 수 없으면 로컬 버퍼에 포인트가 쌓인다")
    void test1() throws InterruptedException {
        InfluxWriterConfig config = new InfluxWriterConfig(
                "http://localhost:1", // 연결 불가
                "token", "org", "bucket",
                10, 200, 1, 10, 1000
        );
        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            writer.accept(new MetricPoint("m", Map.of(), Map.of("f", 1L), 1000L));
            Thread.sleep(600); // flush(200ms) + 1회 재시도(10ms) 대기
            assertTrue(writer.getLocalBufferSize() > 0 || writer.getTotalFailed() > 0,
                    "실패한 포인트가 로컬 버퍼 또는 failed 카운터에 반영되어야 함");
        }
    }

    @Test
    @DisplayName("LocalBuffer는 maxSize 초과 시 오래된 라인을 폐기한다")
    void test2() {
        LocalBuffer buffer = new LocalBuffer(3);
        buffer.add("line1");
        buffer.add("line2");
        buffer.add("line3");
        buffer.add("line4"); // line1이 드롭됨

        assertEquals(3, buffer.size());
        assertEquals(1, buffer.getDroppedCount());

        List<String> drained = buffer.drainBatch(10);
        assertFalse(drained.contains("line1"), "오래된 line1이 폐기되어야 함");
        assertTrue(drained.contains("line4"));
    }

    @Test
    @DisplayName("LocalBuffer.drainBatch는 요청한 수만큼만 꺼낸다")
    void test3() {
        LocalBuffer buffer = new LocalBuffer(100);
        for (int i = 0; i < 10; i++) buffer.add("line" + i);

        List<String> batch = buffer.drainBatch(3);
        assertEquals(3, batch.size());
        assertEquals(7, buffer.size());
    }

    @Test
    @DisplayName("accept()는 pendingQueue가 가득 차도 블로킹하지 않는다")
    void test4() throws InterruptedException {
        InfluxWriterConfig config = new InfluxWriterConfig(
                "http://localhost:1", "t", "o", "b",
                1, 60_000, 1, 10, 100 // 매우 긴 flush 간격으로 큐가 쌓이게
        );
        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                writer.accept(new MetricPoint("m", Map.of(), Map.of("f", (long) i), i));
            }
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 500, "1000개 accept()가 500ms 미만이어야 함 (실제: " + elapsed + "ms)");
        }
    }

    // ── 배치 전송 (임베디드 HTTP 서버) ─────────────────────────────────────────

    @Test
    @DisplayName("batchSize에 도달하면 즉시 flush되어 HTTP 요청이 전송된다")
    void test5() throws Exception {
        List<String> receivedBodies = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v2/write", exchange -> {
            byte[] compressed = exchange.getRequestBody().readAllBytes();
            String body = new String(new GZIPInputStream(new ByteArrayInputStream(compressed)).readAllBytes(),
                    StandardCharsets.UTF_8);
            receivedBodies.add(body);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            latch.countDown();
        });
        server.start();

        InfluxWriterConfig config = new InfluxWriterConfig(
                "http://localhost:" + server.getAddress().getPort(),
                "test-token", "fbp", "fbp-metrics",
                2, 10_000, 3, 50, 1000
        );
        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            writer.accept(new MetricPoint("node_stats", Map.of("node_id", "n1"), Map.of("in_count", 10L), 1000L));
            writer.accept(new MetricPoint("node_stats", Map.of("node_id", "n2"), Map.of("in_count", 20L), 2000L));

            assertTrue(latch.await(3, TimeUnit.SECONDS), "3초 안에 HTTP 요청이 도착해야 함");
        }
        server.stop(0);

        assertFalse(receivedBodies.isEmpty(), "HTTP 요청이 최소 1회 전송되어야 함");
        String body = receivedBodies.get(0);
        assertTrue(body.contains("node_stats"), "line protocol에 measurement가 포함되어야 함");
        assertTrue(body.contains("in_count=10i"), "in_count=10i가 포함되어야 함");
    }

    @Test
    @DisplayName("HTTP 204 응답 시 totalWritten이 증가한다")
    void test6() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v2/write", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        InfluxWriterConfig config = new InfluxWriterConfig(
                "http://localhost:" + server.getAddress().getPort(),
                "token", "fbp", "fbp-metrics",
                1, 10_000, 3, 50, 1000
        );
        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            writer.accept(new MetricPoint("flow_stats", Map.of("flow_id", "f1"), Map.of("processed", 5L), 100L));
            Thread.sleep(500);
            assertTrue(writer.getTotalWritten() > 0, "totalWritten이 0보다 커야 함");
        }
        server.stop(0);
    }

    // ── 통합 테스트 (실제 InfluxDB 필요) ────────────────────────────────────────

    @Test
    @Tag("integration")
    @DisplayName("[통합] 실제 InfluxDB에 데이터를 적재한다")
    void integrationTest() throws InterruptedException {
        InfluxWriterConfig config = InfluxWriterConfig.defaults(
                "http://localhost:8086",
                "iot-lab-super-secret-auth-token",
                "iot-lab", "fbp-metrics"
        );
        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            for (int i = 0; i < 5; i++) {
                writer.accept(new MetricPoint(
                        "node_stats",
                        Map.of("flow_id", "integration-flow", "node_id", "test-node"),
                        Map.of("in_count", (long) i, "avg_time_ms", 0.5),
                        System.currentTimeMillis() * 1_000_000L
                ));
            }
            Thread.sleep(2000);
            assertTrue(writer.getTotalWritten() > 0, "InfluxDB에 데이터가 적재되어야 함");
        }
    }
}