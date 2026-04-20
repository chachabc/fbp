package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.TransformNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransformNodeTest {
    private TransformNode transformNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        received = new ArrayList<>();
        transformNode = new TransformNode("transform-1", message -> {
            Double value = message.get("temperature");
            return message.withEntry("temperature", value * 2);
        });

        Connection connection = new Connection("connect-1");
        connection.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                received.add(message);
            }
        });
        transformNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("변환 정상 동작")
    void test1(){
        transformNode.process(new Message(Map.of("temperature", 10.0)));

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(20.0, received.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("null 반환 시 미전달")
    void test2(){
        TransformNode nullTransformer = new TransformNode("null-transform", message -> null);
        Connection connection = new Connection("connect");

        List<Message> nullReceived = new ArrayList<>();
        connection.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                nullReceived.add(message);
            }
        });
        nullTransformer.getOutputPort("out").connect(connection);

        nullTransformer.process(new Message(Map.of("temperature", 10.0)));
        Assertions.assertEquals(0, nullReceived.size());
    }

    @Test
    @DisplayName("원본 메시지 불변")
    void test3(){
        Message message = new Message(Map.of("temperature", 10.0));
        transformNode.process(message);
        Assertions.assertEquals(10.0, message.get("temperature"));
    }
}
