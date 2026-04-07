package com.fbp.engine.node;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;

public class FilterNode implements Node {
    private final String id;
    private final String key;
    private final double threshold;
    private final InputPort inputPort;
    private final OutputPort outputPort;

    public FilterNode(String id, String key, double threshold){
        this.id = id;
        this.key = key;
        this.threshold = threshold;
        this.inputPort = new DefaultInputPort("in", this);
        this.outputPort = new DefaultOutputPort("out");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void process(Message message) {
        if (!message.hasKey(key)){
            return;
        }
        Double value = message.get(key);

        if (value >= threshold){
            outputPort.send(message);
        }
    }

    public InputPort getInputPort() {
        return inputPort;
    }

    public OutputPort getOutputPort() {
        return outputPort;
    }
}
