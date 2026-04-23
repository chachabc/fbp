package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 수신한 메시지를 내부 리스트에 순서대로 수집하는 테스트 전용 종단 노드.
 * 콘솔 출력 없이 {@link #getCollected()}로 수집된 메시지를 프로그램적으로 검증할 수 있어,
 * 테스트에서 {@link PrintNode} 대신 파이프라인 끝에 연결하여 사용한다.
 * OutputPort가 없다.
 */
public class CollectorNode extends AbstractNode {
    private final List<Message> collected;

    /**
     * CollectorNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public CollectorNode(String id){
        super(id);
        this.collected = new ArrayList<>();
        addInputPort("in");
    }

    /**
     * 수신한 메시지를 내부 수집 리스트에 추가한다.
     *
     * @param message 수집할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        collected.add(message);
    }

    /**
     * 수집된 메시지 리스트를 반환한다.
     * 반환된 리스트를 통해 수신 수, 순서, 내용을 테스트에서 검증할 수 있다.
     *
     * @return 수집된 메시지 리스트 (수신 순서 보장)
     */
    public List<Message> getCollected(){
        return collected;
    }
}
