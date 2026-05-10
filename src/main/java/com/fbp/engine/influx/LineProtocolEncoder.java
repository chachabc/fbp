package com.fbp.engine.influx;

import com.fbp.engine.metrics.MetricPoint;

import java.util.Map;

/**
 * MetricPoint를 InfluxDB Line Protocol 문자열로 변환한다.
 *
 * <pre>
 * 포맷: measurement[,tag=val...] field=val[,...] timestamp_ns
 *
 * 타입 매핑:
 *   Long / Integer  → 정수 필드 (i 접미사: 123i)
 *   Double / Float  → 실수 필드 (1.23)
 *   Boolean         → 논리 필드 (true / false)
 *   String          → 문자열 필드 ("value")
 * </pre>
 */
public class LineProtocolEncoder {

    public String encode(MetricPoint point) {
        StringBuilder sb = new StringBuilder();

        sb.append(escapeMeasurement(point.measurement()));

        point.tags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(',')
                        .append(escapeTag(e.getKey()))
                        .append('=')
                        .append(escapeTag(e.getValue())));

        sb.append(' ');

        boolean first = true;
        for (Map.Entry<String, Object> e : point.fields().entrySet()) {
            if (!first) sb.append(',');
            sb.append(escapeFieldKey(e.getKey()))
              .append('=')
              .append(encodeFieldValue(e.getValue()));
            first = false;
        }

        sb.append(' ').append(point.timestampNs());
        return sb.toString();
    }

    // ── 이스케이프 헬퍼 ────────────────────────────────────────────────────────

    private String escapeMeasurement(String s) {
        return s.replace(",", "\\,").replace(" ", "\\ ");
    }

    private String escapeTag(String s) {
        return s.replace(",", "\\,").replace("=", "\\=").replace(" ", "\\ ");
    }

    private String escapeFieldKey(String s) {
        return s.replace(",", "\\,").replace("=", "\\=").replace(" ", "\\ ");
    }

    private String encodeFieldValue(Object value) {
        if (value instanceof Long l)    return l + "i";
        if (value instanceof Integer i) return i + "i";
        if (value instanceof Double d)  return String.valueOf(d);
        if (value instanceof Float f)   return String.valueOf(f);
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof String s)  return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        return String.valueOf(value);
    }
}