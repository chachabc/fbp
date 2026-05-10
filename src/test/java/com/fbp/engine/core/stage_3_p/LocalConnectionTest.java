package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalConnectionTest {

    private LocalConnection connection;

    @BeforeEach
    void setUp() {
        connection = new LocalConnection("test-conn");
    }

    @Test
    @DisplayName("Connection 인터페이스를 구현한다")
    void test1() {
        assertInstanceOf(Connection.class, connection);
    }

    @Test
    @DisplayName("deliver 후 poll하면 같은 메시지를 반환한다")
    void test2() throws InterruptedException {
        Message msg = new Message(Map.of("value", 42));
        connection.deliver(msg);

        Message polled = connection.poll();
        assertEquals(msg.getId(), polled.getId());
        assertEquals(42, (int) polled.get("value"));
    }

    @Test
    @DisplayName("FIFO 순서를 보장한다")
    void test3() throws InterruptedException {
        connection.deliver(new Message(Map.of("seq", 1)));
        connection.deliver(new Message(Map.of("seq", 2)));
        connection.deliver(new Message(Map.of("seq", 3)));

        assertEquals(1, (int) connection.poll().get("seq"));
        assertEquals(2, (int) connection.poll().get("seq"));
        assertEquals(3, (int) connection.poll().get("seq"));
    }

    @Test
    @DisplayName("getBufferSize는 현재 큐에 대기 중인 메시지 수를 반환한다")
    void test4() {
        assertEquals(0, connection.getBufferSize());

        connection.deliver(new Message(Map.of("k", "v1")));
        connection.deliver(new Message(Map.of("k", "v2")));
        assertEquals(2, connection.getBufferSize());
    }

    @Test
    @DisplayName("poll 후 bufferSize가 줄어든다")
    void test5() throws InterruptedException {
        connection.deliver(new Message(Map.of("k", "v")));
        assertEquals(1, connection.getBufferSize());

        connection.poll();
        assertEquals(0, connection.getBufferSize());
    }

    @Test
    @DisplayName("getId는 생성 시 지정한 id를 반환한다")
    void test6() {
        assertEquals("test-conn", connection.getId());
    }

    @Test
    @DisplayName("close 후 버퍼가 비워진다")
    void test7() {
        connection.deliver(new Message(Map.of("k", "v1")));
        connection.deliver(new Message(Map.of("k", "v2")));

        connection.close();
        assertEquals(0, connection.getBufferSize());
    }

    @Test
    @DisplayName("용량 지정 생성자로 만들면 해당 용량이 적용된다")
    void test8() throws InterruptedException {
        LocalConnection small = new LocalConnection("small", 2);
        Message m1 = new Message(Map.of("i", 1));
        Message m2 = new Message(Map.of("i", 2));

        small.deliver(m1);
        small.deliver(m2);
        assertEquals(2, small.getBufferSize());

        small.poll();
        small.poll();
        assertEquals(0, small.getBufferSize());
    }

    @Test
    @DisplayName("멀티스레드 환경에서 deliver/poll이 안전하다")
    void test9() throws InterruptedException {
        int messageCount = 100;
        List<Message> received = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(messageCount);

        Thread producer = new Thread(() -> {
            for (int i = 0; i < messageCount; i++) {
                connection.deliver(new Message(Map.of("i", i)));
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < messageCount; i++) {
                    received.add(connection.poll());
                    done.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        producer.start();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(messageCount, received.size());
    }

    @Test
    @DisplayName("poll은 InterruptedException을 선언한다 — 인터럽트 시 전파된다")
    void test10() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                connection.poll(); // 메시지 없으므로 블로킹
                fail("InterruptedException이 발생해야 한다");
            } catch (InterruptedException e) {
                // 정상: 인터럽트를 받아 종료
            }
        });

        t.start();
        Thread.sleep(50); // 블로킹 상태 진입 대기
        t.interrupt();
        t.join(1000);
        assertFalse(t.isAlive());
    }
}