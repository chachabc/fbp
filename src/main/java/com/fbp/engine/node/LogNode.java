package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LogNode extends AbstractNode {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SS");

    public LogNode(String id){
        super(id);
        addInputPort("in");
        addOutputPort("out");
    }
    @Override
    protected void onProcess(Message message) {
        String time = LocalTime.now().format(FORMATTER);
        System.out.println("[" + time + "][" + getId() + "] " + message.getPayload());
        send("out", message);
    }
}
