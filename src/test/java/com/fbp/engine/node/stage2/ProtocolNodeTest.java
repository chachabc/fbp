package com.fbp.engine.node.stage2;

import com.fbp.engine.core.ConnectionState;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

class ProtocolNodeTest {
    private static final Logger log = LoggerFactory.getLogger(ProtocolNodeTest.class);

    static class TestProtocolNode extends ProtocolNode {
        volatile boolean shouldFail = false;
        int connectCallCount = 0;

        TestProtocolNode(String id, Map<String, Object> config) {
            super(id, config);
        }

        @Override
        protected void connect() throws Exception {
            connectCallCount++;
            if (shouldFail) {
                throw new Exception();
            }
        }
        @Override
        protected void disconnect() {}
        @Override
        protected void onProcess(Message message) {}
    }

    @Test
    @DisplayName("초기 상태")
    void test1() {
        TestProtocolNode testProtocolNode = new TestProtocolNode("node-1", Map.of());
        Assertions.assertEquals(ConnectionState.DISCONNECTED, testProtocolNode.getConnectionState());
    }

    @Test
    @DisplayName("config 조회")
    void test2(){
        Map<String, Object> config = Map.of("brokerUrl", "tcp://localhost:1883", "qos", 1);
        TestProtocolNode node =  new TestProtocolNode("node-2", config);

        Assertions.assertEquals("tcp://localhost:1883", node.getConfig("brokerUrl"));
        Assertions.assertEquals(1, node.getConfig("qos"));
        Assertions.assertNull(node.getConfig("none"));
    }

    @Test
    @DisplayName("initialize -> CONNECTED")
    void test3(){
        TestProtocolNode node = new TestProtocolNode("node-3", Map.of());

        node.initialize();

        Assertions.assertEquals(ConnectionState.CONNECTED, node.getConnectionState());

        node.shutdown();
    }

    @Test
    @DisplayName("initialize -> 연길 실패 시 상태")
    void test4() throws InterruptedException{
        Map<String, Object> config = new HashMap<>();
        config.put("reconnectIntervalMs", 100L);
        config.put("maxRetries", 3);

        TestProtocolNode testProtocolNode = new TestProtocolNode("node-4", config);
        testProtocolNode.shouldFail = true;

        testProtocolNode.initialize();

        Assertions.assertNotEquals(ConnectionState.CONNECTED, testProtocolNode.getConnectionState());

        Thread.sleep(350);
        Assertions.assertTrue(testProtocolNode.connectCallCount >= 2);
        log.info("{}", testProtocolNode.connectCallCount);

        testProtocolNode.shouldFail = false;
        testProtocolNode.shutdown();
    }

    @Test
    @DisplayName("shutdown -> DISCONNECTED")
    void test5(){
        TestProtocolNode testProtocolNode = new TestProtocolNode("node-5", Map.of());
        testProtocolNode.initialize();

        testProtocolNode.shutdown();

        Assertions.assertEquals(ConnectionState.DISCONNECTED, testProtocolNode.getConnectionState());
    }

    @Test
    @DisplayName("isConnected 반환 값")
    void test6(){
        TestProtocolNode testProtocolNode = new TestProtocolNode("node-6", Map.of());

        Assertions.assertFalse(testProtocolNode.isConnected());

        testProtocolNode.initialize();
        Assertions.assertTrue(testProtocolNode.isConnected());

        testProtocolNode.shutdown();
        Assertions.assertFalse(testProtocolNode.isConnected());
    }

    @Test
    @DisplayName("재연결 시도")
    void test7() throws InterruptedException{
        Map<String, Object> config = new HashMap<>();
        config.put("reconnectIntervalMs", 100L);
        config.put("maxRetries", 5);

        TestProtocolNode testProtocolNode = new TestProtocolNode("node-7", config);
        testProtocolNode.shouldFail = true;

        testProtocolNode.initialize();

        Thread.sleep(350);
        Assertions.assertTrue(testProtocolNode.connectCallCount >= 3);
        testProtocolNode.shouldFail = false;
        testProtocolNode.shutdown();
    }
}
