package com.fbp.engine.runner.step4_thread;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.FilterNode;
import com.fbp.engine.node.PrintNode;

import java.util.Map;

//과제 4-5: 3노드 스레드 파이프라인
public class PipelinePractice {
    public static void main(String[] args) throws InterruptedException {
        Connection connection1 = new Connection("generator-filter-connect");
        Connection connection2 = new Connection("filter-printer-connect");

        FilterNode filterNode = new FilterNode("filter-1", "temperature", 30.0);
        PrintNode printNode = new PrintNode("printer-1");

        boolean[] running = {true};

        //Thread-1 : Generator(10 messages for 0.5 sec)
        Thread generatorThread = new Thread(() -> {
            double[] temps = {25.0, 35.0, 20.0, 40.0, 30.0, 15.0, 45.0, 28.0, 32.0, 38.0};
            for (double temp : temps){
                connection1.deliver(new Message(Map.of("temperature", temp)));
                System.out.println("[producer] temperature " + temp);
                try{
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
            running[0] = false;
        });

        //Thread-2 : FilterNode
        Thread filterThread = new Thread(() -> {
            while(running[0]){
                Message message = connection1.poll();
                if (message != null){
                    filterNode.process(message);
                }
            }
        });

        //Thread-3 : PrintNode
        Thread printerThread = new Thread(() -> {
            while (running[0]){
                Message message = connection2.poll();
                if (message != null){
                    printNode.process(message);
                }
            }
        });

        generatorThread.start();
        filterThread.start();
        printerThread.start();

        generatorThread.join();

        // 생산자 종료 후 잠시 대기하여 남은 메시지 처리
        Thread.sleep(2000);
        filterThread.interrupt();
        printerThread.interrupt();
    }
}
