package com.fbp.engine.runner.step10_refactor;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.*;

public class FinalIntegration {
    public static void main(String[] args) throws InterruptedException {
        Flow flow = new Flow("final-monitoring");

        flow.addNode(new TimerNode("timer-1", 500))
                .addNode(new TemperatureSensorNode("sensor-1", 15, 45))
                .addNode(new ThresholdFilterNode("threshold-1", "temperature", 30))
                .addNode(new AlertNode("alert-1"))
                .addNode(new LogNode("log-1"))
                .addNode(new FileWriterNode("file-writer-1", "src/main/resources/normal-temperature.log"));

        flow.connect("timer-1", "out", "sensor-1", "trigger")
                .connect("sensor-1", "out", "threshold-1", "in")
                .connect("threshold-1", "alert", "alert-1", "in")
                .connect("threshold-1", "normal", "log-1", "in")
                .connect("log-1", "out", "file-writer-1", "in");

        FlowEngine engine = new FlowEngine();
        engine.register(flow);
        engine.startFlow("final-monitoring");

        Thread.sleep(10000);

        engine.shutdown();
    }
}
