package com.fbp.engine.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Message {
    private final String id;
    private final Map<String, Object> payload;
    private final long timestamp;

    public Message(Map<String, Object> payload){
        this.id = UUID.randomUUID().toString();
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String id, Map<String, Object> payload, long timestamp){
        this.id = id;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.timestamp = timestamp;
    }

    public String getId(){return id;}
    public Map<String, Object> getPayload(){return payload;}
    public long getTimestamp(){return timestamp;}

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) payload.get(key);
    }

    public Message withEntry(String key, Object value){
        Map<String, Object> newPayload = new HashMap<>(payload);
        newPayload.put(key, value);
        return new Message(this.id, newPayload, this.timestamp);
    }

    public boolean hasKey(String key){
        return payload.containsKey(key);
    }

    public Message withoutKey(String key){
        Map<String, Object> newPayload = new HashMap<>(payload);
        newPayload.remove(key);
        return new Message(this.id, newPayload, this.timestamp);
    }

    @Override
    public String toString() {
        return "Message{id='" + id + "', payload=" + payload + ", timestamp=" + timestamp + "}";
    }
}
