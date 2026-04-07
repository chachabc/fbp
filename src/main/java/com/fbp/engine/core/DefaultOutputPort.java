package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DefaultOutputPort implements OutputPort{
    private final String name;
    private final List<Connection> connections;

    public DefaultOutputPort(String name){
        this.name = name;
        this.connections = new ArrayList<>();
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void connect(Connection connection) {
        connections.add(connection);
    }

    @Override
    public void send(Message message) {
        for (Connection connection : connections){
            connection.deliver(message);
        }
    }
}
