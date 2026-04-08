package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AbstractPrintNodeTest {

    @Test
    @DisplayName("포트 구성 확인")
    void test1(){
        PrintNode printNode = new PrintNode("printer-1");
        Assertions.assertNotNull(printNode.getInputPort("in"));
    }

    @Test
    @DisplayName("process 정상 동작")
    void test2(){
        PrintNode printNode = new PrintNode("printer-1");
        Message message = new Message(Map.of("temperature", 25.5));

        Assertions.assertDoesNotThrow(() -> printNode.process(message));
    }

    @Test
    @DisplayName("AbstractNode 상속 확인")
    void test3(){
        PrintNode printNode = new PrintNode("printer-1");
        Assertions.assertInstanceOf(AbstractNode.class, printNode);
    }
}
