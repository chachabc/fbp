package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.function.Function;

/**
 * 생성 시 주입된 변환 함수({@code Function<Message, Message>})로 메시지를 변환하여 전달하는 노드.
 * 변환 로직을 람다식으로 외부에서 주입하므로, 다양한 변환을 새 클래스 없이 정의할 수 있다.
 * 변환 함수가 {@code null}을 반환하면 메시지가 전달되지 않아 필터 효과를 낼 수 있다.
 */
public class TransformNode extends AbstractNode {
    private final Function<Message, Message> transform;

    /**
     * TransformNode를 생성한다.
     *
     * @param id          노드의 고유 식별자
     * @param transform   메시지를 변환하는 함수. null을 반환하면 해당 메시지는 전달되지 않는다.
     */
    public TransformNode(String id, Function<Message, Message> transform){
        super(id);
        this.transform = transform;
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * transformer 함수를 적용하여 변환된 메시지를 {@code "out"} 포트로 전달한다.
     * 변환 결과가 {@code null}이면 전달을 생략한다.
     *
     * @param message 변환할 원본 메시지
     */
    @Override
    protected void onProcess(Message message) {
        Message result = transform.apply(message);
        if (result != null){
            send("out", result);
        }
    }
}