package com.fbp.engine.message;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class MessageTest {
    private Message message;

    @BeforeEach
    void setUp(){
        message = new Message(Map.of("temperature", 25.5));
    }

    @Test
    @DisplayName("생성시 ID 자동 할당")
    void id(){
        Assertions.assertNotNull(message.getId());
        Assertions.assertFalse(message.getId().isBlank());
    }

    @Test
    @DisplayName("생성 시 timestamp 자동 기록")
    void timestamp(){
        Assertions.assertTrue(message.getTimestamp() > 0);
    }

    @Test
    @DisplayName("페이로드 조회")
    void payload(){
        Assertions.assertTrue(message.hasKey("temperature"));
        Assertions.assertEquals(25.5, message.get("temperature"));
    }

    @Test
    @DisplayName("제네릭 get 타입 캐스팅")
    void generic(){
        Assertions.assertInstanceOf(Double.class, message.get("temperature"));
    }

    @Test
    @DisplayName("존재하지 않는 키 조회")
    void test1(){
        Assertions.assertNull(message.get("aaa"));
    }

    @Test
    @DisplayName("페이로드 불변 - 외부 수정 차단")
    void test2(){
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> message.getPayload().put("testKey", "testValue"));
    }

    @Test
    @DisplayName("페이로드 불변 - 원본 Map 수정 무영향")
    void test3(){
        Map<String, Object> original = new HashMap<>();
        original.put("temperature", 25.5);

        Message message1 = new Message(original);
        original.put("testKey", "testValue");
        Assertions.assertFalse(message1.hasKey("testKey"));
    }

    @Test
    @DisplayName("withEntry - 새 객체 반환")
    void test4(){
        Message newMessage = message.withEntry("humidity", 60.0);
        Assertions.assertNotSame(message, newMessage);
    }

    @Test
    @DisplayName("withEntry - 원본 불멸")
    void test5(){
        message.withEntry("humidity", 60.0);
        Assertions.assertFalse(message.hasKey("humidity"));
    }

    @Test
    @DisplayName("withEntry - 새 메시지에 값 존재")
    void test6(){
        Message newMessage = message.withEntry("humidity", 60.0);
        Assertions.assertEquals(60.0, newMessage.get("humidity"));
    }

    @Test
    @DisplayName("hasKey - 존재하는 키")
    void test7(){
        Assertions.assertTrue(message.hasKey("temperature"));
    }

    @Test
    @DisplayName("hasKey - 존재하지 않는 키")
    void test8(){
        Assertions.assertFalse(message.hasKey("testKey"));
    }


    @Test
    @DisplayName("withoutKey - 키 제거 확인")
    void test9(){
        Message newMessage = message.withoutKey("temperature");
        Assertions.assertFalse(newMessage.hasKey("temperature"));
    }

    @Test
    @DisplayName("withoutKey - 원본 불변")
    void test10(){
        message.withoutKey("temperature");
        Assertions.assertTrue(message.hasKey("temperature"));
    }
    @Test
    @DisplayName("toString 포멧")
    void test11(){
        String string = message.toString();
        Assertions.assertNotNull(string);
        Assertions.assertTrue(string.contains("temperature"));
    }

}
