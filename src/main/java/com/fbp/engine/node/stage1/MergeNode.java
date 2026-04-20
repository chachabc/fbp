package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;

import java.util.LinkedHashMap;
import java.util.Map;

public class MergeNode extends AbstractNode {
    private Message pending1;
    private Message pending2;

    public MergeNode(String id){
        super(id);

        addInputPort("in-1", new InputPort() {
            @Override
            public String getName() {
                return "in-1";
            }

            @Override
            public void receive(Message message) {
                process(message.withEntry("source", "in-1"));
            }
        });

        addInputPort("in-2", new InputPort() {
            @Override
            public String getName() {
                return "in-2";
            }

            @Override
            public void receive(Message message) {
                process(message.withEntry("source", "in-2"));
            }
        });
        addOutputPort("out");
    }

    @Override
    protected void onProcess(Message message) {
        String source = message.get("source");

        synchronized (this) {
            if ("in-1".equals(source)) {
                pending1 = message.withoutKey("source");
            } else if ("in-2".equals(source)) {
                pending2 = message.withoutKey("source");
            }

            if (pending1 != null && pending2 != null) {
                Map<String, Object> mergePayload = new LinkedHashMap<>();
                mergePayload.putAll(pending1.getPayload());
                mergePayload.putAll(pending2.getPayload());

                Message merged = new Message(mergePayload);
                send("out", merged);

                pending1 = null;
                pending2 = null;
            }
        }
    }
}
