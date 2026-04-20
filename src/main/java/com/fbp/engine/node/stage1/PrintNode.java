package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class PrintNode extends AbstractNode {

    public PrintNode(String id){
        super(id);
        addInputPort("in");
    }

    @Override
    protected void onProcess(Message message){
        System.out.println("[" + getId() + "]" + message.getPayload());
    }
}
