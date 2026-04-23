package com.fbp.engine.node.stage2;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 외부 프로토콜(MQTT, MODBUS 등)과 통신하는 모든 노드의 공통 추상 기반 클래스.
 *
 */
public abstract class ProtocolNode extends AbstractNode {
    private static final Logger log = LoggerFactory.getLogger(ProtocolNode.class);
    private final Map<String, Object> config;
    private volatile ConnectionState connectionState;
    private final long reconnectIntervalMs;

    private ScheduledExecutorService reconnectScheduler;

    // ── 생성자 ────────────────────────────────────────────────────────────────
    protected ProtocolNode(String id, Map<String, Object> config) {
        super(id);
        this.config = config;
        this.connectionState = ConnectionState.DISCONNECTED;

        Object interval = config.get("reconnectIntervalMs");
        this.reconnectIntervalMs = (interval instanceof Number number) ? number.longValue() : 5000L;
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────
    /**
     * connectionState를 CONNECTING으로 변경 → connect() 호출
     * → 성공 시 CONNECTED, 실패 시 재연결 스케줄러 시작
     */
    @Override
    public void initialize() {
        super.initialize();
        connectionState = ConnectionState.CONNECTING;
        try {
            connect();
            connectionState = ConnectionState.CONNECTED;
            log.info("[{}] 연결 성공", getId());
        } catch (Exception e) {
            log.warn("[{}] 연결 실패: {} - 재연결 시작", getId(), e.getMessage());
            connectionState = ConnectionState.ERROR;
            reconnect();
        }
    }

    @Override
    public void shutdown() {
        stopReconnectScheduler();
        try {
            disconnect();
        } catch (Exception e) {
            log.warn("[{}] 연결 해제 중 오류: {}", getId(), e.getMessage());
        }
        connectionState = ConnectionState.DISCONNECTED;
        super.shutdown();
    }

    // ── 추상 메서드 ───────────────────────────────────────────────────────────
    /** 실제 연결 로직. 실패하면 반드시 예외를 던진다. */
    protected abstract void connect() throws Exception;

    /** 실제 연결 해제 로직. */
    protected abstract void disconnect();

    // ── 재연결 ────────────────────────────────────────────────────────────────
    /**
     * 연결 끊김 시 호출한다.
     * reconnectIntervalMs 간격으로 connect()를 재시도한다.
     * 최대 재시도 횟수는 config의 "maxRetries" 키에서 읽으며, 기본값은 10회다.
     */
    public void reconnect() {
        Object maxRetriesVal = config.get("maxRetries");
        int maxRetries = (maxRetriesVal instanceof Number number) ? number.intValue() : 10;

        reconnectScheduler = Executors.newSingleThreadScheduledExecutor( r -> {
            Thread thread = new Thread(r, getId() + "reconnect");
            thread.setDaemon(true);
            return thread;
        });

        int[] attempt = {0};
        ScheduledFuture<?>[] future = new ScheduledFuture<?>[1];

        future[0] = reconnectScheduler.scheduleWithFixedDelay(() -> {
            attempt[0]++;
            log.info("[{}] 재연결 시도 {}/{}", getId(), attempt[0], maxRetries);
            try {
                connectionState = ConnectionState.CONNECTING;
                connect();
                connectionState = ConnectionState.CONNECTED;
                log.info("[{}] 재연결 성공", getId());
                future[0].cancel(false);
                reconnectScheduler.shutdown();
            } catch (Exception e) {
                connectionState = ConnectionState.ERROR;
                log.warn("[{}] 재연결 실패 ({}/{}): {}", getId(), attempt[0], maxRetries, e.getMessage());
                if (attempt[0] >= maxRetries) {
                    log.error("[{}] 최대 연결 횟수 초과.", getId());
                    future[0].cancel(false);
                    reconnectScheduler.shutdown();
                }
            }
        }, reconnectIntervalMs, reconnectIntervalMs, TimeUnit.MILLISECONDS);
    }

    // ── 상태 / config 조회 ────────────────────────────────────────────────────
    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }

    public Object getConfig(String key) {
        return config.get(key);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────
    private void stopReconnectScheduler() {
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
        }
    }
}
