package com.fbp.engine.core;

import java.util.*;

public class Flow {
    private final String id;
    private final Map<String, AbstractNode> nodes;
    private final List<Connection> connections;
    private final Map<String, List<String>> graph = new HashMap<>();
    private enum nodeState {UNVISITED, VISITING,VISITED}

    public enum FlowState {RUNNING, STOPPED}

    private FlowState flowState;

    public Flow(String id){
        this.id = id;
        this.nodes = new HashMap<>();
        this.connections = new ArrayList<>();
        this.flowState = FlowState.STOPPED;
    }

    public Flow addNode(AbstractNode node){
        nodes.put(node.getId(), node);
        graph.put(node.getId(), new ArrayList<>());
        return this;
    }

    public Flow connect(String sourceNodeId, String sourcePort,
                        String targetNodeId, String targetPort){
        AbstractNode sourceNode = nodes.get(sourceNodeId);
        AbstractNode targetNode = nodes.get(targetNodeId);

        if (sourceNode == null) throw new IllegalArgumentException("소스 노드 없음: " + sourceNodeId);
        if (targetNode == null) throw new IllegalArgumentException("타켓 노드 없음: " + targetNodeId);

        OutputPort outputPort = sourceNode.getOutputPort(sourcePort);
        InputPort inputPort = targetNode.getInputPort(targetPort);

        if (outputPort == null) throw new IllegalArgumentException("소스 포트 없음: " + sourcePort);
        if (inputPort == null) throw new IllegalArgumentException("타겟 포트 없음: " + sourcePort);

        String connectId = sourceNodeId + ":" + sourcePort + "->" + targetNodeId + ":" + targetPort;
        Connection connection = new Connection(connectId);
        connection.setTarget(inputPort);
        outputPort.connect(connection);
        connections.add(connection);
        //연결 정보로 그래프 구성
        graph.get(sourceNodeId).add(targetNodeId);

        return this;
    }

    public void initialize(){
        nodes.values().forEach(AbstractNode::initialize);
        this.flowState = FlowState.RUNNING;
    }

    public void shutdown(){
        nodes.values().forEach(AbstractNode::shutdown);
        this.flowState = FlowState.STOPPED;
    }

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

    public String getId(){return id;}
    public Map<String, AbstractNode> getNodes(){return nodes;}
    public List<Connection> getConnections(){return connections;}
    public FlowState getFlowState(){return flowState;}
}
