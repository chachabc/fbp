package com.fbp.engine.influx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * InfluxDB 전송 실패 시 인코딩된 Line Protocol 라인을 보관하는 인메모리 버퍼.
 *
 * maxSize 초과 시 가장 오래된 라인부터 폐기한다.
 * flow_events 등 중요 이벤트 보존 우선순위는 Part F(CLI) 이후 과제로 남긴다.
 */
public class LocalBuffer {

    private final ArrayDeque<String> lines;
    private final int maxSize;
    private final LongAdder droppedCount = new LongAdder();

    public LocalBuffer(int maxSize) {
        this.maxSize = maxSize;
        this.lines   = new ArrayDeque<>(Math.min(maxSize, 1024));
    }

    /** 인코딩된 라인 하나를 버퍼에 추가한다. 버퍼가 가득 차면 가장 오래된 것을 제거한다. */
    public synchronized void add(String encodedLine) {
        if (lines.size() >= maxSize) {
            lines.poll();
            droppedCount.increment();
        }
        lines.addLast(encodedLine);
    }

    /** 여러 라인을 버퍼에 추가한다. */
    public void addAll(List<String> encodedLines) {
        for (String line : encodedLines) add(line);
    }

    /** 버퍼에서 최대 maxLines개 라인을 꺼낸다 (소비). */
    public synchronized List<String> drainBatch(int maxLines) {
        List<String> result = new ArrayList<>(maxLines);
        while (!lines.isEmpty() && result.size() < maxLines) {
            result.add(lines.poll());
        }
        return result;
    }

    public synchronized boolean isEmpty() { return lines.isEmpty(); }
    public synchronized int size()        { return lines.size(); }
    public long getDroppedCount()         { return droppedCount.sum(); }
}