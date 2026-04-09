package com.fbp.engine.runner.step5_abstract;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

public class TimerFilterPractice {
    public static void main(String[] args) throws InterruptedException{
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

        Thread.sleep(7000);
        timerNode.shutdown();
        filterNode.shutdown();
        printNode.shutdown();
    }

}
