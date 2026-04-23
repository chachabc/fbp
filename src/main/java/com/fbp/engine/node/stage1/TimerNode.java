package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 지정한 주기마다 tick 메시지를 자동으로 생성하여 {@code "out"} 포트로 전송하는 노드.
 * {@link java.util.concurrent.ScheduledExecutorService}로 구현되며,
 * 다운스트림 노드의 주기적 실행을 구동하는 트리거 역할을 한다.
 * InputPort가 없으며, {@code initialize()} 호출 시 타이머가 시작된다.
 */
public class TimerNode extends AbstractNode {

    private final long intervalMs;
    private int tickCount;
    private ScheduledExecutorService scheduler;

    /**
     * TimerNode를 생성한다.
     *
     * @param id         노드의 고유 식별자
     * @param intervalMs 메시지 생성 주기 (밀리초)
     */
    public TimerNode(String id, long intervalMs){
        super(id);
        this.intervalMs = intervalMs;
        this.tickCount = 0;
        addOutputPort("out");
    }

    /**
     * 스케줄러를 시작하고 지정된 주기마다 tick 메시지를 생성한다.
     * 메시지 페이로드: {@code {"tick": tickCount, "timestamp": 현재시각}}
     */
    @Override
    public void initialize(){
        super.initialize();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SS"));
            Message message = new Message(Map.of(
                    "tick", tickCount,
                    "timestamp", time
            ));
            send("out", message);
            tickCount++;
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 스케줄러를 종료하여 tick 메시지 생성을 중단한다.
     */
    @Override
    public void shutdown() {
        if (scheduler != null){
            scheduler.shutdown();
        }
        super.shutdown();
    }

    /**
     * TimerNode는 외부 메시지를 처리하지 않으므로 빈 구현이다.
     *
     * @param message 무시되는 메시지
     */
    @Override
    protected void onProcess(Message message){
        //메시지를 처리하지 않음
    }

    /**
     * 현재까지 생성된 tick 메시지의 총 수를 반환한다.
     *
     * @return tick 카운트
     */
    public int getTickCount() {
        return tickCount;
    }
}
