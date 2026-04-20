package com.fbp.engine.core;

import com.fbp.engine.node.stage1.GeneratorNode;
import com.fbp.engine.node.stage1.PrintNode;
import com.fbp.engine.node.stage1.TimerNode;
import org.junit.jupiter.api.*;

class FlowEngineTest {
    private FlowEngine engine;
    private Flow flow;

    @BeforeEach
    void setUp(){
        engine = new FlowEngine();

        flow = new Flow("test-flow");
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"))
                .connect("generator-1", "out", "printer-1", "in");
    }

    @AfterEach
    void tearDown(){
        engine.shutdown();
    }

    @Test
    @DisplayName("초기 상태")
    void test1(){
        Assertions.assertEquals(FlowEngine.State.INITIALIZED, engine.getState());
    }

    @Test
    @DisplayName("플로우 등록")
    void test2(){
        engine.register(flow);
        Assertions.assertTrue(engine.getFlows().containsKey("test-flow"));
    }

    @Test
    @DisplayName("startFlow 정상")
    void test3(){
        engine.register(flow);
        engine.startFlow("test-flow");
        Assertions.assertEquals(FlowEngine.State.RUNNING, engine.getState());
    }

    @Test
    @DisplayName("startFlow - 없는 ID")
    void test4(){
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                engine.startFlow("none"));
    }

    @Test
    @DisplayName("startFlow - 유효성 실패")
    void test5(){
        Flow emptyFlow = new Flow("empty-flow");
        engine.register(emptyFlow);
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                engine.startFlow("empty-flow"));
    }

    @Test
    @DisplayName("stopFlow 정상")
    void test6(){
        engine.register(flow);
        engine.startFlow("test-flow");
        engine.stopFlow("test-flow");
        Assertions.assertEquals(Flow.FlowState.STOPPED, flow.getFlowState());
    }

    @Test
    @DisplayName("shutdown 전체")
    void test7(){
        engine.register(flow);
        engine.startFlow("test-flow");
        engine.shutdown();
        Assertions.assertEquals(FlowEngine.State.STOPPED, engine.getState());
    }

    @Test
    @DisplayName("다중 플로우 독립 동작")
    void test8(){
        Flow flow1 = new Flow("test-flow2");
        flow1.addNode(new TimerNode("timer-1", 500))
                .addNode(new PrintNode("printer-1"))
                .connect("timer-1", "out", "printer-1", "in");

        engine.register(flow1);
        engine.register(flow);

        engine.startFlow("test-flow");
        engine.startFlow("test-flow2");
        engine.stopFlow("test-flow2");

        Assertions.assertEquals(Flow.FlowState.STOPPED, flow1.getFlowState());
        Assertions.assertEquals(Flow.FlowState.RUNNING, flow.getFlowState());
    }

    @Test
    @DisplayName("listFlows 출력")
    void test9(){
        engine.register(flow);
        Assertions.assertDoesNotThrow(() -> engine.listFlows());
        Assertions.assertEquals(1, engine.getFlows().size());
    }
}
