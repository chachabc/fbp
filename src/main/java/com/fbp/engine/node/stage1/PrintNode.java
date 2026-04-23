package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 수신한 메시지를 콘솔에 출력하는 종단 노드.
 * OutputPort가 없으며, 파이프라인의 끝에 연결하여 결과를 확인하는 용도로 사용한다.
 */
public class PrintNode extends AbstractNode {

    private static final Logger log = LoggerFactory.getLogger(PrintNode.class);

    /**
     * PrintNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public PrintNode(String id){
        super(id);
        addInputPort("in");
    }

    /**
     * 수신한 메시지를 {@code [노드ID] 메시지내용} 형식으로 콘솔에 출력한다.
     *
     * @param message 출력할 메시지
     */
    @Override
    protected void onProcess(Message message){
        log.info("[{}] {}", getId(), message.getPayload());
    }
}
