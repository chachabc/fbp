package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 지정한 필드 값이 임계값을 초과하면 {@code "alert"} 포트로, 이하이면 {@code "normal"} 포트로 분기하는 IoT 특화 노드.
 * 범용 분기 노드인 {@link SplitNode}와 달리, 포트 이름이 IoT 도메인의 의미를 직접 반영한다.
 */
public class ThresholdFilterNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(ThresholdFilterNode.class);
    private final String fieldName;
    private final double threshold;

    /**
     * ThresholdFilterNode를 생성한다.
     *
     * @param id        노드의 고유 식별자
     * @param fieldName 임계값 비교 대상 페이로드 키 (예: {@code "temperature"}, {@code "humidity"})
     * @param threshold 임계값. 이 값을 초과하면 alert, 이하이면 normal로 분기한다.
     */
    public ThresholdFilterNode(String id, String fieldName, double threshold){
        super(id);
        this.fieldName = fieldName;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("alert");
        addOutputPort("normal");
    }

    /**
     * 메시지의 지정 필드 값이 임계값을 초과하면 {@code "alert"} 포트로,
     * 이하이면 {@code "normal"} 포트로 전달한다.
     * 필드가 없으면 메시지를 무시한다.
     *
     * @param message 분기할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey(fieldName)) return;

        Number value = message.get(fieldName);

        Object raw = message.get(fieldName);
        if (!(raw instanceof Number)) {
            log.info("[{}] 경고: '{}' 값이 숫자가 아님", getId(), fieldName);
            return;
        }

        if (value.doubleValue() > threshold){
            send("alert", message);
        } else {
            send("normal", message);
        }
    }
}
