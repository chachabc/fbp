package com.fbp.engine.network;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.CollectorNode;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 과제 1-2 검증 테스트.
 *
 * 1. MessageConverter — raw bytes → FBP Message 변환 단위 테스트
 * 2. TcpReceiverNode — TCP 수신 → 콜백 → FBP 파이프라인 통합 테스트
 */
class MessageListenerTest {

    // ─── 1. MessageConverter 단위 테스트 ─────────────────────────────────

    @Test
    @DisplayName("JSON 페이로드 → FBP Message로 변환")
    void convertJsonPayload() {
        byte[] payload = "{\"temperature\": 28.5, \"unit\": \"C\"}".getBytes(StandardCharsets.UTF_8);
        Message message = MessageConverter.convert("sensor/temp", payload);

        // JSON 필드가 올바르게 파싱됨
        assertEquals(28.5, ((Number) message.get("temperature")).doubleValue(), 0.001);
        assertEquals("C", message.get("unit"));

        // 메타데이터 자동 추가
        assertEquals("sensor/temp", message.get("topic"));
        assertNotNull(message.get("receivedAt"));
    }

    @Test
    @DisplayName("JSON 아닌 페이로드 → rawPayload 키로 보존")
    void convertRawPayload() {
        byte[] payload = "Hello FBP".getBytes(StandardCharsets.UTF_8);
        Message message = MessageConverter.convert("tcp://localhost:9000", payload);

        assertEquals("Hello FBP", message.get("rawPayload"));
        assertEquals("tcp://localhost:9000", message.get("topic"));
        assertNotNull(message.get("receivedAt"));
    }

    @Test
    @DisplayName("빈 JSON 객체 {} → 메타데이터만 포함")
    void convertEmptyJson() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        Message message = MessageConverter.convert("test/topic", payload);

        assertNotNull(message.get("topic"));
        assertNotNull(message.get("receivedAt"));
        assertNull(message.get("rawPayload")); // rawPayload 없어야 함
    }

    @Test
    @DisplayName("정수·문자열·불리언 혼합 JSON 파싱")
    void convertMixedTypeJson() {
        byte[] payload = "{\"count\": 42, \"name\": \"sensor-1\", \"active\": true}".getBytes(StandardCharsets.UTF_8);
        Message message = MessageConverter.convert("topic", payload);

        assertEquals(42L, (Long) message.get("count"));
        assertEquals("sensor-1", message.get("name"));
        assertEquals(Boolean.TRUE, message.get("active"));
    }

    @Test
    @DisplayName("isJson — 형식 판별 정확성")
    void isJsonDetection() {
        assertTrue(MessageConverter.isJson("{\"key\": \"value\"}"));
        assertTrue(MessageConverter.isJson("{}"));
        assertFalse(MessageConverter.isJson("Hello"));
        assertFalse(MessageConverter.isJson("[1, 2, 3]"));
        assertFalse(MessageConverter.isJson(null));
        assertFalse(MessageConverter.isJson(""));
    }

    // ─── 2. TcpReceiverNode 통합 테스트 ──────────────────────────────────

    private static final int PORT = 19100;
    private TcpReceiverNode receiverNode;
    private CollectorNode collector;

    @BeforeEach
    void setUp() throws Exception {
        receiverNode = new TcpReceiverNode("tcp-receiver", PORT);
        collector = new CollectorNode("collector");

        // TcpReceiverNode "out" → CollectorNode "in" 연결
        Connection conn = new Connection("receiver-collector");
        conn.setTarget(collector.getInputPort("in"));
        receiverNode.getOutputPort("out").connect(conn);

        receiverNode.initialize();
        Thread.sleep(100); // 서버 소켓 준비 대기
    }

    @AfterEach
    void tearDown() {
        receiverNode.shutdown();
    }

    @Test
    @DisplayName("JSON 전송 → FBP Message로 변환되어 CollectorNode에 수집")
    void tcpJsonToFbpMessage() throws Exception {
        sendTcp("{\"temperature\": 35.0, \"unit\": \"C\"}");
        Thread.sleep(200); // 비동기 수신 대기

        List<Message> collected = collector.getCollected();
        assertEquals(1, collected.size());

        Message msg = collected.getFirst();
        assertEquals(35.0, ((Number) msg.get("temperature")).doubleValue(), 0.001);
        assertEquals("C", msg.get("unit"));
        assertNotNull(msg.get("topic"));      // 클라이언트 주소
        assertNotNull(msg.get("receivedAt")); // 수신 시각
    }

    @Test
    @DisplayName("plain text 전송 → rawPayload로 보존")
    void tcpPlainTextToRawPayload() throws Exception {
        sendTcp("Hello FBP");
        Thread.sleep(200);

        List<Message> collected = collector.getCollected();
        assertEquals(1, collected.size());
        assertEquals("Hello FBP", collected.getFirst().get("rawPayload"));
    }

    @Test
    @DisplayName("단일 연결로 여러 메시지 전송 → 순서대로 수집")
    void tcpMultipleMessages() throws Exception {
        sendTcpMultiple("{\"seq\": 1}", "{\"seq\": 2}", "{\"seq\": 3}");
        Thread.sleep(400);

        List<Message> collected = collector.getCollected();
        assertEquals(3, collected.size());
        assertEquals(1L, (Long) collected.get(0).get("seq"));
        assertEquals(2L, (Long) collected.get(1).get("seq"));
        assertEquals(3L, (Long) collected.get(2).get("seq"));
    }

    /** TCP 소켓으로 한 줄 전송 (연결 1개, 메시지 1개) */
    private void sendTcp(String text) throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer.println(text);
        }
    }

    /** TCP 소켓 하나로 여러 줄을 순차 전송 (연결 1개, 메시지 N개) */
    private void sendTcpMultiple(String... messages) throws IOException {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            for (String msg : messages) {
                writer.println(msg);
            }
        }
    }
}