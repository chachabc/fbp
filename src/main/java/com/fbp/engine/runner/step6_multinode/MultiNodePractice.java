package com.fbp.engine.runner.step6_multinode;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.*;

public class MultiNodePractice {
    public static void main(String[] args) throws InterruptedException{
        TimerNode timerNode = new TimerNode("timer-1", 1000);
        LogNode logNode = new LogNode("log-1");
        FilterNode filterNode = new FilterNode("filter-1", "tick", 3.0);
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection1 = new Connection("timer-log-connect");
        connection1.setTarget(logNode.getInputPort("in"));
        timerNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("log-filter-connect");
        connection2.setTarget(filterNode.getInputPort("in"));
        logNode.getOutputPort("out").connect(connection2);

        Connection connection3 = new Connection("filter-printer-connect");
        connection3.setTarget(printNode.getInputPort("in"));
        filterNode.getOutputPort("out").connect(connection3);

        timerNode.initialize();
        logNode.initialize();
        filterNode.initialize();
        printNode.initialize();

        Thread.sleep(7000);

        timerNode.shutdown();
        logNode.shutdown();
        filterNode.shutdown();
        printNode.shutdown();
    }
}
