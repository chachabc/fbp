package com.fbp.engine.parser;

/**
 * 플로우의 전송 계층 설정을 나타내는 불변 데이터 객체.
 * <pre>
 * JSON에 "transport" 섹션이 없으면 → LOCAL (BlockingQueue 사용)
 * "transport.type": "mqtt" 이면 → MQTT 브릿지 (시스템 브로커 경유)
 * </pre>
 */
public class TransportDefinition {

    public static final String TYPE_LOCAL = "local";
    public static final String TYPE_MQTT  = "mqtt";

    /** transport 섹션 없을 때 사용하는 기본 로컬 정의 */
    public static final TransportDefinition LOCAL = new TransportDefinition(TYPE_LOCAL, null, 1);

    private final String type;
    private final String broker; // mqtt일 때만 유효
    private final int qos;

    public TransportDefinition(String type, String broker, int qos) {
        this.type   = type;
        this.broker = broker;
        this.qos    = qos;
    }

    public boolean isMqtt()   { return TYPE_MQTT.equals(type); }
    public boolean isLocal()  { return TYPE_LOCAL.equals(type); }

    public String getType()   { return type; }
    public String getBroker() { return broker; }
    public int getQos()       { return qos; }

    @Override
    public String toString() {
        return isMqtt() ? "mqtt(" + broker + ", QoS=" + qos + ")" : "local";
    }
}