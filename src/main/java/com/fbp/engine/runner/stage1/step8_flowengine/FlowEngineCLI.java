package com.fbp.engine.runner.stage1.step8_flowengine;

import com.fbp.engine.core.Flow;
import com.fbp.engine.core.FlowEngine;
import com.fbp.engine.node.stage1.FilterNode;
import com.fbp.engine.node.stage1.PrintNode;
import com.fbp.engine.node.stage1.TimerNode;

import java.util.Scanner;

public class FlowEngineCLI {
    public static void main(String[] args) throws InterruptedException{
        Flow flow = new Flow("monitoring");
        flow.addNode(new TimerNode("timer-1", 1000))
                .addNode(new FilterNode("filter-1", "tick", 3.0))
                .addNode(new PrintNode("printer-1"))
                .connect("timer-1", "out", "filter-1", "in")
                .connect("filter-1", "out", "printer-1", "in");

        FlowEngine engine = new FlowEngine();
        engine.register(flow);

        Scanner scanner = new Scanner(System.in);
        System.out.print("fbp> ");

        while(scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            String[] parts = input.split(" ");

            try {
                switch (parts[0]){
                    case "list": {
                        engine.listFlows();
                        break;
                    }
                    case "start": {
                        if (parts.length < 2) System.out.println("사용법: start <id>");
                        else engine.startFlow(parts[1]);
                        break;
                    }
                    case "stop": {
                        if (parts.length < 2) System.out.println("사용법: stop <id>");
                        else engine.startFlow(parts[1]);
                        break;
                    }
                    case "exit": {
                        engine.shutdown();
                        scanner.close();
                        return;
                    }
                    default : System.out.println("알 수 없는 명령어: " + parts[0]);
                    break;
                }
            } catch (IllegalArgumentException e){
                System.out.println("[오류] 존재하지 않는 플로우: " + e.getMessage());
            } catch (IllegalStateException e) {
                System.out.println("[오류] 유효하지 않은 플로우: " + e.getMessage());
            } catch (Exception e){
                System.out.println("[오류] " + e.getMessage());
            }
            System.out.print("fbp> ");
        }
    }
}
