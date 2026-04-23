package com.fbp.engine.network;

import com.fbp.engine.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 외부 수신 데이터(raw bytes)를 FBP {@code Message}로 변환하는 유틸리티 클래스.
 *
 * <p>변환 전략을 여기에 집중시켜, MessageListener 구현체가
 * 변환 로직에 신경 쓰지 않고 콜백 처리에만 집중할 수 있도록 한다.</p>
 *
 * <pre>
 * 변환 흐름:
 *   raw bytes
 *     └─ UTF-8 문자열로 디코딩
 *           └─ JSON이면 → Map 파싱 → FBP Message
 *           └─ JSON 아니면 → {"rawPayload": 원본문자열} → FBP Message
 *           (+ "topic", "receivedAt" 메타데이터 자동 추가)
 * </pre>
 */
public class MessageConverter {
    /**
     * raw 바이트 페이로드와 topic을 FBP Message로 변환한다.
     *
     * <p>JSON 파싱을 시도하고, 실패하면 원본 문자열을 {@code "rawPayload"} 키에 담는다.
     * 항상 {@code "topic"}과 {@code "receivedAt"} 메타데이터를 포함한다.</p>
     *
     * @param topic   메시지 출처 식별자
     * @param payload 수신한 원본 바이트
     * @return 변환된 FBP Message
     */
    public static Message convert(String topic, byte[] payload) {
        String raw = new String(payload, StandardCharsets.UTF_8).trim();

        Map<String, Object> data = new HashMap<>();

        // JSON 파싱 시도 (외부 라이브러리 없이 간단한 파싱)
        if (isJson(raw)) {
            Map<String, Object> parsed = parseJson(raw);
            data.putAll(parsed);
        } else {
            // JSON이 아니면 원본 문자열을 rawPayload에 보존
            data.put("rawPayload", raw);
        }

        // 메타데이터 추가 — 항상 포함
        data.put("topic", topic);
        data.put("receivedAt", System.currentTimeMillis());

        return new Message(data);
    }

    /**
     * 문자열이 JSON 객체 형식({...})인지 간단히 판별한다.
     */
    static boolean isJson(String text) {
        return text != null && text.startsWith("{") && text.endsWith("}");
    }

    /**
     * 단순 JSON 객체를 파싱한다. Jackson 없이 기본 포맷만 처리.
     *
     * <p>실제 프로젝트에서는 Jackson ObjectMapper를 사용할 것.
     * 여기서는 학습 목적으로 직접 구현하여 의존성 없이 동작하도록 한다.</p>
     *
     * <p>지원 형식:
     * <ul>
     *   <li>문자열 값: {@code "key": "value"}</li>
     *   <li>숫자 값: {@code "key": 123} 또는 {@code "key": 12.5}</li>
     *   <li>불리언 값: {@code "key": true} 또는 {@code "key": false}</li>
     * </ul>
     * </p>
     */
    static Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();

        // 앞뒤 중괄호 제거
        String inner = json.trim().substring(1, json.trim().length() - 1).trim();
        if (inner.isEmpty()) return result;

        // 쉼표로 분리 (중첩 구조는 미지원 — Jackson이 필요한 이유)
        String[] pairs = splitTopLevel(inner);

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx < 0) continue;

            String key = pair.substring(0, colonIdx).trim()
                    .replaceAll("^\"|\"$", "");          // 따옴표 제거
            String valueStr = pair.substring(colonIdx + 1).trim();

            result.put(key, parseValue(valueStr));
        }

        return result;
    }

    /**
     * 최상위 레벨의 쉼표로만 분리한다 (중첩 객체 내부 쉼표는 무시).
     */
    private static String[] splitTopLevel(String s) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    parts.add(s.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        parts.add(s.substring(start).trim());
        return parts.toArray(new String[0]);
    }

    /**
     * JSON 값 문자열을 적절한 Java 타입으로 변환한다.
     */
    private static Object parseValue(String valueStr) {
        if (valueStr.startsWith("\"") && valueStr.endsWith("\"")) {
            // 문자열
            return valueStr.substring(1, valueStr.length() - 1);
        }
        if ("true".equals(valueStr)) return Boolean.TRUE;
        if ("false".equals(valueStr)) return Boolean.FALSE;
        if ("null".equals(valueStr)) return null;
        try {
            if (valueStr.contains(".")) return Double.parseDouble(valueStr);
            return Long.parseLong(valueStr);
        } catch (NumberFormatException e) {
            return valueStr; // 파싱 불가 → 원본 문자열 반환
        }
    }
}
