package com.fbp.engine.influx;

import com.fbp.engine.metrics.MetricPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LineProtocolEncoderTest {

    private LineProtocolEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new LineProtocolEncoder();
    }

    @Test
    @DisplayName("Long 필드는 'i' 접미사가 붙는다")
    void test1() {
        MetricPoint point = new MetricPoint(
                "node_stats",
                Map.of("node_id", "sensor"),
                Map.of("in_count", 42L),
                1000L
        );
        String line = encoder.encode(point);
        assertTrue(line.contains("in_count=42i"), "Long 필드: " + line);
    }

    @Test
    @DisplayName("Double 필드는 소수점 형태로 인코딩된다")
    void test2() {
        MetricPoint point = new MetricPoint(
                "node_stats",
                Map.of("node_id", "rule"),
                Map.of("avg_time_ms", 1.5),
                2000L
        );
        String line = encoder.encode(point);
        assertTrue(line.contains("avg_time_ms=1.5"), "Double 필드: " + line);
    }

    @Test
    @DisplayName("태그는 measurement와 공백 사이에 콤마로 이어진다")
    void test3() {
        MetricPoint point = new MetricPoint(
                "flow_stats",
                Map.of("flow_id", "f1"),
                Map.of("processed", 100L),
                3000L
        );
        String line = encoder.encode(point);
        assertTrue(line.startsWith("flow_stats,flow_id=f1 "), "태그 포맷: " + line);
    }

    @Test
    @DisplayName("태그가 없으면 measurement 바로 뒤에 공백이 온다")
    void test4() {
        MetricPoint point = new MetricPoint(
                "my_measure",
                Map.of(),
                Map.of("val", 1L),
                4000L
        );
        String line = encoder.encode(point);
        assertTrue(line.startsWith("my_measure "), "태그 없는 포맷: " + line);
    }

    @Test
    @DisplayName("타임스탬프가 라인 맨 끝에 붙는다")
    void test5() {
        long ts = 1_717_401_782_000_000_000L;
        MetricPoint point = new MetricPoint("m", Map.of(), Map.of("f", 1L), ts);
        String line = encoder.encode(point);
        assertTrue(line.endsWith(" " + ts), "타임스탬프: " + line);
    }

    @Test
    @DisplayName("String 필드는 큰따옴표로 감싼다")
    void test6() {
        MetricPoint point = new MetricPoint(
                "m",
                Map.of(),
                Map.of("label", "hello world"),
                5000L
        );
        String line = encoder.encode(point);
        assertTrue(line.contains("label=\"hello world\""), "String 필드: " + line);
    }

    @Test
    @DisplayName("태그 값의 공백은 백슬래시로 이스케이프된다")
    void test7() {
        MetricPoint point = new MetricPoint(
                "m",
                Map.of("tag key", "tag val"),
                Map.of("f", 1L),
                6000L
        );
        String line = encoder.encode(point);
        assertTrue(line.contains("tag\\ key=tag\\ val"), "공백 이스케이프: " + line);
    }

    @Test
    @DisplayName("여러 태그는 알파벳 순으로 정렬된다")
    void test8() {
        MetricPoint point = new MetricPoint(
                "wire_stats",
                Map.of("wire_id", "w1", "flow_id", "f1"),
                Map.of("delivered", 10L),
                7000L
        );
        String line = encoder.encode(point);
        // flow_id가 wire_id보다 알파벳 순으로 앞에 와야 한다
        int flowIdx = line.indexOf("flow_id=f1");
        int wireIdx = line.indexOf("wire_id=w1");
        assertTrue(flowIdx < wireIdx, "태그 정렬 순서: " + line);
    }

    @Test
    @DisplayName("Boolean 필드는 true/false로 인코딩된다")
    void test9() {
        MetricPoint point = new MetricPoint(
                "m",
                Map.of(),
                Map.of("active", true, "paused", false),
                8000L
        );
        String line = encoder.encode(point);
        assertTrue(line.contains("active=true") || line.contains("paused=false"),
                "Boolean 필드: " + line);
    }
}