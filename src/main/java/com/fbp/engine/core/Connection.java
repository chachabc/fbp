package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import java.util.LinkedList;
import java.util.Queue;

public class Connection {
    private final String id;
    private final Queue<Message> buffer;
    private InputPort target;

    public Connection(String id){
        this.id = id;
        this.buffer = new LinkedList<>();
    }

    public void setTarget(InputPort target){
        this.target = target;
    }

    public void deliver(Message message){
        buffer.offer(message);
        if(target != null){
            target.receive(buffer.poll());
        }
    }

    public int getBufferSize(){
        return buffer.size();
    }

    public String getId(){
        return id;
    }
}
