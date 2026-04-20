package com.fbp.engine.runner.stage1.step4_thread;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.GeneratorNode;
import com.fbp.engine.node.stage1.PrintNode;

import java.util.Map;

//과제 4-4: 두 노드를 별도 스레드에서 실행하라.
public class ThreadPractice {
    public static void main(String[] args) throws InterruptedException{
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        PrintNode printNode = new PrintNode("printer-1");
        Connection connection = new Connection("connect-1");

        //producer - 5 messages for 1 sec
        java.lang.Thread producerThread  = new java.lang.Thread(() -> {
            for (int i = 0; i < 5; i++){
                generatorNode.generate("index", i);
                connection.deliver(new Message(Map.of("index", i)));
                System.out.println("[producer] message " + i);
                try{
                    java.lang.Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println("[producer] complete");
        });

        //consumer
        java.lang.Thread consumerThread = new java.lang.Thread(() -> {
            for (int i = 0; i < 5; i++){
                Message message = connection.poll();
                if (message != null){
                    printNode.process(message);
                }
            }
            System.out.println("[consumer] complete");
        });

        producerThread.start();
        consumerThread.start();
        producerThread.join();
        consumerThread.join();
    }
}
