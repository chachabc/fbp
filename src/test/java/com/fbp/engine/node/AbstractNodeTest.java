package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AbstractNodeTest {
    static class TestNode extends AbstractNode{
        List<Message> processed = new ArrayList<>();

        TestNode(String id){
            super(id);
        }

        public void setupInputPort(String name){
            addInputPort(name);
        }

        public void setupOutputPort(String name){
            addOutputPort(name);
        }

        public void sendMessage(String portName, Message message) {
            send(portName, message);
        }

        @Override
        protected void onProcess(Message message){
            processed.add(message);
        }
    }

    @Test
    @DisplayName("getId 반환")
    void test1(){
        TestNode node = new TestNode("test-node");
        Assertions.assertEquals("test-node", node.getId());
    }

    @Test
    @DisplayName("addInputPort 등록")
    void test2(){
        TestNode node = new TestNode("test-node");
        node.setupInputPort("in");
        Assertions.assertNotNull(node.getInputPort("in"));
    }

    @Test
    @DisplayName("addOutputPort 등록")
    void test3(){
        TestNode node = new TestNode("test-node");
        node.setupOutputPort("out");
        Assertions.assertNotNull(node.getOutputPort("out"));
    }

    @Test
    @DisplayName("미등록 포트 조회")
    void test4(){
        TestNode node = new TestNode("test-node");
        Assertions.assertNull(node.getInputPort("none"));
    }

    @Test
    @DisplayName("process -> onProcess 호출")
    void test5(){
        TestNode node = new TestNode("test-node");
        Message message = new Message(Map.of("key", "value"));
        node.process(message);

        Assertions.assertEquals(1, node.processed.size());
        Assertions.assertEquals("value", node.processed.getFirst().get("key"));
    }

    @Test
    @DisplayName("send로 메시지 전달")
    void test6(){
        TestNode sender = new TestNode("sender");
        sender.setupOutputPort("out");

        TestNode receiver = new TestNode("receiver");
        receiver.setupInputPort("in");

        Connection connection = new Connection("connect-1");
        connection.setTarget(receiver.getInputPort("in"));
        sender.getOutputPort("out").connect(connection);

        Message message = new Message(Map.of("data", "hello"));
        sender.sendMessage("out", message);

        Assertions.assertEquals(1, receiver.processed.size());
        Assertions.assertEquals("hello", receiver.processed.getFirst().get("data"));
    }
}
