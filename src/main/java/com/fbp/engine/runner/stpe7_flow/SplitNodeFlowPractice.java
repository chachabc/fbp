package com.fbp.engine.runner.stpe7_flow;

import com.fbp.engine.core.Flow;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.SplitNode;
import com.fbp.engine.node.TimerNode;

import javax.swing.*;

public class SplitNodeFlowPractice {
    public static void main(String[] args) throws InterruptedException{
        Flow flow = new Flow("split-flow");

        flow.addNode(new TimerNode("timer-1", 500))
                .addNode(new SplitNode("split-1", "tick", 3.0))
                .addNode(new PrintNode("alert-printer"))
                .addNode(new PrintNode("normal-printer"));

        flow.connect("timer-1", "out", "split-1", "in")
                .connect("split-1", "match", "alert-printer", "in")
                .connect("split-1", "mismatch", "normal-printer", "in");

        flow.initialize();
        Thread.sleep(5000);
        flow.shutdown();
    }
}
