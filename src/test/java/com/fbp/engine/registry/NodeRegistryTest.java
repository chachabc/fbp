package com.fbp.engine.registry;

import com.fbp.engine.core.Node;
import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NodeRegistryTest {

    private NodeRegistry registry;

    /** 테스트용 최소 Node 구현 */
    static class StubNode implements Node {
        final String id;
        final Map<String, Object> config;

        StubNode(String id, Map<String, Object> config) {
            this.id = id;
            this.config = config;
        }

        @Override public String getId() { return id; }
        @Override public void process(Message message) {}
        @Override public void initialize() {}
        @Override public void shutdown() {}
    }

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
    }

    // ── register / create ─────────────────────────────────────────────────────

    @Test
    @DisplayName("register 후 create하면 노드 인스턴스가 반환된다")
    void test1() {
        registry.register("Stub", (id, config) -> new StubNode(id, config));

        Node node = registry.create("Stub", "node-1", Map.of());

        assertNotNull(node);
        assertEquals("node-1", node.getId());
    }

    @Test
    @DisplayName("create 시 전달한 config가 노드에 올바르게 전달된다")
    void test2() {
        registry.register("Stub", (id, config) -> new StubNode(id, config));

        StubNode node = (StubNode) registry.create("Stub", "n", Map.of("threshold", 30));

        assertEquals(30, node.config.get("threshold"));
    }

    @Test
    @DisplayName("미등록 타입으로 create하면 NodeRegistryException이 발생한다")
    void test3() {
        NodeRegistryException ex = assertThrows(NodeRegistryException.class,
                () -> registry.create("Unknown", "n", Map.of()));

        assertTrue(ex.getMessage().contains("Unknown"));
    }

    @Test
    @DisplayName("중복 등록 시 마지막 팩토리가 적용된다")
    void test4() {
        registry.register("Stub", (id, config) -> new StubNode("first-" + id, config));
        registry.register("Stub", (id, config) -> new StubNode("second-" + id, config));

        Node node = registry.create("Stub", "n", Map.of());

        assertEquals("second-n", node.getId());
    }

    @Test
    @DisplayName("팩토리 실행 중 예외가 발생하면 NodeRegistryException으로 감싸진다")
    void test5() {
        registry.register("Broken", (id, config) -> {
            throw new IllegalArgumentException("필수 설정 누락");
        });

        NodeRegistryException ex = assertThrows(NodeRegistryException.class,
                () -> registry.create("Broken", "n", Map.of()));

        assertNotNull(ex.getCause());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // ── isRegistered ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("등록된 타입은 isRegistered가 true를 반환한다")
    void test6() {
        registry.register("Stub", (id, config) -> new StubNode(id, config));

        assertTrue(registry.isRegistered("Stub"));
    }

    @Test
    @DisplayName("미등록 타입은 isRegistered가 false를 반환한다")
    void test7() {
        assertFalse(registry.isRegistered("Nope"));
    }

    @Test
    @DisplayName("isRegistered에 null 또는 빈 문자열을 전달하면 false를 반환한다")
    void test8() {
        assertFalse(registry.isRegistered(null));
        assertFalse(registry.isRegistered(""));
        assertFalse(registry.isRegistered("  "));
    }

    // ── getRegisteredTypes ────────────────────────────────────────────────────

    @Test
    @DisplayName("getRegisteredTypes는 등록된 타입 목록을 정확히 반환한다")
    void test9() {
        registry.register("TypeA", (id, config) -> new StubNode(id, config));
        registry.register("TypeB", (id, config) -> new StubNode(id, config));

        Set<String> types = registry.getRegisteredTypes();

        assertEquals(2, types.size());
        assertTrue(types.contains("TypeA"));
        assertTrue(types.contains("TypeB"));
    }

    @Test
    @DisplayName("getRegisteredTypes 반환값은 수정 불가하다")
    void test10() {
        registry.register("Stub", (id, config) -> new StubNode(id, config));

        Set<String> types = registry.getRegisteredTypes();

        assertThrows(UnsupportedOperationException.class,
                () -> types.add("ShouldFail"));
    }

    // ── 유효성 검사 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("typeName이 null이면 register 시 NodeRegistryException이 발생한다")
    void test11() {
        assertThrows(NodeRegistryException.class,
                () -> registry.register(null, (id, config) -> new StubNode(id, config)));
    }

    @Test
    @DisplayName("factory가 null이면 register 시 NodeRegistryException이 발생한다")
    void test12() {
        assertThrows(NodeRegistryException.class,
                () -> registry.register("Stub", null));
    }

    // ── 람다 등록 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("NodeFactory는 람다로 등록할 수 있다")
    void test13() {
        AtomicInteger callCount = new AtomicInteger(0);

        registry.register("Counter", (id, config) -> {
            callCount.incrementAndGet();
            return new StubNode(id, config);
        });

        registry.create("Counter", "n1", Map.of());
        registry.create("Counter", "n2", Map.of());

        assertEquals(2, callCount.get());
    }
}