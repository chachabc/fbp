package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.DelayNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DelayNodeTest {
    private DelayNode delayNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        delayNode = new DelayNode("delay-1", 500);
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
        delayNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("지연 후 전달")
    void test1(){
        long start = System.currentTimeMillis();
        delayNode.process(new Message(Map.of("key", "value")));
        long end = System.currentTimeMillis() - start;

        Assertions.assertTrue(end >= 500);
        Assertions.assertEquals(1, received.size());
    }

    @Test
    @DisplayName("메시지 내용 보존")
    void test2(){
        delayNode.process(new Message(Map.of("key", "value")));
        Assertions.assertEquals("value", received.getFirst().get("key"));
    }
}
