package com.fbp.engine.node.stage2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 2-4 — FBP 플로우에서 처리된 메시지를 MQTT Broker에 발행하는 싱크 노드.
 *
 * <pre>
 * config 키:
 *   brokerUrl (String)  — 예: "tcp://localhost:1883"
 *   clientId  (String)  — Broker 내 고유 식별자
 *   topic     (String)  — 기본 발행 토픽
 *   qos       (int)     — 기본 1
 *   retained  (boolean) — 기본 false
 *
 * 흐름:
 *   FBP Message (in 포트)
 *     └─ payload → JSON 변환
 *           └─ 발행 토픽 결정 (메시지의 "topic" 키 > config 기본 토픽)
 *                 └─ client.publish(topic, mqttMessage)
 * </pre>
 */
public class MqttPublisherNode extends ProtocolNode{
    private static final Logger log = LoggerFactory.getLogger(MqttPublisherNode.class);
    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttPublisherNode(String id, Map<String, Object> config) {
        super(id, config);
        this.objectMapper = new ObjectMapper();
        addInputPort("in");
    }

    @Override
    protected void connect() throws Exception {
        String brokerUrl = (String) getConfig("brokerUrl");
        String clientId = (String) getConfig("clientId");

        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(false); // 재연결은 ProtocolNode가 관리

        client.connect(options);
        log.info("[{}] Broker 연결: {}", getId(), brokerUrl);
    }

    @Override
    protected void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (MqttException e) {
            log.warn("[{}] 연결 해제 중 오류: {}", getId(), e.getMessage());
        }
    }

    // ── 메시지 발행 ───────────────────────────────────────────────────────────
    /**
     * FBP Message를 수신하여 MQTT Broker에 발행한다.
     *
     * <ol>
     *   <li>FBP Message 페이로드를 JSON 문자열로 변환</li>
     *   <li>발행 토픽 결정: 메시지에 "publisherTopic" 키가 있으면 그 값, 없으면 config의 기본 토픽</li>
     *   <li>MqttMessage 생성 (QoS, retained 설정)</li>
     *   <li>client.publish()</li>
     * </ol>
     */
    @Override
    protected void onProcess(Message message) {
        if (!isConnected()) {
            log.warn("[{}] 연결되지 않은 상태 - 메시지 무시", getId());
            return;
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(message.getPayload());
        } catch (JsonProcessingException e) {
            log.error("[{}] JSON 변환 실패: {}", getId(), e.getMessage());
            return;
        }

        String topic = message.get("publisherTopic") != null ? (String) message.get("publisherTopic")
                                : (String) getConfig("topic");

        if (topic == null) {
            log.error("[{}] 발행 토픽 없음 - 메시지 무시", getId());
            return;
        }

        int qos = getConfig("qos") instanceof Number number ? number.intValue() : 1;
        boolean retained = getConfig("retained") instanceof Boolean b ? b : false;

        MqttMessage mqttMessage = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
        mqttMessage.setQos(qos);
        mqttMessage.setRetained(retained);

        try {
            client.publish(topic, mqttMessage);
            log.info("[{}] 발행 - topic: {}, payload: {}", getId(), topic, jsonPayload);
        } catch (MqttException e) {
            log.error("[{}] 발행 실패 - topic: {}, 오류: {}", getId(), topic, e.getMessage());
        }
    }
}
