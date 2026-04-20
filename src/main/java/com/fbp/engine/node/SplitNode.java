package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class SplitNode extends AbstractNode {
    private final String key;
    private final double threshold;

    public SplitNode(String id, String key, double threshold){
        super(id);
        this.key = key;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("match");
        addOutputPort("mismatch");
    }

    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey(key)){
            return;
        }

        Object raw = message.get(key);
        if (!(raw instanceof Number)) {
            System.out.println("[" + getId() + "] 경고: '" + key + "' 값이 숫자가 아님");
            return;
        }

        Number value = message.get(key);
        if (value.doubleValue() >= threshold){
            send("match", message);
        } else {
            send("mismatch", message);
        }
    }
}
