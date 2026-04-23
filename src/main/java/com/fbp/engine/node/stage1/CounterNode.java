package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 수신한 메시지의 수를 누적하여 {@code "count"} 키를 추가한 새 메시지를 전달하는 노드.
 * 원본 메시지는 변경하지 않으며, {@link Message#withEntry(String, Object)}로 새 객체를 생성한다.
 */
public class CounterNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(CounterNode.class);
    private int count;

    /**
     * CounterNode를 생성한다. 카운트는 0부터 시작한다.
     *
     * @param id 노드의 고유 식별자
     */
    public CounterNode(String id){
        super(id);
        this.count = 0;
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * 수신 카운트를 1 증가시키고, {@code "count"} 키가 추가된 새 메시지를 {@code "out"} 포트로 전달한다.
     *
     * @param message 처리할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        count++;
        Message newMessage = message.withEntry("count", count);
        send("out", newMessage);
    }

    /**
     * 총 처리 메시지 수를 로그에 출력하고 노드를 종료한다.
     */
    @Override
    public void shutdown(){
        log.info("[{}]: 총 처리 메시지 {}건", getId(), count);
    }
}
