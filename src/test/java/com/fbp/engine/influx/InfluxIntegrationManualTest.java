package com.fbp.engine.influx;

import com.fbp.engine.metrics.MetricPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 InfluxDB(iot-influxdb 컨테이너)를 대상으로 하는 수동 통합 테스트.
 * 기본 Surefire 실행에는 포함되지 않는다(@Tag 없음으로 항상 포함).
 *
 * 실행: mvn test -Dtest=InfluxIntegrationManualTest
 */
class InfluxIntegrationManualTest {

    private static final String URL    = "http://localhost:8086";
    private static final String TOKEN  = "fbp-admin-token-please-change";
    private static final String ORG    = "fbp";
    private static final String BUCKET = "fbp-metrics";

    @Test
    @DisplayName("[수동통합] InfluxBatchWriter가 실제 InfluxDB에 node_stats를 적재한다")
    void writeNodeStats() throws InterruptedException {
        InfluxWriterConfig config = InfluxWriterConfig.defaults(URL, TOKEN, ORG, BUCKET);

        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            long tsBase = System.currentTimeMillis() * 1_000_000L;
            for (int i = 0; i < 5; i++) {
                writer.accept(new MetricPoint(
                        "node_stats",
                        Map.of("flow_id", "fbp-test-flow", "node_id", "test-node-" + i),
                        Map.of("in_count", (long) (i * 10), "avg_time_ms", 0.5 + i * 0.1),
                        tsBase + i * 1_000_000L
                ));
            }
            Thread.sleep(2000); // flush 대기
            assertTrue(writer.getTotalWritten() > 0,
                    "InfluxDB에 데이터가 적재되어야 함 (written=" + writer.getTotalWritten() + ")");
            System.out.println("✓ totalWritten=" + writer.getTotalWritten()
                    + ", failed=" + writer.getTotalFailed());
        }
    }

    @Test
    @DisplayName("[수동통합] InfluxBatchWriter가 실제 InfluxDB에 wire_stats를 적재한다")
    void writeWireStats() throws InterruptedException {
        InfluxWriterConfig config = InfluxWriterConfig.defaults(URL, TOKEN, ORG, BUCKET);

        try (InfluxBatchWriter writer = new InfluxBatchWriter(config)) {
            long ts = System.currentTimeMillis() * 1_000_000L;
            writer.accept(new MetricPoint(
                    "wire_stats",
                    Map.of("flow_id", "fbp-test-flow", "wire_id", "src.out-sink.in"),
                    Map.of("delivered", 100L, "queue_size", 3L, "dropped", 0L),
                    ts
            ));
            Thread.sleep(2000);
            assertTrue(writer.getTotalWritten() > 0,
                    "wire_stats 적재 실패 (written=" + writer.getTotalWritten() + ")");
        }
    }
}