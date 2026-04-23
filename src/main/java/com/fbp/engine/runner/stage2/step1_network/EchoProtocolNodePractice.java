package com.fbp.engine.runner.stage2.step1_network;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.CollectorNode;
import com.fbp.engine.node.stage2.EchoProtocolNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EchoProtocolNodePractice {
    private static final int PORT = 9100;
    private static final Logger log = LoggerFactory.getLogger(EchoProtocolNodePractice.class);

    public static void main(String[] args) throws InterruptedException {
        EchoServer server = new EchoServer(PORT);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try { server.start(); } catch (Exception ignored) {}
        });
        Thread.sleep(200);

        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", PORT);

        EchoProtocolNode node = new EchoProtocolNode("echo-node", config);

        CollectorNode collectorNode = new CollectorNode("collector");
        Connection connection = new Connection("echo-out");
        connection.setTarget(collectorNode.getInputPort("in"));
        node.getOutputPort("out").connect(connection);

        log.info("===연결 시도===");
        node.initialize();
        log.info("연결 상태: {}", node.getConnectionState());

        log.info("===메시지 전송===");
        node.process(new Message(Map.of("text", "Hello FBP")));
        node.process(new Message(Map.of("text", "Echo Test")));
        Thread.sleep(200);

        log.info("===에코 응답 수신 결과===");
        collectorNode.getCollected().forEach(message ->
                log.info("echoResponse: {}", (String) message.get("echoResponse")));

        log.info("===연결 해제===");
        node.shutdown();
        log.info("연결 상태: {}", node.getConnectionState());

        server.stop();
        executorService.shutdownNow();
    }
}
