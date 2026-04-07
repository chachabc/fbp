package com.fbp.engine.runner.thread;

import java.util.ArrayList;

//문제 1: ConcurrentModificationException 또는 IndexOutOfBoundsException 발생 가능
//문제 2: 메시지 유실 또는 중복 수신 가능
//문제 3: while(!buffer.isEmpty()) 루프가 CPU를 100% 점유 (busy-waiting)
//문제 4: 생산자 종료 후 소비자가 종료 시점을 알 수 없음

//과제 4-1: ArrayList 공유 버퍼 문제 확인
public class BasicThreadPractice {
    public static void main(String[] args) throws InterruptedException {
        ArrayList<String> buffer = new ArrayList<>();

        //producer
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 100; i++){
                buffer.add("message - " + i);
                try{
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println("[producer] complete");
        });

        //consumer
        Thread consumer = new Thread(() -> {
            int count = 0;
            while (count < 100){
                if (!buffer.isEmpty()){
                    String item = buffer.remove(0);
                    System.out.println("[consumer] " + item);
                    count++;
                }
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

    }
}
