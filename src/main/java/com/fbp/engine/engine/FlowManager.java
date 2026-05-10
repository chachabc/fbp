package com.fbp.engine.engine;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.Node;
import com.fbp.engine.core.OutputPort;
import com.fbp.engine.core.stage_3_p.Connection;
import com.fbp.engine.core.stage_3_p.LocalConnection;
import com.fbp.engine.core.stage_3_p.MqttBridgeConnection;
import com.fbp.engine.message.Message;
import com.fbp.engine.metrics.MetricsCollector;
import com.fbp.engine.metrics.NodeMetricEvent;
import com.fbp.engine.parser.ConnectionDefinition;
import com.fbp.engine.parser.FlowDefinition;
import com.fbp.engine.parser.NodeDefinition;
import com.fbp.engine.parser.TransportDefinition;
import com.fbp.engine.registry.NodeRegistry;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 플로우의 전체 생명주기와 동적 변경을 관리한다.
 *
 * <pre>
 * deploy → start → stop → restart / remove
 *
 * 동적 변경 API:
 *   addNode / removeNode / addWire / removeWire / updateNodeConfig
 *   patch   — diff 기반 핫 패치
 *   getHistory / rollback — 변경 이력 및 롤백
 * </pre>
 */
public class FlowManager {

    private static final Logger log = LoggerFactory.getLogger(FlowManager.class);
    private static final int RUNNER_STOP_TIMEOUT_MS = 3000;

    private final NodeRegistry nodeRegistry;
    private final MetricsCollector metricsCollector; // nullable
    private final Map<String, ManagedFlow> flows = new ConcurrentHashMap<>();

    public FlowManager(NodeRegistry nodeRegistry) {
        this(nodeRegistry, null);
    }

    public FlowManager(NodeRegistry nodeRegistry, MetricsCollector metricsCollector) {
        this.nodeRegistry     = nodeRegistry;
        this.metricsCollector = metricsCollector;
    }

    // ── 기본 생명주기 API ─────────────────────────────────────────────────────

    /**
     * 플로우를 배포한다. 노드를 생성하고 연결을 배선하지만 아직 시작하지 않는다.
     */
    public void deploy(FlowDefinition definition) {
        String flowId = definition.getId();
        if (flows.containsKey(flowId)) {
            throw new FlowManagerException("이미 배포된 플로우 id: " + flowId);
        }

        Map<String, Node> nodeMap = createNodes(definition);
        List<Connection> connections     = new ArrayList<>();
        Map<String, List<Connection>> nodeInputs = new HashMap<>();
        Map<String, WireRuntime> wireRuntimes    = new LinkedHashMap<>();

        for (ConnectionDefinition connDef : definition.getConnections()) {
            Connection conn = createConnection(definition, connDef);
            String wireId   = connectionId(connDef);

            if (metricsCollector != null) {
                metricsCollector.registerWire(wireId, flowId,
                        connDef.getSourceNodeId(), connDef.getSourcePort());
            }

            wireOutput(nodeMap, connDef, conn);
            connections.add(conn);
            nodeInputs.computeIfAbsent(connDef.getTargetNodeId(), k -> new ArrayList<>()).add(conn);
            wireRuntimes.put(wireId, new WireRuntime(conn, null, connDef));
        }

        if (metricsCollector != null) {
            metricsCollector.registerFlow(flowId, definition.getDomainMetrics());
        }

        ManagedFlow flow = new ManagedFlow(definition, nodeMap, connections, nodeInputs, wireRuntimes);
        flows.put(flowId, flow);
        log.info("[{}] 배포 완료 — 노드: {}, 연결: {}, transport: {}",
                flowId, nodeMap.size(), connections.size(), definition.getTransport());
    }

    /** 배포된 플로우를 시작한다. 노드를 초기화하고 러너 스레드를 시작한다. */
    public void start(String flowId) {
        ManagedFlow flow = requireFlow(flowId);
        if (flow.state == FlowState.RUNNING) {
            throw new FlowManagerException("이미 실행 중인 플로우: " + flowId);
        }
        flow.nodeMap.values().forEach(Node::initialize);
        startRunners(flow, flowId);
        flow.state = FlowState.RUNNING;
        flow.startedAt = Instant.now();
        log.info("[{}] 시작", flowId);
    }

