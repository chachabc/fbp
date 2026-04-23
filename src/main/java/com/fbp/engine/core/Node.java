package com.fbp.engine.core;

import com.fbp.engine.message.Message;
/**
 * FBP 엔진의 모든 노드가 구현해야 하는 기본 계약.
 * 노드의 생명주기(initialize → process → shutdown)를 정의한다.
 */
public interface Node {
    /**
     * 노드의 고유 식별자를 반환한다.
     *
     * @return 노드 ID
     */
    String getId();

    /**
     * 수신한 메시지를 처리한다.
     * @param message
     */
    void process(Message message);

    /**
     * 노드를 초기화한다.
     * Flow가 시작될 때 엔진이 호출한다. 자원 할당, 스케줄러 시작 등에 이용된다.
     */
    void initialize();

    /**
     * 노드를 종료한다.
     * Flow가 정지될 때 엔진이 호출한다. 지원 해제, 스케줄러 종료 들에 사용한다.
     */
    void shutdown();
}
