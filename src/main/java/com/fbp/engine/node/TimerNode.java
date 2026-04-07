package com.fbp.engine.node;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerNode extends AbstractNode {

    private final long intervalMs;
    private int tickCount;
    private ScheduledExecutorService scheduler;

    public TimerNode(String id, long intervalMs){
        super(id);
        this.intervalMs = intervalMs;
        this.tickCount = 0;
        addOutputPorts("out");
    }

    @Override
    public void initialize(){
        super.initialize();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Message message = new Message(Map.of(
                    "tick", tickCount,
                    "timestamp", System.currentTimeMillis()
            ));
            send("out", message);
            tickCount++;
        }, 0, intervalMs, TimeUnit.MICROSECONDS);
    }

    @Override
    public void shutdown() {
        if (scheduler != null){
            scheduler.shutdown();
        }
        super.shutdown();
    }

    @Override
    protected void onProcess(Message message){
    }
}
