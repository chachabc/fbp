package com.fbp.engine.node;

import com.fbp.engine.core.Connection;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.TimerNode;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

class TimerNodeTest {
    private TimerNode timerNode;
    private List<Message> received;

    @BeforeEach
    void setUp(){
        timerNode = new TimerNode("timer-1", 500);
        received  = new ArrayList<>();

        Connection connection = new Connection("connect-1");
        connection.setTarget(new InputPort() {
            @Override
            public String getName() {
                return "in";
            }

            @Override
            public void receive(Message message) {
                received.add(message);
            }
        });
        timerNode.getOutputPort("out").connect(connection);
    }

    @AfterEach
    void tearDown(){
        timerNode.shutdown();
    }

    @Test
    @DisplayName("initialize 후 메시지 생성")
    void test1() throws InterruptedException{
        timerNode.initialize();
        Thread.sleep(2000);

        Assertions.assertFalse(received.isEmpty());
    }

    @Test
    @DisplayName("tick 증가")
    void test2() throws InterruptedException{
        timerNode.initialize();
        Thread.sleep(2000);

        for (int i = 0; i < received.size(); i++){
            Assertions.assertEquals(i, (int) received.get(i).get("tick"));
        }
    }

    @Test
    @DisplayName("shutdown 후 정지")
    void test3() throws InterruptedException{
        timerNode.initialize();
        Thread.sleep(800);
        timerNode.shutdown();

        int afterCount = received.size();
        Thread.sleep(1000);

        Assertions.assertEquals(afterCount, received.size());
    }

    @Test
    @DisplayName("주기 확인")
    void test4() throws InterruptedException{
        timerNode.initialize();
        Thread.sleep(2000);

        Assertions.assertTrue(received.size() >= 3 && received.size() <= 5);
    }
}
