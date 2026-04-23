package com.fbp.engine.runner.stage2.step2_mqtt;

import com.fbp.engine.core.Flow;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.GeneratorNode;
import com.fbp.engine.node.stage2.MqttPublisherNode;

import java.util.HashMap;
import java.util.Map;

public class MqttPublisherPractice {
    public static void main(String[] args) throws InterruptedException {
        Map<String, Object> config = new HashMap<>();
        config.put("brokerUrl", "tcp://localhost:1883");
        config.put("clientId", "fbp-publisher-practice");
        config.put("publisherTopic", "sensor/generated");
        config.put("qos", 1);
        config.put("retained", false);

        GeneratorNode generatorNode = new GeneratorNode("generator");
        MqttPublisherNode publisherNode = new MqttPublisherNode("publisher", config);

        Flow flow = new Flow("generator-publisher");
        flow.addNode(generatorNode)
                .addNode(publisherNode)
                .connect("generator", "out", "publisher", "in");
        flow.initialize();

        publisherNode.process(new Message(Map.of("sensorId", "sensor-1", "temperature", 25.5, "unit", "C")));
        Thread.sleep(500);
        publisherNode.process(new Message(Map.of("sensorId", "sensor-1", "temperature", 31.0, "unit", "C")));
        Thread.sleep(500);
        publisherNode.process(new Message(Map.of("sensorId", "sensor-1", "temperature", 28.3, "unit", "C")));
        Thread.sleep(500);

        flow.shutdown();
    }
}
