package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractNode implements Node{
    private final String id;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;

    public AbstractNode(String id){
        this.id = id;
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
    }

    //Port Register
    protected void addInputPorts(String name){
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    protected void addOutputPorts(String name){
        outputPorts.put(name, new DefaultOutputPort(name));
    }

    // Port Selection
    public InputPort getInputPort(String name){
        return inputPorts.get(name);
    }

    public OutputPort getOutputPort(String name){
        return outputPorts.get(name);
    }

    //Send Message
    protected void send(String portName, Message message){
        OutputPort port = outputPorts.get(portName);
        if(port != null){
            port.send(message);
        } else {
            System.out.println("[" + id + "] 경고: OutputPort '" + portName + "'를 찾을 수 없음");
        }
    }

    //Template Method Pattern
    @Override
    public final void process(Message message) {
        System.out.println("[" + id + "] processing message...");
        onProcess(message);
        System.out.println("[" + id + "] done" );
    }

    protected abstract void onProcess(Message message);

    //Life Cycle
    @Override
    public void initialize(){
        System.out.println("[" + id + "] initialized");
    }

    @Override
    public void shutdown(){
        System.out.println("[" + id + "] shutdown");
    }

    //common
    @Override
    public String getId(){
        return id;
    }
}
