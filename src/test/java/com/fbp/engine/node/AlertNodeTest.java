package com.fbp.engine.node;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AlertNodeTest {
    private AlertNode alertNode;

    @BeforeEach
    void setUp(){
        alertNode = new AlertNode("alert-1");
    }

    @Test
    @DisplayName("정상 처리")
    void test1(){
        Assertions.assertDoesNotThrow(() ->
                alertNode.process(new Message(Map.of(
                        "sensorId", "sensor-1",
                        "temperature", 35.0
                ))));
    }

    @Test
    @DisplayName("키 누락 시 처리")
    void test2(){
        Assertions.assertDoesNotThrow(() ->
                alertNode.process(new Message(Map.of("sensorId", "sensor-1"))));
    }

}
