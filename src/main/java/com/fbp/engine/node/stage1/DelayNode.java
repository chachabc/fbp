package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

/**
 * 지정한 시간만큼 대기한 후 메시지를 그대로 다음 노드로 전달하는 노드.
 * 파이프라인 중간에 삽입하여 메시지 흐름 속도를 조절하거나 처리 지연을 시뮬레이션할 때 사용한다.
 */
public class DelayNode extends AbstractNode {
    private final long delayMs;

    /**
     * DelayNode를 생성한다.
     *
     * @param id      노드의 고유 식별자
     * @param delayMs 지연 시간 (밀리초)
     */
    public DelayNode(String id, long delayMs){
        super(id);
        this.delayMs = delayMs;
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * 지정된 시간만큼 대기한 후 메시지를 {@code "out"} 포트로 그대로 전달한다.
     * 대기 중 인터럽트가 발생하면 스레드 인터럽트 상태를 복원하고 메시지 전달을 생략한다.
     *
     * @param message 지연 후 전달할 메시지
     */
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
