package com.fbp.engine.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.engine.FlowManager;
import com.fbp.engine.engine.FlowManagerException;
import com.fbp.engine.engine.FlowState;
import com.fbp.engine.influx.InfluxBatchWriter;
import com.fbp.engine.influx.InfluxWriterConfig;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.MetricsSnapshot;
import com.fbp.engine.parser.ConnectionDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.JsonFlowParser;
import com.fbp.engine.parser.NodeDefinition;
import com.fbp.engine.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * FBP 엔진 관리 CLI.
 *
 * <pre>
 * 사용:
 *   FbpCli cli = new FbpCli(flowManager, nodeRegistry, metricsCollector, influxWriter);
 *   cli.run(System.in, System.out);
 * </pre>
 *
 * metricsCollector, influxWriter는 null을 허용한다 (없으면 관련 명령에서 "N/A" 출력).
 */
public class FbpCli {

    private static final Logger log = LoggerFactory.getLogger(FbpCli.class);

    private final FlowManager      flowManager;
    private final NodeRegistry     nodeRegistry;
    private final MetricsCollector metricsCollector; // nullable
    private final InfluxBatchWriter influxWriter;    // nullable
    private final InfluxWriterConfig influxConfig;  // nullable
    private final JsonFlowParser   parser;
    private final ObjectMapper     mapper;

    public FbpCli(FlowManager flowManager, NodeRegistry nodeRegistry,
                  MetricsCollector metricsCollector, InfluxBatchWriter influxWriter,
                  InfluxWriterConfig influxConfig) {
        this.flowManager      = flowManager;
        this.nodeRegistry     = nodeRegistry;
        this.metricsCollector = metricsCollector;
        this.influxWriter     = influxWriter;
        this.influxConfig     = influxConfig;
        this.parser           = new JsonFlowParser();
        this.mapper           = new ObjectMapper();
    }

    // ── REPL ────────────────────────────────────────────────────────────────

