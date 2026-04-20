package com.fbp.engine.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FBP 엔진의 최상위 관리자.
 * Flow를 등록/시작/정지하고 엔진 전체 상태를 관리한다.
 */
public class FlowEngine {
    public enum State {INITIALIZED, RUNNING, STOPPED}

    private State state;
    private final Map<String, Flow> flows;

    public FlowEngine(){
        this.state = State.INITIALIZED;
        this.flows = new LinkedHashMap<>();
    }

    public void register(Flow flow){
        flows.put(flow.getId(), flow);
        System.out.println("[Engine] 플로우 '" + flow.getId() + "' 등록됨");
    }

    public void startFlow(String flowId){
        Flow flow = flows.get(flowId);
        if(flow == null) throw new IllegalArgumentException("플로우 없음: " + flowId);

        List<String> errors = flow.validate();
        if(!errors.isEmpty()) throw new IllegalArgumentException("유효성 오류: " + errors);

        flow.initialize();
        this.state = State.RUNNING;
        System.out.println("[Engine] 플로우 '" + flowId + "' 시작됨");
    }

    public void stopFlow(String flowId){
        Flow flow = flows.get(flowId);
        if (flow==null) throw new IllegalArgumentException("플로우 없음: " + flowId);

        flow.shutdown();
        System.out.println("[Engine] 플로우 '" + flowId + "' 정지됨");
    }

    public void shutdown(){
        flows.values().forEach(Flow::shutdown);
        this.state = State.STOPPED;
        System.out.println("[Engine] 엔진 종료됨");
    }

    public void listFlows(){
        flows.forEach((id, flow) ->
                System.out.println("[" + id + "]" + flow.getFlowState()));
    }

    public State getState(){return state;}
    public Map<String, Flow> getFlows(){return flows;}
}
