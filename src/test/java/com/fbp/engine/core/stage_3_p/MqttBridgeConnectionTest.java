package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MqttBridgeConnection 통합 테스트.
 *
 * 실행 전 시스템 브로커가 localhost:1884에 실행 중이어야 한다.
 *   docker compose -f docker/docker-compose.yml up -d mqtt-system-broker
 */
@Tag("integration")
class MqttBridgeConnectionTest {

    private static final String BROKER_URL = "tcp://localhost:1884";
    private static final String TOPIC = "fbp/test/nodeA.out-nodeB.in";
    private static final int QOS = 1;

    private MqttBridgeConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = new MqttBridgeConnection("test-bridge", BROKER_URL, TOPIC, QOS);
        // 구독이 브로커에 등록되기까지 짧은 대기
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Connection 인터페이스를 구현한다")
    void test1() {
        assertInstanceOf(Connection.class, connection);
    }

    @Test
    @DisplayName("deliver 후 poll하면 동일한 Message가 반환된다")
    void test2() throws InterruptedException {
        Message original = new Message(Map.of("value", 25.5, "sensor", "temp"));

        connection.deliver(original);

        // 브로커 경유 왕복이 있으므로 timeout 2초로 poll
        Thread pollThread = new Thread(() -> {
            try {
                Message received = connection.poll();
                assertEquals(original.getId(), received.getId());
                assertEquals(25.5, ((Number) received.get("value")).doubleValue(), 0.001);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("poll이 인터럽트됨");
            }
        });
        pollThread.start();
        pollThread.join(2000);
        assertFalse(pollThread.isAlive(), "poll이 2초 안에 완료되어야 한다");
    }

    @Test
    @DisplayName("getId는 생성 시 지정한 id를 반환한다")
    void test3() {
        assertEquals("test-bridge", connection.getId());
    }

    @Test
    @DisplayName("여러 메시지를 순서대로 전달한다")
    void test4() throws InterruptedException {
        int count = 5;
        for (int i = 0; i < count; i++) {
            connection.deliver(new Message(Map.of("seq", i)));
        }

        int[] results = new int[count];
        for (int i = 0; i < count; i++) {
            Message received = connection.poll();
            results[i] = ((Number) received.get("seq")).intValue();
        }

        for (int i = 0; i < count; i++) {
            assertEquals(i, results[i]);
        }
    }

    @Test
    @DisplayName("close 후 내부 큐가 비워진다")
    void test5() {
        connection.close();
        assertEquals(0, connection.getBufferSize());
    }
}