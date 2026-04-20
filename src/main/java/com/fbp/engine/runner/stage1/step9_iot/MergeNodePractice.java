package com.fbp.engine.runner.stage1.step9_iot;

import com.fbp.engine.core.Connection;
import com.fbp.engine.message.Message;
import com.fbp.engine.node.stage1.MergeNode;
import com.fbp.engine.node.stage1.PrintNode;

import java.util.Map;

public class MergeNodePractice {
    public static void main(String[] args){
        MergeNode mergeNode = new MergeNode("merge-1");
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection = new Connection("merge-printer-connect");
        connection.setTarget(printNode.getInputPort("in"));
        mergeNode.getOutputPort("out").connect(connection);

        mergeNode.getInputPort("in-1").receive(new Message(Map.of("temperature", 25.0)));
        mergeNode.getInputPort("in-2").receive(new Message(Map.of("temperature", 35.0)));

        mergeNode.getInputPort("in-1").receive(new Message(Map.of("temperature", 30.0))); // 대기
    }
}
