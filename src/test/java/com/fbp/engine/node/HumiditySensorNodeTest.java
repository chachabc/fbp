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

class HumiditySensorNodeTest {
    private HumiditySensorNode humiditySensorNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        humiditySensorNode = new HumiditySensorNode("humid-sensor-1", 30, 90);
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
        humiditySensorNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("습도 범위 확인")
    void test1() {
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        double humidity = ((Number) received.getFirst().get("humidity")).doubleValue();
        Assertions.assertTrue(humidity >= 30 && humidity <= 90);
    }

    @Test
    @DisplayName("필수 키 포함")
    void test2() {
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        Message message = received.getFirst();
        Assertions.assertTrue(message.hasKey("sensorId"));
        Assertions.assertTrue(message.hasKey("humidity"));
        Assertions.assertTrue(message.hasKey("unit"));
    }

    @Test
    @DisplayName("sensorId 일치")
    void test3() {
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        Assertions.assertEquals("humid-sensor-1", received.getFirst().get("sensorId"));
    }

    @Test
    @DisplayName("트리거마다 생성")
    void test4() {
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        humiditySensorNode.process(new Message(Map.of("trigger", true)));
        Assertions.assertEquals(3, received.size());
    }
}