    /** 실행 중인 플로우를 정지한다. 러너 스레드를 중단하고 노드를 종료한다. */
    public void stop(String flowId) {
        ManagedFlow flow = requireFlow(flowId);
        if (flow.state != FlowState.RUNNING) {
            throw new FlowManagerException("실행 중이 아닌 플로우: " + flowId);
        }
        stopRunners(flow);
        flow.nodeMap.values().forEach(Node::shutdown);
        flow.state = FlowState.STOPPED;
        log.info("[{}] 정지", flowId);
    }

    /** 정지된 플로우를 재시작한다. */
    public void restart(String flowId) {
        ManagedFlow flow = requireFlow(flowId);
        if (flow.state == FlowState.RUNNING) {
            stop(flowId);
        }
        flow.runnerThreads.clear();
        flow.nodeMap.values().forEach(Node::initialize);
        startRunners(flow, flowId);
        flow.state = FlowState.RUNNING;
        log.info("[{}] 재시작", flowId);
    }

    /** 플로우를 삭제한다. 실행 중이면 먼저 정지한다. */
    public void remove(String flowId) {
        ManagedFlow flow = requireFlow(flowId);
        if (flow.state == FlowState.RUNNING) {
            stop(flowId);
        }
        flow.connections.forEach(Connection::close);
        if (metricsCollector != null) {
            metricsCollector.unregisterFlow(flowId);
        }
        flows.remove(flowId);
        log.info("[{}] 삭제", flowId);
    }

    public FlowState getStatus(String flowId) {
        return requireFlow(flowId).state;
    }

    public Map<String, FlowState> list() {
        Map<String, FlowState> result = new LinkedHashMap<>();
        flows.forEach((id, flow) -> result.put(id, flow.state));
        return Collections.unmodifiableMap(result);
    }

    public Node getNode(String flowId, String nodeId) {
        ManagedFlow flow = requireFlow(flowId);
        Node node = flow.nodeMap.get(nodeId);
        if (node == null) {
            throw new FlowManagerException("노드 없음: " + flowId + "/" + nodeId);
        }
        return node;
    }

    // ── CLI 조회 API ──────────────────────────────────────────────────────────

    /** 현재 적용된 FlowDefinition을 반환한다. 동적 변경 후 갱신된 버전이다. */
    public FlowDefinition getCurrentDefinition(String flowId) {
        return requireFlow(flowId).currentDefinition;
    }

    /** 플로우가 마지막으로 start()된 시각을 반환한다. 한 번도 시작하지 않았으면 null. */
    public Instant getStartedAt(String flowId) {
        return requireFlow(flowId).startedAt;
    }

    /**
     * 플로우 내 와이어 목록을 반환한다. CLI의 wire list 명령에 사용한다.
     */
    public List<WireInfo> getWireInfos(String flowId) {
        ManagedFlow flow = requireFlow(flowId);
        return flow.wireRuntimes.values().stream()
                .map(wr -> new WireInfo(
                        connectionId(wr.definition()),
                        wr.definition().getSourceNodeId() + ":" + wr.definition().getSourcePort(),
                        wr.definition().getTargetNodeId() + ":" + wr.definition().getTargetPort(),
                        wr.connection().getBufferSize()
                ))
                .toList();
    }

    /** 와이어 하나의 CLI 표시용 정보. */
    public record WireInfo(String wireId, String from, String to, int queueSize) {}

    /** MQTT 브로커를 사용하는 플로우의 브로커 정보. */
    public record BrokerInfo(String flowId, String brokerUrl, int wireCount) {}

    /** MQTT transport를 사용하는 플로우들의 브로커 정보를 반환한다. */
    public List<BrokerInfo> getBrokerInfos() {
        return flows.values().stream()
                .filter(f -> f.currentDefinition.getTransport().isMqtt())
                .map(f -> new BrokerInfo(
                        f.currentDefinition.getId(),
                        f.currentDefinition.getTransport().getBroker(),
                        f.wireRuntimes.size()))
                .toList();
    }

    // ── 동적 변경 API ─────────────────────────────────────────────────────────

