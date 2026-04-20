package com.fbp.engine.runner.stage1.step3_port;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.stage1.GeneratorNode;
import com.fbp.engine.node.stage1.PrintNode;

public class PortConnectionPractice {
    public static void main(String[] args){
        //step3-7, 8
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        PrintNode printNode = new PrintNode("printer-1");
        PrintNode printNode1 = new PrintNode("printer-2");

        Connection connection = new Connection("connect-1");
        Connection connection1 = new Connection("connect-2");

        connection.setTarget(printNode.getInputPort("in"));
        connection1.setTarget(printNode1.getInputPort("in"));

        generatorNode.getOutputPort("out").connect(connection);
        generatorNode.getOutputPort("out").connect(connection1);

        generatorNode.generate("temperature", 25.5);
    }
}
