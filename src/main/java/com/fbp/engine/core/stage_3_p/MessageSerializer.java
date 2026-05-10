package com.fbp.engine.core.stage_3_p;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbp.engine.message.Message;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Message ↔ JSON byte[] 변환.
 *
 *<pre>
 *  MQTT 페이로드로 전송할 때 Message를 아래 봉투 포맷으로 직렬화한다.
 * {
 *   "v": 1,          ← 프로토콜 버전 (향후 포맷 변경 시 하위 호환용)
 *   "id": "uuid",    ← Message 고유 ID (역직렬화 후 동일 ID 유지)
 *   "ts": 1234567890,← 원본 타임스탬프
 *   "payload": {...} ← Message.getPayload()
 * }
 *</pre>
 *
 */
public class MessageSerializer {

    private static final int VERSION = 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper = new ObjectMapper();

    public byte[] serialize(Message message) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("v", VERSION);
        envelope.put("id", message.getId());
        envelope.put("ts", message.getTimestamp());
        envelope.put("payload", message.getPayload());

        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new MessageSerializeException("직렬화 실패: " + message.getId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Message deserialize(byte[] data) {
        Map<String, Object> envelope;
        try {
            envelope = mapper.readValue(data, MAP_TYPE);
        } catch (IOException e) {
            throw new MessageSerializeException("역직렬화 실패", e);
        }

        String id = (String) envelope.get("id");
        long ts = ((Number) envelope.get("ts")).longValue();
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");

        return new Message(id, payload, ts);
    }

    public static class MessageSerializeException extends RuntimeException {
        public MessageSerializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}