    public void run(Reader in, PrintStream out) {
        out.println("FBP Engine CLI — type 'help' for commands, 'exit' to quit.");
        try (BufferedReader reader = new BufferedReader(in)) {
            String line;
            while (true) {
                out.print("fbp> ");
                out.flush();
                line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!dispatch(line, out)) break;
            }
        } catch (IOException e) {
            out.println("[error] 입력 오류: " + e.getMessage());
        }
        out.println("Bye.");
    }

    // ── 디스패치 ─────────────────────────────────────────────────────────────

    private boolean dispatch(String line, PrintStream out) {
        String[] args = line.split("\\s+");
        try {
            switch (args[0].toLowerCase()) {
                case "flow"    -> handleFlow(args, out);
                case "node"    -> handleNode(args, out);
                case "wire"    -> handleWire(args, out);
                case "stats"   -> handleStats(out);
                case "influx"  -> handleInflux(args, out);
                case "broker"  -> handleBroker(args, out);
                case "monitor" -> handleMonitor(args, out);
                case "sensor"  -> handleSensor(args, out);
                case "help"    -> handleHelp(args, out);
                case "exit", "quit" -> { return false; }
                default -> out.println("알 수 없는 명령: " + args[0] + " (help 로 목록 확인)");
            }
        } catch (FlowManagerException e) {
            out.println("[error] " + e.getMessage());
        } catch (Exception e) {
            out.println("[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.debug("CLI 명령 오류", e);
        }
        return true;
    }

    // ── flow 명령 ────────────────────────────────────────────────────────────

    private void handleFlow(String[] args, PrintStream out) throws Exception {
        if (args.length < 2) { out.println("사용법: flow <subcommand> ..."); return; }
        switch (args[1].toLowerCase()) {
            case "list"          -> flowList(out);
            case "deploy"        -> flowDeploy(args, out);
            case "start"         -> { flowManager.start(req(args, 2, "flow start <id>")); out.println("시작 완료."); }
            case "stop"          -> { flowManager.stop(req(args, 2, "flow stop <id>")); out.println("정지 완료."); }
            case "restart"       -> { flowManager.restart(req(args, 2, "flow restart <id>")); out.println("재시작 완료."); }
            case "remove"        -> { flowManager.remove(req(args, 2, "flow remove <id>")); out.println("삭제 완료."); }
            case "status"        -> flowStatus(args, out);
            case "patch"         -> flowPatch(args, out);
            case "add-node"      -> flowAddNode(args, out);
            case "remove-node"   -> flowRemoveNode(args, out);
            case "add-wire"      -> flowAddWire(args, out);
            case "remove-wire"   -> flowRemoveWire(args, out);
            case "update-config" -> flowUpdateConfig(args, out);
            case "history"       -> flowHistory(args, out);
            case "rollback"      -> flowRollback(args, out);
            default -> out.println("알 수 없는 flow 서브커맨드: " + args[1]);
        }
    }

    private void flowList(PrintStream out) {
        Map<String, FlowState> flows = flowManager.list();
        if (flows.isEmpty()) { out.println("배포된 플로우가 없습니다."); return; }
        out.printf("%-30s %-10s %-10s %-6s %-6s%n", "ID", "STATUS", "TRANSPORT", "NODES", "WIRES");
        out.println("-".repeat(65));
        flows.forEach((id, state) -> {
            try {
                FlowDefinition def = flowManager.getCurrentDefinition(id);
                out.printf("%-30s %-10s %-10s %-6d %-6d%n",
                        id, state, def.getTransport().getType(),
                        def.getNodes().size(), def.getConnections().size());
            } catch (Exception e) {
                out.printf("%-30s %-10s%n", id, state);
            }
        });
    }

    private void flowDeploy(String[] args, PrintStream out) throws Exception {
        String filePath = req(args, 2, "flow deploy <file>");
        String json = Files.readString(Path.of(filePath));
        FlowDefinition def = parser.parse(json);
        flowManager.deploy(def);
        out.println("배포 완료: " + def.getId() + " (노드=" + def.getNodes().size()
                + ", 연결=" + def.getConnections().size() + ")");
    }

    private void flowStatus(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow status <id>");
        FlowState state = flowManager.getStatus(flowId);
        FlowDefinition def = flowManager.getCurrentDefinition(flowId);
        Instant startedAt = flowManager.getStartedAt(flowId);
        List<FlowManager.ChangeRecord> history = flowManager.getHistory(flowId);

        out.println("Flow: " + flowId);
        out.println("  Status:    " + state);
        out.println("  Transport: " + def.getTransport().getType());
        out.println("  Nodes:     " + def.getNodes().size());
        out.println("  Wires:     " + def.getConnections().size());
        out.println("  Uptime:    " + (startedAt != null ? formatDuration(Duration.between(startedAt, Instant.now())) : "-"));
        out.println("  Revision:  " + history.size());

        if (metricsCollector != null) {
            MetricsSnapshot snap = metricsCollector.getLastSnapshot();
            MetricsSnapshot.FlowStatsSnapshot fs = snap.flows().get(flowId);
            if (fs != null) {
                double errPct = fs.processed() > 0 ? fs.errors() * 100.0 / fs.processed() : 0.0;
                out.printf("  Processed: %,d messages%n", fs.processed());
                out.printf("  Errors:    %d (%.3f%%)%n", fs.errors(), errPct);
            }
        }
    }

    private void flowPatch(String[] args, PrintStream out) throws Exception {
        String flowId  = req(args, 2, "flow patch <id> <file>");
        String filePath = req(args, 3, "flow patch <id> <file>");
        String json = Files.readString(Path.of(filePath));
        FlowDefinition newDef = parser.parse(json);

        FlowDefinition current = flowManager.getCurrentDefinition(flowId);
        int addedNodes = (int) newDef.getNodes().stream()
                .filter(n -> current.findNode(n.getId()).isEmpty()).count();
        int removedNodes = (int) current.getNodes().stream()
                .filter(n -> newDef.findNode(n.getId()).isEmpty()).count();

        out.printf("변경 계산... (+%d 노드, -%d 노드)%n", addedNodes, removedNodes);
        flowManager.patch(flowId, newDef);
        out.println("패치 완료. Revision: " + flowManager.getHistory(flowId).size());
    }

    private void flowAddNode(String[] args, PrintStream out) throws Exception {
        String flowId   = req(args, 2, "flow add-node <id> <spec-file-or-json>");
        String specArg  = req(args, 3, "flow add-node <id> <spec-file-or-json>");
        NodeDefinition nd = parseNodeSpec(specArg);
        flowManager.addNode(flowId, nd);
        out.println("노드 추가 완료: " + nd.getId());
    }

    private void flowRemoveNode(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow remove-node <id> <node-id>");
        String nodeId = req(args, 3, "flow remove-node <id> <node-id>");
        flowManager.removeNode(flowId, nodeId);
        out.println("노드 제거 완료: " + nodeId);
    }

    private void flowAddWire(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow add-wire <id> <from> <to>");
        String from   = req(args, 3, "flow add-wire <id> <from> <to>");
        String to     = req(args, 4, "flow add-wire <id> <from> <to>");
        ConnectionDefinition cd = ConnectionDefinition.parse(from, to);
        flowManager.addWire(flowId, cd);
        out.println("연결 추가 완료: " + from + " → " + to);
    }

    private void flowRemoveWire(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow remove-wire <id> <wire-id>");
        String wireId = req(args, 3, "flow remove-wire <id> <wire-id>");
        flowManager.removeWire(flowId, wireId);
        out.println("연결 제거 완료: " + wireId);
    }

    private void flowUpdateConfig(String[] args, PrintStream out) throws Exception {
        String flowId  = req(args, 2, "flow update-config <id> <node-id> <config-file-or-json>");
        String nodeId  = req(args, 3, "flow update-config <id> <node-id> <config-file-or-json>");
        String cfgArg  = req(args, 4, "flow update-config <id> <node-id> <config-file-or-json>");
        Map<String, Object> config = parseConfig(cfgArg);
        flowManager.updateNodeConfig(flowId, nodeId, config);
        out.println("설정 변경 완료: " + nodeId);
    }

    private void flowHistory(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow history <id>");
        List<FlowManager.ChangeRecord> history = flowManager.getHistory(flowId);
        if (history.isEmpty()) { out.println("변경 이력이 없습니다."); return; }
        out.printf("%-4s %-16s %-15s %s%n", "REV", "TIMESTAMP", "OPERATION", "DESCRIPTION");
        out.println("-".repeat(60));
        history.forEach(r -> out.printf("%-4d %-16s %-15s %s%n",
                r.revision(),
                new java.util.Date(r.timestamp()).toString().substring(4, 20),
                r.operation(),
                r.description()));
    }

    private void flowRollback(String[] args, PrintStream out) {
        String flowId = req(args, 2, "flow rollback <id> <revision>");
        int rev = Integer.parseInt(req(args, 3, "flow rollback <id> <revision>"));
        flowManager.rollback(flowId, rev);
        out.println("롤백 완료 → revision " + rev);
    }

    // ── node 명령 ────────────────────────────────────────────────────────────

    private void handleNode(String[] args, PrintStream out) {
        if (args.length < 2) { out.println("사용법: node <list|info|stats> ..."); return; }
        switch (args[1].toLowerCase()) {
            case "list"  -> nodeList(args, out);
            case "info"  -> nodeInfo(args, out);
            case "stats" -> nodeStats(args, out);
            default -> out.println("알 수 없는 node 서브커맨드: " + args[1]);
        }
    }

    private void nodeList(String[] args, PrintStream out) {
        String flowId = req(args, 2, "node list <flow-id>");
        FlowDefinition def = flowManager.getCurrentDefinition(flowId);
        out.printf("%-20s %-20s%n", "ID", "TYPE");
        out.println("-".repeat(42));
        def.getNodes().forEach(n -> out.printf("%-20s %-20s%n", n.getId(), n.getType()));
    }

    private void nodeInfo(String[] args, PrintStream out) {
        String flowId = req(args, 2, "node info <flow-id> <node-id>");
        String nodeId = req(args, 3, "node info <flow-id> <node-id>");
        FlowDefinition def = flowManager.getCurrentDefinition(flowId);
        NodeDefinition nd = def.findNode(nodeId)
                .orElseThrow(() -> new FlowManagerException("노드 없음: " + nodeId));

        out.println("Node: " + nd.getId());
        out.println("  Type:   " + nd.getType());
        out.println("  Config: " + nd.getConfig());
    }

    private void nodeStats(String[] args, PrintStream out) {
        String flowId = req(args, 2, "node stats <flow-id> <node-id>");
        String nodeId = req(args, 3, "node stats <flow-id> <node-id>");

        if (metricsCollector == null) { out.println("MetricsCollector가 비활성화되어 있습니다."); return; }
        MetricsSnapshot snap = metricsCollector.getLastSnapshot();
        MetricsSnapshot.NodeStatsSnapshot ns = snap.nodes().get(flowId + "|" + nodeId);

        if (ns == null) { out.println("통계 없음 (tick 주기 이전이거나 이벤트 없음)"); return; }
        out.println("Node: " + nodeId);
        out.printf("  In:       %,d msg%n",   ns.inCount());
        out.printf("  Out:      %,d msg%n",   ns.outCount());
        out.printf("  Errors:   %d%n",         ns.errors());
        out.printf("  Avg Time: %.2f ms%n",    ns.avgMs());
        out.printf("  P99 Time: %.2f ms%n",    ns.p99Ms());
    }

    // ── wire 명령 ────────────────────────────────────────────────────────────

    private void handleWire(String[] args, PrintStream out) {
        if (args.length < 2) { out.println("사용법: wire <list|info|stats> ..."); return; }
        switch (args[1].toLowerCase()) {
            case "list"  -> wireList(args, out);
            case "info"  -> wireInfo(args, out);
            case "stats" -> wireStats(args, out);
            default -> out.println("알 수 없는 wire 서브커맨드: " + args[1]);
        }
    }

    private void wireList(String[] args, PrintStream out) {
        String flowId = req(args, 2, "wire list <flow-id>");
        List<FlowManager.WireInfo> wires = flowManager.getWireInfos(flowId);
        if (wires.isEmpty()) { out.println("연결이 없습니다."); return; }
        out.printf("%-35s %-25s %-25s %-5s%n", "ID", "FROM", "TO", "QUEUE");
        out.println("-".repeat(95));
        wires.forEach(w -> out.printf("%-35s %-25s %-25s %-5d%n",
                w.wireId(), w.from(), w.to(), w.queueSize()));
    }

    private void wireInfo(String[] args, PrintStream out) {
        String flowId = req(args, 2, "wire info <flow-id> <wire-id>");
        String wireId = req(args, 3, "wire info <flow-id> <wire-id>");
        FlowDefinition def = flowManager.getCurrentDefinition(flowId);
        FlowManager.WireInfo info = flowManager.getWireInfos(flowId).stream()
                .filter(w -> w.wireId().equals(wireId))
                .findFirst()
                .orElseThrow(() -> new FlowManagerException("와이어 없음: " + wireId));

        out.println("Wire: " + wireId);
        out.println("  From:       " + info.from());
        out.println("  To:         " + info.to());
        out.println("  Transport:  " + def.getTransport().getType());
        out.println("  Queue Size: " + info.queueSize());
    }

    private void wireStats(String[] args, PrintStream out) {
        String flowId = req(args, 2, "wire stats <flow-id> <wire-id>");
        String wireId = req(args, 3, "wire stats <flow-id> <wire-id>");

        if (metricsCollector == null) { out.println("MetricsCollector가 비활성화되어 있습니다."); return; }
        MetricsSnapshot snap = metricsCollector.getLastSnapshot();
        MetricsSnapshot.WireStatsSnapshot ws = snap.wires().get(wireId);

        if (ws == null) { out.println("통계 없음 (tick 주기 이전이거나 이벤트 없음)"); return; }
        out.println("Wire: " + wireId);
        out.printf("  Delivered:    %,d%n",       ws.delivered());
        out.printf("  Payload:      %,d bytes%n", ws.payloadBytes());
        out.printf("  Queue Size:   %d%n",         ws.queueSize());
        out.printf("  Dropped:      %d%n",         ws.dropped());
    }

    // ── stats 명령 ───────────────────────────────────────────────────────────

    private void handleStats(PrintStream out) {
        Map<String, FlowState> flows = flowManager.list();
        long running = flows.values().stream().filter(s -> s == FlowState.RUNNING).count();
        long stopped = flows.values().stream().filter(s -> s == FlowState.STOPPED).count();

        long totalNodes = 0, totalWires = 0, totalProcessed = 0, totalErrors = 0;
        for (String id : flows.keySet()) {
            try {
                FlowDefinition def = flowManager.getCurrentDefinition(id);
                totalNodes += def.getNodes().size();
                totalWires += def.getConnections().size();
            } catch (Exception ignored) {}
        }

        if (metricsCollector != null) {
            MetricsSnapshot snap = metricsCollector.getLastSnapshot();
            for (MetricsSnapshot.FlowStatsSnapshot fs : snap.flows().values()) {
                totalProcessed += fs.processed();
                totalErrors    += fs.errors();
            }
        }

        Runtime rt = Runtime.getRuntime();
        long heapUsed  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapTotal = rt.totalMemory() / (1024 * 1024);

        String influxStatus = influxWriter != null
                ? "OK (" + String.format("%,d", influxWriter.getTotalWritten()) + " pts)"
                : "N/A";

        out.println("╔══════════════════════════════════════════╗");
        out.println("║         FBP Engine Statistics            ║");
        out.println("╠══════════════════════════════════════════╣");
        out.printf( "║ Active Flows:  %-27d║%n", running);
        out.printf( "║ Stopped Flows: %-27d║%n", stopped);
        out.printf( "║ Total Nodes:   %-27d║%n", totalNodes);
        out.printf( "║ Total Wires:   %-27d║%n", totalWires);
        out.printf( "║ Total Msgs:    %-27s║%n", String.format("%,d", totalProcessed));
        out.printf( "║ Total Errors:  %-27d║%n", totalErrors);
        out.printf( "║ Heap Used:     %-27s║%n", heapUsed + " MB / " + heapTotal + " MB");
        out.printf( "║ Threads:       %-27d║%n", Thread.activeCount());
        out.printf( "║ Influx:        %-27s║%n", influxStatus);
        out.println("╚══════════════════════════════════════════╝");
    }

    // ── influx 명령 ──────────────────────────────────────────────────────────

    private void handleInflux(String[] args, PrintStream out) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("status")) {
            out.println("사용법: influx status");
            return;
        }
        if (influxWriter == null) {
            out.println("InfluxDB 연동이 비활성화되어 있습니다.");
            return;
        }
        String url    = influxConfig != null ? influxConfig.url() : "N/A";
        String bucket = influxConfig != null ? influxConfig.bucket() : "N/A";

        out.println("InfluxDB: " + url + " (bucket=" + bucket + ")");
        out.println("  Status:        " + (influxWriter.getTotalFailed() == 0 ? "OK" : "DEGRADED"));
        out.printf( "  Batch Queue:   %d pending%n",  influxWriter.getLocalBufferSize());
        out.printf( "  Total Written: %,d points%n",  influxWriter.getTotalWritten());
        out.printf( "  Total Failed:  %d%n",           influxWriter.getTotalFailed());
    }

    // ── broker 명령 ──────────────────────────────────────────────────────────

    private void handleBroker(String[] args, PrintStream out) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("status")) {
            out.println("사용법: broker status"); return;
        }
        List<FlowManager.BrokerInfo> brokers = flowManager.getBrokerInfos();
        if (brokers.isEmpty()) {
            out.println("MQTT transport를 사용하는 플로우가 없습니다."); return;
        }
        brokers.forEach(b -> {
            out.println("System Broker: " + b.brokerUrl());
            out.println("  Flow:          " + b.flowId());
            out.println("  Active Wires:  " + b.wireCount());
        });
    }

    // ── monitor 명령 ─────────────────────────────────────────────────────────

    private void handleMonitor(String[] args, PrintStream out) throws InterruptedException {
        if (args.length < 3) { out.println("사용법: monitor flow <id>  또는  monitor node <flow-id> <node-id>"); return; }
        switch (args[1].toLowerCase()) {
            case "flow" -> monitorFlow(args[2], out);
            case "node" -> {
                String flowId  = req(args, 2, "monitor node <flow-id> <node-id>");
                String nodeId  = req(args, 3, "monitor node <flow-id> <node-id>");
                monitorNode(flowId, nodeId, out);
            }
            default -> out.println("알 수 없는 monitor 서브커맨드: " + args[1]);
        }
    }

    private void monitorFlow(String flowId, PrintStream out) throws InterruptedException {
        out.println("Monitoring flow '" + flowId + "'... (Enter로 중단)");
        Thread poller = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                MetricsSnapshot snap = metricsCollector != null ? metricsCollector.getLastSnapshot() : MetricsSnapshot.EMPTY;
                out.println("─── " + Instant.now() + " ───");
                snap.nodes().entrySet().stream()
                        .filter(e -> e.getKey().startsWith(flowId + "|"))
                        .forEach(e -> {
                            String nodeId = e.getKey().split("\\|", 2)[1];
                            MetricsSnapshot.NodeStatsSnapshot ns = e.getValue();
                            out.printf("  %-20s  in=%,d  out=%,d  err=%d  avg=%.1fms%n",
                                    nodeId, ns.inCount(), ns.outCount(), ns.errors(), ns.avgMs());
                        });
                try { Thread.sleep(1000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        try { new BufferedReader(new java.io.InputStreamReader(System.in)).readLine(); }
        catch (IOException ignored) {}
        poller.interrupt();
        poller.join(500);
        out.println("모니터링 중단.");
    }

    private void monitorNode(String flowId, String nodeId, PrintStream out) throws InterruptedException {
        out.printf("Monitoring node '%s/%s'... (Enter로 중단)%n", flowId, nodeId);
        Thread poller = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (metricsCollector != null) {
                    MetricsSnapshot.NodeStatsSnapshot ns =
                            metricsCollector.getLastSnapshot().nodes().get(flowId + "|" + nodeId);
                    if (ns != null) {
                        out.printf("[%s]  in=%,d  out=%,d  err=%d  avg=%.2fms  p99=%.2fms%n",
                                Instant.now().toString().substring(11, 19),
                                ns.inCount(), ns.outCount(), ns.errors(), ns.avgMs(), ns.p99Ms());
                    }
                }
                try { Thread.sleep(1000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        try { new BufferedReader(new java.io.InputStreamReader(System.in)).readLine(); }
        catch (IOException ignored) {}
        poller.interrupt();
        poller.join(500);
        out.println("모니터링 중단.");
    }

    // ── sensor 명령 ──────────────────────────────────────────────────────────

    private void handleSensor(String[] args, PrintStream out) {
        if (args.length < 2) { out.println("사용법: sensor list  또는  sensor stats <name> --window <1m|1h|1d> [--range <Nh|Nd>]"); return; }
        switch (args[1].toLowerCase()) {
            case "list"  -> sensorList(out);
            case "stats" -> sensorStats(args, out);
            default -> out.println("알 수 없는 sensor 서브커맨드: " + args[1]);
        }
    }

    private void sensorList(PrintStream out) {
        if (metricsCollector == null) { out.println("MetricsCollector 비활성화."); return; }
        Map<String, ?> defs = metricsCollector.getDomainDefs();
        if (defs.isEmpty()) { out.println("등록된 도메인 메트릭이 없습니다."); return; }
        out.printf("%-20s %-20s %-10s %-30s%n", "SENSOR", "FLOW", "FIELD", "WINDOWS");
        out.println("-".repeat(82));
        metricsCollector.getDomainDefs().forEach((flowId, list) ->
                list.forEach(d -> out.printf("%-20s %-20s %-10s %-30s%n",
                        d.name(), flowId, d.field(), d.windows())));
    }

    private void sensorStats(String[] args, PrintStream out) {
        if (args.length < 3) { out.println("사용법: sensor stats <name> --window <1m|1h|1d> [--range <Nh|Nd>]"); return; }
        String name   = args[2];
        String window = argValue(args, "--window", "1h");
        String range  = argValue(args, "--range",  "24h");

        if (influxConfig == null) { out.println("InfluxDB 설정 없음."); return; }

        String measurement = "sensor_stats_" + window;
        String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "%s" and r.sensor_name == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """, influxConfig.bucket(), range, measurement, name);

        try {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(influxConfig.url() + "/api/v2/query?org=" + influxConfig.org()))
                    .header("Authorization", "Token " + influxConfig.token())
                    .header("Content-Type", "application/vnd.flux")
                    .header("Accept", "application/csv")
                    .POST(HttpRequest.BodyPublishers.ofString(flux))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                out.println("[error] InfluxDB 응답: " + resp.statusCode()); return;
            }

            String[] lines = resp.body().split("\n");
            if (lines.length <= 1) { out.println("데이터 없음 (window=" + window + ", range=" + range + ")"); return; }

            // CSV 헤더에서 컬럼 인덱스 찾기
            String[] header = lines[1].split(",");
            int iTime = indexOf(header, "_time");
            int iAvg  = indexOf(header, "avg");
            int iMin  = indexOf(header, "min");
            int iMax  = indexOf(header, "max");
            int iCnt  = indexOf(header, "count");

            out.printf("Sensor: %s  (window=%s, range=%s)%n", name, window, range);
            out.printf("%-28s %8s %8s %8s %8s%n", "TIME", "AVG", "MIN", "MAX", "COUNT");
            out.println("-".repeat(64));

            for (int i = 2; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split(",");
                if (cols.length <= Math.max(iTime, iAvg)) continue;
                String time = iTime >= 0 ? cols[iTime].replace("T", " ").substring(0, 19) : "?";
                String avg  = iAvg >= 0  ? String.format("%.2f", Double.parseDouble(cols[iAvg])) : "-";
                String min  = iMin >= 0  ? String.format("%.2f", Double.parseDouble(cols[iMin])) : "-";
                String max  = iMax >= 0  ? String.format("%.2f", Double.parseDouble(cols[iMax])) : "-";
                String cnt  = iCnt >= 0  ? cols[iCnt] : "-";
                out.printf("%-28s %8s %8s %8s %8s%n", time, avg, min, max, cnt);
            }
        } catch (Exception e) {
            out.println("[error] InfluxDB 조회 실패: " + e.getMessage());
        }
    }

    private static String argValue(String[] args, String flag, String def) {
        for (int i = 0; i < args.length - 1; i++)
            if (args[i].equals(flag)) return args[i + 1];
        return def;
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++)
            if (header[i].trim().equals(name)) return i;
        return -1;
    }

    // ── help 명령 ────────────────────────────────────────────────────────────

    private void handleHelp(String[] args, PrintStream out) {
        out.println("""
                FBP Engine CLI 명령어
                ─────────────────────────────────────────────────────────────────
                flow list                               플로우 목록
                flow deploy <file>                      JSON 파일에서 배포
                flow start|stop|restart|remove <id>     생명주기 관리
                flow status <id>                        상세 상태
                flow patch <id> <file>                  동적 패치
                flow add-node <id> <spec>               노드 추가 (파일 또는 인라인 JSON)
                flow remove-node <id> <node-id>         노드 제거
                flow add-wire <id> <from> <to>          연결 추가 (ex: src:out sink:in)
                flow remove-wire <id> <wire-id>         연결 제거
                flow update-config <id> <node-id> <cfg> 노드 설정 변경
                flow history <id>                       변경 이력 조회
                flow rollback <id> <revision>           특정 revision으로 롤백
                ─────────────────────────────────────────────────────────────────
                node list <flow-id>                     노드 목록
                node info <flow-id> <node-id>           노드 상세
                node stats <flow-id> <node-id>          노드 통계
                ─────────────────────────────────────────────────────────────────
                wire list <flow-id>                     연결 목록
                wire info <flow-id> <wire-id>           연결 상세
                wire stats <flow-id> <wire-id>          연결 통계
                ─────────────────────────────────────────────────────────────────
                monitor flow <flow-id>                  플로우 실시간 모니터링 (Enter로 중단)
                monitor node <flow-id> <node-id>        노드 실시간 모니터링 (Enter로 중단)
                ─────────────────────────────────────────────────────────────────
                sensor list                             등록된 도메인 메트릭 목록
                sensor stats <name> --window <1m|1h|1d> [--range <Nh|Nd>]
                ─────────────────────────────────────────────────────────────────
                stats                                   엔진 전체 통계
                broker status                           MQTT 브로커 연결 상태
                influx status                           InfluxDB 연동 상태
                help                                    이 도움말
                exit                                    종료
                """);
    }

    // ── 유틸리티 ─────────────────────────────────────────────────────────────

    /** args[index]를 반환한다. 인자가 없으면 IllegalArgumentException을 던진다. */
    private static String req(String[] args, int index, String usage) {
        if (index >= args.length) {
            throw new IllegalArgumentException("인자 부족. 사용법: " + usage);
        }
        return args[index];
    }

    private NodeDefinition parseNodeSpec(String specArg) throws Exception {
        String json = jsonOrFile(specArg);
        Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
        String id   = (String) map.get("id");
        String type = (String) map.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = map.containsKey("config")
                ? (Map<String, Object>) map.get("config")
                : Map.of();
        return new NodeDefinition(id, type, config);
    }

    private Map<String, Object> parseConfig(String cfgArg) throws Exception {
        String json = jsonOrFile(cfgArg);
        return mapper.readValue(json, new TypeReference<>() {});
    }

    private String jsonOrFile(String arg) throws IOException {
        if (arg.trim().startsWith("{")) return arg;
        return Files.readString(Path.of(arg));
    }

    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
