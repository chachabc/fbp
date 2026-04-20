package com.fbp.engine.node.stage1;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;

public class FilterNode extends AbstractNode{

    private final String key;
    private final double threshold;

    public FilterNode(String id, String key, double threshold){
        super(id);
        this.key = key;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    public void onProcess(Message message) {
        if (!message.hasKey(key)) {
            return;
        }

        Object raw = message.get(key);
        if (!(raw instanceof Number)) {
            System.out.println("[" + getId() + "] 경고: '" + key + "' 값이 숫자가 아님");
            return;
        }

        Number value = message.get(key);
        if (value.doubleValue() >= threshold) {
            send("out", message);
        }
    }
}
