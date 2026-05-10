package com.fbp.engine.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 플로우 JSON 내 노드 하나를 나타내는 불변 데이터 객체. */
public class NodeDefinition {

    private final String id;
    private final String type;
    private final Map<String, Object> config;

    public NodeDefinition(String id, String type, Map<String, Object> config) {
        this.id = id;
        this.type = type;
        this.config = Collections.unmodifiableMap(new HashMap<>(config));
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public Map<String, Object> getConfig() { return config; }

    @Override
    public String toString() {
        return "NodeDefinition{id='" + id + "', type='" + type + "'}";
    }
}