package com.fbp.engine.core;

import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Node 인터페이스의 공통 구현체.
 * id 관리, 포트 맵 관리, Template Method 패턴으로 onProcess() 위임.
 * 모든 구체 노드는 이 클래스를 상속하여 onProcess()만 구현하면 된다.
 */
public abstract class AbstractNode implements Node{
    private static final Logger log = LoggerFactory.getLogger(AbstractNode.class);
    private final String id;
    private final Map<String, InputPort> inputPorts;
    private final Map<String, OutputPort> outputPorts;

    /**
     * 노드를 생성한다.
     * @param id 노드의 고유 식별자
     */
    protected AbstractNode(String id){
        this.id = id;
        this.inputPorts = new HashMap<>();
        this.outputPorts = new HashMap<>();
    }

    /**
     * 이름으로 InputPort를 생성하여 내부 맵에 등록한다.
     * 생성된 포트를 {@link DefaultInputPort} 이며, 메시지 수신 시 이 노도의 process()를 호출한다.
     *
     * @param name 포트 이름 (ex: "in", "trigger")
     */
    protected void addInputPort(String name){
        inputPorts.put(name, new DefaultInputPort(name, this));
    }

    /**
     * 외부에서 생성한ㄷ InputPort를 내부 맵에 등록한다.
     * 커스텀 수신 동작이 필요한 경우 (ex: MergeNode)에 사용한다.
     *
     * @param name 포트이름
     * @param port 등록할 InputPort 구현체
     */
    protected void addInputPort(String name, InputPort port) {
        inputPorts.put(name, port);
    }

    /**
     * 이름으로 OutputPort를 생성하여 내부 맵에 등록한다.
     * 생성된 포트는 {@link DefaultOutputPort}이며 1:N 전송을 지원한다.
     *
     * @param name 포트 이름 (ex: "out", "match", "alert")
     */
    protected void addOutputPort(String name){
        outputPorts.put(name, new DefaultOutputPort(name));
    }

    /**
     * 이름으로 등록된 InputPort를 조회한다.
     *
     * @param name 포트 이름
     * @return 해당 InputPort, 없으면 null
     */
    public InputPort getInputPort(String name){
        return inputPorts.get(name);
    }

    /**
     * 이름으로 등록된 OutputPort를 조회한다.
     *
     * @param name 포트 이름
     * @return 해당 OutputPort, 없으면 null
     */
    public OutputPort getOutputPort(String name){
        return outputPorts.get(name);
    }

    /**
     * 지정한 OutputPort로 메시지를 전송한다.
     * 포트가 존재하지 않으면 경고를 출력하고 전송을 건너뛴다.
     *
     * @param portName 전송할 OutputPort 이름
     * @param message 전송할 메시지
     */
    protected void send(String portName, Message message){
        OutputPort port = outputPorts.get(portName);
        if(port != null){
            port.send(message);
        } else {
            System.out.println("[" + id + "] 경고: OutputPort '" + portName + "'를 찾을 수 없음");
        }
    }

    /**
     * 메시지 처리의 공통 흐름을 정의한다. (Template Method Pattern)
     * 처리 전후 로그를 출력하고, 핵심 로직은 {@link #onProcess(Message)}에 위임한다.
     * 하위 클래스에서 오버라이드 할 수 없다.
     *
     * @param message 처리할 메시지
     */
    @Override
    public final void process(Message message) {
        log.debug("[{}] processing message...", id);
        onProcess(message);
        log.debug("[{}] done", id);
    }

    /**
     * 노드의 핵심 처리 로직을 구현한다.
     * {@link #process(Message)} 에 의해 호출되며 하위 클래스에서 반드시 구현해야 한다.
     *
     * @param message 처리할 메시지
     */
    protected abstract void onProcess(Message message);

    /**
     * 노드를 초기화한다.
     * 기본 구현은 초기화 로그를 출력한다. 자원 할당이 필요한 노드는 오버라이드 한다.
     */
    @Override
    public void initialize(){
        System.out.println("[" + id + "] initialized");
    }

    /**
     * 노드를 종료한다.
     * 기본 구현은 종료 로그를 출력한다. 자원 해제가 필요한 노드는 오버라이드한다.
     */
    @Override
    public void shutdown(){
        System.out.println("[" + id + "] shutdown");
    }

    /**
     * 노드의 고유 식별자를 반환한다.
     *
     * @return 노드 ID
     */
    @Override
    public String getId(){
        return id;
    }
}
