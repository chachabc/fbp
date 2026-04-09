package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.function.Function;

public class TransformNode extends AbstractNode {
    private final Function<Message, Message> transform;

    public TransformNode(String id, Function<Message, Message> transform){
        super(id);
        this.transform = transform;
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        Message result = transform.apply(message);
        if (result != null){
            send("out", result);
        }
    }
}
