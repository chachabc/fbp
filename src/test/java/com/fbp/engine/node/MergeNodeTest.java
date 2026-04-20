package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.MergeNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MergeNodeTest {
    private MergeNode mergeNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        mergeNode = new MergeNode("merge-1");
        received = new ArrayList<>();

        Connection connection =new Connection("merge-out-connect");
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
        mergeNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("양쪽 입력 수신")
    void test1(){
        Assertions.assertDoesNotThrow(() -> {
            mergeNode.getInputPort("in-1").receive(new Message(Map.of("temperature", 25.0)));
            mergeNode.getInputPort("in-2").receive(new Message(Map.of("humidity", 60.0)));
        });
    }

    @Test
    @DisplayName("합쳐진 메시지 출력")
    void test2(){

        mergeNode.getInputPort("in-1").receive(new Message(Map.of("temperature", 25.0)));
        mergeNode.getInputPort("in-2").receive(new Message(Map.of("humidity", 60.0)));

        Assertions.assertEquals(1, received.size());
        Assertions.assertEquals(25.0, received.getFirst().get("temperature"));
        Assertions.assertEquals(60.0, received.getFirst().get("humidity"));
    }

    @Test
    @DisplayName("한쪽만 도착 시 대기")
    void test3(){
        mergeNode.getInputPort("in-1").receive(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(0, received.size());
    }

    @Test
    @DisplayName("포트 구성 확인")
    void test4(){
        Assertions.assertNotNull(mergeNode.getInputPort("in-1"));
        Assertions.assertNotNull(mergeNode.getInputPort("in-2"));
        Assertions.assertNotNull(mergeNode.getOutputPort("out"));
    }
}
