package com.fbp.engine.runner.step8_flowengine;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;

public class FlowStatePractice {
    public static void main(String[] args) throws InterruptedException{
        Flow flow1 = new Flow("flow-1");
        flow1.addNode(new TimerNode("timer-1", 500))
                .addNode(new PrintNode("printer-1"))
                .connect("timer-1", "out", "printer-1", "in");

        Flow flow2 = new Flow("flow-2");
        flow2.addNode(new TimerNode("timer-2", 1000))
                .addNode(new PrintNode("printer-2"))
                .connect("timer-2", "out", "printer-2", "in");
        FlowEngine engine = new FlowEngine();

        engine.register(flow1);
        engine.register(flow2);

        engine.startFlow("flow-1");
        engine.listFlows();
        engine.startFlow("flow-2");
        engine.listFlows();
        Thread.sleep(5000);

        engine.shutdown();
        engine.listFlows();
    }
}
