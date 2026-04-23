package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;

/**
 * 지정한 key-value로 Message를 직접 생성하여 OutputPort로 전송하는 노드.
 * 외부 트리거 없이 테스트용 데이터를 수동으로 플로우에 주입할 때 사용한다.
 */
public class GeneratorNode extends AbstractNode {

    /**
     * GeneratorNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public GeneratorNode(String id){
        super(id);
        addOutputPort("out");
    }

    /**
     * GeneratorNode는 외부 메시지를 처리하지 않으므로 빈 구현이다.
     *
     * @param message 무시되는 메시지
     */
    @Override
    protected void onProcess(Message message){
        // 외부 메시지를 처리하지 않음
    }

    /**
     * 지정한 key-value로 새 Message를 생성하여 {@code "out"} 포트로 전송한다.
     *
     * @param key   페이로드에 추가할 키
     * @param value 페이로드에 추가할 값
     */
    public void generate(String key, Object value){
        Message message = new Message(Map.of(key, value));
        send("out", message);
    }

}
