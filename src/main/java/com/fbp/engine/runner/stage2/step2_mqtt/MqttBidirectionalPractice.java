package com.fbp.engine.runner.stage2.step2_mqtt;

import com.fbp.engine.core.Flow;
import com.fbp.engine.node.stage1.FilterNode;
import com.fbp.engine.node.stage2.MqttPublisherNode;
import com.fbp.engine.node.stage2.MqttSubscriberNode;

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 * mosquitto_pub -t sensor/temp -m '{"temperature": 35.0}'
 *   └─ MqttSubscriberNode 수신
 *        └─ {temperature=35.0, topic=sensor/temp, mqttTimestamp=...}
 *             └─ FilterNode (temperature >= 30.0 → 통과)
 *                  └─ MqttPublisherNode → alert/temp 발행
 *                       └─ mosquitto_sub 터미널에 출력
 * </pre>
 */
public class MqttBidirectionalPractice {
    public static void main(String[] args) throws InterruptedException {
        Map<String, Object> subConfig = new HashMap<>();
        subConfig.put("brokerUrl", "tcp://localhost:1883");
        subConfig.put("clientId",  "fbp-subscriber-bidirectional");
        subConfig.put("topic",     "sensor/temp");
        subConfig.put("qos",       1);

        Map<String, Object> pubConfig = new HashMap<>();
        pubConfig.put("brokerUrl", "tcp://localhost:1883");
        pubConfig.put("clientId",  "fbp-publisher-bidirectional");
        pubConfig.put("topic",     "alert/temp");
        pubConfig.put("qos",       1);
        pubConfig.put("retained",  false);

        Flow flow = new Flow("mqtt-bidirectional");
        flow.addNode(new MqttSubscriberNode("subscriber", subConfig))
                .addNode(new FilterNode("filter", "temperature", 30.0))
                .addNode(new MqttPublisherNode("publisher", pubConfig))
                .connect("subscriber", "out", "filter", "in")
                .connect("filter", "out", "publisher", "in");
        flow.initialize();

        Thread.sleep(30000);

        flow.shutdown();
    }
}
