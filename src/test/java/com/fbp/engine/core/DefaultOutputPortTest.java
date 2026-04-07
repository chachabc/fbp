package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DefaultOutputPortTest {
    private DefaultOutputPort outputPort;

    private InputPort collectingPort(List<Message> bucket){
        return new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                bucket.add(message);
            }
        };
    }

    @BeforeEach
    void setUp(){
        outputPort = new DefaultOutputPort("out");
    }

    @Test
    @DisplayName("단일 Connection 전달")
    void test1(){
        List<Message> received = new ArrayList<>();

        Connection connection = new Connection("connect-1");
        connection.setTarget(collectingPort(received));
        outputPort.connect(connection);

        Message message = new Message(Map.of("temperature", 25.5));
        outputPort.send(message);

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(25.5, (double) received.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("다중 Connection 전달")
    void test2(){
        List<Message> received1 = new ArrayList<>();
        List<Message> received2 = new ArrayList<>();

        Connection connection1 = new Connection("connect-1");
        Connection connection2 = new Connection("connect-2");
        connection1.setTarget(collectingPort(received1));
        connection2.setTarget(collectingPort(received2));

        outputPort.connect(connection1);
        outputPort.connect(connection2);

        Message message = new Message(Map.of("temperature", 25.5));
        outputPort.send(message);

        Assertions.assertEquals(1, received1.size());
        Assertions.assertEquals(1, received2.size());
        Assertions.assertEquals(25.5, (double) received1.getFirst().get("temperature"));
        Assertions.assertEquals(25.5, (double) received2.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("Connection 미연결 시")
    void test3(){
        Message message = new Message(Map.of("temperature", 25.5));
        Assertions.assertDoesNotThrow(() -> outputPort.send(message));
    }
}
