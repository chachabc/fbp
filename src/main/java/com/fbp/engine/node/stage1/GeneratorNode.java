package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;

public class GeneratorNode extends AbstractNode {

    public GeneratorNode(String id){
        super(id);
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message){

    }

    public void generate(String key, Object value){
        Message message = new Message(Map.of(key, value));
        send("out", message);
    }

}
