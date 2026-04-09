package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class CounterNode extends AbstractNode {
    private int count;

    public CounterNode(String id){
        super(id);
        this.count = 0;
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        count++;
        Message newMessage = message.withEntry("count", count);
        send("out", newMessage);
    }

    @Override
    public void shutdown(){
        System.out.println("[" + getId() + "] : 총 처리 메시지: " + count + "건");
    }
}
