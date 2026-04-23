package com.fbp.engine.node.stage2;

import com.fbp.engine.message.Message;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class MqttPublisherTest {
    private static final Map<String, Object> BASE_CONFIG = Map.of(
            "brokerUrl", "tcp://localhost:1883",
            "clientId",  "test-publisher",
            "topic",     "alert/temp",
            "qos",       1,
            "retained",  false
    );

    @Test
    @DisplayName("포트 구성")
    void test1() {
        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-1", BASE_CONFIG);
        Assertions.assertNotNull(publisherNode.getInputPort("in"));
    }

    @Test
    @DisplayName("초기 상태")
    void test2() {
        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-2", BASE_CONFIG);
        Assertions.assertFalse(publisherNode.isConnected());
    }

    @Test
    @DisplayName("config 기본 토픽 조회")
    void test3() {
        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-3", BASE_CONFIG);
        Assertions.assertEquals("alert/temp", publisherNode.getConfig("topic"));
    }

    @Test
    @Tag("integration")
    @DisplayName("Broker 연결 성공")
    void test4() {
        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-4", BASE_CONFIG);
        publisherNode.initialize();
        Assertions.assertTrue(publisherNode.isConnected());
        publisherNode.shutdown();
    }

    @Test
    @Tag("integration")
    @DisplayName("메시지 발행")
    void test5() throws Exception {
        Map<String, Object> pubConfig = new HashMap<>(BASE_CONFIG);
        pubConfig.put("clientId", "pub-5");

        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-5", pubConfig);
        publisherNode.initialize();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        MqttClient subscriber = createSubscriber("tcp://localhost:1883", "sub-5",
                "alert/temp", latch, received);

        publisherNode.process(new Message(Map.of("temperature", 35.0)));

        boolean arrived = latch.await(3, TimeUnit.SECONDS);

        publisherNode.shutdown();
        subscriber.disconnect();
        subscriber.close();

        Assertions.assertTrue(arrived, "Broker에서 메시지가 수신되어야 함");
        Assertions.assertNotNull(received.get());
        Assertions.assertTrue(received.get().contains("temperature"));
    }

    @Test
    @Tag("integration")
    @DisplayName("동적 토픽")
    void test6() throws Exception {
        Map<String, Object> pubConfig = new HashMap<>(BASE_CONFIG);
        pubConfig.put("clientId", "pub-6");

        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-6", pubConfig);
        publisherNode.initialize();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        MqttClient subscriber = createSubscriberWithTopicCapture(
                "tcp://localhost:1883", "sub-6", "sensor/alert",
                latch, receivedTopic
        );

        publisherNode.process(new Message(Map.of("value", 40.0, "publisherTopic", "sensor/alert")));

        boolean arrived = latch.await(3, TimeUnit.SECONDS);

        publisherNode.shutdown();
        subscriber.disconnect();
        subscriber.close();

        Assertions.assertTrue(arrived);
        Assertions.assertEquals("sensor/alert", receivedTopic.get());
    }

    @Test
    @Tag("integration")
    @DisplayName("shutdown 후 연결 해제")
    void test7() {
        Map<String, Object> config = new HashMap<>(BASE_CONFIG);
        config.put("client", "pub-7");

        MqttPublisherNode publisherNode = new MqttPublisherNode("pub-7", config);
        publisherNode.initialize();
        Assertions.assertTrue(publisherNode.isConnected());

        publisherNode.shutdown();
        Assertions.assertFalse(publisherNode.isConnected());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────
    private MqttClient createSubscriber(String brokerUrl, String clientId,
                                        String topic, CountDownLatch latch,
                                        AtomicReference<String> received) throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                // document why this method is empty
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                // document why this method is empty
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                received.set(new String(mqttMessage.getPayload(), StandardCharsets.UTF_8));
                latch.countDown();
            }

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {
                // document why this method is empty
            }

            @Override
            public void connectComplete(boolean b, String s) {
                // document why this method is empty
            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {
                // document why this method is empty
            }
        });

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        client.subscribe(topic, 1);
        return client;
    }

    private MqttClient createSubscriberWithTopicCapture(String brokerUrl, String clientId,
                                                        String topic, CountDownLatch latch,
                                                        AtomicReference<String> receivedTopic) throws MqttException {
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                // document why this method is empty
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                // document why this method is empty
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                receivedTopic.set(s);
                latch.countDown();
            }

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {
                // document why this method is empty
            }

            @Override
            public void connectComplete(boolean b, String s) {
                // document why this method is empty
            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {
                // document why this method is empty
            }
        });

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        client.connect(options);
        client.subscribe(topic, 1);
        return client;
    }

}
