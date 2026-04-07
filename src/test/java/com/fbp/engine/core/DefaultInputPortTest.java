package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DefaultInputPortTest {

    @Test
    @DisplayName("receive 시 owner 호출")
    void test1(){
        List<Message> processed = new ArrayList<>();

        Node node = new Node() {
            @Override
            public String getId() {
                return "fake";
            }

            @Override
            public void process(Message message) {
                processed.add(message);
            }
        };

        DefaultInputPort inputPort = new DefaultInputPort("in", node);

        Message message = new Message(Map.of("temperature", 25.5));
        inputPort.receive(message);

        Assertions.assertEquals(1, processed.size());
        Assertions.assertEquals(25.5, (double) processed.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("포트 이름 확인")
    void test2(){
        Node node = new Node() {
            @Override
            public String getId() {
                return "fake";
            }

            @Override
            public void process(Message message) {
            }
        };
        DefaultInputPort inputPort = new DefaultInputPort("trigger", node);
        Assertions.assertEquals("trigger", inputPort.getName());
    }
}
