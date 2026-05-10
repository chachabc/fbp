package com.fbp.engine.engine;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import com.fbp.engine.parser.ConnectionDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.parser.NodeDefinition;
import com.fbp.engine.parser.TransportDefinition;
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

class FlowManagerDynamicTest {

    private NodeRegistry registry;
    private FlowManager manager;

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
        volatile CountDownLatch latch;

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

        void resetLatch(int count) { latch = new CountDownLatch(count); }
    }

    // ── 공통 설정 ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        registry.register("Source", StubSourceNode::new);
        registry.register("Sink",   StubSinkNode::new);
        manager = new FlowManager(registry);
    }

    private FlowDefinition twoNodeFlow(String flowId) {
        return new FlowDefinition(flowId, TransportDefinition.LOCAL,
                List.of(
                        new NodeDefinition("src",  "Source", Map.of()),
                        new NodeDefinition("sink", "Sink",   Map.of("waitCount", 1))
                ),
                List.of(ConnectionDefinition.parse("src:out", "sink:in")));
    }

    // ── addNode ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addNode — 실행 중 노드를 추가하면 nodeMap에 반영된다")
    void test1() {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        manager.addNode("f1", new NodeDefinition("extra", "Sink", Map.of("waitCount", 0)));

        assertNotNull(manager.getNode("f1", "extra"), "추가된 노드가 nodeMap에 있어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("addNode — 중복 id이면 FlowManagerException이 발생한다")
    void test2() {
        manager.deploy(twoNodeFlow("f1"));
        assertThrows(FlowManagerException.class,
                () -> manager.addNode("f1", new NodeDefinition("src", "Source", Map.of())));
        manager.remove("f1");
    }

    // ── removeNode ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeNode — 노드 제거 시 연결된 와이어도 함께 제거된다")
    void test3() throws InterruptedException {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        manager.removeNode("f1", "sink");

        // sink가 없으므로 src에서 emit해도 예외 없이 처리됨
        StubSourceNode src = (StubSourceNode) manager.getNode("f1", "src");
        src.emit(new Message(Map.of("payload", "data")));

        assertThrows(FlowManagerException.class, () -> manager.getNode("f1", "sink"));
        manager.remove("f1");
    }

    @Test
    @DisplayName("removeNode — 존재하지 않는 노드이면 FlowManagerException")
    void test4() {
        manager.deploy(twoNodeFlow("f1"));
        assertThrows(FlowManagerException.class, () -> manager.removeNode("f1", "ghost"));
        manager.remove("f1");
    }

    // ── addWire / removeWire ──────────────────────────────────────────────────

    @Test
    @DisplayName("addWire — 와이어를 추가하면 메시지가 새 target으로 전달된다")
    void test5() throws InterruptedException {
        // src 노드만 있는 플로우 시작
        FlowDefinition srcOnly = new FlowDefinition("f1", TransportDefinition.LOCAL,
                List.of(
                        new NodeDefinition("src",  "Source", Map.of()),
                        new NodeDefinition("sink", "Sink",   Map.of("waitCount", 1))
                ),
                List.of()); // 연결 없음
        manager.deploy(srcOnly);
        manager.start("f1");

        // 와이어 추가
        manager.addWire("f1", ConnectionDefinition.parse("src:out", "sink:in"));

        StubSourceNode src  = (StubSourceNode) manager.getNode("f1", "src");
        StubSinkNode   sink = (StubSinkNode)   manager.getNode("f1", "sink");

        sink.resetLatch(1);
        src.emit(new Message(Map.of("val", "hello")));

        assertTrue(sink.latch.await(2, TimeUnit.SECONDS), "와이어 추가 후 메시지가 전달되어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("removeWire — 와이어 제거 후 메시지가 더 이상 전달되지 않는다")
    void test6() throws InterruptedException {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        String wireId = FlowManager.connectionId(ConnectionDefinition.parse("src:out", "sink:in"));
        manager.removeWire("f1", wireId);

        StubSourceNode src  = (StubSourceNode) manager.getNode("f1", "src");
        StubSinkNode   sink = (StubSinkNode)   manager.getNode("f1", "sink");

        sink.resetLatch(1);
        src.emit(new Message(Map.of("val", "should not arrive")));

        // 500ms 안에 메시지가 도착하지 않아야 한다
        assertFalse(sink.latch.await(500, TimeUnit.MILLISECONDS),
                "와이어 제거 후 메시지가 전달되어서는 안 됨");
        manager.remove("f1");
    }

    @Test
    @DisplayName("removeWire — 존재하지 않는 wireId이면 FlowManagerException")
    void test7() {
        manager.deploy(twoNodeFlow("f1"));
        assertThrows(FlowManagerException.class, () -> manager.removeWire("f1", "ghost-wire"));
        manager.remove("f1");
    }

    // ── updateNodeConfig ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateNodeConfig — 설정 변경 후 새 노드로 교체되어 메시지를 계속 처리한다")
    void test8() throws InterruptedException {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        // sink 설정 변경 (waitCount 변경)
        manager.updateNodeConfig("f1", "sink", Map.of("waitCount", 2));

        StubSourceNode src     = (StubSourceNode) manager.getNode("f1", "src");
        StubSinkNode   newSink = (StubSinkNode)   manager.getNode("f1", "sink");

        newSink.resetLatch(2);
        src.emit(new Message(Map.of("val", "msg1")));
        src.emit(new Message(Map.of("val", "msg2")));

        assertTrue(newSink.latch.await(2, TimeUnit.SECONDS), "설정 변경 후 메시지가 처리되어야 함");
        manager.remove("f1");
    }

    // ── patch ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("patch — 새 노드가 추가되고 기존 노드는 유지된다")
    void test9() {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        FlowDefinition patched = new FlowDefinition("f1", TransportDefinition.LOCAL,
                List.of(
                        new NodeDefinition("src",   "Source", Map.of()),
                        new NodeDefinition("sink",  "Sink",   Map.of("waitCount", 1)),
                        new NodeDefinition("sink2", "Sink",   Map.of("waitCount", 0))
                ),
                List.of(ConnectionDefinition.parse("src:out", "sink:in")));

        manager.patch("f1", patched);

        assertNotNull(manager.getNode("f1", "sink2"), "패치로 추가된 노드가 있어야 함");
        assertNotNull(manager.getNode("f1", "src"),   "기존 노드가 유지되어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("patch — 제거된 노드와 와이어는 사라진다")
    void test10() {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");

        // sink 노드와 와이어를 제거한 버전으로 패치
        FlowDefinition patched = new FlowDefinition("f1", TransportDefinition.LOCAL,
                List.of(new NodeDefinition("src", "Source", Map.of())),
                List.of());

        manager.patch("f1", patched);

        assertThrows(FlowManagerException.class, () -> manager.getNode("f1", "sink"),
                "패치로 제거된 노드는 없어야 함");
        manager.remove("f1");
    }

    // ── getHistory / rollback ─────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory — 변경 작업마다 이력이 쌓인다")
    void test11() {
        manager.deploy(twoNodeFlow("f1"));
        manager.addNode("f1", new NodeDefinition("extra", "Sink", Map.of("waitCount", 0)));
        manager.removeNode("f1", "extra");

        List<FlowManager.ChangeRecord> history = manager.getHistory("f1");
        assertEquals(2, history.size());
        assertEquals("add-node",    history.get(0).operation());
        assertEquals("remove-node", history.get(1).operation());
        assertEquals(1, history.get(0).revision());
        assertEquals(2, history.get(1).revision());
        manager.remove("f1");
    }

    @Test
    @DisplayName("rollback — 이전 revision 스냅샷으로 복원되어 이력에 rollback 항목이 추가된다")
    void test12() {
        manager.deploy(twoNodeFlow("f1"));

        // revision 1: extra 노드 추가
        manager.addNode("f1", new NodeDefinition("extra", "Sink", Map.of("waitCount", 0)));
        // revision 2: extra 노드 제거
        manager.removeNode("f1", "extra");

        // revision 1 상태(extra 노드가 있던 상태)로 롤백
        manager.rollback("f1", 1);

        assertNotNull(manager.getNode("f1", "extra"), "롤백 후 extra 노드가 복원되어야 함");

        List<FlowManager.ChangeRecord> history = manager.getHistory("f1");
        assertEquals("rollback", history.get(history.size() - 1).operation());
        manager.remove("f1");
    }

    @Test
    @DisplayName("rollback — 존재하지 않는 revision이면 FlowManagerException")
    void test13() {
        manager.deploy(twoNodeFlow("f1"));
        assertThrows(FlowManagerException.class, () -> manager.rollback("f1", 999));
        manager.remove("f1");
    }
}