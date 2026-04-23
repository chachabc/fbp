package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 지정한 필드 값을 기준으로 메시지를 두 경로로 분기하는 노드.
 * 임계값 이상이면 {@code "match"} 포트, 미만이면 {@code "mismatch"} 포트로 전달한다.
 * 조건 미달 메시지를 버리는 {@link FilterNode}와 달리, 모든 메시지가 어느 한쪽으로 전달된다.
 */
public class SplitNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(SplitNode.class);
    private final String key;
    private final double threshold;

    /**
     * SplitNode를 생성한다.
     *
     * @param id        노드의 고유 식별자
     * @param key       분기 기준이 되는 페이로드 키
     * @param threshold 분기 기준 임계값 (이 값 이상이면 match, 미만이면 mismatch)
     */
    public SplitNode(String id, String key, double threshold){
        super(id);
        this.key = key;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("match");
        addOutputPort("mismatch");
    }

    /**
     * 메시지의 지정 키 값이 임계값 이상이면 {@code "match"} 포트로, 미만이면 {@code "mismatch"} 포트로 전달한다.
     * 키가 없으면 메시지를 무시한다.
     *
     * @param message 분기할 메시지
     */
    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey(key)){
            return;
        }

        Object raw = message.get(key);
        if (!(raw instanceof Number)) {
            log.info("[{}] 경고: '{}' 값이 숫자가 아님", getId(), key);
            return;
        }

        Number value = message.get(key);
        if (value.doubleValue() >= threshold){
            send("match", message);
        } else {
            send("mismatch", message);
        }
    }
}
