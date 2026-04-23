package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 두 포트를 잇는 메시지 버퍼 채널.
 * LinkedBlockingQueue 기반으로 스레드 안전하게 메시지를 전달한다.
 */
public class Connection {
    private final String id;
    private final LinkedBlockingQueue<Message> buffer;
    private InputPort target;

    /**
     * Connection을 생성한다.
     *
     * @param id Connection의 고유 식별자 (ex: "timer-filter-connect)
     */
    public Connection(String id){
        this(id, 100);
    }

    public Connection(String id, int capacity){
        this.id = id;
        this.buffer = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * 메시지를 수신할 대상 InputPort를 설정한다.
     *
     * @param target 수신 대상 InputPort
     */
    public void setTarget(InputPort target){
        this.target = target;
    }

    /**
     * 메시지를 버퍼에 추가하고 대상 InputPort로 전달한다.
     * target이 설정되어 있지 않으면 버퍼에만 적재하고 전달하지 않는다.
     *
     * @param message 전달할 메시지
     */
    public void deliver(Message message){
        try {
            buffer.put(message);
            if (target != null) {
                target.receive(buffer.take());  //stage_1 step4에서는 분리
            }
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    public Message poll(){
        try{
            return buffer.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 현재 버퍼에 적재된 메시지 수를 반환한다.
     *
     * @return 버퍼 내 메시지 수
     */
    public int getBufferSize(){
        return buffer.size();
    }

    /**
     * 현재 Connection ID를 반환한다.
     *
     * @return Connection ID
     */
    public String getId(){
        return id;
    }
}
