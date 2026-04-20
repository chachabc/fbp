package com.fbp.engine.node;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FinalIntegrationTest {
    private FlowEngine flowEngine;
    private CollectorNode alertCollector;
    private CollectorNode normalCollector;
    private Path logFile;
    private TimerNode timerNode;

    @BeforeEach
    void setUp() throws IOException {
        logFile = Files.createTempFile("final-test", ".log");

        timerNode = new TimerNode("timer-1", 500);
        alertCollector = new CollectorNode("alert-collector");
        normalCollector = new CollectorNode("normal-collector");

        Flow flow = new Flow("final-monitoring");
        flow.addNode(timerNode)
                .addNode(new TemperatureSensorNode("sensor-1", 15, 45))
                .addNode(new ThresholdFilterNode("threshold-1", "temperature", 30))
                .addNode(alertCollector)
                .addNode(normalCollector)
                .addNode(new FileWriterNode("file-writer-1", logFile.toString()));

        flow.connect("timer-1", "out", "sensor-1", "trigger")
                .connect("sensor-1", "out", "threshold-1", "in")
                .connect("threshold-1", "alert", "alert-collector", "in")
                .connect("threshold-1", "normal", "normal-collector", "in")
                .connect("threshold-1", "normal", "file-writer-1", "in");

        flowEngine = new FlowEngine();
        flowEngine.register(flow);
    }

    @AfterEach
    void tearDown() throws IOException {
        flowEngine.shutdown();
        Files.deleteIfExists(logFile);
    }

    @Test
    @DisplayName("엔진 시작/종료")
    void test1(){
        flowEngine.startFlow("final-monitoring");
        Assertions.assertEquals(FlowEngine.State.RUNNING, flowEngine.getState());

        flowEngine.shutdown();
        Assertions.assertEquals(FlowEngine.State.STOPPED, flowEngine.getState());
    }

    @Test
    @DisplayName("alert 경로 정확성")
    void test2() throws InterruptedException {
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        List<Message> alerts = alertCollector.getCollected();
        Assertions.assertFalse(alerts.isEmpty());
        for (Message message : alerts) {
            double temp = ((Number) message.get("temperature")).doubleValue();
            Assertions.assertTrue(temp > 30);
        }
    }

    @Test
    @DisplayName("normal 경로 정확성")
    void test3() throws InterruptedException {
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        List<Message> normals = normalCollector.getCollected();
        Assertions.assertFalse(normals.isEmpty());
        for (Message message : normals) {
            double temp = ((Number) message.get("temperature")).shortValue();
            Assertions.assertTrue(temp <= 30);
        }
    }

    @Test
    @DisplayName("전체 분기 완전성")
    void test4() throws InterruptedException {
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        int alertCount = alertCollector.getCollected().size();
        int normalCount = normalCollector.getCollected().size();
        int tickCount = timerNode.getTickCount();

        Assertions.assertTrue(Math.abs(tickCount - (alertCount + normalCount)) <= 1);
    }

    @Test
    @DisplayName("파일 기록 검증")
    void test5() throws InterruptedException, IOException{
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        List<String> lines = Files.readAllLines(logFile);
        int normalCount = normalCollector.getCollected().size();
        Assertions.assertEquals(normalCount, lines.size());
    }

    @Test
    @DisplayName("센서 데이터 형식")
    void test6() throws InterruptedException{
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        List<Message> all = alertCollector.getCollected();
        all.addAll(normalCollector.getCollected());

        for (Message message : all){
            Assertions.assertTrue(message.hasKey("sensorId"));
            Assertions.assertTrue(message.hasKey("temperature"));
            Assertions.assertTrue(message.hasKey("unit"));
        }
    }

    @Test
    @DisplayName("온도 범위")
    void test7() throws InterruptedException {
        flowEngine.startFlow("final-monitoring");
        Thread.sleep(5000);
        flowEngine.shutdown();

        List<Message> all = alertCollector.getCollected();
        all.addAll(normalCollector.getCollected());

        for (Message message : all) {
            double temp = ((Number) message.get("temperature")).doubleValue();
            Assertions.assertTrue(temp >= 15.0 && temp <= 45.0);
        }
    }
}
