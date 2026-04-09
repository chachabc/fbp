package com.fbp.engine.runner.step6_multinode;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.DelayNode;
import com.fbp.engine.node.PrintNode;
import com.fbp.engine.node.TimerNode;


public class DelayNodePractice {
    public static void main(String[] args) throws InterruptedException{
        TimerNode timerNode = new TimerNode("timer-1", 500);
        DelayNode delayNode = new DelayNode("delay-1", 2000);
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection1 = new Connection("timer-delay-connect");

        timerNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("delay-printer-connect");
        connection2.setTarget(printNode.getInputPort("in"));
        delayNode.getOutputPort("out").connect(connection2);

        timerNode.initialize();

        Thread delayThread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()){
                System.out.println("[버퍼 크기] " + connection1.getBufferSize());
                Message message = connection1.poll();
                if(message != null){
                    delayNode.process(message);
                }
            }
        });
        delayThread.start();

        Thread.sleep(20000);

        timerNode.shutdown();
        delayThread.interrupt();
    }
}
