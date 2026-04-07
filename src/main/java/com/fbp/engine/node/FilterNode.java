package com.fbp.engine.node;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;

public class FilterNode extends AbstractNode{

    private final String key;
    private final double threshold;

    public FilterNode(String id, String key, double threshold){
        super(id);
        this.key = key;
        this.threshold = threshold;
        addInputPorts("in");
        addOutputPorts("out");
    }

    @Override
    public void onProcess(Message message) {
        if (!message.hasKey(key)){
            return;
        }
        Double value = message.get(key);
        if (value >= threshold){
            send("out", message);
        }
    }
}
