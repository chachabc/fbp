package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.SplitNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SplitNodeTest {
    private SplitNode splitNode;
    private List<Message> matchReceived;
    private List<Message> mismatchReceived;

    @BeforeEach
    void setUp(){
        splitNode = new SplitNode("split-1", "temperature", 30.0);
        matchReceived = new ArrayList<>();
        mismatchReceived = new ArrayList<>();

        Connection matchCon = new Connection("split-match-connect");
        matchCon.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                matchReceived.add(message);
            }
        });
        splitNode.getOutputPort("match").connect(matchCon);

        Connection mismatchCon = new Connection("split-mismatch-connect");
        mismatchCon.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                mismatchReceived.add(message);
            }
        });
        splitNode.getOutputPort("mismatch").connect(mismatchCon);
    }

    @Test
    @DisplayName("조건 만족 -> match 포트")
    void test1(){
        splitNode.process(new Message(Map.of("temperature", 35.0)));
        Assertions.assertEquals(1, matchReceived.size());
        Assertions.assertEquals(0, mismatchReceived.size());
    }

    @Test
    @DisplayName("조건 미달 -> mismatch 포트")
    void test2(){
        splitNode.process(new Message(Map.of("temperature", 25.0)));
//        Assertions.assertEquals(0, matchReceived.size());
        Assertions.assertEquals(1, mismatchReceived.size());
    }

    @Test
    @DisplayName("양쪽 동시 확인")
    void test3(){
        splitNode.process(new Message(Map.of("temperature", 35.0)));
        splitNode.process(new Message(Map.of("temperature", 25.0)));
        Assertions.assertEquals(1, matchReceived.size());
//        Assertions.assertEquals(1, mismatchReceived.size());
    }

    @Test
    @DisplayName("경계값 처리")
    void test4(){
        splitNode.process(new Message(Map.of("temperature", 30.0)));
        Assertions.assertEquals(1, matchReceived.size());
        Assertions.assertEquals(0, mismatchReceived.size());
    }
}
