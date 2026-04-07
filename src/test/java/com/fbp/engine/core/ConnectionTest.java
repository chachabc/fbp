package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ConnectionTest {
    private Connection connection;

    private InputPort collectingPort(List<Message> bucket) {
        return new InputPort() {
            @Override public String getName() { return "in"; }
            @Override public void receive(Message message) { bucket.add(message); }
        };
    }

    @BeforeEach
    void setUp(){
        connection = new Connection("test-connect");
    }

    @Test
    @DisplayName("deliver 후 target 수신")
    void test1(){
        List<Message> received = new ArrayList<>();
        connection.setTarget(collectingPort(received));

        Message msg = new Message(Map.of("temperature", 25.5));
        connection.deliver(msg);

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(25.5, received.get(0).get("temperature"));
    }

    @Test
    @DisplayName("target 미설정 시 동작")
    void test2(){
        Message message = new Message(Map.of("temperature", 25.5));
        Assertions.assertDoesNotThrow(() -> connection.deliver(message));
    }

    @Test
    @DisplayName("버퍼 크기 확인")
    void test3(){
        connection.deliver(new Message(Map.of("key", "value1")));
        connection.deliver(new Message(Map.of("key", "value2")));
        Assertions.assertEquals(2, connection.getBufferSize());
    }

    @Test
    @DisplayName("다수 메시지 순서 보장")
    void test4(){
        List<Message> received = new ArrayList<>();
        connection.setTarget(collectingPort(received));

        connection.deliver(new Message(Map.of("key", 1)));
        connection.deliver(new Message(Map.of("key", 2)));
        connection.deliver(new Message(Map.of("key", 3)));

        Assertions.assertEquals(3, received.size());
        Assertions.assertEquals(1, (int) received.get(0).get("key"));
        Assertions.assertEquals(2, (int) received.get(1).get("key"));
        Assertions.assertEquals(3, (int) received.get(2).get("key"));
    }
}
