package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.GeneratorNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GeneratorNodeTest {
    private GeneratorNode generatorNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        generatorNode = new GeneratorNode("generator-1");
        received = new ArrayList<>();

        Connection connection = new Connection("connect");
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
        generatorNode.getOutputPort("out").connect(connection);
    }

    @Test
    @DisplayName("generator 메시지 생성")
    void test1(){
        generatorNode.generate("temperature", 25.5);
        Assertions.assertEquals(1, received.size());
    }

    @Test
    @DisplayName("메시지 내용 확인")
    void test2(){
        generatorNode.generate("temperature", 25.5);
        Assertions.assertEquals(25.5, (double) received.getFirst().get("temperature"));
    }

    @Test
    @DisplayName("OutputPort 조회")
    void test3(){
        Assertions.assertNotNull(generatorNode.getOutputPort("out"));
    }

    @Test
    @DisplayName("다수 generate 호출")
    void test4(){
        generatorNode.generate("key", 1.0);
        generatorNode.generate("key", 2.0);
        generatorNode.generate("key", 3.0);

        Assertions.assertEquals(3, received.size());
        Assertions.assertEquals(1, (double) received.get(0).get("key"));
        Assertions.assertEquals(2, (double) received.get(1).get("key"));
        Assertions.assertEquals(3, (double) received.get(2).get("key"));
    }
}
