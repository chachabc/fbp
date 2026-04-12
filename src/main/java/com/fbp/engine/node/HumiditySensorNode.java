package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;

public class HumiditySensorNode extends AbstractNode {
    private final double min;
    private final double max;

    public HumiditySensorNode(String id, double min, double max){
        super(id);
        this.max = max;
        this.min = min;
        addInputPort("trigger");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        double humidity = Math.round((min + Math.random() * (max - min)) * 10.0) / 10.0;
        Message newMessage = new Message(Map.of("sensorId", getId(), "humidity", humidity,
                                                    "unit", "%", "timestamp", System.currentTimeMillis()));
    send("out", newMessage);
    }
}
