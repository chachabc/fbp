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

    public Connection(String id){
        this(id, 100);
    }

    public Connection(String id, int capacity){
        this.id = id;
        this.buffer = new LinkedBlockingQueue<>(capacity);
    }

    public void setTarget(InputPort target){
        this.target = target;
    }

    public void deliver(Message message){
        try {
            buffer.put(message);
            if (target != null) {
                target.receive(buffer.take());  // step4에서는 분리
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

    public int getBufferSize(){
        return buffer.size();
    }

    public String getId(){
        return id;
    }
}
