package com.fbp.engine.runner.step6_multinode;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.CounterNode;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

public class CounterNodePractice {
    public static void main(String[] args){
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        CounterNode counterNode = new CounterNode("counter");
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection1 = new Connection("generator-counter-connect");
        connection1.setTarget(counterNode.getInputPort("in"));
        generatorNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("counter-printer-connect");
        connection2.setTarget(printNode.getInputPort("in"));
        counterNode.getOutputPort("out").connect(connection2);

        generatorNode.generate("temperature", 10);
        generatorNode.generate("temperature", 20);
        generatorNode.generate("temperature", 30);
    }

}
