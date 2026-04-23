package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

import java.util.Map;

/**
 * 트리거 메시지를 받을 때마다 지정 범위 내의 랜덤 습도 데이터를 생성하여 전송하는 IoT 센서 시뮬레이터 노드.
 * {@link TimerNode}와 연결하면 주기적인 습도 측정을 시뮬레이션할 수 있다.
 * 출력 메시지 페이로드: {@code {sensorId, humidity, unit, timestamp}}
 */
public class HumiditySensorNode extends AbstractNode {
    private final double min;
    private final double max;

    /**
     * HumiditySensorNode를 생성한다.
     *
     * @param id  노드의 고유 식별자이자 센서 ID ({@code sensorId})로 사용된다.
     * @param min 생성할 습도의 최솟값 (%)
     * @param max 생성할 습도의 최댓값 (%)
     */
    public HumiditySensorNode(String id, double min, double max){
        super(id);
        this.max = max;
        this.min = min;
        addInputPort("trigger");
        addOutputPort("out");
    }

    /**
     * 트리거 메시지를 수신하면 min~max 범위의 랜덤 습도(소수점 1자리)를 생성하여
     * {@code "out"} 포트로 전송한다.
     *
     * @param message 트리거 메시지 (내용은 무시됨)
     */
    @Override
    protected void onProcess(Message message) {
        double humidity = Math.round((min + Math.random() * (max - min)) * 10.0) / 10.0;
        Message newMessage = new Message(Map.of("sensorId", getId(), "humidity", humidity,
                                                    "unit", "%", "timestamp", System.currentTimeMillis()));
    send("out", newMessage);
    }
}
