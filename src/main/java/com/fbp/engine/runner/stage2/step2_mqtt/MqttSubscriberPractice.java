package com.fbp.engine.runner.stage2.step2_mqtt;

import com.fbp.engine.core.Flow;
import com.fbp.engine.node.stage1.PrintNode;
import com.fbp.engine.node.stage2.MqttSubscriberNode;

import java.util.HashMap;
import java.util.Map;

public class MqttSubscriberPractice {
    public static void main(String[] args) throws InterruptedException {
        Map<String, Object> config = new HashMap<>();
        config.put("brokerUrl", "tcp://localhost:1883");
        config.put("clientId",  "fbp-subscriber-practice");
        config.put("topic",     "sensor/temp");
        config.put("qos",       1);

        Flow flow = new Flow("sub-print");
        flow.addNode(new MqttSubscriberNode("subscriber", config))
                .addNode(new PrintNode("printer"))
                .connect("subscriber", "out", "printer", "in");

        flow.initialize();
        Thread.sleep(10000);
        flow.shutdown();
    }

}
