package com.fbp.engine.registry;

import com.fbp.engine.core.Node;

import java.util.Map;

/**
 * 노드 인스턴스를 생성하는 팩토리 함수형 인터페이스.
 *<pre>
 * 람다로 등록할 수 있다.
 *   registry.register("PrintNode", (id, config) -> new PrintNode(id, config));
 *   </pre>
 */
@FunctionalInterface
public interface NodeFactory {
    /**
     * 노드 인스턴스를 생성하여 반환한다.
     *
     * @param id     노드 고유 식별자 (플로우 정의의 node.id)
     * @param config 노드 설정 맵 (플로우 정의의 node.config)
     * @return 생성된 Node 인스턴스
     */
    Node create(String id, Map<String, Object> config);
}
