package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class DelayNode extends AbstractNode {
    private final long delayMs;

    public DelayNode(String id, long delayMs){
        super(id);
        this.delayMs = delayMs;
        addInputPort("in");
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        try{
            Thread.sleep(delayMs); // 모든 플로우가 멈추버림 -> 딜레이 노드를 별도의 스레드에서 관리?
            send("out", message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
