package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;

public class TemperatureSensorNode extends AbstractNode {
    private final double min;
    private final double max;

    public TemperatureSensorNode(String id, double min, double max){
        super(id);
        this.min = min;
        this.max = max;
        addInputPort("trigger");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        double temperature = Math.round(min + Math.random() * (max-min));
        Message newMessage = new Message(Map.of("sensorId", getId(), "temperature", temperature,
                                            "unit", "°C", "timestamp", System.currentTimeMillis()));
        send("out", newMessage);
    }
}
