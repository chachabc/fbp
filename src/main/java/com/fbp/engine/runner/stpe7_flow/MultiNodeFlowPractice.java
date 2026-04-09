package com.fbp.engine.runner.stpe7_flow;

import com.fbp.engine.core.Flow;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.LogNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

public class MultiNodeFlowPractice {
    public static void main(String[] args) throws InterruptedException{
        Flow flow = new Flow("mulit-node-pipeline");
        flow.addNode(new TimerNode("timer-1", 1000))
                .addNode(new LogNode("log-1"))
                .addNode(new FilterNode("filter-1", "tick", 3.0))
                .addNode(new PrintNode("printer-1"));

        flow.connect("timer-1", "out", "log-1", "in")
                .connect("log-1", "out", "filter-1", "in")
                .connect("filter-1", "out", "printer-1", "in");

        flow.initialize();
        Thread.sleep(10000);
        flow.shutdown();
    }
}
