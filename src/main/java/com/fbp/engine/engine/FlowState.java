package com.fbp.engine.engine;

public enum FlowState {
    DEPLOYED,  // 배포됨, 아직 시작하지 않음
    RUNNING,   // 실행 중
    STOPPED    // 정지됨
}