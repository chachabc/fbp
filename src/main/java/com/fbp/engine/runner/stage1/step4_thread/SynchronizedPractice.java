package com.fbp.engine.runner.stage1.step4_thread;

import java.util.ArrayList;

//단계 A — synchronized + wait() / notify()
public class SynchronizedPractice {
    public static void main(String[] args) throws InterruptedException {
        ArrayList<String> buffer = new ArrayList<>();
        final String END = "END";

        //producer
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++){
                synchronized (buffer){
                    buffer.add("message - " + i);
                    buffer.notify();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
            synchronized (buffer){
                buffer.add("END");
                buffer.notify();
            }
            System.out.println("[producer] complete");
        });

        //consumer
        Thread consumer = new Thread(() -> {
            while (true){
                String item;
                synchronized (buffer){
                    while (buffer.isEmpty()){
                        try{
                            buffer.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    item = buffer.remove(0);
                }
                if (END.equals(item)) break;
                System.out.println("[consumer] " + item);
            }
            System.out.println("consumer complete");
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}
