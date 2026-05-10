package com.fbp.engine.registry;

import com.fbp.engine.core.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 노드 타입 이름 → NodeFactory 매핑을 관리하는 중앙 등록소.
 *<pre>
 * FlowParser가 JSON의 "type": "ThresholdFilter"를 읽으면
 * 이 레지스트리를 통해 노드 인스턴스를 생성한다.
 *
 * 스레드 안전: ConcurrentHashMap 사용, 동시 등록/조회 모두 안전하다.
 * 중복 등록 정책: 마지막 등록이 이전 등록을 덮어쓴다 (경고 로그 출력).
 * </pre>
 */
public class NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistry.class);

    private final Map<String, NodeFactory> factories = new ConcurrentHashMap<>();

    /**
     * 노드 타입을 등록한다.
     *
     * @param typeName 타입 이름 (플로우 JSON의 "type" 값과 일치해야 한다)
     * @param factory  노드 인스턴스 생성 팩토리
     * @throws NodeRegistryException typeName이 null이거나 비어있을 때
     */
    public void register(String typeName, NodeFactory factory) {
        validateTypeName(typeName);
        if (factory == null) {
            throw new NodeRegistryException("factory는 null일 수 없습니다: " + typeName);
        }
        if (factories.containsKey(typeName)) {
            log.warn("이미 등록된 타입을 덮어씁니다: {}", typeName);
        }
        factories.put(typeName, factory);
        log.debug("노드 타입 등록: {}", typeName);
    }

    /**
     * 등록된 팩토리로 노드 인스턴스를 생성한다.
     *
     * @param typeName 생성할 노드 타입 이름
     * @param id       노드 고유 식별자
     * @param config   노드 설정 맵
     * @return 생성된 Node 인스턴스
     * @throws NodeRegistryException 미등록 타입이거나 팩토리 실행 중 예외가 발생할 때
     */
    public Node create(String typeName, String id, Map<String, Object> config) {
        validateTypeName(typeName);
        NodeFactory factory = factories.get(typeName);
        if (factory == null) {
            throw new NodeRegistryException("미등록 노드 타입: " + typeName);
        }
        try {
            return factory.create(id, config);
        } catch (NodeRegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new NodeRegistryException(
                    "노드 생성 실패 — type: " + typeName + ", id: " + id, e);
        }
    }

    /**
     * 해당 타입이 등록되어 있는지 확인한다.
     */
    public boolean isRegistered(String typeName) {
        if (typeName == null || typeName.isBlank()) return false;
        return factories.containsKey(typeName);
    }

    /**
     * 현재 등록된 모든 타입 이름을 반환한다. 수정 불가 뷰.
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    private void validateTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            throw new NodeRegistryException("typeName은 null이거나 빈 문자열일 수 없습니다");
        }
    }
}
