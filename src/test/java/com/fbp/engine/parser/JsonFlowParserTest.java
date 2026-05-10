package com.fbp.engine.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonFlowParserTest {

    private JsonFlowParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonFlowParser();
    }

    // ── 정상 파싱 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효한 JSON을 파싱하면 FlowDefinition이 반환된다")
    void test1() {
        String json = """
                {
                  "id": "temperature-monitoring",
                  "nodes": [
                    {"id": "sensor", "type": "MqttSubscriber", "config": {"broker": "tcp://localhost:1883", "topic": "sensor/temp"}},
                    {"id": "rule",   "type": "ThresholdFilter", "config": {"field": "value", "threshold": 30}},
                    {"id": "alert",  "type": "MqttPublisher",  "config": {"broker": "tcp://localhost:1883", "topic": "alert/temp"}}
                  ],
                  "connections": [
                    {"from": "sensor:out", "to": "rule:in"},
                    {"from": "rule:out",   "to": "alert:in"}
                  ]
                }
                """;

        FlowDefinition flow = parser.parse(json);

        assertEquals("temperature-monitoring", flow.getId());
        assertEquals(3, flow.getNodes().size());
        assertEquals(2, flow.getConnections().size());
    }

    @Test
    @DisplayName("파싱된 노드의 id, type, config가 JSON과 일치한다")
    void test2() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [
                    {"id": "rule", "type": "ThresholdFilter", "config": {"field": "value", "threshold": 30, "enabled": true}}
                  ],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);
        NodeDefinition node = flow.getNodes().get(0);

        assertEquals("rule", node.getId());
        assertEquals("ThresholdFilter", node.getType());
        assertEquals("value", node.getConfig().get("field"));
        assertEquals(30, ((Number) node.getConfig().get("threshold")).intValue());
        assertEquals(true, node.getConfig().get("enabled"));
    }

    @Test
    @DisplayName("파싱된 연결의 from/to 정보가 JSON과 일치한다")
    void test3() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [
                    {"id": "sensor", "type": "Sensor", "config": {}},
                    {"id": "rule",   "type": "Filter", "config": {}}
                  ],
                  "connections": [
                    {"from": "sensor:out", "to": "rule:in"}
                  ]
                }
                """;

        FlowDefinition flow = parser.parse(json);
        ConnectionDefinition conn = flow.getConnections().get(0);

        assertEquals("sensor", conn.getSourceNodeId());
        assertEquals("out",    conn.getSourcePort());
        assertEquals("rule",   conn.getTargetNodeId());
        assertEquals("in",     conn.getTargetPort());
    }

    @Test
    @DisplayName("transport 섹션이 없으면 LOCAL 전송이 적용된다")
    void test4() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [{"id": "n", "type": "T", "config": {}}],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);

        assertTrue(flow.getTransport().isLocal());
    }

    @Test
    @DisplayName("transport.type=mqtt이면 MqttBridge 전송이 적용된다")
    void test5() {
        String json = """
                {
                  "id": "flow-1",
                  "transport": {"type": "mqtt", "broker": "tcp://localhost:1884", "qos": 1},
                  "nodes": [{"id": "n", "type": "T", "config": {}}],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);
        TransportDefinition transport = flow.getTransport();

        assertTrue(transport.isMqtt());
        assertEquals("tcp://localhost:1884", transport.getBroker());
        assertEquals(1, transport.getQos());
    }

    @Test
    @DisplayName("config가 없는 노드는 빈 config로 처리된다")
    void test6() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [{"id": "n", "type": "T"}],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);

        assertTrue(flow.getNodes().get(0).getConfig().isEmpty());
    }

    // ── 필수 필드 누락 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("플로우 id가 없으면 FlowParserException이 발생한다")
    void test7() {
        String json = """
                {
                  "nodes": [{"id": "n", "type": "T", "config": {}}],
                  "connections": []
                }
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("nodes 배열이 없으면 FlowParserException이 발생한다")
    void test8() {
        String json = """
                {"id": "flow-1", "connections": []}
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("nodes 배열이 비어있으면 FlowParserException이 발생한다")
    void test9() {
        String json = """
                {"id": "flow-1", "nodes": [], "connections": []}
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("잘못된 JSON 형식이면 FlowParserException이 발생한다")
    void test10() {
        assertThrows(FlowParserException.class, () -> parser.parse("{ not json"));
    }

    // ── 연결 유효성 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("연결에서 존재하지 않는 노드를 참조하면 FlowParserException이 발생한다")
    void test11() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [{"id": "sensor", "type": "T", "config": {}}],
                  "connections": [
                    {"from": "sensor:out", "to": "ghost:in"}
                  ]
                }
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("연결의 from 포맷이 잘못되면 FlowParserException이 발생한다")
    void test12() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [
                    {"id": "sensor", "type": "T", "config": {}},
                    {"id": "rule",   "type": "T", "config": {}}
                  ],
                  "connections": [{"from": "sensor", "to": "rule:in"}]
                }
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("중복된 노드 id가 있으면 FlowParserException이 발생한다")
    void test13() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [
                    {"id": "dup", "type": "A", "config": {}},
                    {"id": "dup", "type": "B", "config": {}}
                  ],
                  "connections": []
                }
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    @Test
    @DisplayName("transport.type=mqtt인데 broker가 없으면 FlowParserException이 발생한다")
    void test14() {
        String json = """
                {
                  "id": "flow-1",
                  "transport": {"type": "mqtt"},
                  "nodes": [{"id": "n", "type": "T", "config": {}}],
                  "connections": []
                }
                """;

        assertThrows(FlowParserException.class, () -> parser.parse(json));
    }

    // ── FlowDefinition 조회 ───────────────────────────────────────────────────

    @Test
    @DisplayName("findNode로 특정 노드 정의를 조회할 수 있다")
    void test15() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [
                    {"id": "sensor", "type": "Sensor", "config": {}},
                    {"id": "rule",   "type": "Filter", "config": {}}
                  ],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);

        assertTrue(flow.findNode("sensor").isPresent());
        assertEquals("Sensor", flow.findNode("sensor").get().getType());
        assertTrue(flow.findNode("ghost").isEmpty());
    }

    @Test
    @DisplayName("getNodes, getConnections는 수정 불가하다")
    void test16() {
        String json = """
                {
                  "id": "flow-1",
                  "nodes": [{"id": "n", "type": "T", "config": {}}],
                  "connections": []
                }
                """;

        FlowDefinition flow = parser.parse(json);

        assertThrows(UnsupportedOperationException.class,
                () -> flow.getNodes().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> flow.getConnections().clear());
    }
}