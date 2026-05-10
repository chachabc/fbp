package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageSerializerTest {

    private MessageSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new MessageSerializer();
    }

    @Test
    @DisplayName("직렬화 후 역직렬화하면 동일한 Message가 복원된다")
    void test1() {
        Message original = new Message(Map.of("value", 31.2, "unit", "celsius"));

        byte[] bytes = serializer.serialize(original);
        Message restored = serializer.deserialize(bytes);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getTimestamp(), restored.getTimestamp());
        assertEquals(31.2, ((Number) restored.get("value")).doubleValue(), 0.001);
        assertEquals("celsius", restored.get("unit"));
    }

    @Test
    @DisplayName("직렬화 결과는 비어있지 않다")
    void test2() {
        byte[] bytes = serializer.serialize(new Message(Map.of("k", "v")));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("페이로드의 다양한 타입(숫자, 문자열, boolean)이 보존된다")
    void test3() {
        Message original = new Message(Map.of(
                "intVal", 42,
                "doubleVal", 3.14,
                "strVal", "hello",
                "boolVal", true
        ));

        Message restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(42, ((Number) restored.get("intVal")).intValue());
        assertEquals(3.14, ((Number) restored.get("doubleVal")).doubleValue(), 0.001);
        assertEquals("hello", restored.get("strVal"));
        assertEquals(true, restored.get("boolVal"));
    }

    @Test
    @DisplayName("복원된 Message의 id와 timestamp는 원본과 동일하다")
    void test4() {
        Message original = new Message("fixed-id", Map.of("key", "val"), 1717401782345L);

        Message restored = serializer.deserialize(serializer.serialize(original));

        assertEquals("fixed-id", restored.getId());
        assertEquals(1717401782345L, restored.getTimestamp());
    }

    @Test
    @DisplayName("빈 페이로드도 직렬화/역직렬화된다")
    void test5() {
        Message original = new Message(Map.of());

        Message restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(original.getId(), restored.getId());
        assertTrue(restored.getPayload().isEmpty());
    }

    @Test
    @DisplayName("잘못된 바이트 배열은 MessageSerializeException을 던진다")
    void test6() {
        byte[] invalid = "not-json".getBytes();
        assertThrows(MessageSerializer.MessageSerializeException.class,
                () -> serializer.deserialize(invalid));
    }
}