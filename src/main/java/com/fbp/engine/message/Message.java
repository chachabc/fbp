package com.fbp.engine.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
/**
 * 노드 간 전달되는 불변 데이터 패킷.
 * UUID, 페이로드(Map), 타임스탬프를 가지며 생성 후 수정 불가.
 * 생성 후 내부 상태를 변경할 수 없으며, 데이터를 추가, 제거할 때는
 * {@link #withEntry(String, Object)} 또는 {@link #withoutKey(String)}으로 새 객체를 반환한다.
 * 멀티스레드 환경에서 별도의 동기화 없이 안전하게 공유할 수 있다.
 *
 */
public class Message {
    private final String id;
    private final Map<String, Object> payload;
    private final long timestamp;

    /**
     * 페이로드를 지정하여 Message를 생성한다.
     * ID는 UUID로 자동 생성되고, 타임스탬프틑 생성 시각으로 자동 기록된다.
     * 전달된 Map은 내부에 방어적 복사본으로 저장되엉 원본 수정에 영향을 받지 않는다.
     *
     * @param payload 초기 페이로드, null값은 허용되지 않는다.
     */
    public Message(Map<String, Object> payload){
        this.id = UUID.randomUUID().toString();
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * ID, 페이로드, 타임스탬프를 모두 직접 지정하여 Message를 생성한다.
     * {@link #withoutKey(String)}, {@link #withEntry(String, Object)} 내부에서
     * 원본 메시지의 ID와 타임스탬프를 유지한 채 새 객체를 만들 때 사용한다.
     *
     * @param id 메시지 고유 식별자
     * @param payload 페이로드
     * @param timestamp 생성 타임스탬프
     */
    public Message(String id, Map<String, Object> payload, long timestamp){
        this.id = id;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.timestamp = timestamp;
    }

    /**
     * 메시지의 고유 식별자를 반환한다.
     *
     * @return UUID 형식의 메시지 ID
     */
    public String getId(){return id;}

    /**
     * 페이로드 전체를 반환한다.
     * 반환된 Map은 수정 불가(unmodifiable)이며, 수정을 시도하면 ({@link UnsupportedOperationException} 이 발생한다.
     *
     * @return 수정 불가 페이로드 Map
     */
    public Map<String, Object> getPayload(){return payload;}

    /**
     * 메시지 생성 시각을 반환한다.
     *
     * @return 생성 타임스탬프 (밀리초, {@code System.currentTimeMillis()} 기준)
     */
    public long getTimestamp(){return timestamp;}

    /**
     * 페이로드에서 지정한 키의 값을 꺼낸다.
     * 제네릭 캐스팅을 내부에서 처리하므로 호출부에서 별도 캐스팅이 불필요하다.
     *
     * <pre>{@code
     * Double temp = message.get("temperature");
     * String id   = message.get("sensorId");
     * }</pre>
     *
     * @param <T> 반환 타입
     * @param key 조회할 키
     * @return 해당 키의 값. 키가 없으면 null.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) payload.get(key);
    }

    /**
     * 현재 페이로드에 키-값 쌍을 추가한 새 Message를 반환한다.
     * 원본 메시지는 변경되지 않는다. ID와 타임스탬프는 원본과 동일하게 유지된다.
     * 이미 존재하는 키를 지정하면 해당 값이 덮어쓰인다.
     *
     * @param key   추가할 키
     * @param value 추가할 값
     * @return 키-값이 추가된 새 Message
     */
    public Message withEntry(String key, Object value){
        Map<String, Object> newPayload = new HashMap<>(payload);
        newPayload.put(key, value);
        return new Message(this.id, newPayload, this.timestamp);
    }

    /**
     * 페이로드에 해당 키가 존재하는지 확인한다.
     *
     * @param key 확인할 키
     * @return 키가 존재하면 {@code true}, 없으면 {@code false}
     */
    public boolean hasKey(String key){
        return payload.containsKey(key);
    }

    /**
     * 현재 페이로드에서 지정한 키를 제거한 새 Message를 반환한다.
     * 원본 메시지는 변경되지 않는다. ID와 타임스탬프는 원본과 동일하게 유지된다.
     * 존재하지 않는 키를 지정해도 예외 없이 처리된다.
     *
     * @param key 제거할 키
     * @return 해당 키가 제거된 새 Message
     */
    public Message withoutKey(String key){
        Map<String, Object> newPayload = new HashMap<>(payload);
        newPayload.remove(key);
        return new Message(this.id, newPayload, this.timestamp);
    }

    /**
     * 메시지의 문자열 표현을 반환한다.
     * ID, 페이로드, 타임스탬프를 포함한다.
     *
     * @return {@code Message{id='...', payload={...}, timestamp=...}} 형식의 문자열
     */
    @Override
    public String toString() {
        return "Message{id='" + id + "', payload=" + payload + ", timestamp=" + timestamp + "}";
    }
}
