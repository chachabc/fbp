package com.fbp.engine.core;

import com.fbp.engine.message.Message;

/**
 * 노드의 입력 지점을 나타내는 인터페이스.
 * Connection으로부터 메시지를 수신하여 소속 노드의 process()를 호출하는 역할을 한다.
 */
public interface InputPort {

    /**
     * 포트의 이름을 반환한다.
     * @return 포트 이름 (ex: "in", "trigger")
     */
    String getName();

    /**
     * Connection으로부터 메시지를 수신한다.
     * 구현체는 이 메서드 내에서 소속 노드의 process()를 호출해야 한다.
     *
     * @param message 수신한 메시지
     */
    void receive(Message message);
}
