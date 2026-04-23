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

    /**
     * FlowEngine을 생성한다.
     * 초기 상태는 {@link State#INITIALIZED}이다.
     */
    public FlowEngine(){
        this.state = State.INITIALIZED;
        this.flows = new LinkedHashMap<>();
    }

    /**
     * Flow를 엔진에 등록한다.
     * 등록된 Flow는 {@link #startFlow(String)}으로 시작할 수 있다.
     *
     * @param flow 등록할 Flow
     */
    public void register(Flow flow){
        flows.put(flow.getId(), flow);
        System.out.println("[Engine] 플로우 '" + flow.getId() + "' 등록됨");
    }

    /**
     * 지정한 Flow를 시작한다.
     * 유효성 검증 후 Flow의 initialize()를 호출하며, 엔진 상태를 {@link State#RUNNING}으로 변경한다.
     *
     * @param flowId 시작할 Flow의 ID
     * @throws IllegalArgumentException 해당 ID의 Flow가 등록되어 있지 않은 경우
     * @throws IllegalStateException    Flow의 validate()에서 오류가 발생한 경우
     */
    public void startFlow(String flowId){
        Flow flow = flows.get(flowId);
        if(flow == null) throw new IllegalArgumentException("플로우 없음: " + flowId);

        List<String> errors = flow.validate();
        if(!errors.isEmpty()) throw new IllegalArgumentException("유효성 오류: " + errors);

        flow.initialize();
        this.state = State.RUNNING;
        System.out.println("[Engine] 플로우 '" + flowId + "' 시작됨");
    }

    /**
     * 지정한 Flow를 정지한다.
     * Flow의 shutdown()을 호출한다.
     *
     * @param flowId 정지할 Flow의 ID
     * @throws IllegalArgumentException 해당 ID의 Flow가 등록되어 있지 않은 경우
     */
    public void stopFlow(String flowId){
        Flow flow = flows.get(flowId);
        if (flow==null) throw new IllegalArgumentException("플로우 없음: " + flowId);

        flow.shutdown();
        System.out.println("[Engine] 플로우 '" + flowId + "' 정지됨");
    }

    /**
     * 등록된 모든 Flow를 정지하고 엔진 상태를 {@link State#STOPPED}로 변경한다.
     */
    public void shutdown(){
        flows.values().forEach(Flow::shutdown);
        this.state = State.STOPPED;
        System.out.println("[Engine] 엔진 종료됨");
    }

    /**
     * 등록된 모든 Flow의 ID와 상태를 콘솔에 출력한다.
     */
    public void listFlows(){
        flows.forEach((id, flow) ->
                System.out.println("[" + id + "]" + flow.getFlowState()));
    }

    /**
     * 현재 엔진 상태를 반환한다.
     *
     * @return {@link State#INITIALIZED}, {@link State#RUNNING}, {@link State#STOPPED} 중 하나
     */
    public State getState(){return state;}

    /**
     * 등록된 Flow 맵을 반환한다.
     *
     * @return Flow ID를 키로 하는 Flow 맵
     */
    public Map<String, Flow> getFlows(){return flows;}
}
