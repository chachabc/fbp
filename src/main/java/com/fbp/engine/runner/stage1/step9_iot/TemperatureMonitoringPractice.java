package com.fbp.engine.runner.stage1.step9_iot;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.stage1.*;

public class TemperatureMonitoringPractice {
    public static void main(String[] args) throws InterruptedException {
        Flow flow = new Flow("temperature-monitoring");

        flow.addNode(new TimerNode("timer-1", 1000))
                .addNode(new TemperatureSensorNode("temp-sensor-1", 15, 45))
                .addNode(new ThresholdFilterNode("temp-threshold-1", "temperature", 30))
                .addNode(new AlertNode("temp-alert-1"))
                .addNode(new LogNode("temp-log-1"))
                .addNode(new FileWriterNode("temp-writer-1", "src/main/resources/normal-temperature.log"))

                .addNode(new HumiditySensorNode("humid-sensor-1", 30, 90))
                .addNode(new ThresholdFilterNode("humid-threshold-1", "humidity", 70))
                .addNode(new AlertNode("humid-alert"))
                .addNode(new LogNode("humid-log"))
                .addNode(new FileWriterNode("humid-writer-1", "src/main/resources/normal-humidity.log"))

                .connect("timer-1", "out", "temp-sensor-1", "trigger")
                .connect("temp-sensor-1", "out", "temp-threshold-1", "in")
                .connect("temp-threshold-1", "alert", "temp-alert-1", "in")
                .connect("temp-threshold-1", "normal", "temp-log-1", "in")
                .connect("temp-threshold-1", "normal", "temp-writer-1", "in")

                .connect("timer-1", "out", "humid-sensor-1", "trigger")
                .connect("humid-sensor-1", "out", "humid-threshold-1", "in")
                .connect("humid-threshold-1", "alert", "humid-alert", "in")
                .connect("humid-threshold-1", "normal", "humid-log", "in")
                .connect("humid-threshold-1", "normal", "humid-writer-1", "in");
        FlowEngine engine = new FlowEngine();
        engine.register(flow);
        engine.startFlow("temperature-monitoring");

        Thread.sleep(10000);

        engine.shutdown();
    }
}
