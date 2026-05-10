package com.fbp.engine.influx;

import com.fbp.engine.metrics.MetricPoint;
import com.fbp.engine.metrics.PointSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * MetricPoint를 InfluxDB에 배치로 적재하는 PointSink 구현체.
 *
 * <pre>
 * 설계 원칙:
 *   accept()는 pendingQueue.offer()만 수행하고 즉시 반환 — 호출자(MetricsCollector)를 블로킹하지 않음.
 *   writerThread 단독으로 HTTP 전송, 재시도, 로컬 버퍼 관리를 담당.
 *
 * 전송 실패 시:
 *   1. 지수 backoff로 maxRetryAttempts 회 재시도
 *   2. 모두 실패하면 LocalBuffer에 저장
 *   3. 다음 전송 성공 시점에 LocalBuffer 내용을 함께 재전송
 * </pre>
 */
public class InfluxBatchWriter implements PointSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InfluxBatchWriter.class);

    private final InfluxWriterConfig config;
    private final LineProtocolEncoder encoder;
    private final LocalBuffer localBuffer;
    private final HttpClient httpClient;
    private final BlockingQueue<MetricPoint> pendingQueue;
    private final Thread writerThread;

    private final LongAdder totalWritten  = new LongAdder();
    private final LongAdder totalFailed   = new LongAdder();
    private volatile boolean running      = true;

    // ── 생성자 ──────────────────────────────────────────────────────────────────

    public InfluxBatchWriter(InfluxWriterConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    /** 테스트용: 외부에서 HttpClient를 주입할 수 있다. */
    InfluxBatchWriter(InfluxWriterConfig config, HttpClient httpClient) {
        this.config       = config;
        this.encoder      = new LineProtocolEncoder();
        this.localBuffer  = new LocalBuffer(config.localBufferMaxSize());
        this.httpClient   = httpClient;
        this.pendingQueue = new ArrayBlockingQueue<>(config.batchSize() * 4);
        this.writerThread = Thread.ofVirtual()
                .name("fbp-influx-writer")
                .start(this::writerLoop);
    }

    // ── PointSink (hot path) ─────────────────────────────────────────────────

    @Override
    public void accept(MetricPoint point) {
        if (!pendingQueue.offer(point)) {
            log.debug("[Influx] pending 큐 가득 참 — 포인트 드롭");
        }
    }

    // ── 생명주기 ─────────────────────────────────────────────────────────────────

    @Override
    public void close() {
        running = false;
        writerThread.interrupt();
        try { writerThread.join(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log.info("[Influx] 종료 — written:{}, failed:{}, buffer:{}",
                totalWritten.sum(), totalFailed.sum(), localBuffer.size());
    }

    // ── 상태 조회 ────────────────────────────────────────────────────────────────

    public long getTotalWritten()  { return totalWritten.sum(); }
    public long getTotalFailed()   { return totalFailed.sum(); }
    public int  getLocalBufferSize() { return localBuffer.size(); }

    // ── 내부 — writer loop ────────────────────────────────────────────────────

    private void writerLoop() {
        List<MetricPoint> batch   = new ArrayList<>(config.batchSize());
        long lastFlushMs          = System.currentTimeMillis();

        while (running || !pendingQueue.isEmpty()) {
            try {
                MetricPoint point = pendingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (point != null) batch.add(point);

                boolean timeTick  = System.currentTimeMillis() - lastFlushMs >= config.flushIntervalMs();
                boolean batchFull = batch.size() >= config.batchSize();

                if (!batch.isEmpty() && (timeTick || batchFull)) {
                    sendBatch(batch);
                    batch.clear();
                    lastFlushMs = System.currentTimeMillis();
                }

                if (!localBuffer.isEmpty() && batch.isEmpty()) {
                    retryFromBuffer();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!batch.isEmpty()) sendBatch(batch);
    }

    // ── 내부 — 전송 ───────────────────────────────────────────────────────────

    private void sendBatch(List<MetricPoint> batch) {
        List<String> encoded     = batch.stream().map(encoder::encode).toList();
        String lineProtocol      = String.join("\n", encoded);
        boolean success          = sendWithRetry(lineProtocol, batch.size());
        if (!success) {
            localBuffer.addAll(encoded);
        }
    }

    private void retryFromBuffer() {
        List<String> lines = localBuffer.drainBatch(config.batchSize());
        if (lines.isEmpty()) return;
        String lineProtocol = String.join("\n", lines);
        boolean success = sendWithRetry(lineProtocol, lines.size());
        if (!success) {
            localBuffer.addAll(lines); // 실패 시 되돌림
        }
    }

    /**
     * 지수 backoff 재시도를 포함한 전송. 모든 시도 실패 시 false를 반환한다.
     */
    private boolean sendWithRetry(String lineProtocol, int pointCount) {
        for (int attempt = 0; attempt < config.maxRetryAttempts(); attempt++) {
            if (attempt > 0) {
                long backoff = config.initialBackoffMs() * (1L << (attempt - 1));
                try { Thread.sleep(Math.min(backoff, 30_000)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
            if (trySend(lineProtocol, pointCount)) return true;
        }
        totalFailed.increment();
        log.warn("[Influx] 최대 재시도 초과 — {} points를 로컬 버퍼에 보관", pointCount);
        return false;
    }

    private boolean trySend(String lineProtocol, int pointCount) {
        try {
            byte[] body = gzip(lineProtocol.getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/api/v2/write?org=" + config.org()
                            + "&bucket=" + config.bucket() + "&precision=ns"))
                    .header("Authorization", "Token " + config.token())
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("Content-Encoding", "gzip")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                totalWritten.add(pointCount);
                log.debug("[Influx] 쓰기 성공 — {} points", pointCount);
                return true;
            }
            log.warn("[Influx] 쓰기 실패 (status={}, body={})", response.statusCode(),
                    response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
            return false;
        } catch (IOException | InterruptedException e) {
            log.debug("[Influx] 전송 오류: {}", e.getMessage());
            return false;
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(data);
        }
        return bos.toByteArray();
    }
}
