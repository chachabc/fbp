package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 임계값을 초과한 센서 데이터에 대한 경고 메시지를 콘솔에 출력하는 종단 노드.
 * 온도({@code temperature})와 습도({@code humidity}) 두 가지 센서 유형을 지원한다.
 * OutputPort가 없으며, 주로 {@link ThresholdFilterNode}의 {@code "alert"} 포트에 연결하여 사용한다.
 */
public class AlertNode extends AbstractNode {


    private static final Logger log = LoggerFactory.getLogger(AlertNode.class);

    /**
     * AlertNode를 생성한다.
     *
     * @param id 노드의 고유 식별자
     */
    public AlertNode(String id){
        super(id);
        addInputPort("in");
    }


    /**
     * 메시지에서 {@code sensorId}와 센서 값을 꺼내 경고 메시지를 콘솔에 출력한다.
     * {@code temperature} 키가 있으면 온도 경고, {@code humidity} 키가 있으면 습도 경고를 출력한다.
     * {@code sensorId}가 없거나 알 수 없는 형식이면 대체 메시지를 출력한다.
     *
     * @param message 경고 내용을 담은 메시지
     */
    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey("sensorId")){
            log.info("[경고] 알 수 없는 센서");
            return;
        }
        String sensorId = message.get("sensorId");

        if (message.hasKey("temperature")) {
            double temperature = ((Number) message.get("temperature")).doubleValue();
            log.info("[경고] 센서 {} 온도 {}°C - 임계값 초과!", sensorId, temperature);
        } else if (message.hasKey("humidity")) {
            double humidity = ((Number) message.get("humidity")).doubleValue();
            log.info("[경고] 센서 {} 습도 {}% - 임계값 초과!", sensorId, humidity);
        } else {
            log.info("[경고] 알 수 없는 센서 데이터");
        }


    }
}
