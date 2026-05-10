package com.fbp.engine.influx;

/**
 * InfluxDB 배치 라이터 설정.
 *
 * @param url              InfluxDB 주소 (예: http://localhost:8086)
 * @param token            API 토큰
 * @param org              조직 이름
 * @param bucket           버킷 이름
 * @param batchSize        배치 최대 포인트 수 — 이 수에 도달하면 즉시 flush
 * @param flushIntervalMs  주기적 flush 간격 (밀리초)
 * @param maxRetryAttempts 전송 실패 시 최대 재시도 횟수
 * @param initialBackoffMs 첫 재시도 대기 시간 (이후 2배씩 증가)
 * @param localBufferMaxSize 로컬 메모리 버퍼 최대 라인 수 (초과 시 오래된 것부터 폐기)
 */
public record

InfluxWriterConfig(
        String url,
        String token,
        String org,
        String bucket,
        int batchSize,
        long flushIntervalMs,
        int maxRetryAttempts,
        long initialBackoffMs,
        int localBufferMaxSize
) {
    public static InfluxWriterConfig defaults(String url, String token, String org, String bucket) {
        return new InfluxWriterConfig(url, token, org, bucket,
                1000, 1000, 5, 200, 100_000);
    }
}
