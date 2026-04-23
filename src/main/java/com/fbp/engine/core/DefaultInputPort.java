package com.fbp.engine.core;

import com.fbp.engine.message.Message;

/**
 * InputPort의 기본 구현체
 * 메시지를 수신하면 생성 시 주입된 소속 노드(owner)의 process()를 호출한다.
 */
public class DefaultInputPort implements InputPort{
    private final String name;
    private final Node owner;

    /**
     * Input port를 생성한다.
     *
     * @param name 포트 이름
     * @param owner 이 포트가 속한 노드. 메시지 수신 시 owner.process()가 호출된다.
     */
    public DefaultInputPort(String name, Node owner){
        this.name = name;
        this.owner = owner;
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
     * Connection으로부터 메시지를 수신하여 소속 노드의 process()를 호출한다.
     *
     * @param message 수신한 메시지
     */
    @Override
    public void receive(Message message) {
        owner.process(message);
    }
}
