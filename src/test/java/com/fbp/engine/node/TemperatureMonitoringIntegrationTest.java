package com.fbp.engine.node;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.CollectorNode;
import com.fbp.engine.node.stage1.TemperatureSensorNode;
import com.fbp.engine.node.stage1.ThresholdFilterNode;
import com.fbp.engine.node.stage1.TimerNode;
import org.junit.jupiter.api.*;

import java.util.List;

class TemperatureMonitoringIntegrationTest {
    private FlowEngine engine;
    private CollectorNode alertCollector;
    private CollectorNode normalCollector;
    private TimerNode timerNode;

    @BeforeEach
    void setUp(){
        alertCollector = new CollectorNode("alert-collector");
        normalCollector = new CollectorNode("normal-collector");
        timerNode = new TimerNode("timer-1", 500);

        Flow flow = new Flow("temperature-monitoring");
        flow.addNode(timerNode)
                .addNode(new TemperatureSensorNode("sensor-1", 15, 45))
                .addNode(new ThresholdFilterNode("threshold-1", "temperature", 30))
                .addNode(alertCollector)
                .addNode(normalCollector);

        flow.connect("timer-1", "out", "sensor-1", "trigger")
                .connect("sensor-1", "out", "threshold-1", "in")
                .connect("threshold-1", "alert", "alert-collector", "in")
                .connect("threshold-1", "normal", "normal-collector", "in");

        engine = new FlowEngine();
        engine.register(flow);
    }

    @AfterEach
    void tearDown(){
        engine.shutdown();
    }

    @Test
    @DisplayName("alert 경로 검증")
    void test1() throws InterruptedException {
        engine.startFlow("temperature-monitoring");
        Thread.sleep(5000);
        engine.shutdown();

        List<Message> alerts = alertCollector.getCollected();
        for (Message message : alerts){
            double temp = ((Number) message.get("temperature")).doubleValue();
            Assertions.assertTrue(temp > 30);
        }
    }

    @Test
    @DisplayName("normal 경로 검증")
    void test2() throws InterruptedException{
        engine.startFlow("temperature-monitoring");
        Thread.sleep(5000);
        engine.shutdown();

        List<Message> normals = normalCollector.getCollected();
        for (Message message : normals){
            double temp = ((Number) message.get("temperature")).doubleValue();
            Assertions.assertTrue(temp <= 30);
        }
    }

    @Test
    @DisplayName("전체 메시지 수")
    void test3() throws InterruptedException{
        engine.startFlow("temperature-monitoring");
        Thread.sleep(5000);
        engine.shutdown();

        int total = alertCollector.getCollected().size()
                    + normalCollector.getCollected().size();
        Assertions.assertTrue(Math.abs(timerNode.getTickCount() - total) <= 1);
    }
}
