package com.fbp.engine.network;

import com.fbp.engine.runner.stage2.step1_network.EchoClient;
import com.fbp.engine.runner.stage2.step1_network.EchoServer;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 과제 1-1 검증 테스트.
 * EchoServer를 백그라운드에서 구동하고,
 * EchoClient로 "Hello FBP"를 전송하여 그대로 돌아오는지 확인한다.
 */
class EchoTest {

    private static final int PORT = 19000; // 테스트 전용 포트 (9000과 충돌 방지)
    private EchoServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void setUp() throws Exception {
        server = new EchoServer(PORT);
        serverExecutor = Executors.newSingleThreadExecutor();

        // 서버를 별도 스레드에서 시작
        serverExecutor.submit(() -> {
            try {
                server.start();
            } catch (Exception e) {
                // 테스트 종료 시 stop()으로 서버소켓이 닫히므로 정상
            }
        });

        // 서버가 accept 대기 상태가 될 때까지 잠시 대기
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        serverExecutor.shutdownNow();
    }

    @Test
    @DisplayName("Hello FBP 에코 — 전송한 메시지가 그대로 돌아온다")
    void echoHelloFbp() throws Exception {
        EchoClient client = new EchoClient("localhost", PORT);
        String response = client.send("Hello FBP");
        assertEquals("Hello FBP", response);
    }

    @Test
    @DisplayName("여러 메시지 연속 전송 — 각각 에코된다")
    void echoMultipleMessages() throws Exception {
        EchoClient client = new EchoClient("localhost", PORT);

        String[] messages = {"Hello FBP", "temperature=25.5", "tick=3"};
        for (String msg : messages) {
            String response = client.send(msg);
            assertEquals(msg, response, "에코 불일치: " + msg);
        }
    }

    @Test
    @DisplayName("다중 클라이언트 — 동시에 연결해도 각자 에코된다")
    void echoConcurrentClients() throws Exception {
        int clientCount = 5;
        CountDownLatch latch = new CountDownLatch(clientCount);
        ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);
        String[] results = new String[clientCount];

        for (int i = 0; i < clientCount; i++) {
            final int idx = i;
            clientPool.submit(() -> {
                try {
                    EchoClient client = new EchoClient("localhost", PORT);
                    results[idx] = client.send("client-" + idx);
                } catch (Exception e) {
                    results[idx] = null;
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "클라이언트 처리 타임아웃");
        clientPool.shutdown();

        for (int i = 0; i < clientCount; i++) {
            assertEquals("client-" + i, results[i]);
        }
    }
}