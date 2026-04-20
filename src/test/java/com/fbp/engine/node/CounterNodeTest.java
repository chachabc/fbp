package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.CounterNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CounterNodeTest {
    private CounterNode counterNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        counterNode = new CounterNode("counter-1");
        received = new ArrayList<>();

        Connection connection = new Connection("connect");
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
        counterNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("count키 추가")
    void test1(){
        counterNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(1, (int) received.getFirst().get("count"));
    }

    @Test
    @DisplayName("count 누적")
    void test2(){
        counterNode.process(new Message(Map.of("temperature", 25.0)));
        counterNode.process(new Message(Map.of("temperature", 25.0)));
        counterNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(3, (int) received.getLast().get("count"));
    }

    @Test
    @DisplayName("원본 키 유지")
    void test3(){
        counterNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(25.0, received.getFirst().get("temperature"));
    }
}
