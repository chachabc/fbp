package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class LogNodeTest {
    private LogNode logNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        logNode = new LogNode("log-1");
        received = new ArrayList<>();

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
        logNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("메시지 통과 전달")
    void test1(){
        Message message = new Message(Map.of("temperature", 25.5));
        logNode.process(message);

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(25.5, received.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("중간 삽입 가능")
    void test2(){
        GeneratorNode nodeA = new GeneratorNode("generator-A");

        Connection conn = new Connection("connect-A-log");
        conn.setTarget(logNode.getInputPort("in"));
        nodeA.getOutputPort("out").connect(conn);

        nodeA.generate("data", "hello");

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals("hello", received.get(0).get("data"));
    }
}
