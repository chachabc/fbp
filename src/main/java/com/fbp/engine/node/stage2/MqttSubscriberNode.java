package com.fbp.engine.node.stage2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 과제 2-3 — MQTT 토픽을 구독하여 수신 메시지를 FBP 플로우에 주입하는 소스 노드.
 *
 * <pre>
 * config 키:
 *   brokerUrl (String) — 예: "tcp://localhost:1883"
 *   clientId  (String) — Broker 내 고유 식별자
 *   topic     (String) — 구독할 토픽 (와일드카드 지원: sensor/+, sensor/#)
 *   qos       (int)    — 기본 1
 *
 * 흐름:
 *   MQTT Broker
 *     └─ (메시지 수신) → Paho 콜백 스레드
 *           └─ JSON 파싱 → FBP Message 생성
 *                 └─ send("out", message) → 다음 노드
 * </pre>
 */
public class MqttSubscriberNode extends ProtocolNode {
    private static final Logger log = LoggerFactory.getLogger(MqttSubscriberNode.class);
    private MqttClient client;
    private final ObjectMapper objectMapper;

    public MqttSubscriberNode(String id, Map<String, Object> config) {
        super(id, config);
        this.objectMapper = new ObjectMapper();
        addOutputPort("out");
    }

    @Override
    protected void connect() throws Exception {
        String brokerUrl = (String) getConfig("brokerUrl");
        String clientId  = (String) getConfig("clientId");
        String topic = (String) getConfig("topic");
        int qos = getConfig("qos") instanceof Number number ? number.intValue() : 1;

        client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(false);

        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                log.warn("[{}] Broker 연결 끊김: {}", getId(), mqttDisconnectResponse.getReasonString());
                reconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                log.error("[{}] MQTT 오류: {}", getId(), e.getMessage());
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                handleMqttMessage(s, mqttMessage.getPayload());
            }

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {
                // 내가 Publish 한 메시지가 브로커에 도착했을때 호출
                // MqttSubscriberNode는 Subscribe 담당
            }

            @Override
            public void connectComplete(boolean b, String s) {
                log.info("[{}] Broker 연결 완료 (재연결: {})", getId(), b);
            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {
                // 브로커와 클라이언트 간에 향상된 인증(Enhanced Authentication) 패킷을 교환할 때 호출
                // 현재 단순 연결을 사용, MQTT v5.0의 Enhanced Authentication을 사용하지 않으므로 무시함
            }
        });

        // connect
        client.connect(options);
        log.info("[{}] Broker 연결: {}", getId(), brokerUrl);

        // subscribe topic
        client.subscribe(topic, qos);
        log.info("[{}] 구독 시작 — topic: {}, QoS: {}", getId(), topic, qos);
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
            log.warn("[{}] 연결 해제 요청 중 오류: {}", getId(), e.getMessage());
        }
    }

    @Override
    protected void onProcess(Message message) {
        // MqttSubscriberNode는 외부 MQTT 메시지만 소스로 사용.
        // 다른 노드에서 process()를 직접 호출하는 경우는 없으므로 빈 구현.
    }

    // ── MQTT 메시지 → FBP Message 변환 ──────────────────────────────────────
    /**
     * Paho 콜백에서 호출된다.
     * payload를 JSON 파싱하여 FBP Message로 변환하고 "out" 포트로 전송한다.
     */
    protected void handleMqttMessage(String topic, byte[] payload) {
        Message message = convertToFbpMessage(topic, payload);
        log.debug("[{}] 수신 -> FBP Message: {}", getId(), message.getPayload());
        send("out", message);
    }

    /**
     * raw 바이트 payload를 FBP Message로 변환한다.
     *
     * <p>JSON 파싱에 성공하면 필드들을 그대로 Map에 담는다.
     * 파싱에 실패하면 원본 문자열을 {@code "rawPayload"} 키에 넣는다.
     * 항상 {@code "topic"}과 {@code "mqttTimestamp"}를 추가한다.</p>
     */
    protected Message convertToFbpMessage(String topic, byte[] payload) {
        String raw = new String(payload, StandardCharsets.UTF_8);
        Map<String, Object> data = new HashMap<>();

        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    raw, new TypeReference<Map<String, Object>>() {});
            data.putAll(parsed);
        } catch (JsonProcessingException e) {
            log.debug("[{}] JSON 파싱 실패, rawPayload로 전달: {}", getId(), e.getMessage());
            data.put("rawPayload", raw);
        }

        data.put("topic", topic);
        data.put("mqttTimestamp", System.currentTimeMillis());

        return new Message(data);
    }
}
