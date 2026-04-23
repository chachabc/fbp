package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DefaultPort의 기본 구현체
 * 내부에 Connection 목록을 관리하며, send() 호출 시 연결된 모든 Connection에 메시지를 전달한다.
 */
public class DefaultOutputPort implements OutputPort{
    private final String name;
    private final List<Connection> connections;

    /**
     * OutputPort를 생성한다.
     *
     * @param name 포트 이름
     */
    public DefaultOutputPort(String name){
        this.name = name;
        this.connections = new ArrayList<>();
    }

    /**
     * 포트의 이름을 반환한다.
     *
     * @return 포트 이름
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 이 포트에 Connection을 추가한다.
     *
     * @param connection 연결할 Connection
     */
    @Override
    public void connect(Connection connection) {
        connections.add(connection);
    }

    /**
     * 연결된 모든 Connection에 메시지를 전달한다.
     *
     * @param message 전송할 메시지
     */
    @Override
    public void send(Message message) {
        for (Connection connection : connections){
            connection.deliver(message);
        }
    }
}
