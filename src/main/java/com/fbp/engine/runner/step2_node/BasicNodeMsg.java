package com.fbp.engine.runner.step2_node;

import com.fbp.engine.message.Message;
import com.fbp.engine.node.PrintNode;

import java.util.Map;

public class BasicNodeMsg {
    public static void main(String[] args){
        //step2-5
        Message message = new Message(Map.of("temperature", 25.5, "location", "server-room"));

        PrintNode printNode = new PrintNode("printer-1");
        printNode.process(message);

        Message message1 = message.withEntry("humidity", 60.0);
        printNode.process(message1);

        System.out.println(message.hasKey("humidity"));

    }
}
