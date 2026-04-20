package com.fbp.engine.core;

import com.fbp.engine.message.Message;
/**
 * FBP 엔진의 모든 노드가 구현해야 하는 기본 계약.
 * 노드의 생명주기(initialize → process → shutdown)를 정의한다.
 */
public interface Node {
    String getId();
    void process(Message message);
    void initialize();
    void shutdown();
}
