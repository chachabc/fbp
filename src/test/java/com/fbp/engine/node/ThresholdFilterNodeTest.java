package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.ThresholdFilterNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ThresholdFilterNodeTest {
    private ThresholdFilterNode filterNode;
    private List<Message> alertReceived;
    private List<Message> normalReceived;

    @BeforeEach
    void setUp(){
        filterNode = new ThresholdFilterNode("threshold-1", "temperature", 30.0);
        alertReceived = new ArrayList<>();
        normalReceived = new ArrayList<>();

        Connection alertConn = new Connection("alert-connect");
        alertConn.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                alertReceived.add(message);
            }
        });
        filterNode.getOutputPort("alert").connect(alertConn);

        Connection normalConn = new Connection("normal-conn");
        normalConn.setTarget(new InputPort() {
            @Override public String getName() { return "in"; }
            @Override public void receive(Message message) { normalReceived.add(message); }
        });
        filterNode.getOutputPort("normal").connect(normalConn);
    }

    @Test
    @DisplayName("초과 → alert 포트")
    void test1() {
        filterNode.process(new Message(Map.of("temperature", 35.0)));
        Assertions.assertEquals(1, alertReceived.size());
        Assertions.assertEquals(0, normalReceived.size());
    }

    @Test
    @DisplayName("이하 → normal 포트")
    void test2() {
        filterNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(0, alertReceived.size());
        Assertions.assertEquals(1, normalReceived.size());
    }

    @Test
    @DisplayName("경계값 — 정확히 같은 값")
    void test3() {
        filterNode.process(new Message(Map.of("temperature", 30.0)));
        Assertions.assertEquals(0, alertReceived.size());
        Assertions.assertEquals(1, normalReceived.size());
    }

    @Test
    @DisplayName("키 없는 메시지")
    void test4() {
        Assertions.assertDoesNotThrow(() ->
                filterNode.process(new Message(Map.of("humidity", 70.0)))
        );
        Assertions.assertEquals(0, alertReceived.size());
        Assertions.assertEquals(0, normalReceived.size());
    }

    @Test
    @DisplayName("양쪽 동시 검증")
    void test5() {
        filterNode.process(new Message(Map.of("temperature", 35.0)));
        filterNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(1, alertReceived.size());
        Assertions.assertEquals(1, normalReceived.size());
    }
}
