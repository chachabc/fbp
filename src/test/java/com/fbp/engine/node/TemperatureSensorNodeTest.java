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

public class TemperatureSensorNodeTest {
    private TemperatureSensorNode sensorNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        sensorNode = new TemperatureSensorNode("temp-sensor-1", 15, 25);
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
        sensorNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("온도 범위 확인")
    void test1(){
        sensorNode.process(new Message(Map.of("trigger", true)));
        double temperature = ((Number) received.getFirst().get("temperature")).doubleValue();
        Assertions.assertTrue(temperature >= 15 && temperature <= 45);
    }

    @Test
    @DisplayName("필수 키 포함")
    void test2(){
        sensorNode.process(new Message(Map.of("trigger", true)));
        Message message = received.getFirst();
        Assertions.assertTrue(message.hasKey("sensorId"));
        Assertions.assertTrue(message.hasKey("temperature"));
        Assertions.assertTrue(message.hasKey("unit"));
        Assertions.assertTrue(message.hasKey("timestamp"));
    }


    @Test
    @DisplayName("sensorId 일치")
    void test3(){
        sensorNode.process(new Message(Map.of("trigger", true)));
        Assertions.assertEquals("temp-sensor-1", received.getFirst().get("sensorId"));
    }

    @Test
    @DisplayName("트리거 마다 생성")
    void test4(){
        sensorNode.process(new Message(Map.of("trigger", true)));
        sensorNode.process(new Message(Map.of("trigger", true)));
        sensorNode.process(new Message(Map.of("trigger", true)));
        sensorNode.process(new Message(Map.of("trigger", true)));
        Assertions.assertEquals(4, received.size());
    }
}
