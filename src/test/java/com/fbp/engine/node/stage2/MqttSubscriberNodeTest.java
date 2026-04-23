package com.fbp.engine.node.stage2;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.CollectorNode;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2-8 — MqttSubscriberNode 테스트

 * 단위 테스트 (1~5): Broker 없이 실행 가능
 * 통합 테스트 (6~9): Mosquitto Broker 필요 — @Tag("integration")
 *   실행 방법: mvn test -Dgroups=integration
 */
class MqttSubscriberNodeTest {
    private static final Map<String, Object> BASE_CONFIG = Map.of(
            "brokerUrl", "tcp://localhost:1883",
            "clientId",  "test-subscriber",
            "topic",     "sensor/temp",
            "qos",       1
    );

    // ── 단위 테스트 (Broker 불필요) ───────────────────────────────────────────
    @Test
    @DisplayName("포트 구성")
    void test1() {
        MqttSubscriberNode node = new MqttSubscriberNode("sub-1", BASE_CONFIG);
        Assertions.assertNotNull(node.getOutputPort("out"));
    }

    @Test
    @DisplayName("초기 상태")
    void test2(){
        MqttSubscriberNode node = new MqttSubscriberNode("sub-2", BASE_CONFIG);
        Assertions.assertFalse(node.isConnected());
    }

    @Test
    @DisplayName("config 조회")
    void test3() {
        MqttSubscriberNode node = new MqttSubscriberNode("sub-3", BASE_CONFIG);
        Assertions.assertEquals("tcp://localhost:1883", node.getConfig("brokerUrl"));
        Assertions.assertEquals("sensor/temp", node.getConfig("topic"));
    }

    @Test
    @DisplayName("JSON -> Message 변환")
    void test4() {
        MqttSubscriberNode node = new MqttSubscriberNode("sub-4", BASE_CONFIG);
        byte[] payload = "{\"value\": 28.5, \"unit\": \"C\"}".getBytes(StandardCharsets.UTF_8);

        Message message = node.convertToFbpMessage("sensor/temp", payload);

        Assertions.assertEquals(28.5, ((Number) message.get("value")).doubleValue(), 0.001);
        Assertions.assertEquals("C", message.get("unit"));
        Assertions.assertEquals("sensor/temp", message.get("topic"));
        Assertions.assertNotNull(message.get("mqttTimestamp"));
    }

    @Test
    @DisplayName("JSON 파싱 처리 실패")
    void test5() {
        MqttSubscriberNode node = new MqttSubscriberNode("sub-5", BASE_CONFIG);
        byte[] payload = "not-json-payload".getBytes(StandardCharsets.UTF_8);

        Message message = node.convertToFbpMessage("sensor/temp", payload);

        Assertions.assertEquals("not-json-payload", message.get("rawPayload"));
        Assertions.assertNull(message.get("value"));
        Assertions.assertEquals("sensor/temp", message.get("topic"));
    }

    // ── 통합 테스트 (Mosquitto Broker 필요) ──────────────────────────────────
    @Test
    @Tag("integration")
    @DisplayName("Broker 연결 성공")
    void test6() {
        MqttSubscriberNode node = new MqttSubscriberNode("sub-6", BASE_CONFIG);
        node.initialize();
        Assertions.assertTrue(node.isConnected());
        node.shutdown();
    }

    @Test
    @Tag("integration")
    @DisplayName("메시지 수신")
    void test7() throws Exception {
        Map<String, Object> config = new HashMap<>(BASE_CONFIG);
        config.put("clientId", "sub-7");

        MqttSubscriberNode node = new MqttSubscriberNode("sub-7", config);
        CollectorNode collectorNode = new CollectorNode("collector");

        Connection connection = new Connection("sub-out");
        connection.setTarget(collectorNode.getInputPort("in"));
        node.getOutputPort("out").connect(connection);

        node.initialize();


        publishTestMessage("tcp://localhost:1883", "pub-7", "sensor/temp",
                "{\"value\": 25.0}");

        Thread.sleep(500);
        node.shutdown();

        List<Message> collected = collectorNode.getCollected();
        Assertions.assertFalse(collected.isEmpty());
        Assertions.assertEquals(25.0, ((Number) collected.getFirst().get("value"))
                .doubleValue(), 0.001);
    }

    @Test
    @Tag("integration")
    @DisplayName("토픽 정보 포함")
    void test8() throws Exception {
        Map<String, Object> config = new HashMap<>(BASE_CONFIG);
        config.put("clientId", "sub-8");

        MqttSubscriberNode subscriberNode = new MqttSubscriberNode("sub-8", config);
        CollectorNode collectorNode = new CollectorNode("collector");

        Connection connection = new Connection("sub-out");
        connection.setTarget(collectorNode.getInputPort("in"));
        subscriberNode.getOutputPort("out").connect(connection);

        subscriberNode.initialize();
        publishTestMessage("tcp://localhost:1883", "pub-8", "sensor/temp",
                "{\"value\": 30.0}");

        Thread.sleep(500);
        subscriberNode.shutdown();

        Assertions.assertFalse(collectorNode.getCollected().isEmpty());
        Assertions.assertEquals("sensor/temp", collectorNode.getCollected().getFirst()
                                .get("topic"));
    }

    @Test
    @Tag("integration")
    @DisplayName("shutdown 후 연결 해제")
    void test9() {
        Map<String, Object> config = new HashMap<>(BASE_CONFIG);
        config.put("clientId", "sub-9");

        MqttSubscriberNode subscriberNode = new MqttSubscriberNode("sub-9", config);
        subscriberNode.initialize();
        Assertions.assertTrue(subscriberNode.isConnected());

        subscriberNode.shutdown();
        Assertions.assertFalse(subscriberNode.isConnected());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * 통합 테스트에서 Broker에 메시지를 발행하는 헬퍼.
     * Publisher와 Subscriber의 clientId가 달라야 함에 주의.
     */
    private void publishTestMessage(String brokerUrl, String clientId,
                                    String topic, String payload) throws Exception {
        MqttClient publisher = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        publisher.connect(options);

        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        publisher.publish(topic, message);

        publisher.disconnect();
        publisher.close();
    }
}
