package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.WireMetricEvent;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * MQTT 시스템 브로커를 경유하는 Connection 구현체.
 *<pre>
 * 노드는 이 클래스의 존재를 모른다.
 * deliver() → MQTT publish → 시스템 브로커 → MQTT subscribe → internalQueue → poll()
 *
 * pub/sub 클라이언트를 분리한 이유:
 *   하나의 MqttClient로 pub+sub을 함께 하면 deliver()가 자기 자신이 발행한 메시지를
 *   콜백으로 다시 받아 internalQueue에 넣는 루프가 생긴다.
 *   pub/sub 클라이언트 ID를 달리 하면 브로커가 둘을 독립 세션으로 처리한다.
 *
 * 토픽 네이밍 규칙 (FlowManager가 생성해서 전달):
 *   fbp/{flowId}/{srcNodeId}.{srcPort}-{dstNodeId}.{dstPort}
 *   예: fbp/temperature-monitoring/sensor.out-rule.in
 *   </pre>
 */
public class MqttBridgeConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(MqttBridgeConnection.class);
    private static final int DEFAULT_INTERNAL_CAPACITY = 200;

    private final String id;
    private final String topic;
    private final int qos;
    private final MessageSerializer serializer;
    private final LinkedBlockingQueue<Message> internalQueue;
    private final MetricsCollector collector; // nullable
    private final String flowId;              // nullable

    private final MqttClient publisher;
    private final MqttClient subscriber;

    public MqttBridgeConnection(String id, String brokerUrl, String topic, int qos) throws MqttException {
        this(id, brokerUrl, topic, qos, DEFAULT_INTERNAL_CAPACITY, null, null);
    }

    public MqttBridgeConnection(String id, String brokerUrl, String topic, int qos, int internalCapacity)
            throws MqttException {
        this(id, brokerUrl, topic, qos, internalCapacity, null, null);
    }

    public MqttBridgeConnection(String id, String brokerUrl, String topic, int qos,
                                MetricsCollector collector, String flowId) throws MqttException {
        this(id, brokerUrl, topic, qos, DEFAULT_INTERNAL_CAPACITY, collector, flowId);
    }

    MqttBridgeConnection(String id, String brokerUrl, String topic, int qos, int internalCapacity,
                         MetricsCollector collector, String flowId) throws MqttException {
        this.id            = id;
        this.topic         = topic;
        this.qos           = qos;
        this.collector     = collector;
        this.flowId        = flowId;
        this.serializer    = new MessageSerializer();
        this.internalQueue = new LinkedBlockingQueue<>(internalCapacity);

        this.publisher  = new MqttClient(brokerUrl, "fbp-pub-" + id, new MemoryPersistence());
        this.subscriber = new MqttClient(brokerUrl, "fbp-sub-" + id, new MemoryPersistence());

        connectClients();
    }

    private void connectClients() throws MqttException {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(300); // 5분 — 비활성 와이어의 빈번한 타임아웃 방지

        publisher.connect(options);
        log.info("[{}] pub 연결 완료 — topic: {}", id, topic);

        subscriber.setCallback(new MqttCallback() {
            @Override
            public void messageArrived(String t, MqttMessage mqttMsg) {
                try {
                    Message message = serializer.deserialize(mqttMsg.getPayload());
                    boolean dropped = !internalQueue.offer(message);
                    if (dropped) {
                        log.warn("[{}] 내부 큐 가득 참 — 메시지 드롭: {}", id, message.getId());
                    }
                    if (collector != null) {
                        collector.record(new WireMetricEvent(
                                flowId, id, mqttMsg.getPayload().length,
                                internalQueue.size(), dropped, dropped ? null : message));
                    }
                } catch (Exception e) {
                    log.error("[{}] 수신 메시지 역직렬화 실패: {}", id, e.getMessage());
                }
            }

            @Override
            public void connectComplete(boolean reconnect, String uri) {
                if (reconnect) {
                    try {
                        subscriber.subscribe(topic, qos);
                        log.info("[{}] sub 재연결 후 재구독 완료", id);
                    } catch (MqttException e) {
                        log.error("[{}] 재구독 실패: {}", id, e.getMessage());
                    }
                }
            }

            @Override public void disconnected(MqttDisconnectResponse r) {
                log.warn("[{}] sub 연결 끊김: {}", id, r.getReasonString());
            }
            @Override public void mqttErrorOccurred(MqttException e) {
                log.error("[{}] MQTT 오류: {}", id, e.getMessage());
            }
            @Override public void deliveryComplete(IMqttToken t) {}
            @Override public void authPacketArrived(int i, MqttProperties p) {}
        });

        subscriber.connect(options);
        subscriber.subscribe(topic, qos);
        log.info("[{}] sub 연결 및 구독 완료 — topic: {}", id, topic);
    }

    @Override
    public void deliver(Message message) {
        try {
            byte[] payload = serializer.serialize(message);
            MqttMessage mqttMsg = new MqttMessage(payload);
            mqttMsg.setQos(qos);
            publisher.publish(topic, mqttMsg);
            if (collector != null) {
                collector.record(new WireMetricEvent(flowId, id, payload.length, internalQueue.size(), false, message));
            }
        } catch (MqttException e) {
            log.error("[{}] 발행 실패: {}", id, e.getMessage());
        }
    }

    @Override
    public Message poll() throws InterruptedException {
        return internalQueue.take();
    }

    @Override
    public int getBufferSize() {
        return internalQueue.size();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void close() {
        try {
            if (subscriber.isConnected()) {
                subscriber.unsubscribe(topic);
                subscriber.disconnect();
            }
            subscriber.close();
        } catch (MqttException e) {
            log.warn("[{}] sub 해제 중 오류: {}", id, e.getMessage());
        }
        try {
            if (publisher.isConnected()) {
                publisher.disconnect();
            }
            publisher.close();
        } catch (MqttException e) {
            log.warn("[{}] pub 해제 중 오류: {}", id, e.getMessage());
        }
        internalQueue.clear();
        log.info("[{}] 브릿지 연결 닫힘", id);
    }
}