    /**
     * 실행 중인 플로우에 노드를 추가한다.
     * 노드만 추가되며, 연결은 {@link #addWire}로 별도 추가한다.
     */
    public void addNode(String flowId, NodeDefinition nodeDef) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            if (flow.nodeMap.containsKey(nodeDef.getId())) {
                throw new FlowManagerException("이미 존재하는 노드: " + nodeDef.getId());
            }
            Node node = createNode(nodeDef);
            flow.nodeMap.put(nodeDef.getId(), node);
            if (flow.state == FlowState.RUNNING) {
                node.initialize();
            }

            flow.currentDefinition = appendNode(flow.currentDefinition, nodeDef);
            recordHistory(flow, "add-node", "노드 추가: " + nodeDef.getId());
            log.info("[{}] 노드 추가: {}", flowId, nodeDef.getId());
        } finally {
            flow.patchLock.unlock();
        }
    }

    /**
     * 실행 중인 플로우에서 노드를 제거한다.
     * 연결된 모든 와이어를 먼저 제거한 뒤 노드를 종료한다.
     */
    public void removeNode(String flowId, String nodeId) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            if (!flow.nodeMap.containsKey(nodeId)) {
                throw new FlowManagerException("노드 없음: " + nodeId);
            }
            // 이 노드와 연결된 모든 와이어 먼저 제거
            List<String> wiresToRemove = flow.wireRuntimes.entrySet().stream()
                    .filter(e -> {
                        ConnectionDefinition d = e.getValue().definition();
                        return d.getSourceNodeId().equals(nodeId) || d.getTargetNodeId().equals(nodeId);
                    })
                    .map(Map.Entry::getKey)
                    .toList();
            wiresToRemove.forEach(wId -> removeWireInternal(flow, wId));

            flow.nodeMap.remove(nodeId).shutdown();

            flow.currentDefinition = dropNode(flow.currentDefinition, nodeId);
            recordHistory(flow, "remove-node", "노드 제거: " + nodeId);
            log.info("[{}] 노드 제거: {}", flowId, nodeId);
        } finally {
            flow.patchLock.unlock();
        }
    }

    /**
     * 실행 중인 플로우에 연결(와이어)을 추가한다.
     * 양쪽 노드가 이미 존재해야 한다.
     */
    public void addWire(String flowId, ConnectionDefinition connDef) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            String wireId = connectionId(connDef);
            if (flow.wireRuntimes.containsKey(wireId)) {
                throw new FlowManagerException("이미 존재하는 연결: " + wireId);
            }
            addWireInternal(flow, flowId, connDef);

            flow.currentDefinition = appendWire(flow.currentDefinition, connDef);
            recordHistory(flow, "add-wire", "연결 추가: " + wireId);
            log.info("[{}] 연결 추가: {}", flowId, wireId);
        } finally {
            flow.patchLock.unlock();
        }
    }

    /**
     * 실행 중인 플로우에서 연결(와이어)을 제거한다.
     * 러너를 중단하고 Connection을 닫은 뒤 source 포트에서 분리한다.
     *
     * @param wireId {@code connectionId(connDef)} 형식의 식별자
     */
    public void removeWire(String flowId, String wireId) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            if (!flow.wireRuntimes.containsKey(wireId)) {
                throw new FlowManagerException("존재하지 않는 연결: " + wireId);
            }
            removeWireInternal(flow, wireId);

            flow.currentDefinition = dropWire(flow.currentDefinition, wireId);
            recordHistory(flow, "remove-wire", "연결 제거: " + wireId);
            log.info("[{}] 연결 제거: {}", flowId, wireId);
        } finally {
            flow.patchLock.unlock();
        }
    }

    /**
     * 노드 설정을 변경한다.
     * 입력 러너를 정지하고 노드를 교체한 뒤 다시 시작한다(stop/replace/start).
     */
    public void updateNodeConfig(String flowId, String nodeId, Map<String, Object> newConfig) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            Node oldNode = flow.nodeMap.get(nodeId);
            if (oldNode == null) {
                throw new FlowManagerException("노드 없음: " + nodeId);
            }
            NodeDefinition oldDef = flow.currentDefinition.findNode(nodeId)
                    .orElseThrow(() -> new FlowManagerException("노드 정의 없음: " + nodeId));

            // 이 노드를 target으로 하는 와이어의 러너 정지
            List<WireRuntime> inputWires = flow.wireRuntimes.values().stream()
                    .filter(wr -> wr.definition().getTargetNodeId().equals(nodeId))
                    .toList();
            for (WireRuntime wr : inputWires) {
                stopThread(wr.runnerThread(), flow);
            }

            oldNode.shutdown();

            NodeDefinition newDef = new NodeDefinition(nodeId, oldDef.getType(), newConfig);
            Node newNode = createNode(newDef);
            flow.nodeMap.put(nodeId, newNode);

            if (flow.state == FlowState.RUNNING) {
                newNode.initialize();
                for (WireRuntime wr : inputWires) {
                    Thread t = startRunner(flowId, newNode, wr.connection());
                    flow.runnerThreads.add(t);
                    flow.wireRuntimes.put(connectionId(wr.definition()),
                            new WireRuntime(wr.connection(), t, wr.definition()));
                }
            }

            flow.currentDefinition = replaceNodeDef(flow.currentDefinition, newDef);
            recordHistory(flow, "update-config", "노드 설정 변경: " + nodeId);
            log.info("[{}] 노드 설정 변경: {}", flowId, nodeId);
        } finally {
            flow.patchLock.unlock();
        }
    }

    /**
     * 새 FlowDefinition을 받아 현재 정의와의 diff를 계산하고 변경 부분만 적용한다.
     * 플로우 전체를 재배포하지 않으므로 영향 없는 노드는 메시지 처리를 중단하지 않는다.
     */
    public void patch(String flowId, FlowDefinition newDefinition) {
        ManagedFlow flow = requireFlow(flowId);
        flow.patchLock.lock();
        try {
            FlowDefinition current = flow.currentDefinition;

            Set<String> currentNodeIds = nodeIdSet(current);
            Set<String> newNodeIds     = nodeIdSet(newDefinition);
            Set<String> currentWireIds = wireIdSet(current);
            Set<String> newWireIds     = wireIdSet(newDefinition);

            // 1. 제거된 와이어 먼저 (노드 제거 전)
            diff(currentWireIds, newWireIds).forEach(wId -> removeWireInternal(flow, wId));

            // 2. 제거된 노드
            diff(currentNodeIds, newNodeIds).forEach(nId -> {
                Node node = flow.nodeMap.remove(nId);
                if (node != null) node.shutdown();
            });

            // 3. 추가된 노드
            for (NodeDefinition nd : newDefinition.getNodes()) {
                if (!currentNodeIds.contains(nd.getId())) {
                    Node node = createNode(nd);
                    flow.nodeMap.put(nd.getId(), node);
                    if (flow.state == FlowState.RUNNING) node.initialize();
                }
            }

            // 4. 추가된 와이어
            for (ConnectionDefinition cd : newDefinition.getConnections()) {
                if (!currentWireIds.contains(connectionId(cd))) {
                    addWireInternal(flow, flowId, cd);
                }
            }

            flow.currentDefinition = newDefinition;
            recordHistory(flow, "patch", "플로우 패치");
            log.info("[{}] 패치 완료 — 노드 {}→{}, 연결 {}→{}",
                    flowId, currentNodeIds.size(), newNodeIds.size(),
                    currentWireIds.size(), newWireIds.size());
        } finally {
            flow.patchLock.unlock();
        }
    }

    /** 변경 이력 목록을 반환한다. */
    public List<ChangeRecord> getHistory(String flowId) {
        return Collections.unmodifiableList(requireFlow(flowId).history);
    }

    /**
     * 지정 revision의 스냅샷으로 플로우를 복원한다.
     * 내부적으로 {@link #patch}를 사용하므로 변경 이력에 rollback 항목이 추가된다.
     */
    public void rollback(String flowId, int revision) {
        ManagedFlow flow = requireFlow(flowId);
        ChangeRecord target = flow.history.stream()
                .filter(r -> r.revision() == revision)
                .findFirst()
                .orElseThrow(() -> new FlowManagerException("이력 없음: revision=" + revision));

        patch(flowId, target.snapshot());  // patchLock은 ReentrantLock이므로 재진입 가능
        // patch가 기록한 "patch" 항목을 "rollback"으로 덮어씀
        if (!flow.history.isEmpty()) {
            ChangeRecord last = flow.history.remove(flow.history.size() - 1);
            flow.history.add(new ChangeRecord(last.revision(), last.timestamp(),
                    "rollback", "revision " + revision + " 으로 롤백", last.snapshot()));
        }
        log.info("[{}] 롤백 완료 → revision {}", flowId, revision);
    }

    // ── 내부 — 와이어 추가/제거 ────────────────────────────────────────────────

    private void addWireInternal(ManagedFlow flow, String flowId, ConnectionDefinition connDef) {
        Connection conn = createConnection(flow.currentDefinition, connDef);
        String wireId   = connectionId(connDef);

        if (metricsCollector != null) {
            metricsCollector.registerWire(wireId, flowId,
                    connDef.getSourceNodeId(), connDef.getSourcePort());
        }

        wireOutput(flow.nodeMap, connDef, conn);
        flow.connections.add(conn);
        flow.nodeInputs.computeIfAbsent(connDef.getTargetNodeId(), k -> new ArrayList<>()).add(conn);

        Thread runnerThread = null;
        if (flow.state == FlowState.RUNNING) {
            Node target = flow.nodeMap.get(connDef.getTargetNodeId());
            if (target != null) {
                runnerThread = startRunner(flowId, target, conn);
                flow.runnerThreads.add(runnerThread);
            }
        }
        flow.wireRuntimes.put(wireId, new WireRuntime(conn, runnerThread, connDef));
    }

    private void removeWireInternal(ManagedFlow flow, String wireId) {
        WireRuntime wr = flow.wireRuntimes.remove(wireId);
        if (wr == null) return;

        // source 포트에서 LegacyConnectionBridge 분리
        Node src = flow.nodeMap.get(wr.definition().getSourceNodeId());
        if (src instanceof AbstractNode abstractSrc) {
            OutputPort port = abstractSrc.getOutputPort(wr.definition().getSourcePort());
            if (port != null) port.disconnect(wireId);
        }

        stopThread(wr.runnerThread(), flow);
        wr.connection().close();

        flow.connections.remove(wr.connection());
        List<Connection> inputs = flow.nodeInputs.get(wr.definition().getTargetNodeId());
        if (inputs != null) inputs.remove(wr.connection());
    }

    // ── 내부 — FlowDefinition 불변 변환 헬퍼 ─────────────────────────────────

    private FlowDefinition appendNode(FlowDefinition base, NodeDefinition nd) {
        List<NodeDefinition> nodes = new ArrayList<>(base.getNodes());
        nodes.add(nd);
        return new FlowDefinition(base.getId(), base.getTransport(), nodes,
                new ArrayList<>(base.getConnections()), new ArrayList<>(base.getDomainMetrics()));
    }

    private FlowDefinition dropNode(FlowDefinition base, String nodeId) {
        List<NodeDefinition> nodes = base.getNodes().stream()
                .filter(n -> !n.getId().equals(nodeId)).collect(Collectors.toList());
        List<ConnectionDefinition> conns = base.getConnections().stream()
                .filter(c -> !c.getSourceNodeId().equals(nodeId) && !c.getTargetNodeId().equals(nodeId))
                .collect(Collectors.toList());
        return new FlowDefinition(base.getId(), base.getTransport(), nodes, conns,
                new ArrayList<>(base.getDomainMetrics()));
    }

    private FlowDefinition appendWire(FlowDefinition base, ConnectionDefinition cd) {
        List<ConnectionDefinition> conns = new ArrayList<>(base.getConnections());
        conns.add(cd);
        return new FlowDefinition(base.getId(), base.getTransport(),
                new ArrayList<>(base.getNodes()), conns, new ArrayList<>(base.getDomainMetrics()));
    }

    private FlowDefinition dropWire(FlowDefinition base, String wireId) {
        List<ConnectionDefinition> conns = base.getConnections().stream()
                .filter(c -> !connectionId(c).equals(wireId)).collect(Collectors.toList());
        return new FlowDefinition(base.getId(), base.getTransport(),
                new ArrayList<>(base.getNodes()), conns, new ArrayList<>(base.getDomainMetrics()));
    }

    private FlowDefinition replaceNodeDef(FlowDefinition base, NodeDefinition newDef) {
        List<NodeDefinition> nodes = base.getNodes().stream()
                .map(n -> n.getId().equals(newDef.getId()) ? newDef : n)
                .collect(Collectors.toList());
        return new FlowDefinition(base.getId(), base.getTransport(), nodes,
                new ArrayList<>(base.getConnections()), new ArrayList<>(base.getDomainMetrics()));
    }

    // ── 내부 — diff 유틸 ────────────────────────────────────────────────────

    private static Set<String> nodeIdSet(FlowDefinition d) {
        return d.getNodes().stream().map(NodeDefinition::getId).collect(Collectors.toSet());
    }

    private static Set<String> wireIdSet(FlowDefinition d) {
        return d.getConnections().stream().map(FlowManager::connectionId).collect(Collectors.toSet());
    }

    /** current에는 있지만 next에는 없는 항목 (제거됨). */
    private static Set<String> diff(Set<String> current, Set<String> next) {
        Set<String> removed = new HashSet<>(current);
        removed.removeAll(next);
        return removed;
    }

    // ── 내부 — 이력 관리 ────────────────────────────────────────────────────

    private void recordHistory(ManagedFlow flow, String operation, String description) {
        int rev = flow.nextRevision++;
        flow.history.add(new ChangeRecord(rev, System.currentTimeMillis(),
                operation, description, flow.currentDefinition));
    }

    // ── 내부 — 노드/스레드 생성 ────────────────────────────────────────────

    private Node createNode(NodeDefinition nd) {
        try {
            return nodeRegistry.create(nd.getType(), nd.getId(), nd.getConfig());
        } catch (Exception e) {
            throw new FlowManagerException("노드 생성 실패 — type: " + nd.getType() + ", id: " + nd.getId(), e);
        }
    }

    private Map<String, Node> createNodes(FlowDefinition definition) {
        Map<String, Node> nodeMap = new LinkedHashMap<>();
        for (NodeDefinition nd : definition.getNodes()) {
            nodeMap.put(nd.getId(), createNode(nd));
        }
        return nodeMap;
    }

    private Connection createConnection(FlowDefinition definition, ConnectionDefinition connDef) {
        String connId = connectionId(connDef);
        TransportDefinition transport = definition.getTransport();
        if (transport.isMqtt()) {
            String topic = "fbp/" + definition.getId() + "/" + connId;
            try {
                return new MqttBridgeConnection(connId, transport.getBroker(), topic, transport.getQos(),
                        metricsCollector, definition.getId());
            } catch (MqttException e) {
                throw new FlowManagerException("MQTT 연결 생성 실패: " + connId, e);
            }
        }
        return new LocalConnection(connId, metricsCollector, definition.getId());
    }

    public static String connectionId(ConnectionDefinition connDef) {
        return connDef.getSourceNodeId() + "." + connDef.getSourcePort()
                + "-" + connDef.getTargetNodeId() + "." + connDef.getTargetPort();
    }

    private void wireOutput(Map<String, Node> nodeMap, ConnectionDefinition connDef, Connection conn) {
        Node srcNode = nodeMap.get(connDef.getSourceNodeId());
        if (!(srcNode instanceof AbstractNode abstractSrc)) {
            throw new FlowManagerException(
                    "source 노드가 AbstractNode를 상속하지 않습니다: " + connDef.getSourceNodeId());
        }
        OutputPort outputPort = abstractSrc.getOutputPort(connDef.getSourcePort());
        if (outputPort == null) {
            throw new FlowManagerException(
                    "OutputPort 없음: " + connDef.getSourceNodeId() + ":" + connDef.getSourcePort());
        }
        outputPort.connect(new LegacyConnectionBridge(conn.getId(), conn));
    }

    private Thread startRunner(String flowId, Node node, Connection conn) {
        return Thread.ofVirtual()
                .name("fbp-runner-" + flowId + "-" + node.getId())
                .start(new NodeRunner(flowId, node, conn, metricsCollector));
    }

    private void startRunners(ManagedFlow flow, String flowId) {
        flow.nodeInputs.forEach((nodeId, conns) -> {
            Node node = flow.nodeMap.get(nodeId);
            for (Connection conn : conns) {
                Thread t = startRunner(flowId, node, conn);
                flow.runnerThreads.add(t);
                // wireRuntimes에 스레드 정보 갱신
                WireRuntime wr = flow.wireRuntimes.get(conn.getId());
                if (wr != null) {
                    flow.wireRuntimes.put(conn.getId(), new WireRuntime(conn, t, wr.definition()));
                }
            }
        });
    }

    private void stopRunners(ManagedFlow flow) {
        flow.runnerThreads.forEach(Thread::interrupt);
        for (Thread t : flow.runnerThreads) {
            try { t.join(RUNNER_STOP_TIMEOUT_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        flow.runnerThreads.clear();
    }

    private void stopThread(Thread t, ManagedFlow flow) {
        if (t == null) return;
        t.interrupt();
        try { t.join(RUNNER_STOP_TIMEOUT_MS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        flow.runnerThreads.remove(t);
    }

    private ManagedFlow requireFlow(String flowId) {
        ManagedFlow flow = flows.get(flowId);
        if (flow == null) {
            throw new FlowManagerException("존재하지 않는 플로우: " + flowId);
        }
        return flow;
    }

    // ── 내부 클래스 ──────────────────────────────────────────────────────────

    /**
     * 구 AbstractNode(DefaultOutputPort)와 새 stage_3_p.Connection을 이어주는 어댑터.
     */
    private static class LegacyConnectionBridge extends com.fbp.engine.core.Connection {

        private final Connection target;

        LegacyConnectionBridge(String id, Connection target) {
            super(id, 1);
            this.target = target;
        }

        @Override
        public void deliver(Message message) { target.deliver(message); }

        @Override
        public int getBufferSize() { return target.getBufferSize(); }
    }

    /** 입력 Connection을 poll하여 메시지를 노드에 전달하는 러너. */
    private record NodeRunner(String flowId, Node node, Connection connection,
                              MetricsCollector collector) implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message message = connection.poll();
                    long t0 = System.nanoTime();
                    boolean hadError = false;
                    try {
                        node.process(message);
                    } catch (Exception e) {
                        hadError = true;
                        log.warn("[{}][{}] 노드 처리 중 예외: {}", flowId, node.getId(), e.getMessage());
                    }
                    if (collector != null) {
                        collector.record(new NodeMetricEvent(
                                flowId, node.getId(), System.nanoTime() - t0, hadError));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 와이어 하나의 런타임 상태.
     * {@code runnerThread}는 start() 전에는 null일 수 있다.
     */
    record WireRuntime(Connection connection, Thread runnerThread, ConnectionDefinition definition) {}

    /**
     * 변경 이력 항목.
     * {@code snapshot}은 변경 직후의 FlowDefinition 불변 스냅샷이다.
     */
    public record ChangeRecord(int revision, long timestamp,
                               String operation, String description,
                               FlowDefinition snapshot) {}

    /** 배포된 플로우의 런타임 상태를 담는 컨테이너. */
    private static class ManagedFlow {
        FlowDefinition currentDefinition;
        final Map<String, Node>             nodeMap;
        final List<Connection>              connections;
        final Map<String, List<Connection>> nodeInputs;
        final Map<String, WireRuntime>      wireRuntimes;
        final List<Thread>                  runnerThreads = new ArrayList<>();
        final List<ChangeRecord>            history       = new ArrayList<>();
        final ReentrantLock                 patchLock     = new ReentrantLock();
        volatile FlowState state = FlowState.DEPLOYED;
        volatile Instant startedAt = null;
        int nextRevision = 1;

        ManagedFlow(FlowDefinition definition,
                    Map<String, Node> nodeMap,
                    List<Connection> connections,
                    Map<String, List<Connection>> nodeInputs,
                    Map<String, WireRuntime> wireRuntimes) {
            this.currentDefinition = definition;
            this.nodeMap           = nodeMap;
            this.connections       = connections;
            this.nodeInputs        = nodeInputs;
            this.wireRuntimes      = wireRuntimes;
        }
    }
}