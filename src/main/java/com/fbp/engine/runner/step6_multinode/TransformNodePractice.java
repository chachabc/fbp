package com.fbp.engine.runner.step6_multinode;

import com.fbp.engine.core.Connection;
import com.fbp.engine.node.*;

public class TransformNodePractice {
    public static void main(String[] args){
        //step6-2
        GeneratorNode generatorNode = new GeneratorNode("generator-1");
        TransformNode transformNode = new TransformNode("transform-1", message -> {
            Double fahrenheit = message.get("temperature");
            double celsius = Math.round((fahrenheit - 32) * 5.0 / 9.0 * 10.0) / 10.0;
            return message.withEntry("temperature", celsius);
        });

        PrintNode printNode = new PrintNode("printer-1");

        Connection connection1 = new Connection("generator-transform-connect");
        connection1.setTarget(transformNode.getInputPort("in"));
        generatorNode.getOutputPort("out").connect(connection1);

        Connection connection2 = new Connection("transform-printer-connect");
        connection2.setTarget(printNode.getInputPort("in"));
        transformNode.getOutputPort("out").connect(connection2);

        generatorNode.generate("temperature", 32.0);
        generatorNode.generate("temperature", 212.0);
        generatorNode.generate("temperature", 98.6);
    }

}
