package com.fbp.engine.runner;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

import java.util.Map;

public class Main {
    public static void main(String[] args) throws InterruptedException{
        //step2-5
        /*
        Message message = new Message(Map.of("temperature", 25.5, "location", "server-room"));

        PrintNode printNode = new PrintNode("printer-1");
        printNode.process(message);

        Message message1 = message.withEntry("humidity", 60.0);
        printNode.process(message1);

        System.out.println(message.hasKey("humidity"));
        */

        //step3-7, 8
        /*
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        PrintNode printNode = new PrintNode("printer-1");
        PrintNode printNode1 = new PrintNode("printer-2");

        Connection connection = new Connection("connect-1");
        Connection connection1 = new Connection("connect-2");

        connection.setTarget(printNode.getInputPort());
        connection1.setTarget(printNode1.getInputPort());

        generatorNode.getOutputPort().connect(connection);
        generatorNode.getOutputPort().connect(connection1);

        generatorNode.generate("temperature", 25.5);
         */

        //step3-10
        /*
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        FilterNode filterNode = new FilterNode("filter-1", "temperature", 30.0);
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection = new Connection("connect-generator-filter");
        connection.setTarget(filterNode.getInputPort());
        generatorNode.getOutputPort().connect(connection);

        Connection connection1 = new Connection("connect-filter-printer");
        connection1.setTarget(printNode.getInputPort());
        filterNode.getOutputPort().connect(connection1);

        generatorNode.generate("temperature", 25.0);
        generatorNode.generate("temperature", 35.0);
         */

        //step5-6
        TimerNode timerNode = new TimerNode("timer-1", 500);
        FilterNode filterNode = new FilterNode("filter-1", "tick", 3.0);
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection1 = new Connection("timer-filter-connect");
        connection1.setTarget(filterNode.getInputPort("in"));
        timerNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("filter-printer-connect");
        connection2.setTarget(printNode.getInputPort("in"));
        filterNode.getOutputPort("out").connect(connection2);

        timerNode.initialize();
        filterNode.initialize();
        printNode.initialize();

        Thread.sleep(3000);
        timerNode.shutdown();
        filterNode.shutdown();
        printNode.shutdown();
    }
}
