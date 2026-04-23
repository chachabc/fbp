package com.fbp.engine.core;

import java.util.*;

/**
 * 노드와 Connection을 하나의 단위로 묶어 관리하는 클래스.
 * 연결 정의, 유효성 검증, 일괄 초기화/종료를 담당한다.
 */
public class Flow {
    private final String id;
    private final Map<String, AbstractNode> nodes;
    private final List<Connection> connections;
    private final Map<String, List<String>> graph = new HashMap<>();
    private enum nodeState {UNVISITED, VISITING,VISITED}

    public enum FlowState {RUNNING, STOPPED}

    private FlowState flowState;

    /**
     * Flow를 생성한다.
     *
     * @param id Flow의 고유 식별자
     */
    public Flow(String id){
        this.id = id;
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.flowState = FlowState.STOPPED;
    }

    /**
     * 노드를 Flow에 등록한다.
     * 메서드 체이닝을 지원한다.
     *
     * @param node 등록할 노드
     * @return 이 Flow 인스턴스
     */
    public Flow addNode(AbstractNode node){
        nodes.put(node.getId(), node);
        graph.put(node.getId(), new ArrayList<>());
        return this;
    }

    /**
     * 두 노드의 포트를 Connection으로 연결한다.
     * 메서드 체이닝을 지원한다.
     *
     * @param sourceNodeId 송신 노드 ID
     * @param sourcePort   송신 포트 이름
     * @param targetNodeId 수신 노드 ID
     * @param targetPort   수신 포트 이름
     * @return 이 Flow 인스턴스
     * @throws IllegalStateException 노드 ID 또는 포트 이름이 존재하지 않는 경우
     */
    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort){
        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null) throw new IllegalArgumentException("소스 노드 없음: " + sourceNodeId);
        if (targetNode == null) throw new IllegalArgumentException("타켓 노드 없음: " + targetNodeId);

        OutputPort outputPort = sourceNode.getOutputPort(sourcePort);
        InputPort inputPort = targetNode.getInputPort(targetPort);

        if (outputPort == null) throw new IllegalArgumentException("소스 포트 없음: " + sourcePort);
        if (inputPort == null) throw new IllegalArgumentException("타겟 포트 없음: " + targetPort);

        String connectId = sourceNodeId + ":" + sourcePort + "->" + targetNodeId + ":" + targetPort;
        Connection connection = new Connection(connectId);
        connection.setTarget(inputPort);
        outputPort.connect(connection);
        connections.add(connection);
        //연결 정보로 그래프 구성
        graph.get(sourceNodeId).add(targetNodeId);

        return this;
    }

    /**
     * 등록된 모든 노드의 initialize()를 호출하고 Flow 상태를 RUNNING으로 변경한다.
     */
    public void initialize(){
        nodes.values().forEach(AbstractNode::initialize);
        this.flowState = FlowState.RUNNING;
    }

    /**
     * 등록된 모든 노드의 shutdown()을 호출하고 Flow 상태를 STOPPED로 변경한다.
     */
    public void shutdown(){
        nodes.values().forEach(AbstractNode::shutdown);
        this.flowState = FlowState.STOPPED;
    }

    /**
     * Flow의 유효성을 검증하고 오류 목록을 반환한다.
     * 노드가 없거나 순환 참조가 있는 경우 해당 오류 메시지를 포함한다.
     *
     * @return 오류 메시지 목록. 유효하면 빈 리스트를 반환한다.
     */
    public List<String> validate(){
        List<String> errors = new ArrayList<>();
        if (nodes.isEmpty()){
            errors.add("노드가 없습니다.");
            return errors;
        }

        Map<String, nodeState> state = new HashMap<>();
        nodes.keySet().forEach(id -> state.put(id, nodeState.UNVISITED));

        for (String nodeId : nodes.keySet()){
            if (state.get(nodeId) == nodeState.UNVISITED){
                if (hasCycle(nodeId, graph, state)){
                    errors.add("순환 참조가 감지되었습니다.");
                    break;
                }
            }
        }
        return errors;
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> graph,
                             Map<String, nodeState> state){
        state.put(nodeId, nodeState.VISITING);

        for (String next : graph.get(nodeId)){
            if (state.get(next) == nodeState.VISITING) return true;
            if (state.get(next) == nodeState.UNVISITED) {
                if (hasCycle(next, graph, state)) return true;
            }
        }
        state.put(nodeId, nodeState.VISITED);
        return false;
    }

    /**
     * Flow의 고유 식별자를 반환한다.
     *
     * @return Flow ID
     */
    public String getId(){return id;}

    /**
     * 등록된 노드 맵을 반환한다.
     *
     * @return 노드 ID를 키로 하는 노드 맵
     */
    public Map<String, AbstractNode> getNodes(){return nodes;}

    /**
     * 생성된 Connection 목록을 반환한다.
     *
     * @return Connection 목록
     */
    public List<Connection> getConnections(){return connections;}

    /**
     * 현재 Flow의 실행 상태를 반환한다.
     *
     * @return {@link FlowState#RUNNING} 또는 {@link FlowState#STOPPED}
     */
    public FlowState getFlowState(){return flowState;}
}
