package com.fbp.engine.engine;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FlowManagerTest {

    private NodeRegistry registry;
    private FlowManager manager;
    private JsonFlowParser parser;

    // ── 테스트용 스텁 노드 ────────────────────────────────────────────────────

    static class StubSourceNode extends AbstractNode {
        StubSourceNode(String id, Map<String, Object> config) {
            super(id);
            addOutputPort("out");
        }
        @Override protected void onProcess(Message message) {}
        public void emit(Message message) { send("out", message); }
    }

    static class StubSinkNode extends AbstractNode {
        final List<Message> received = new ArrayList<>();
        final CountDownLatch latch;

        StubSinkNode(String id, Map<String, Object> config) {
            super(id);
            addInputPort("in");
            int waitCount = config.get("waitCount") instanceof Number n ? n.intValue() : 0;
            latch = new CountDownLatch(waitCount);
        }

        @Override
        protected void onProcess(Message message) {
            received.add(message);
            latch.countDown();
        }
    }

    // ── 공통 설정 ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        registry.register("Source", StubSourceNode::new);
        registry.register("Sink",   StubSinkNode::new);
        manager = new FlowManager(registry);
        parser  = new JsonFlowParser();
    }

    private FlowDefinition simpleFlow(String flowId) {
        return parser.parse("""
                {
                  "id": "%s",
                  "nodes": [
                    {"id": "src",  "type": "Source", "config": {}},
                    {"id": "sink", "type": "Sink",   "config": {"waitCount": 1}}
                  ],
                  "connections": [{"from": "src:out", "to": "sink:in"}]
                }
                """.formatted(flowId));
    }

    // ── deploy ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deploy하면 DEPLOYED 상태가 된다")
    void test1() {
        manager.deploy(simpleFlow("flow-1"));
        assertEquals(FlowState.DEPLOYED, manager.getStatus("flow-1"));
    }

    @Test
    @DisplayName("동일 id로 두 번 deploy하면 FlowManagerException이 발생한다")
    void test2() {
        manager.deploy(simpleFlow("flow-1"));
        assertThrows(FlowManagerException.class, () -> manager.deploy(simpleFlow("flow-1")));
    }

    @Test
    @DisplayName("미등록 노드 타입이 있으면 deploy가 실패한다")
    void test3() {
        FlowDefinition def = parser.parse("""
                {"id": "x", "nodes": [{"id": "n", "type": "UnknownType", "config": {}}], "connections": []}
                """);
        assertThrows(FlowManagerException.class, () -> manager.deploy(def));
    }

    // ── start ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start하면 RUNNING 상태가 된다")
    void test4() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        assertEquals(FlowState.RUNNING, manager.getStatus("flow-1"));
        manager.stop("flow-1");
    }

    @Test
    @DisplayName("이미 RUNNING인 플로우를 start하면 FlowManagerException이 발생한다")
    void test5() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        assertThrows(FlowManagerException.class, () -> manager.start("flow-1"));
        manager.stop("flow-1");
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stop하면 STOPPED 상태가 된다")
    void test6() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        manager.stop("flow-1");
        assertEquals(FlowState.STOPPED, manager.getStatus("flow-1"));
    }

    @Test
    @DisplayName("RUNNING이 아닌 플로우를 stop하면 FlowManagerException이 발생한다")
    void test7() {
        manager.deploy(simpleFlow("flow-1"));
        assertThrows(FlowManagerException.class, () -> manager.stop("flow-1"));
    }

    // ── restart ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stop 후 restart하면 다시 RUNNING 상태가 된다")
    void test8() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        manager.stop("flow-1");
        manager.restart("flow-1");
        assertEquals(FlowState.RUNNING, manager.getStatus("flow-1"));
        manager.stop("flow-1");
    }

    @Test
    @DisplayName("RUNNING 상태에서 restart하면 stop 후 재시작된다")
    void test9() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        assertDoesNotThrow(() -> manager.restart("flow-1"));
        assertEquals(FlowState.RUNNING, manager.getStatus("flow-1"));
        manager.stop("flow-1");
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEPLOYED 상태의 플로우를 remove하면 목록에서 사라진다")
    void test10() {
        manager.deploy(simpleFlow("flow-1"));
        manager.remove("flow-1");
        assertThrows(FlowManagerException.class, () -> manager.getStatus("flow-1"));
    }

    @Test
    @DisplayName("RUNNING 상태의 플로우를 remove하면 자동 stop 후 삭제된다")
    void test11() {
        manager.deploy(simpleFlow("flow-1"));
        manager.start("flow-1");
        assertDoesNotThrow(() -> manager.remove("flow-1"));
        assertThrows(FlowManagerException.class, () -> manager.getStatus("flow-1"));
    }

    // ── 존재하지 않는 id ──────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 id로 조작하면 FlowManagerException이 발생한다")
    void test12() {
        assertThrows(FlowManagerException.class, () -> manager.start("ghost"));
        assertThrows(FlowManagerException.class, () -> manager.stop("ghost"));
        assertThrows(FlowManagerException.class, () -> manager.remove("ghost"));
        assertThrows(FlowManagerException.class, () -> manager.getStatus("ghost"));
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list는 모든 플로우의 id와 상태를 반환한다")
    void test13() {
        manager.deploy(simpleFlow("flow-a"));
        manager.deploy(simpleFlow("flow-b"));
        manager.start("flow-a");

        Map<String, FlowState> list = manager.list();
        assertEquals(2, list.size());
        assertEquals(FlowState.RUNNING,  list.get("flow-a"));
        assertEquals(FlowState.DEPLOYED, list.get("flow-b"));
        manager.stop("flow-a");
    }

    @Test
    @DisplayName("list 반환값은 수정 불가하다")
    void test14() {
        assertThrows(UnsupportedOperationException.class,
                () -> manager.list().put("x", FlowState.RUNNING));
    }

    // ── 메시지 흐름 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("start 후 소스에서 발행한 메시지가 싱크에 도달한다")
    void test15() throws InterruptedException {
        manager.deploy(simpleFlow("msg-flow"));
        manager.start("msg-flow");

        StubSourceNode src  = (StubSourceNode) manager.getNode("msg-flow", "src");
        StubSinkNode   sink = (StubSinkNode)   manager.getNode("msg-flow", "sink");

        src.emit(new Message(Map.of("value", 99)));

        boolean arrived = sink.latch.await(2, TimeUnit.SECONDS);
        assertTrue(arrived, "메시지가 2초 안에 싱크에 도달해야 한다");
        assertEquals(99, (int) sink.received.get(0).get("value"));

        manager.stop("msg-flow");
    }

    @Test
    @DisplayName("getNode는 존재하지 않는 노드 id에 FlowManagerException을 던진다")
    void test16() {
        manager.deploy(simpleFlow("flow-1"));
        assertThrows(FlowManagerException.class, () -> manager.getNode("flow-1", "ghost"));
    }
}