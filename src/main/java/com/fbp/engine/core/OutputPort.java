package com.fbp.engine.core;

import com.fbp.engine.message.Message;

/**
 * 노드의 출력 지점을 나타내는 인터페이스.
 * 하나의 포트에 여러 Connection을 연결할 수 있으며(1:N), send() 호출 시 모든 Connection으로 메시지를 전달한다.
 */
public interface OutputPort {
    /**
     * 포트의 이름을 반환한다.
     *
     * @return 포트 이름 (ex: "out", "match", "alert")
     */
    String getName();

    /**
     * 이 포트에 Connection을 연결한다.
     * 하나의 포트에 여러 Connection을 연결하면 1:N 브로드캐스트가 가능하다.
     *
     * @param connection 연결할 Connection
     */
    void connect(Connection connection);

    /**
     * 연결된 모든 Connection으로 메시지를 전송한다.
     * Connection이 없으면 메시지는 무시된다.
     *
     * @param message 전송할 메시지
     */
    void send(Message message);
}
