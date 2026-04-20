package com.fbp.engine.node.stage1;

import com.fbp.engine.core.AbstractNode;
import com.fbp.engine.message.Message;

public class AlertNode extends AbstractNode {

    public AlertNode(String id){
        super(id);
        addInputPort("in");
    }

    //temperature, humidity
    @Override
    protected void onProcess(Message message) {
        if (!message.hasKey("sensorId")){
            System.out.println("[경고] 알 수 없는 센서");
            return;
        }
        String sensorId = message.get("sensorId");

        if (message.hasKey("temperature")) {
            double temperature = ((Number) message.get("temperature")).doubleValue();
            System.out.println("[경고] 센서 " + sensorId + " 온도 " + temperature + "°C - 임계값 초과!" );
        } else if (message.hasKey("humidity")) {
            double temperature = ((Number) message.get("humidity")).doubleValue();
            System.out.println("[경고] 센서 " + sensorId + " 습도 " + temperature + "% - 임계값 초과!" );
        } else {
            System.out.println("[경고] 알 수 없는 센서 데이터");
        }


    }
}
