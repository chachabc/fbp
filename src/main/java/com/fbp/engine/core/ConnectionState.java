package com.fbp.engine.core;

/**
 * 프로토콜 노드의 연결 상태를 나타내는 열거형.
 *
 * <pre>
 * 상태 전이도:
 *
 *   [생성]
 *     │
 *     ▼
 * DISCONNECTED ──initialize()──▶ CONNECTING ──connect() 성공──▶ CONNECTED
 *     ▲                              │                              │
 *     │                     connect() 실패                  연결 끊김
 *     │                              │                     onConnectionLost()
 *     │                              ▼
 *     │                    재시도 스케줄러 시작
 *     │                         (재시도 중)──connect() 성공──▶ CONNECTED
 *     │                              │
 *     │                     최대 재시도 초과
 *     │                              ▼
 *     │                           ERROR
 *     │                              │
 *     └──────────shutdown()──────────┘
 * </pre>
 */
public enum ConnectionState {

    /** 연결되지 않은 초기 상태. 또는 shutdown() 이후 상태. */
    DISCONNECTED,

    /** initialize() 호출 후 연결 시도 중인 상태. */
    CONNECTING,

    /** connect()가 성공하여 외부 시스템과 연결된 상태. */
    CONNECTED,

    /** 최대 재시도 횟수를 초과하여 더 이상 재연결을 시도하지 않는 상태. */
    ERROR
}