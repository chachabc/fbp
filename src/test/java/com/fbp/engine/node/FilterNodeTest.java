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

class FilterNodeTest {
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
        filterNode.getOutputPort().connect(connection);
    }

    @Test
    @DisplayName("조건 만족 시 통과")
    void test1(){
        Message message = new Message(Map.of("temperature", 35.0));
        filterNode.process(message);

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(35.0, (double) received.get(0).get("temperature"));
    }

    @Test
    @DisplayName("조건 미달 시 차단")
    void test2(){
        Message message = new Message(Map.of("temperature", 25.0));
        filterNode.process(message);

        Assertions.assertEquals(0, received.size());
    }

    @Test
    @DisplayName("경계값 처리")
    void test3(){
        Message message = new Message(Map.of("temperature", 30.0));
        filterNode.process(message);

        Assertions.assertEquals(1, received.size());
    }

    @Test
    @DisplayName("키 없는 메시지")
    void test4(){
        Message message = new Message(Map.of("humidity", 70.0));
        Assertions.assertDoesNotThrow(() -> filterNode.process(message));
        Assertions.assertEquals(0, received.size());
    }
}
