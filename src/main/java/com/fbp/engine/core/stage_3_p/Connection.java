package com.fbp.engine.core.stage_3_p;

import com.fbp.engine.message.Message;

/**
 * 두 노드 사이의 메시지 전달 채널 계약.
 *<pre>
 * 구현체는 전송 방식(BlockingQueue, MQTT 등)을 캡슐화한다.
 * 노드는 이 인터페이스만 알고, 실제 전송 방식은 모른다.
 *
 * deliver() → 생산자(OutputPort) 측이 호출
 * poll()    → 소비자(노드 스레드 루프) 측이 호출
 *
 *</pre>

 */
public interface Connection {

    /**
     * 메시지를 채널에 전달한다.
     * 구현에 따라 큐에 적재하거나 MQTT로 발행한다.
     * 채널이 꽉 찬 경우 블로킹하거나 드롭할 수 있다(구현체 정책).
     */
    void deliver(Message message);

    /**
     * 채널에서 메시지를 꺼낸다. 메시지가 없으면 도착할 때까지 블로킹한다.
     *
     * @throws InterruptedException 대기 중 스레드가 인터럽트되면 발생
     */
    Message poll() throws InterruptedException;

    /** 현재 내부 버퍼에 대기 중인 메시지 수를 반환한다. */
    int getBufferSize();

    /** 이 연결의 고유 식별자를 반환한다. */
    String getId();

    /**
     * 채널을 닫고 리소스를 해제한다.
     * MQTT 구현체는 구독 해지 및 클라이언트 해제를 수행한다.
     */
    void close();
}
