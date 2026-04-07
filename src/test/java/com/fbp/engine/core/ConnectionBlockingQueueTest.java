package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class ConnectionBlockingQueueTest {
    private Connection connection;

    @BeforeEach
    void setUp(){
        connection = new Connection("test-connect");
    }

    @Test
    @DisplayName("diver-poll 기본 동작")
    void test1() throws InterruptedException{
        Message message = new Message(Map.of("temperature", 25.5));

        Thread producer = new Thread(() -> connection.deliver(message));
        producer.start();

        Message result = connection.poll();
        producer.join();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(25.5, result.get("temperature"));
    }

    @Test
    @DisplayName("메시지 순서 보장")
    void test2() throws InterruptedException{
        Thread producer = new Thread(() -> {
            connection.deliver(new Message(Map.of("key", 1)));
            connection.deliver(new Message(Map.of("key", 2)));
            connection.deliver(new Message(Map.of("key", 3)));
        });
        producer.start();
        producer.join();

        Assertions.assertEquals(1, (int) connection.poll().get("key"));
        Assertions.assertEquals(2, (int) connection.poll().get("key"));
        Assertions.assertEquals(3, (int) connection.poll().get("key"));
    }

    @Test
    @DisplayName("멀티스레드 diver-poll")
    void test3() throws InterruptedException{
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> received = new AtomicReference<>();

        Thread producer  = new Thread(() ->
                connection.deliver(new Message(Map.of("key", "value")))
        );

        Thread consumer = new Thread(() -> {
            received.set(connection.poll());
            latch.countDown();
        });

        consumer.start();
        Thread.sleep(100);
        producer.start();

        Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS));
        Assertions.assertNotNull(received.get());
        Assertions.assertEquals("value", received.get().get("key"));
    }

    @Test
    @DisplayName("poll 대기 동작")
    void test4() throws InterruptedException{
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> received = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            received.set(connection.poll());
            latch.countDown();
        });

        consumer.start();
        Thread.sleep(500);

        Assertions.assertNull(received.get());

        connection.deliver(new Message(Map.of("key", "value")));

        Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS));
        Assertions.assertNotNull(received.get());
        Assertions.assertEquals("value", received.get().get("key"));
    }

    @Test
    @DisplayName("버퍼 크기 제한")
    void test5() throws InterruptedException{
        Connection smallConn = new Connection("small-connect", 2);
        smallConn.deliver(new Message(Map.of("key", 1)));
        smallConn.deliver(new Message(Map.of("key", 2)));

        CountDownLatch latch = new CountDownLatch(1);

        Thread blocker = new Thread(() -> {
            smallConn.deliver(new Message(Map.of("key", 3)));
            latch.countDown();
        });

        blocker.start();

        boolean delivered = latch.await(1, TimeUnit.SECONDS);
        Assertions.assertFalse(delivered, "버퍼가 찼으므로 deliver가 블로킹 되어야 한다");
    }

    @Test
    @DisplayName("버퍼 크기 조회")
    void test6(){
        connection.deliver(new Message(Map.of("key", 1)));
        connection.deliver(new Message(Map.of("key", 2)));

        Assertions.assertEquals(2, connection.getBufferSize());
    }
}
