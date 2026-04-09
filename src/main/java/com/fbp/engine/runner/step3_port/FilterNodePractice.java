package com.fbp.engine.runner.step3_port;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.GeneratorNode;
import com.fbp.engine.node.PrintNode;

public class FilterNodePractice {
    public static void main(String[] args){
        //step3-10
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        com.fbp.engine.node.FilterNode filterNode = new com.fbp.engine.node.FilterNode("filter-1", "temperature", 30.0);
        PrintNode printNode = new PrintNode("printer-1");

        Connection connection = new Connection("connect-generator-filter");
        connection.setTarget(filterNode.getInputPort("in"));
        generatorNode.getOutputPort("out").connect(connection);

        Connection connection1 = new Connection("connect-filter-printer");
        connection1.setTarget(printNode.getInputPort("in"));
        filterNode.getOutputPort("out").connect(connection1);

        generatorNode.generate("temperature", 25.0);
        generatorNode.generate("temperature", 35.0);
    }
}
