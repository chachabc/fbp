package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class ThresholdFilterNode extends AbstractNode {
    private final String fieldName;
    private final double threshold;

    public ThresholdFilterNode(String id, String fieldName, double threshold){
        super(id);
        this.fieldName = fieldName;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("alert");
        addOutputPort("normal");
    }

    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey(fieldName)) return;

        Number value = message.get(fieldName);

        Object raw = message.get(fieldName);
        if (!(raw instanceof Number)) {
            System.out.println("[" + getId() + "] 경고: '" + fieldName + "' 값이 숫자가 아님");
            return;
        }

        if (value.doubleValue() > threshold){
            send("alert", message);
        } else {
            send("normal", message);
        }
    }
}
