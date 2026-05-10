package com.fbp.engine.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.metrics.DomainMetricDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 형식의 플로우 정의를 파싱하는 FlowParser 구현체.
 *<pre>
 * 지원 포맷:
 * {
 *   "id": "flow-id",
 *   "transport": { "type": "mqtt", "broker": "tcp://...", "qos": 1 },  // 선택
 *   "nodes": [
 *     { "id": "n1", "type": "ThresholdFilter", "config": { "threshold": 30 } }
 *   ],
 *   "connections": [
 *     { "from": "n1:out", "to": "n2:in" }
 *   ]
 * }
 * </pre>
 */
public class JsonFlowParser implements FlowParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public FlowDefinition parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return parseRoot(root);
        } catch (IOException e) {
            throw new FlowParserException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public FlowDefinition parse(InputStream input) {
        try {
            JsonNode root = mapper.readTree(input);
            return parseRoot(root);
        } catch (IOException e) {
            throw new FlowParserException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    // ── 내부 파싱 ─────────────────────────────────────────────────────────────

    private FlowDefinition parseRoot(JsonNode root) {
        String id = requireText(root, "id", "플로우 id");

        TransportDefinition transport = parseTransport(root.get("transport"));

        JsonNode nodesNode = root.get("nodes");
        if (nodesNode == null || !nodesNode.isArray()) {
            throw new FlowParserException("'nodes' 배열이 없거나 형식이 올바르지 않습니다");
        }
        List<NodeDefinition> nodes = parseNodes(nodesNode);
        if (nodes.isEmpty()) {
            throw new FlowParserException("'nodes' 배열이 비어있습니다");
        }

        JsonNode connsNode = root.get("connections");
        List<ConnectionDefinition> connections = connsNode != null && connsNode.isArray()
                ? parseConnections(connsNode)
                : List.of();

        List<DomainMetricDefinition> domainMetrics = parseDomainMetrics(root);

        return new FlowDefinition(id, transport, nodes, connections, domainMetrics);
    }

    private TransportDefinition parseTransport(JsonNode node) {
        if (node == null || node.isNull()) {
            return TransportDefinition.LOCAL;
        }
        String type   = node.path("type").asText(TransportDefinition.TYPE_LOCAL);
        String broker = node.path("broker").asText(null);
        int qos       = node.path("qos").asInt(1);

        if (TransportDefinition.TYPE_MQTT.equals(type) && (broker == null || broker.isBlank())) {
            throw new FlowParserException("transport.type이 mqtt이면 broker가 필요합니다");
        }
        return new TransportDefinition(type, broker, qos);
    }

    private List<NodeDefinition> parseNodes(JsonNode nodesNode) {
        List<NodeDefinition> result = new ArrayList<>();
        for (JsonNode nodeJson : nodesNode) {
            String id   = requireText(nodeJson, "id",   "노드 id");
            String type = requireText(nodeJson, "type", "노드 type (노드: " + id + ")");
            Map<String, Object> config = parseConfig(nodeJson.get("config"));
            result.add(new NodeDefinition(id, type, config));
        }
        return result;
    }

    private List<ConnectionDefinition> parseConnections(JsonNode connsNode) {
        List<ConnectionDefinition> result = new ArrayList<>();
        for (JsonNode connJson : connsNode) {
            String from = requireText(connJson, "from", "연결 from");
            String to   = requireText(connJson, "to",   "연결 to");
            result.add(ConnectionDefinition.parse(from, to));
        }
        return result;
    }

    private Map<String, Object> parseConfig(JsonNode configNode) {
        if (configNode == null || configNode.isNull()) {
            return new HashMap<>();
        }
        try {
            return mapper.convertValue(configNode, MAP_TYPE);
        } catch (Exception e) {
            throw new FlowParserException("config 파싱 실패: " + e.getMessage(), e);
        }
    }

    private List<DomainMetricDefinition> parseDomainMetrics(JsonNode root) {
        JsonNode metricsNode = root.get("metrics");
        if (metricsNode == null || !metricsNode.has("domain")) return Collections.emptyList();
        JsonNode domainArray = metricsNode.get("domain");
        if (!domainArray.isArray()) return Collections.emptyList();

        List<DomainMetricDefinition> result = new ArrayList<>();
        for (JsonNode d : domainArray) {
            String name  = requireText(d, "name",  "metrics.domain[].name");
            JsonNode src = d.get("source");
            if (src == null) throw new FlowParserException("metrics.domain[].source 누락");
            String sourceNodeId = requireText(src, "node", "metrics.domain[].source.node");
            String sourcePort   = requireText(src, "port", "metrics.domain[].source.port");
            String field        = requireText(d, "field", "metrics.domain[].field");
            JsonNode windowsNode = d.get("windows");
            if (windowsNode == null || !windowsNode.isArray()) {
                throw new FlowParserException("metrics.domain[].windows 배열 누락");
            }
            List<String> windows = new ArrayList<>();
            for (JsonNode w : windowsNode) windows.add(w.asText());
            result.add(new DomainMetricDefinition(name, sourceNodeId, sourcePort, field, windows));
        }
        return result;
    }

    private String requireText(JsonNode node, String field, String label) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new FlowParserException("필수 필드 누락: " + label);
        }
        return value.asText();
    }
}