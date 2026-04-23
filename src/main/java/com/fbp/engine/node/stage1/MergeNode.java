package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.core.InputPort;
import com.fbp.engine.message.Message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 두 InputPort({@code "in-1"}, {@code "in-2"})로 수신한 메시지를 순서 기반으로 매칭하여
 * 두 페이로드를 합친 새 메시지를 {@code "out"} 포트로 전달하는 노드.
 * <p>
 * in-1의 N번째 메시지와 in-2의 N번째 메시지를 한 쌍으로 묶는다.
 * 한쪽만 먼저 도착하면 반대쪽이 도착할 때까지 내부에 보관(pending)한다.
 * 두 입력이 동시에 다른 스레드에서 도착할 수 있으므로, 매칭 처리는 {@code synchronized}로 보호된다.
 * </p>
 */
public class MergeNode extends AbstractNode {
    private Message pending1;
    private Message pending2;

    /**
     * MergeNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public MergeNode(String id){
        super(id);

        addInputPort("in-1", new InputPort() {
            @Override
            public String getName() {
                return "in-1";
            }

            @Override
            public void receive(Message message) {
                process(message.withEntry("source", "in-1"));
            }
        });

        addInputPort("in-2", new InputPort() {
            @Override
            public String getName() {
                return "in-2";
            }

            @Override
            public void receive(Message message) {
                process(message.withEntry("source", "in-2"));
            }
        });
        addOutputPort("out");
    }

    /**
     * 메시지의 출처 포트를 판별하여 해당 pending 슬롯에 저장한다.
     * 양쪽 pending이 모두 채워지면 두 페이로드를 합친 새 Message를 생성하여
     * {@code "out"} 포트로 전달하고 pending을 초기화한다.
     *
     * @param message 출처 포트 정보({@code "source"} 키)가 포함된 메시지
     */
    @Override
    protected void onProcess(Message message) {
        String source = message.get("source");

        synchronized (this) {
            if ("in-1".equals(source)) {
                pending1 = message.withoutKey("source");
            } else if ("in-2".equals(source)) {
                pending2 = message.withoutKey("source");
            }

            if (pending1 != null && pending2 != null) {
                Map<String, Object> mergePayload = new LinkedHashMap<>();
                mergePayload.putAll(pending1.getPayload());
                mergePayload.putAll(pending2.getPayload());

                Message merged = new Message(mergePayload);
                send("out", merged);

                pending1 = null;
                pending2 = null;
            }
        }
    }
}
