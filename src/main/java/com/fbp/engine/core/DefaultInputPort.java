package com.fbp.engine.core;

import com.fbp.engine.message.Message;

public class DefaultInputPort implements InputPort{
    private final String name;
    private final Node owner;

    public DefaultInputPort(String name, Node owner){
        this.name = name;
        this.owner = owner;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void receive(Message message) {
        owner.process(message);
    }
}
