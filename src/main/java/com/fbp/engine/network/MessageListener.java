package com.fbp.engine.network;

/**
 * 외부 프로토콜(TCP, MQTT, MODBUS 등)로부터 데이터가 수신될 때 호출되는 콜백 인터페이스.
 *
 * <p>외부 라이브러리(Paho MQTT, 소켓 등)는 데이터가 도착하면 자신의 내부 스레드에서
 * 이 인터페이스의 구현을 호출한다. 구현 측(ProtocolNode)은 수신 데이터를
 * FBP {@code Message}로 변환하여 OutputPort로 흘려보낸다.</p>
 *
 * <pre>
 * 외부 라이브러리 스레드
 *   └─ MessageListener.onMessage(topic, payload)
 *         └─ (구현) payload → FBP Message 변환
 *               └─ send("out", message)  →  FBP 파이프라인
 * </pre>
 */
public interface MessageListener {
    /**
     * 외부로부터 메시지가 수신되었을 때 호출된다.
     *
     * @param topic   메시지 출처 식별자. MQTT라면 토픽명, TCP라면 "host:port" 형식 등
     * @param payload 수신한 원본 바이트 데이터
     */
    void onMessage(String topic, byte[] payload);

    /**
     * 연결이 비정상적으로 끊겼을 때 호출된다.
     *
     * @param cause 연결 끊김의 원인 예외
     */
    void onConnectionLost(Throwable cause);

    /**
     * 연결이 성공적으로 수립되었을 때 호출된다.
     */
    void onConnected();
}
