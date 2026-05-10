package com.fbp.engine.parser;

import com.fbp.engine.metrics.DomainMetricDefinition;

import java.util.*;

/**
 * 파싱된 플로우 전체 정의를 담는 불변 데이터 객체.
 *
 * 생성 시 다음을 검증한다:
 *   - 노드 id 중복 없음
 *   - 모든 연결이 존재하는 노드를 참조함
 */
public class FlowDefinition {

    private final String id;
    private final TransportDefinition transport;
    private final List<NodeDefinition> nodes;
    private final List<ConnectionDefinition> connections;
    private final List<DomainMetricDefinition> domainMetrics;
    private final Map<String, NodeDefinition> nodeIndex; // id → NodeDefinition 빠른 조회

    public FlowDefinition(String id,
                          TransportDefinition transport,
                          List<NodeDefinition> nodes,
                          List<ConnectionDefinition> connections) {
        this(id, transport, nodes, connections, Collections.emptyList());
    }

    public FlowDefinition(String id,
                          TransportDefinition transport,
                          List<NodeDefinition> nodes,
                          List<ConnectionDefinition> connections,
                          List<DomainMetricDefinition> domainMetrics) {
        this.id            = id;
        this.transport     = transport != null ? transport : TransportDefinition.LOCAL;
        this.nodes         = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.connections   = Collections.unmodifiableList(new ArrayList<>(connections));
        this.domainMetrics = Collections.unmodifiableList(new ArrayList<>(domainMetrics));
        this.nodeIndex     = buildIndex(this.nodes);

        validate();
    }

    private Map<String, NodeDefinition> buildIndex(List<NodeDefinition> nodes) {
        Map<String, NodeDefinition> index = new LinkedHashMap<>();
        for (NodeDefinition node : nodes) {
            if (index.containsKey(node.getId())) {
                throw new FlowParserException("중복된 노드 id: " + node.getId());
            }
            index.put(node.getId(), node);
        }
        return Collections.unmodifiableMap(index);
    }

    private void validate() {
        for (ConnectionDefinition conn : connections) {
            if (!nodeIndex.containsKey(conn.getSourceNodeId())) {
                throw new FlowParserException(
                        "연결의 source 노드가 존재하지 않습니다: " + conn.getSourceNodeId());
            }
            if (!nodeIndex.containsKey(conn.getTargetNodeId())) {
                throw new FlowParserException(
                        "연결의 target 노드가 존재하지 않습니다: " + conn.getTargetNodeId());
            }
        }
    }

    public String getId()                              { return id; }
    public TransportDefinition getTransport()          { return transport; }
    public List<NodeDefinition> getNodes()             { return nodes; }
    public List<ConnectionDefinition> getConnections() { return connections; }
    public List<DomainMetricDefinition> getDomainMetrics() { return domainMetrics; }

    /** 노드 id로 NodeDefinition을 조회한다. 없으면 empty. */
    public Optional<NodeDefinition> findNode(String nodeId) {
        return Optional.ofNullable(nodeIndex.get(nodeId));
    }

    public boolean hasNode(String nodeId) {
        return nodeIndex.containsKey(nodeId);
    }

    @Override
    public String toString() {
        return "FlowDefinition{id='" + id + "', transport=" + transport
                + ", nodes=" + nodes.size() + ", connections=" + connections.size() + "}";
    }
}