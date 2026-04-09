package com.fbp.engine.runner.step8_flowengine;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

public class FlowEnginePractice {
    public static void main(String[] args) throws InterruptedException{
        Flow flow = new Flow("monitoring");
        flow.addNode(new TimerNode("timer-1", 1000))
                .addNode(new FilterNode("filter-1", "tick", 3.0))
                .addNode(new PrintNode("printer-1"))
                .connect("timer-1", "out", "filter-1", "in")
                .connect("filter-1", "out", "printer-1", "in");

        FlowEngine engine = new FlowEngine();
        engine.register(flow);
        engine.startFlow("monitoring");

        Thread.sleep(7000);

        engine.shutdown();
    }
}
