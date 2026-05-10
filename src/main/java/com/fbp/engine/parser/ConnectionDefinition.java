package com.fbp.engine.parser;

/**
 * 플로우 JSON 내 연결 하나를 나타내는 불변 데이터 객체.
 *
 * <p>JSON 표현 "sensor:out" → sourceNodeId="sensor", sourcePort="out"</p>
 *
 */
public class ConnectionDefinition {

    private final String sourceNodeId;
    private final String sourcePort;
    private final String targetNodeId;
    private final String targetPort;

    private ConnectionDefinition(String sourceNodeId, String sourcePort,
                                  String targetNodeId, String targetPort) {
        this.sourceNodeId = sourceNodeId;
        this.sourcePort   = sourcePort;
        this.targetNodeId = targetNodeId;
        this.targetPort   = targetPort;
    }

    /**
     * "nodeId:portName" 형식의 문자열을 파싱하여 ConnectionDefinition을 생성한다.
     *
     * @param from "sourceNodeId:sourcePort" (예: "sensor:out")
     * @param to   "targetNodeId:targetPort" (예: "rule:in")
     * @throws FlowParserException 형식이 올바르지 않을 때
     */
    public static ConnectionDefinition parse(String from, String to) {
        String[] src = splitEndpoint(from, "from");
        String[] dst = splitEndpoint(to, "to");
        return new ConnectionDefinition(src[0], src[1], dst[0], dst[1]);
    }

    private static String[] splitEndpoint(String endpoint, String field) {
        if (endpoint == null || !endpoint.contains(":")) {
            throw new FlowParserException(
                    "연결 " + field + " 형식 오류 (nodeId:portName 필요): " + endpoint);
        }
        String[] parts = endpoint.split(":", 2);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            throw new FlowParserException(
                    "연결 " + field + " nodeId 또는 portName이 비어있습니다: " + endpoint);
        }
        return parts;
    }

    public String getSourceNodeId() { return sourceNodeId; }
    public String getSourcePort()   { return sourcePort; }
    public String getTargetNodeId() { return targetNodeId; }
    public String getTargetPort()   { return targetPort; }

    @Override
    public String toString() {
        return sourceNodeId + ":" + sourcePort + " → " + targetNodeId + ":" + targetPort;
    }
}