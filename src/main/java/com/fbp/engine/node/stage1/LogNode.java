package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 수신한 메시지를 타임스탬프와 함께 콘솔에 출력한 후, 원본 메시지를 그대로 다음 노드로 전달하는 중간 노드.
 * 파이프라인의 어느 위치에도 삽입할 수 있으며, 메시지 흐름을 변경하지 않는다.
 * 출력만 하고 전달하지 않는 {@link PrintNode}와 달리, 체인 중간에서 디버깅 용도로 사용한다.
 */
public class LogNode extends AbstractNode {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SS");
    private static final Logger log = LoggerFactory.getLogger(LogNode.class);

    /**
     * LogNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public LogNode(String id){
        super(id);
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * {@code [HH:mm:ss.SSS][노드ID] 메시지내용} 형식으로 콘솔에 출력하고,
     * 원본 메시지를 {@code "out"} 포트로 그대로 전달한다.
     *
     * @param message 로깅 및 전달할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        String time = LocalTime.now().format(FORMATTER);
        log.info("[{}][{}] {}", time, getId(), message.getPayload());
        send("out", message);
    }
}
