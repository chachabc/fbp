package com.fbp.engine.node;

import com.fbp.engine.core.Node;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.PrintNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PrintNodeTest {
    @Test
    @DisplayName("getId 반환")
    void test1(){
        PrintNode printNode = new PrintNode("printer-1");
        Assertions.assertEquals("printer-1", printNode.getId());
    }

    @Test
    @DisplayName("process 정상 동작")
    void test2(){
        PrintNode printNode = new PrintNode("printer-1");
        Message message = new Message(Map.of("temperature", 25.5));
        Assertions.assertDoesNotThrow(() -> printNode.process(message));
    }

    @Test
    @DisplayName("Node 인터페이스 구현")
    void test3(){
        Node node = new PrintNode("printer-1");
        Assertions.assertNotNull(node);
        Assertions.assertInstanceOf(Node.class, node);
    }

    @Test
    @DisplayName("InputPort 조회")
    void test4(){
        PrintNode node = new PrintNode("printer-1");
        Assertions.assertNotNull(node.getInputPort("in"));
    }

    @Test
    @DisplayName("InputPort를 통한 수신")
    void test5(){
        PrintNode node = new PrintNode("printer-1");
        Message message = new Message(Map.of("temperature", 25.5));

        Assertions.assertDoesNotThrow(() -> node.getInputPort("in").receive(message));
    }
}
