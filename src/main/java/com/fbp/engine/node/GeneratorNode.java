package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.DefaultOutputPort;
import com.fbp.engine.core.Node;
import com.fbp.engine.core.OutputPort;
import com.fbp.engine.message.Message;

import java.util.Map;

public class GeneratorNode extends AbstractNode {

    public GeneratorNode(String id){
        super(id);
        addOutputPorts("out");
    }

    @Override
    protected void onProcess(Message message){

    }

    public void generate(String key, Object value){
        Message message = new Message(Map.of(key, value));
        send("out", message);
    }

}
