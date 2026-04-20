package com.fbp.engine.core;

import com.fbp.engine.node.stage1.FilterNode;
import com.fbp.engine.node.stage1.GeneratorNode;
import com.fbp.engine.node.stage1.PrintNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class FlowTest {
    private Flow flow;

    @BeforeEach
    void setUp(){
        flow = new Flow("test-flow");
    }

    @Test
    @DisplayName("노드 등록")
    void test1(){
        flow.addNode(new PrintNode("printer-1"));
        Assertions.assertTrue(flow.getNodes().containsKey("printer-1"));
    }

    @Test
    @DisplayName("메서드 체이닝")
    void test2(){
        Assertions.assertDoesNotThrow(() -> {
            flow.addNode(new PrintNode("printer-1"))
                    .addNode(new GeneratorNode("generator-1"))
                    .connect("generator-1", "out", "printer-1", "in");
        });
    }

    @Test
    @DisplayName("정상 연결")
    void test3(){
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"))
                .connect("generator-1", "out", "printer-1", "in");

        Assertions.assertEquals(1, flow.getConnections().size());
    }


    @Test
    @DisplayName("존재하지 않는 소스 노드 ID")
    void test4(){
        flow.addNode(new PrintNode("printer-1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            flow.connect("none", "out", "printer-1", "in");
        });
    }

    @Test
    @DisplayName("존재하지 않는 대상 노드 ID")
    void test5(){
        flow.addNode(new GeneratorNode("generator-1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            flow.connect("generator-1", "out", "none", "in");
        });
    }

    @Test
    @DisplayName("존재하지 않는 소스 포트")
    void test6(){
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            flow.connect("generator-1", "none", "printer-1", "in");
        });
    }

    @Test
    @DisplayName("존재하지 않는 대상 포트")
    void test7(){
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            flow.connect("generator-1", "out", "printer-1", "none");
        });
    }

    @Test
    @DisplayName("validate - 빈 flow")
    void test8(){
        List<String> errors = flow.validate();
        Assertions.assertEquals(1, errors.size());
    }

    @Test
    @DisplayName("validate - 정상 flow")
    void test9(){
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"))
                .connect("generator-1", "out", "printer-1", "in");
        List<String> errors = flow.validate();
        Assertions.assertEquals(0, errors.size());
    }

    @Test
    @DisplayName("initialize - 전체 호출")
    void test10(){
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"))
                .connect("generator-1", "out", "printer-1", "in");
        Assertions.assertDoesNotThrow(() -> {
            flow.initialize();
            Thread.sleep(5000);
            flow.shutdown();
        });
    }

    @Test
    @DisplayName("shutdown - 전체 호출")
    void test11() throws InterruptedException{
        flow.addNode(new GeneratorNode("generator-1"))
                .addNode(new PrintNode("printer-1"))
                .connect("generator-1", "out", "printer-1", "in");
        flow.initialize();
        Thread.sleep(5000);
        Assertions.assertDoesNotThrow(() -> flow.shutdown());
    }

    @Test
    @DisplayName("순환 참조 탐지(도전)")
    void test12(){
        flow.addNode(new FilterNode("generator-1", "tick", 3.0))
                .addNode(new FilterNode("printer-1", "tick", 3.0))
                .connect("generator-1", "out", "printer-1", "in")
                .connect("printer-1", "out", "generator-1", "in");
        List<String> errors = flow.validate();
        Assertions.assertFalse(errors.isEmpty());
    }


}
