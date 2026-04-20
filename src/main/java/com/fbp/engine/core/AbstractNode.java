package com.fbp.engine.core;

import com.fbp.engine.message.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Node 인터페이스의 공통 구현체.
 * id 관리, 포트 맵 관리, Template Method 패턴으로 onProcess() 위임.
 * 모든 구체 노드는 이 클래스를 상속하여 onProcess()만 구현하면 된다.
 */
public abstract class AbstractNode implements Node{
    private final String id;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;

    protected AbstractNode(String id){
        this.id = id;
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
    }

    //Port Register
    protected void addInputPort(String name){
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    protected void addInputPort(String name, InputPort port) {
        inputPorts.put(name, port);
    }

    protected void addOutputPort(String name){
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
