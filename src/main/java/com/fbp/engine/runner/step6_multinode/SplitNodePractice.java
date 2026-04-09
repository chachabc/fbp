package com.fbp.engine.runner.step6_multinode;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.SplitNode;
import com.fbp.engine.node.TimerNode;

public class SplitNodePractice {
    public static void main(String[] args) throws InterruptedException{
        //step6-4
        TimerNode timerNode = new TimerNode("timer-1", 500);
        SplitNode splitNode = new SplitNode("split-1", "tick", 3.0);
        PrintNode alertPrinter = new PrintNode("경고");
        PrintNode normalPrinter = new PrintNode("정상");

        Connection connection1 = new Connection("timer-split-connect");
        connection1.setTarget(splitNode.getInputPort("in"));
        timerNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("split-match-connect");
        connection2.setTarget(alertPrinter.getInputPort("in"));
        splitNode.getOutputPort("match").connect(connection2);

        Connection connection3 = new Connection("split-mismatch-connect");
        connection3.setTarget(normalPrinter.getInputPort("in"));
        splitNode.getOutputPort("mismatch").connect(connection3);

        timerNode.initialize();

        Thread.sleep(4000);

        timerNode.shutdown();
    }

}
