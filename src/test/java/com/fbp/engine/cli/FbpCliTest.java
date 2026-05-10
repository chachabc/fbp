package com.fbp.engine.cli;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.message.Message;
import com.fbp.engine.parser.ConnectionDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.NodeDefinition;
import com.fbp.engine.parser.TransportDefinition;
import com.fbp.engine.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FbpCliTest {

    private NodeRegistry registry;
    private FlowManager manager;
    private FbpCli cli;

    static class StubNode extends AbstractNode {
        StubNode(String id, Map<String, Object> config) {
            super(id);
            addOutputPort("out");
            addInputPort("in");
        }
        @Override protected void onProcess(Message message) {}
    }

    @BeforeEach
    void setUp() {
        registry = new NodeRegistry();
        registry.register("Stub", StubNode::new);
        manager  = new FlowManager(registry);
        cli      = new FbpCli(manager, registry, null, null, null);
    }

    private String run(String commands) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf);
        cli.run(new StringReader(commands + "\nexit\n"), out);
        return buf.toString();
    }

    private FlowDefinition twoNodeFlow(String id) {
        return new FlowDefinition(id, TransportDefinition.LOCAL,
                List.of(
                        new NodeDefinition("src",  "Stub", Map.of()),
                        new NodeDefinition("sink", "Stub", Map.of())
                ),
                List.of(ConnectionDefinition.parse("src:out", "sink:in")));
    }

    // ── flow list ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("flow list — 플로우가 없으면 안내 메시지를 출력한다")
    void test1() {
        String out = run("flow list");
        assertTrue(out.contains("배포된 플로우가 없습니다") || out.contains("없습니다"));
    }

    @Test
    @DisplayName("flow list — 배포된 플로우가 목록에 표시된다")
    void test2() {
        manager.deploy(twoNodeFlow("my-flow"));
        String out = run("flow list");
        assertTrue(out.contains("my-flow"), "플로우 id가 목록에 있어야 함");
        assertTrue(out.contains("DEPLOYED"), "DEPLOYED 상태가 표시되어야 함");
    }

    // ── flow start / stop ─────────────────────────────────────────────────────

    @Test
    @DisplayName("flow start 후 status 조회 시 RUNNING이 표시된다")
    void test3() {
        manager.deploy(twoNodeFlow("f1"));
        String out = run("flow start f1\nflow status f1");
        assertTrue(out.contains("RUNNING"));
        manager.stop("f1");
        manager.remove("f1");
    }

    @Test
    @DisplayName("flow stop 후 status 조회 시 STOPPED가 표시된다")
    void test4() {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");
        String out = run("flow stop f1\nflow status f1");
        assertTrue(out.contains("STOPPED"));
        manager.remove("f1");
    }

    // ── flow status ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("flow status — 노드 수와 연결 수가 표시된다")
    void test5() {
        manager.deploy(twoNodeFlow("f1"));
        String out = run("flow status f1");
        assertTrue(out.contains("Nodes:"), "노드 수 항목이 있어야 함");
        assertTrue(out.contains("Wires:"), "연결 수 항목이 있어야 함");
        assertTrue(out.contains("2"),      "노드 2개가 표시되어야 함");
        manager.remove("f1");
    }

    // ── flow add-node / remove-node ───────────────────────────────────────────

    @Test
    @DisplayName("flow add-node — 인라인 JSON으로 노드를 추가하면 node list에 나타난다")
    void test6() {
        manager.deploy(twoNodeFlow("f1"));
        String addCmd = "flow add-node f1 {\"id\":\"extra\",\"type\":\"Stub\",\"config\":{}}";
        String out    = run(addCmd + "\nnode list f1");
        assertTrue(out.contains("extra"), "추가된 노드 id가 목록에 있어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("flow remove-node — 제거한 노드는 node list에서 사라진다")
    void test7() {
        manager.deploy(twoNodeFlow("f1"));
        manager.addNode("f1", new NodeDefinition("extra-node", "Stub", Map.of()));
        String out = run("flow remove-node f1 extra-node\nnode list f1");
        // "extra-node"가 node list의 행으로 출력되지 않아야 함
        // 단, 제거 완료 메시지("노드 제거 완료: extra-node")에는 포함될 수 있으므로
        // 마지막 node list 출력 부분만 검사
        String[] lines = out.split("\n");
        boolean foundInList = false;
        boolean inNodeList = false;
        for (String line : lines) {
            if (line.contains("ID") && line.contains("TYPE")) { inNodeList = true; continue; }
            if (inNodeList && line.contains("extra-node")) { foundInList = true; break; }
        }
        assertFalse(foundInList, "node list에 제거된 노드가 없어야 함");
        manager.remove("f1");
    }

    // ── flow add-wire / remove-wire ───────────────────────────────────────────

    @Test
    @DisplayName("flow add-wire — 추가된 연결이 wire list에 나타난다")
    void test8() {
        FlowDefinition noWire = new FlowDefinition("f1", TransportDefinition.LOCAL,
                List.of(new NodeDefinition("src", "Stub", Map.of()),
                        new NodeDefinition("sink", "Stub", Map.of())),
                List.of());
        manager.deploy(noWire);
        String out = run("flow add-wire f1 src:out sink:in\nwire list f1");
        assertTrue(out.contains("src:out"), "wire list에 from 포트가 표시되어야 함");
        assertTrue(out.contains("sink:in"), "wire list에 to 포트가 표시되어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("flow remove-wire — 제거된 연결은 wire list에서 사라진다")
    void test9() {
        manager.deploy(twoNodeFlow("f1"));
        String wireId = FlowManager.connectionId(ConnectionDefinition.parse("src:out", "sink:in"));
        String out = run("flow remove-wire f1 " + wireId + "\nwire list f1");
        assertFalse(out.contains("src:out") && out.contains("sink:in"),
                "제거된 연결이 wire list에 없어야 함");
        manager.remove("f1");
    }

    // ── flow history / rollback ───────────────────────────────────────────────

    @Test
    @DisplayName("flow history — 변경 이력이 표시된다")
    void test10() {
        manager.deploy(twoNodeFlow("f1"));
        manager.addNode("f1", new NodeDefinition("extra", "Stub", Map.of()));
        String out = run("flow history f1");
        assertTrue(out.contains("add-node"), "이력에 add-node가 있어야 함");
        assertTrue(out.contains("1"),        "revision 1이 표시되어야 함");
        manager.remove("f1");
    }

    @Test
    @DisplayName("flow rollback — 롤백 후 제거된 노드가 복원된다")
    void test11() {
        manager.deploy(twoNodeFlow("f1"));
        manager.addNode("f1", new NodeDefinition("extra", "Stub", Map.of())); // rev 1
        manager.removeNode("f1", "extra");                                     // rev 2
        String out = run("flow rollback f1 1\nnode list f1");
        assertTrue(out.contains("extra"), "롤백 후 extra 노드가 복원되어야 함");
        manager.remove("f1");
    }

    // ── node info ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("node info — 노드의 type과 config이 표시된다")
    void test12() {
        manager.deploy(twoNodeFlow("f1"));
        String out = run("node info f1 src");
        assertTrue(out.contains("Stub"),  "노드 타입이 표시되어야 함");
        assertTrue(out.contains("src"),   "노드 id가 표시되어야 함");
        manager.remove("f1");
    }

    // ── stats ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stats — 엔진 통계 박스가 출력된다")
    void test13() {
        manager.deploy(twoNodeFlow("f1"));
        manager.start("f1");
        String out = run("stats");
        assertTrue(out.contains("FBP Engine Statistics"), "통계 헤더가 있어야 함");
        assertTrue(out.contains("Active Flows"),           "Active Flows 항목이 있어야 함");
        manager.stop("f1");
        manager.remove("f1");
    }

    // ── help ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("help — 명령어 목록이 출력된다")
    void test14() {
        String out = run("help");
        assertTrue(out.contains("flow list"),  "flow list 명령이 help에 있어야 함");
        assertTrue(out.contains("wire list"),  "wire list 명령이 help에 있어야 함");
        assertTrue(out.contains("influx status"), "influx status 명령이 help에 있어야 함");
    }

    // ── 오류 처리 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 플로우 명령은 error 메시지를 출력하고 종료하지 않는다")
    void test15() {
        String out = run("flow start ghost\nflow list");
        assertTrue(out.contains("[error]"),          "오류 메시지가 있어야 함");
        assertTrue(out.contains("ghost") || out.contains("error"), "ghost 플로우 오류가 표시되어야 함");
    }
}
