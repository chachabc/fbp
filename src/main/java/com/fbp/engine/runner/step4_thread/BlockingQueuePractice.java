package com.fbp.engine.runner.step4_thread;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

//단계 B — BlockingQueue 대체
public class BlockingQueuePractice {
    public static void main(String[] args) throws InterruptedException{
        BlockingDeque<String> buffer = new LinkedBlockingDeque<>(200);
        final String END = "END";

        //producer
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++){
                try{
                    buffer.put("message - " + i);
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            try {
                buffer.put(END);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[producer] complete");
        });

        //consumer
        Thread consumer = new Thread(() -> {
            while (true){
                try {
                    String item = buffer.take();
                    if (END.equals(item)) break;
                    System.out.println("[consumer] " + item);
                } catch (InterruptedException e){
                    break;
                }
            }
            System.out.println("[consumer] complete");
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}
