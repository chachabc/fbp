package com.fbp.engine.runner.stage2.step2_mqtt;

import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 과제 2-2: Paho 라이브러리를 직접 사용하여 MQTT Broker에 연결하고,
 * 토픽을 구독/발행하는 독립 테스트 프로그램을 작성하라.
 * FBP 엔진과 무관하게 라이브러리 사용법만 익히는 것이 목적이다.
 *
 * 실행 전 Mosquitto Broker 가동 필요:
 * docker run -d --name mosquitto -p 1883:1883 eclipse-mosquitto:2
 * (mosquitto.conf에 listener 1883 / allow_anonymous true 설정)
 */
public class MqttPractice {
    private static final String BROKER_URL = "tcp://localhost:1883";
    private static final String TOPIC = "sensor/temp";
    private static final Logger log = LoggerFactory.getLogger(MqttPractice.class);

    public static void main(String[] args) throws Exception {
        // ── 1. Subscriber 연결 ──────────────────────────────────────────────
        log.info("===Subscriber 연결===");

        MqttClient subscriber = new MqttClient(BROKER_URL, "fbp-subscriber");
        MqttConnectionOptions subOptions = new MqttConnectionOptions();
        subOptions.setCleanStart(true);

        //callback
        subscriber.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
                System.out.println("[Subscriber] 연결 끊김: " + mqttDisconnectResponse.getReasonString());
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
                System.err.println("[Subscriber] 오류: " + e.getMessage());
            }

            @Override
            public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
                System.out.print("[Subscriber] 수신 - topic: " + s + ", payload: " + payload
                                    + ", QoS: " + mqttMessage.getQos());
            }

            @Override
            public void deliveryComplete(IMqttToken iMqttToken) {

            }

            @Override
            public void connectComplete(boolean b, String s) {
                System.out.println("[Subscriber] 연결 완료 (재연결: " + b + ")");
            }

            @Override
            public void authPacketArrived(int i, MqttProperties mqttProperties) {

            }
        });

        subscriber.connect(subOptions);

        // ── 2. 토픽 구독 ──────────────────────────────────────────────────────
        log.info("===토픽 구독: {} ===", TOPIC);

        subscriber.subscribe(TOPIC, 1);
        log.info("[Subscriber] 구독 시작");

        Thread.sleep(500);

        // ── 3. Publisher 연결 ──────────────────────────────────────────────
        log.info("===Publisher 연결===");

        MqttClient publisher = new MqttClient(BROKER_URL, "fbp-publisher");
        MqttConnectionOptions pubOptions = new MqttConnectionOptions();
        pubOptions.setCleanStart(true);
        publisher.connect(pubOptions);
        log.info("[Publisher] 연결 왼료");

        // ── 4. 메시지 발행 ────────────────────────────────────────────────
        log.info("===메시지 발행===");

        String[] payloads = {
                "{\"value\": 25.5, \"unit\": \"C\"}",
                "{\"value\": 31.2, \"unit\": \"C\"}",
                "{\"value\": 28.0, \"unit\": \"C\"}"
        };

        for (String payload : payloads) {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(false);
            publisher.publish(TOPIC, message);
            log.info("[Publisher] 발행 - " + payload);
            Thread.sleep(300);
        }

        // ── 5. 수신 완료 대기 후 연결 해제 ───────────────────────────────
        Thread.sleep(500);

        System.out.println("\n=== 연결 해제 ===");
        publisher.disconnect();
        publisher.close();
        System.out.println("[Publisher] 연결 해제");

        subscriber.disconnect();
        subscriber.close();
        System.out.println("[Subscriber] 연결 해제");

        System.out.println("\n=== 완료 ===");
    }
}
