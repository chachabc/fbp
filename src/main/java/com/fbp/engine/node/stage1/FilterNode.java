package com.fbp.engine.node.stage1;

import com.fbp.engine.core.*;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 지정한 필드 값이 임계값 이상이면 다음 노드로 통과시키고, 미만이면 메시지를 차단하는 노드.
 * 조건 미달 메시지를 버리는 단방향 게이트 역할을 한다.
 * 양방향 분기가 필요하면 {@link SplitNode}를 사용한다.
 */
public class FilterNode extends AbstractNode{

    private static final Logger log = LoggerFactory.getLogger(FilterNode.class);
    private final String key;
    private final double threshold;

    /**
     * FilterNode를 생성한다.
     *
     * @param id        노드의 고유 식별자
     * @param key       필터링 기준이 되는 페이로드 키
     * @param threshold 통과 기준 임계값 (이 값 이상이면 통과)
     */
    public FilterNode(String id, String key, double threshold){
        super(id);
        this.key = key;
        this.threshold = threshold;
        addInputPort("in");
        addOutputPort("out");
    }

    /**
     * 메시지의 지정 키 값이 임계값 이상이면 {@code "out"} 포트로 전달하고, 미만이거나 키가 없으면 무시한다.
     *
     * @param message 필터링할 메시지
     */
    @Override
    public void onProcess(Message message) {
        if (!message.hasKey(key)) {
            return;
        }

        Object raw = message.get(key);
        if (!(raw instanceof Number)) {
            log.info("[{}] 경고: '{}' 값이 숫자가 아님", getId(), key);
            return;
        }

        Number value = message.get(key);
        if (value.doubleValue() >= threshold) {
            send("out", message);
        }
    }
}
