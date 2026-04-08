package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AbstractFilterNodeTest {
    private FilterNode filterNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        filterNode = new FilterNode("filter-1", "temperature", 30.0);
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
        filterNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("조건 만족 -> send 호출")
    void test1(){
        filterNode.process(new Message(Map.of("temperature", 35.0)));
        Assertions.assertEquals(1, received.size());
    }

    @Test
    @DisplayName("조건 미달 -> 차단")
    void test2(){
        filterNode.process(new Message(Map.of("temperature", 25.5)));
        Assertions.assertEquals(0, received.size());
    }

    @Test
    @DisplayName("포트 구성 확인")
    void test3(){
        Assertions.assertNotNull(filterNode.getInputPort("in"));
        Assertions.assertNotNull(filterNode.getOutputPort("out"));
    }
}
