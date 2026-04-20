package com.fbp.engine.runner.stage1.step4_thread;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;

import java.util.Map;

public class SpeedPractice {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("==========produce(0.1 sec) / consume(1 sec)==========");
        testSpeed(100, 1000, 10);

        System.out.println("==========produce(1 sec) / consume(0.1 sec)==========");
        testSpeed(100, 1000, 10);
    }

    private static void testSpeed(long produceMs, long consumeMs, int count) throws InterruptedException{
        Connection connection = new Connection("connect-1", 50);

        Thread producerThread = new Thread(() -> {
            for (int i = 0; i < count; i++){
                connection.deliver(new Message(Map.of("index", 1)));
                System.out.println("[producer] " + i + " | BufferSize: " + connection.getBufferSize());
                try {
                    Thread.sleep(produceMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        Thread consumerThread = new Thread(() -> {
            for (int i = 0; i < count; i++){
                Message message = connection.poll();
                System.out.println("[consumer] " + message.get("index") + " | BufferSize: " + connection.getBufferSize());
                try {
                    Thread.sleep(consumeMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        producerThread.start();
        consumerThread.start();
        producerThread.join();
        consumerThread.join();
    }
}
