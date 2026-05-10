# FBP IoT Rule Engine — 종합 과제: MQTT 브릿지 기반 분산 FBP 엔진

## 1. 과제 개요

### 배경

지금까지 구현한 FBP 엔진에서 노드 간 메시지 전달은 **JVM 내부의 `BlockingQueue` 기반 `Connection`**으로 이루어졌다. 모든 노드가 같은 프로세스 안에서 동작하므로, 노드 수가 늘어나면 단일 JVM의 메모리와 CPU에 제약을 받는다.

이 과제에서는 노드 간 연결의 **전송 계층(Transport Layer)**을 추상화하여, 기존 `BlockingQueue` 대신 **외부 MQTT 브로커**를 통해 메시지를 주고받는 구조로 확장한다. 이렇게 하면 노드를 서로 다른 프로세스, 서로 다른 머신에 분산 배치할 수 있는 기반이 된다.

또한 운영 관점에서 필요한 **동적 플로우 관리**, **다층적 통계 수집**, **시계열 데이터베이스(InfluxDB) 연동**을 추가하여 실제 운영 환경에서 사용 가능한 수준의 IoT 룰 엔진으로 완성한다.

### 핵심 원칙

```
┌─────────────────────────────────────────────────────────────┐
│  노드는 아무것도 모른다                                       │
│                                                             │
│  노드는 여전히 InputPort에서 메시지를 받고,                     │
│  OutputPort로 메시지를 보낸다.                                │
│  그 메시지가 BlockingQueue를 통해 전달되는지,                   │
│  MQTT 브로커를 경유하는지 노드는 알지 못한다.                    │
│                                                             │
│  전송 방식의 결정은 엔진(FlowManager)이 플로우 설정을 읽어       │
│  Connection 구현체를 선택하는 것으로 이루어진다.                 │
└─────────────────────────────────────────────────────────────┘
```

### 브로커 구분

| 구분 | 용도 | 예시 |
|------|------|------|
| **데이터 브로커** | IoT 센서 데이터 수집용. 2단계에서 구현한 `MqttSubscriberNode`, `MqttPublisherNode`가 연결하는 브로커 | `tcp://data-broker:1883` |
| **시스템 브로커** | FBP 엔진 내부 노드 간 메시지 전달용. Connection의 전송 계층으로 사용 | `tcp://system-broker:1884` |

두 브로커는 **반드시 분리**한다. 데이터 브로커의 트래픽이 엔진 내부 통신에 영향을 주지 않아야 하며, 보안 정책도 다를 수 있다.

---

## 2. 아키텍처

### 기존 구조 (로컬)

```
[NodeA] ──OutputPort──→ Connection(BlockingQueue) ──InputPort──→ [NodeB]
                            │
                      동일 JVM, 동일 스레드 풀
```

### 목표 구조 (MQTT 브릿지)

```
[NodeA] ──OutputPort──→ MqttBridgeConnection ──→ System Broker ──→ MqttBridgeConnection ──InputPort──→ [NodeB]
                            │                    (tcp://system    │
                            │  publish to          -broker:1884)  │  subscribe from
                            │  topic: fbp/flow-1/                 │  topic: fbp/flow-1/
                            │         nodeA.out→nodeB.in          │         nodeA.out→nodeB.in
                            │                                     │
                       동일 또는 다른 JVM                      동일 또는 다른 JVM
```

### Connection 추상화

```
                        «interface»
                        Connection
                     ┌──────────────┐
                     │ deliver(msg) │
                     │ poll(): msg  │
                     │ getBufferSize│
                     └──────┬───────┘
                            │
              ┌─────────────┼─────────────┐
              │                           │
   LocalConnection              MqttBridgeConnection
   (BlockingQueue 기반)          (MQTT Pub/Sub 기반)
   - 기존 구현 그대로             - deliver() → MQTT publish
                                 - poll() → MQTT subscribe → 내부 큐
```

노드 입장에서는 `Connection` 인터페이스만 사용하므로, 어떤 구현체가 주입되었는지 알 수 없다. **Strategy 패턴**의 전형적인 적용이다.

### 플로우 설정을 통한 전송 계층 선택

```json
{
  "id": "temperature-monitoring",
  "transport": {
    "type": "mqtt",
    "broker": "tcp://system-broker:1884",
    "qos": 1
  },
  "nodes": [
    {"id": "sensor", "type": "MqttSubscriber", "config": {"broker": "tcp://data-broker:1883", "topic": "sensor/temp"}},
    {"id": "rule",   "type": "ThresholdFilter", "config": {"field": "value", "threshold": 30}},
    {"id": "alert",  "type": "MqttPublisher",   "config": {"broker": "tcp://data-broker:1883", "topic": "alert/temp"}}
  ],
  "connections": [
    {"from": "sensor:out", "to": "rule:in"},
    {"from": "rule:out",   "to": "alert:in"}
  ]
}
```

- `transport` 섹션이 **없으면** → 기존 `LocalConnection`(BlockingQueue) 사용
- `transport.type`이 `"mqtt"`이면 → `MqttBridgeConnection`으로 모든 연결 구성
- 연결 단위로 override 가능: 특정 연결만 local, 나머지는 mqtt (선택 구현)

### 토픽 네이밍 규칙

시스템 브로커에서 노드 간 메시지를 구분하기 위한 토픽 규칙:

```
fbp/{flow-id}/{sourceNodeId}.{sourcePort}→{targetNodeId}.{targetPort}
```

예시:
```
fbp/temperature-monitoring/sensor.out→rule.in
fbp/temperature-monitoring/rule.out→alert.in
```

### 통합 아키텍처 (전체 그림)

```
┌──────────────────────────────────────────────────────────────────┐
│  CLI / REST API (운영 인터페이스)                                  │
└────────────────┬─────────────────────────────────────────────────┘
                 │ deploy / start / stop / patch / metrics
                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  FlowManager  (동적 플로우 관리)                                   │
│   ├─ 플로우 라이프사이클 (deploy/start/stop/restart/remove)         │
│   ├─ 런타임 변경 (노드 추가/제거, 연결 추가/제거, 설정 변경)         │
│   └─ BridgeConnectionFactory ─→ Local / MqttBridge Connection     │
└────────────────┬─────────────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  실행 중 플로우 (다수)                                             │
│   Node ──Connection──→ Node ──Connection──→ Node                  │
│        │                    │                                    │
│        └─── MetricsCollector(노드/연결/도메인 메트릭 수집) ────────│
└────────────────┬─────────────────────────────────────────────────┘
                 │ 메트릭 이벤트 스트림
                 ▼
┌──────────────────────────────────────────────────────────────────┐
│  InfluxDB Writer (시계열 적재)                                    │
│   measurement: flow_stats, node_stats, wire_stats, sensor_stats   │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 구현 파트

### Part A — Connection 추상화 및 MqttBridgeConnection

#### 목표

1. 기존 `Connection` 클래스를 `Connection` **인터페이스**로 추출
2. 기존 BlockingQueue 구현을 `LocalConnection`으로 분리
3. MQTT 기반 `MqttBridgeConnection` 구현
4. 노드 코드는 **한 줄도 변경하지 않는다**

#### MqttBridgeConnection 핵심 동작

```java
public class MqttBridgeConnection implements Connection {

    private final MqttClient publisher;     // deliver() 시 publish
    private final MqttClient subscriber;    // 수신 메시지를 내부 큐에 적재
    private final BlockingQueue<Message> internalQueue;  // poll()용 내부 버퍼
    private final String topic;
    private final MessageSerializer serializer;

    @Override
    public void deliver(Message message) {
        byte[] payload = serializer.serialize(message);
        publisher.publish(topic, payload, qos);
        // 노드 입장: deliver()만 호출하면 끝. MQTT는 모른다.
    }

    @Override
    public Message poll() throws InterruptedException {
        return internalQueue.take();
        // subscriber 콜백이 internalQueue에 넣어둔 메시지를 꺼냄
        // 노드 입장: poll()만 호출하면 끝. MQTT는 모른다.
    }
}
```

#### 스스로 답해야 할 질문

- `MqttBridgeConnection` 하나당 MqttClient를 두 개(pub용, sub용) 만들 것인가, 하나로 공유할 것인가?
- 하나의 플로우에 연결이 10개면 MqttClient가 20개 생기는 문제를 어떻게 해결할 것인가? → 공유 클라이언트 풀?
- `deliver()` 시 QoS 0(최대 1회)과 QoS 1(최소 1회)의 트레이드오프는?
- 시스템 브로커가 다운되면 `deliver()`와 `poll()`은 어떻게 동작해야 하는가?
- `Message`의 직렬화 포맷은? JSON이 가장 범용적이지만, 성능이 중요하면 바이너리(MessagePack 등)도 고려할 것인가?
- 토픽명에 특수문자(`→`)를 쓸 수 있는가? MQTT 사양에서 허용되지 않는 문자는?

---

### Part B — 엔진 통합: FlowManager 확장

#### 목표

1. `FlowManager`가 플로우 정의의 `transport` 섹션을 인식하여 적절한 Connection 구현체를 생성
2. 기존 로컬 플로우와 MQTT 브릿지 플로우가 **동일한 엔진**에서 공존
3. 플로우 생명주기(deploy/start/stop/remove) 시 브릿지 연결의 MQTT 세션도 관리

#### 변경 사항

```
기존 FlowManager.deploy(FlowDefinition)
  └── NodeRegistry로 노드 생성
  └── Connection(BlockingQueue)으로 연결     ← 여기만 변경
  └── Flow 객체 생성 → FlowEngine 등록

변경 후 FlowManager.deploy(FlowDefinition)
  └── NodeRegistry로 노드 생성
  └── BridgeConnectionFactory로 Connection 생성  ← transport에 따라 Local 또는 Bridge
  └── Flow 객체 생성 → FlowEngine 등록
```

#### 스스로 답해야 할 질문

- 하나의 플로우 안에서 일부 연결은 Local, 일부는 MQTT Bridge로 섞어 쓸 수 있는가?
- 브릿지 플로우를 stop할 때 MQTT 구독을 해지해야 하는가? 재시작 시 다시 구독?
- 플로우 remove 시 시스템 브로커에 남아있는 retained 메시지는 어떻게 처리할 것인가?
- 두 개의 플로우가 같은 시스템 브로커를 공유할 때, 토픽 네임스페이스 충돌은 없는가?

---

### Part C — 동적 플로우 관리

#### 목표

운영 중 엔진을 멈추지 않고 플로우 토폴로지를 변경할 수 있어야 한다. 센서가 추가되거나 룰이 바뀌었을 때, 전체 시스템을 재시작하지 않고 부분적으로 적용한다.

1. **런타임 노드 조작**: 실행 중인 플로우에 노드 추가/제거/설정 변경
2. **런타임 연결 조작**: 실행 중인 플로우에 연결 추가/제거
3. **플로우 핫 패치**: 새 플로우 정의 JSON을 받아 차이점만 적용 (전체 재배포 불필요)
4. **변경 이력 관리**: 누가 언제 무엇을 바꿨는지 추적, 롤백 가능
5. **상태 보존**: 영향 없는 노드는 메시지 처리를 중단하지 않는다

#### 동적 변경 시나리오

| 시나리오 | 동작 | 영향 범위 |
|---------|------|----------|
| 노드 추가 | 새 노드 인스턴스 생성, 입력 연결까지 추가되면 메시지 수신 시작 | 신규 노드만 |
| 노드 제거 | 입력 연결 차단 → in-flight 메시지 처리 완료 → 노드 종료 | 제거 노드 + 직접 연결된 노드 |
| 노드 설정 변경 | 가능하면 hot-reload, 불가능하면 stop/replace/start | 해당 노드만 |
| 연결 추가 | Connection 객체 생성, 양쪽 노드의 포트에 등록 | 두 노드 간 |
| 연결 제거 | 큐 drain → Connection close | 두 노드 간 |
| 플로우 패치 | 정의 diff 계산 → 위 단위 작업으로 분해하여 순차 실행 | 변경 부분만 |

#### 동적 변경 API (예시)

```
flow patch <flow-id> <patch-file.json>
flow add-node <flow-id> <node-spec.json>
flow remove-node <flow-id> <node-id>
flow add-wire <flow-id> <from> <to>
flow remove-wire <flow-id> <wire-id>
flow update-config <flow-id> <node-id> <config.json>
flow history <flow-id>           # 변경 이력
flow rollback <flow-id> <revision>
```

#### 스스로 답해야 할 질문

- 노드를 제거할 때 입력 큐에 남은 메시지는 처리하고 종료할 것인가, 즉시 폐기할 것인가?
- 설정 변경 중에 들어오는 메시지는 어떻게 처리할 것인가? (잠깐 큐에 적재, 또는 일시 중단)
- 변경 도중 실패하면 부분 적용된 상태를 어떻게 롤백할 것인가? (트랜잭션 경계)
- 변경 이력 저장은 어디에? (메모리, 파일, InfluxDB의 events measurement?)
- 동시에 여러 사용자가 같은 플로우를 패치하려 할 때 충돌을 어떻게 해결할 것인가? (낙관적 락, 비관적 락, 버전 번호?)

---

### Part D — 통계 수집

#### 목표

엔진은 세 계층의 메트릭을 실시간으로 수집한다.

1. **시스템/플로우 계층**: 엔진 전체 처리량, 활성 플로우 수, 에러율
2. **노드 계층**: 노드별 입력/출력 메시지 수, 처리 시간, 데이터량(byte), 에러 수
3. **연결(Wire) 계층**: 연결별 전달 건수, 큐 적체량, 드롭 수, 토픽별 트래픽
4. **도메인(센서) 계층**: 메시지 페이로드의 의미를 해석한 통계 — 온도/습도 등의 시간별·일별 평균/최대/최소

#### 메트릭 항목 상세

| 계층 | 지표 | 단위 | 비고 |
|------|------|------|------|
| 플로우 | 처리 메시지 수 | count | 입력 노드로 진입한 총 메시지 |
| 플로우 | 처리량 | msg/sec | 슬라이딩 윈도우 |
| 플로우 | 평균 종단 지연 | ms | 입력 → 출력까지 |
| 플로우 | 에러율 | % | errors / total |
| 노드 | 입력 메시지 수 | count | 포트별 |
| 노드 | 출력 메시지 수 | count | 포트별 |
| 노드 | 입력 데이터량 | bytes | 직렬화 크기 합 |
| 노드 | 출력 데이터량 | bytes | 직렬화 크기 합 |
| 노드 | 평균 처리 시간 | ms | onMessage 시간 |
| 노드 | 99p 처리 시간 | ms | 백분위수 |
| 노드 | 에러 수 | count | 처리 중 예외 |
| 연결 | 전달 메시지 수 | count | deliver 호출 수 |
| 연결 | 큐 적체량 | count | poll 대기 수 |
| 연결 | 드롭 수 | count | 큐 풀일 때 |
| 연결 | MQTT 왕복 지연 | ms | 브릿지일 경우 |
| 도메인 | 센서값 | numeric | 원천 측정값 |
| 도메인 | 시간별 평균/최대/최소 | numeric | 1분/1시간 버킷 |
| 도메인 | 일별 평균/최대/최소 | numeric | 1일 버킷 |
| 도메인 | 임계값 초과 횟수 | count | 룰 hit 카운트 |

#### 수집 메커니즘

- 노드 실행기(`NodeRunner`)가 `onMessage` 전후에 시간/카운트를 기록
- `Connection` 구현체가 `deliver`/`poll`마다 카운트
- `MetricsCollector`가 비동기 큐로 이벤트를 받아 집계
- 도메인 통계는 별도 `DomainMetricsExtractor`가 `Message.payload`에서 필드를 추출 — 어떤 필드를 추출할지는 플로우 정의 또는 노드 메타데이터에서 선언

#### 도메인 통계 추출 정의 (예시)

```json
{
  "metrics": {
    "domain": [
      {
        "name": "temperature",
        "source": {"node": "sensor", "port": "out"},
        "field": "value",
        "tags": {"location": "field:location", "unit": "celsius"},
        "windows": ["1m", "1h", "1d"]
      },
      {
        "name": "humidity",
        "source": {"node": "humidity-sensor", "port": "out"},
        "field": "value",
        "windows": ["1m", "1h", "1d"]
      }
    ]
  }
}
```

엔진은 이 정의를 읽어 해당 노드의 출력 메시지를 가로채 필드 값을 추출하고 시간 윈도우별로 통계를 계산한다.

#### 실시간 통계 수집 흐름 (예상도)

전체 데이터 흐름은 **Hot Path**(메시지 처리 경로, 동기)와 **Cold Path**(메트릭 집계·적재 경로, 비동기)로 분리된다. Hot Path는 절대 블로킹되지 않으며, 메트릭 이벤트는 lock-free 큐에 던져지고 즉시 반환한다.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                       HOT PATH (메시지 처리 — 동기)                          │
│                                                                            │
│   ┌──────┐  msg  ┌────────────┐  msg  ┌──────┐  msg  ┌────────────┐  msg  │
│   │ NodeA├──────►│ Connection ├──────►│ NodeB├──────►│ Connection ├─────► │
│   └──┬───┘       └─────┬──────┘       └──┬───┘       └─────┬──────┘       │
│      │①②③             │④⑤              │①②③             │④⑤            │
└──────┼─────────────────┼─────────────────┼─────────────────┼──────────────┘
       │                 │                 │                 │
       │   ① in_count, in_bytes  (포트 수신)                  │
       │   ② proc_time           (onMessage 시간)             │
       │   ③ out_count, out_bytes (포트 송신)                 │
       │   ④ delivered, bytes    (Connection.deliver)         │
       │   ⑤ queue_size, dropped (Connection 내부 상태)       │
       │                                                      │
       └──── 비동기 이벤트 큐 (lock-free, MPSC, drop on overflow) ───┐
                                                                     │
┌────────────────────────────────────────────────────────────────────▼─────┐
│                  COLD PATH (집계·적재 — 비동기 전용 스레드)                │
│                                                                          │
│   ┌─────────────────────────────────────────────────────────────────┐    │
│   │                    MetricsCollector                              │    │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │    │
│   │  │ NodeAggreg.  │  │ WireAggreg.  │  │ FlowAggreg.  │           │    │
│   │  │ LongAdder    │  │ LongAdder    │  │ LongAdder    │           │    │
│   │  │ HdrHistogram │  │ queue gauge  │  │ throughput   │           │    │
│   │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │    │
│   │         │                 │                 │                   │    │
│   │         └─────────────────┼─────────────────┘                   │    │
│   │                           ▼                                     │    │
│   │             ┌─────────────────────────────┐                     │    │
│   │             │  DomainMetricsExtractor     │                     │    │
│   │             │  payload[field] → numeric   │                     │    │
│   │             │  (정의된 노드/포트만)       │                     │    │
│   │             └─────────────┬───────────────┘                     │    │
│   │                           ▼                                     │    │
│   │      ┌───────────────────────────────────────────┐              │    │
│   │      │   Time-Window Bucketer (텀블링)            │              │    │
│   │      │   ┌────────┐  ┌────────┐  ┌────────┐      │              │    │
│   │      │   │ 1m bkt │  │ 1h bkt │  │ 1d bkt │      │              │    │
│   │      │   │ avg    │  │ avg    │  │ avg    │      │              │    │
│   │      │   │ min    │  │ min    │  │ min    │      │              │    │
│   │      │   │ max    │  │ max    │  │ max    │      │              │    │
│   │      │   │ count  │  │ count  │  │ count  │      │              │    │
│   │      │   └───┬────┘  └───┬────┘  └───┬────┘      │              │    │
│   │      └──────┼───────────┼───────────┼────────────┘              │    │
│   │             │           │           │                           │    │
│   │             ▼           ▼           ▼                           │    │
│   │      윈도우 close 시점에 Point 발행 (텀블링 경계)               │    │
│   │                                                                 │    │
│   │   ┌─────────────────────────────────────┐                       │    │
│   │   │  Periodic Tick (10초 스케줄러)      │  ◄── flow/node/wire   │    │
│   │   │  현재 카운터 스냅샷 → Point 발행    │      카운터·게이지     │    │
│   │   └────────────────┬────────────────────┘                       │    │
│   └────────────────────┼─────────────────────────────────────────────┘    │
│                        │                                                  │
│                        ▼ Point 스트림 (measurement, tags, fields, ts)    │
│   ┌──────────────────────────────────────────────┐                       │
│   │  InfluxDB Batch Writer                       │                       │
│   │   ├ 배치 큐 (max 1000 / 1초 flush)           │                       │
│   │   ├ HTTP write (gzip)                        │                       │
│   │   ├ 실패 시 지수 backoff 재시도              │                       │
│   │   └ 영속 실패 시 로컬 buffer (메모리/파일)   │                       │
│   └────────────────┬─────────────────────────────┘                       │
└────────────────────┼─────────────────────────────────────────────────────┘
                     ▼
       ┌───────────────────────────────────┐
       │           InfluxDB                │
       │  engine_stats   flow_stats        │
       │  node_stats     wire_stats        │
       │  sensor_raw     sensor_stats_1m   │
       │  sensor_stats_1h sensor_stats_1d  │
       │  flow_events                      │
       └─────┬───────────────────────┬─────┘
             │ Flux/InfluxQL          │ Flux/InfluxQL
             ▼                        ▼
   ┌────────────────────┐   ┌────────────────────┐
   │  CLI 조회           │   │  Grafana 대시보드   │
   │  sensor stats       │   │  실시간 차트       │
   │  node stats         │   │  알람 룰           │
   │  influx status      │   │  장기 추이 분석    │
   └────────────────────┘   └────────────────────┘
```

흐름의 핵심:

| 단계 | 역할 | 주체 / 빈도 |
|------|------|-----------|
| **① Tap** | 노드/Connection이 메시지 처리 시 카운터 증가, 시간 측정 | Hot Path / 메시지마다 |
| **② Enqueue** | 메트릭 이벤트를 lock-free 큐에 push | Hot Path / 메시지마다 |
| **③ Aggregate** | 큐에서 꺼내 카운터/히스토그램에 누적 | Cold Path / 전용 스레드 |
| **④ Extract** | 도메인 정의에 매칭되는 메시지에서 필드 추출 | Cold Path / 매칭 메시지마다 |
| **⑤ Bucket** | 시간 윈도우별 평균/최대/최소/카운트 누적 | Cold Path / 추출 시마다 |
| **⑥ Tick** | 10초 주기로 카운터·게이지 스냅샷을 Point로 발행 | 스케줄러 / 10초 |
| **⑦ Window Close** | 1m/1h/1d 경계 시 윈도우 통계를 Point로 발행 | 스케줄러 / 1m·1h·1d |
| **⑧ Batch** | Point들을 모아 1초 또는 1000건마다 InfluxDB에 일괄 전송 | Writer 스레드 |
| **⑨ Persist** | InfluxDB가 measurement에 저장 | InfluxDB |
| **⑩ Query** | CLI/Grafana가 Flux로 조회 | 사용자 요청 시 |

수집 주기 요약:

```
시간 ▶  0s    10s   20s   30s   40s   50s   60s   ...   3600s   ...   86400s
        │     │     │     │     │     │     │           │           │
flow/   ●     ●     ●     ●     ●     ●     ●           ●           ●     (10초)
node/   ●     ●     ●     ●     ●     ●     ●           ●           ●
wire    │     │     │     │     │     │     │           │           │
        │                                   │           │           │
1m bkt  └──────────── flush ────────────────┘──flush──→ │           │
                                                                    │
1h bkt  └──────────────────── flush ────────────────────────────────┤
                                                                    │
1d bkt  └────────────────────────────────────────────────────── flush
```

성능 보호 장치:

- 큐가 가득 차면 메트릭 이벤트는 **드롭**(메시지 처리는 절대 블로킹하지 않음). 드롭 자체도 카운터로 기록되어 가시성 확보
- 카운터는 `LongAdder`로 contention 분산, 게이지는 `AtomicLong`
- 백분위수는 `HdrHistogram`으로 메모리 한정 + O(1) 기록
- InfluxDB writer는 메인 처리 스레드와 완전히 분리된 단일 워커
- 로컬 버퍼가 가득 차면 **오래된 raw부터** 폐기 (단, `flow_events`와 윈도우 집계 결과는 우선 보존)

#### 스스로 답해야 할 질문

- 메트릭 수집 자체가 처리 성능에 영향을 주지 않게 하려면 어떤 자료구조를 쓸 것인가? (LongAdder, 링버퍼, 비동기 큐)
- 시간 윈도우 집계는 슬라이딩 윈도우인가, 텀블링 윈도우인가?
- 백분위수(99p)는 정확값을 계산할 것인가, HdrHistogram 등 근사 알고리즘을 쓸 것인가?
- 도메인 통계 추출 정의가 없는 노드는 통계를 만들지 않는 것이 맞는가, 모든 메시지에 대해 자동 감지를 시도할 것인가?
- 메트릭 누락(엔진 재시작 등)을 어떻게 다룰 것인가?

---

### Part E — InfluxDB 연동

#### 목표

수집된 모든 통계를 시계열 데이터베이스 InfluxDB에 적재한다. 외부 도구(Grafana 등)에서 실시간 대시보드를 만들거나 장기 보관 데이터를 분석할 수 있다.

1. 메트릭 이벤트를 InfluxDB **line protocol**로 변환
2. 배치 쓰기로 처리량 확보 (개별 쓰기 X)
3. InfluxDB 일시 장애 시 로컬 버퍼링 → 복구 시 재전송
4. measurement / tag / field 스키마 표준화

#### Measurement 스키마

| measurement | tags | fields | 주기 |
|-------------|------|--------|------|
| `engine_stats` | host | active_flows, total_nodes, throughput, errors, heap_used | 10초 |
| `flow_stats` | flow_id, transport | processed, errors, throughput, avg_latency_ms | 10초 |
| `node_stats` | flow_id, node_id, node_type | in_count, out_count, in_bytes, out_bytes, avg_time_ms, p99_time_ms, errors | 10초 |
| `wire_stats` | flow_id, wire_id, transport, topic | delivered, queue_size, dropped, mqtt_rtt_ms | 10초 |
| `sensor_raw` | flow_id, node_id, sensor_name, location | value | 메시지 단위 |
| `sensor_stats_1m` | sensor_name, location | avg, min, max, count | 1분 |
| `sensor_stats_1h` | sensor_name, location | avg, min, max, count | 1시간 |
| `sensor_stats_1d` | sensor_name, location | avg, min, max, count | 1일 |
| `flow_events` | flow_id, event_type, user | revision, change_summary | 이벤트 발생 시 |

#### InfluxDB 설정 (플로우 또는 엔진 레벨)

```yaml
influxdb:
  url: "http://localhost:8086"
  token: "${INFLUX_TOKEN}"
  org: "fbp"
  bucket: "fbp-metrics"
  batch:
    size: 1000
    flush_interval_ms: 1000
  retry:
    max_attempts: 5
    initial_backoff_ms: 200
  buffer:
    type: "memory"      # memory | file
    max_size: 100000    # 메시지 수
```

#### Line Protocol 예시

```
flow_stats,flow_id=temperature-monitoring,transport=mqtt processed=45231i,errors=3i,throughput=125.5,avg_latency_ms=2.3 1717401782000000000

node_stats,flow_id=temperature-monitoring,node_id=rule,node_type=ThresholdFilter in_count=45231i,out_count=12847i,in_bytes=2345678i,out_bytes=678901i,avg_time_ms=0.3,p99_time_ms=4.2,errors=0i 1717401782000000000

sensor_raw,flow_id=temperature-monitoring,node_id=sensor,sensor_name=temperature,location=room-1 value=31.2 1717401782345000000

sensor_stats_1h,sensor_name=temperature,location=room-1 avg=24.7,min=18.3,max=31.5,count=3600i 1717401780000000000
```

#### 데이터 보존 정책 (제안)

| 데이터 | 보존 기간 | 다운샘플링 |
|--------|-----------|----------|
| `*_raw`, `node_stats`, `wire_stats` | 7일 | — |
| `sensor_stats_1m` | 30일 | 1m → 1h |
| `sensor_stats_1h` | 1년 | 1h → 1d |
| `sensor_stats_1d` | 영구 | — |
| `flow_events` | 영구 | — |

#### 스스로 답해야 할 질문

- 메트릭 수집 → InfluxDB 적재 사이를 동기로 할 것인가, 비동기 큐로 분리할 것인가?
- 배치 사이즈를 어떻게 결정할 것인가? 너무 크면 지연, 너무 작으면 비효율
- InfluxDB 장애 시 로컬 버퍼가 가득 차면 어떤 데이터를 먼저 버릴 것인가? (FIFO? 우선순위?)
- 한 메시지마다 `sensor_raw`를 쓰면 카디널리티가 폭발할 수 있다. 어떻게 제한할 것인가? (샘플링, tag 제한)
- InfluxDB v1 / v2 / v3 중 어느 버전을 기준으로 할 것인가? 라이브러리 호환성?
- Grafana 대시보드는 누가 정의하고 어디에 저장할 것인가?

---

### Part F — CLI를 통한 엔진 관리

#### 목표

1. CLI에서 플로우의 전체 생명주기를 관리 (배포, 시작, 정지, 재시작, 삭제)
2. 동적 플로우 변경 명령 지원 (노드/연결 추가·제거, 패치, 롤백)
3. 노드 상태 조회 및 실시간 메시지 모니터링
4. 와이어(Connection) 상태 조회 — 전송 타입(Local/MQTT), 토픽, 큐 상태 포함
5. 엔진 전체 통계 (활성 플로우, 처리량, 에러율) 및 도메인 통계 조회
6. InfluxDB 연동 상태 확인

#### CLI 명령어 전체

| 영역 | 명령어 | 설명 |
|------|--------|------|
| **플로우** | `flow list` | 등록된 플로우 목록 (id, 상태, transport 타입) |
| | `flow deploy <file>` | JSON 파일에서 플로우 배포 |
| | `flow start <id>` | 플로우 시작 |
| | `flow stop <id>` | 플로우 정지 |
| | `flow restart <id>` | 플로우 재시작 |
| | `flow remove <id>` | 플로우 삭제 |
| | `flow status <id>` | 플로우 상태 상세 (노드 수, 연결 수, transport, uptime) |
| | `flow patch <id> <file>` | 정의 변경분 적용 (동적 패치) |
| | `flow add-node <id> <spec>` | 노드 추가 |
| | `flow remove-node <id> <node-id>` | 노드 제거 |
| | `flow add-wire <id> <from> <to>` | 연결 추가 |
| | `flow remove-wire <id> <wire-id>` | 연결 제거 |
| | `flow update-config <id> <node-id> <config>` | 노드 설정 변경 |
| | `flow history <id>` | 변경 이력 조회 |
| | `flow rollback <id> <rev>` | 특정 리비전으로 롤백 |
| **노드** | `node list <flow-id>` | 플로우 내 노드 목록 (id, 타입, 상태) |
| | `node info <node-id>` | 노드 상세 (타입, config, 포트 목록) |
| | `node stats <node-id>` | 노드 통계 (입출력 건수, 데이터량, 에러, 평균 시간) |
| **와이어** | `wire list <flow-id>` | 연결 목록 (id, from→to, transport 타입) |
| | `wire info <wire-id>` | 연결 상세 (transport, 토픽명, 큐 크기, QoS) |
| | `wire stats <wire-id>` | 연결 통계 (전달 건수, 데이터량, 큐 적체량, 드롭 수) |
| **모니터링** | `monitor flow <id>` | 플로우 실시간 메시지 흐름 (tail -f 방식) |
| | `monitor node <id>` | 노드 입출력 메시지 실시간 추적 |
| | `monitor data <id> --filter <expr>` | 조건 필터링 모니터링 |
| **도메인 통계** | `sensor list` | 등록된 도메인 메트릭 목록 |
| | `sensor stats <name> --window 1h` | 센서 시간별 통계 (평균/최대/최소) |
| | `sensor stats <name> --window 1d --range 7d` | 일별 통계, 최근 7일 |
| **시스템** | `stats` | 엔진 전체 통계 (활성 플로우, 노드 수, 처리량, 에러율) |
| | `broker status` | 시스템 브로커 연결 상태 |
| | `influx status` | InfluxDB 연결 상태, 배치 큐 적체량, 누적 적재량 |
| | `help [command]` | 명령어 도움말 |
| | `exit` | CLI 종료 |

#### CLI 출력 예시

```
fbp> flow list
ID                        STATUS   TRANSPORT  NODES  WIRES
temperature-monitoring    RUNNING  mqtt       3      2
humidity-check            RUNNING  local      4      3
data-aggregation          STOPPED  mqtt       5      4

fbp> flow status temperature-monitoring
Flow: temperature-monitoring
  Status:    RUNNING
  Transport: mqtt (tcp://system-broker:1884, QoS=1)
  Nodes:     3
  Wires:     2
  Uptime:    1h 23m 45s
  Processed: 45,231 messages
  Errors:    3 (0.007%)
  Revision:  7 (last patched 2025-05-04 13:21 by admin)

fbp> flow patch temperature-monitoring patches/add-pressure-rule.json
Calculating diff... (+1 node, +2 wires, ~0 nodes, -0)
Applying:
  + node 'pressure-rule' (ThresholdFilter)
  + wire sensor:out → pressure-rule:in
  + wire pressure-rule:out → alert:in
Patch applied. Revision: 8

fbp> wire list temperature-monitoring
ID    FROM            TO            TRANSPORT  TOPIC                                          QUEUE
w-1   sensor:out  →   rule:in       mqtt       fbp/temperature-monitoring/sensor.out-rule.in   2
w-2   rule:out    →   alert:in      mqtt       fbp/temperature-monitoring/rule.out-alert.in    0

fbp> wire info w-1
Wire: w-1
  From:       sensor:out
  To:         rule:in
  Transport:  MqttBridge
  Broker:     tcp://system-broker:1884
  Topic:      fbp/temperature-monitoring/sensor.out-rule.in
  QoS:        1
  Queue Size: 2 / 100
  Delivered:  45,231 (12.3 MB)
  Dropped:    0

fbp> node stats rule
Node: rule (ThresholdFilter)
  Status:     RUNNING
  In:         45,231 msg / 11.2 MB
  Out:        12,847 msg / 3.4 MB
  Filtered:   32,384 (71.6%)
  Errors:     0
  Avg Time:   0.3 ms
  P99 Time:   4.2 ms

fbp> sensor stats temperature --window 1h --range 24h
Sensor: temperature (location=room-1)
  Window: 1h, Range: last 24h
  TIME              AVG    MIN    MAX    COUNT
  2025-05-03 14:00  23.4   21.1   25.6   3,600
  2025-05-03 15:00  24.1   22.0   26.2   3,600
  ...
  2025-05-04 13:00  24.7   18.3   31.5   3,600

fbp> sensor stats humidity --window 1d --range 7d
Sensor: humidity
  Window: 1d, Range: last 7d
  DATE        AVG    MIN    MAX    COUNT
  2025-04-28  56.3   42.1   71.8   86,400
  2025-04-29  58.7   45.2   73.1   86,400
  ...

fbp> influx status
InfluxDB: http://localhost:8086 (bucket=fbp-metrics)
  Status:        CONNECTED
  Batch Queue:   23 / 100,000
  Last Flush:    127ms ago (998 points written)
  Total Written: 12,847,231 points
  Last Error:    -

fbp> broker status
System Broker: tcp://system-broker:1884
  Status:       CONNECTED
  Client ID:    fbp-engine-01
  Active Topics: 4
  Messages/sec:  1,247 (in) / 1,245 (out)

fbp> stats
╔══════════════════════════════════════════╗
║         FBP Engine Statistics            ║
╠══════════════════════════════════════════╣
║ Status:        RUNNING                   ║
║ Uptime:        2h 15m 30s               ║
║ Active Flows:  2 (local: 1, mqtt: 1)    ║
║ Stopped Flows: 1                        ║
║ Total Nodes:   12                       ║
║ Total Wires:   9 (local: 3, mqtt: 6)   ║
║ Throughput:    1,247 msg/s              ║
║ Total Errors:  3 (0.007%)              ║
║ Heap Used:     128 MB / 512 MB         ║
║ Active Threads: 18                      ║
║ Influx:        OK (12.8M pts)          ║
╚══════════════════════════════════════════╝
```

#### 스스로 답해야 할 질문

- CLI 입력 처리는 메인 스레드에서, 모니터링 출력은 별도 스레드에서 해야 하는가?
- `monitor` 명령 실행 중 다른 명령을 어떻게 입력받을 것인가? (Ctrl+C로 중단?)
- CLI가 FlowManager에 직접 접근할 것인가, REST API를 통해 간접 접근할 것인가?
- 도메인 통계 조회는 InfluxDB를 직접 쿼리할 것인가, 엔진 내부 캐시를 쓸 것인가?
- 명령어 자동완성을 구현한다면 flow id, node id, sensor name 목록을 어떻게 동적으로 제공할 것인가?
- 출력 포맷을 사람이 읽기 좋은 형태와 기계가 파싱하기 좋은 형태(JSON) 중 어떤 것으로 할 것인가? 둘 다?

---

### Part G — 통합 시나리오

#### 목표

1. 로컬/브릿지 혼합 환경에서 전체 시스템 정상 동작 확인
2. 동적 플로우 변경이 무중단으로 적용됨을 확인
3. 모든 통계가 InfluxDB에 누적되며 Grafana 등에서 조회 가능함을 확인
4. 브로커/InfluxDB 장애 시 복구 동작 확인
5. CLI를 통한 전체 운영 시나리오 시연

#### 통합 시나리오 구성

```
시나리오: 스마트 빌딩 환경 모니터링

[데이터 브로커: tcp://data-broker:1883]
     │
     ▼
Flow-1 (MQTT 브릿지, 시스템 브로커: tcp://system-broker:1884)
┌─────────────────────────────────────────────────────────────┐
│  MqttSubscriber ──(bridge)──→ DynamicRouter                 │
│       (data-broker)               │                         │
│                    ┌──────────────┼──────────────┐          │
│                    ▼              ▼              ▼          │
│              TempRule       HumidityRule    PressureRule     │
│                    │              │              │          │
│                    ▼              ▼              ▼          │
│              ModbusWriter   MqttPublisher   AlertNode       │
│              (Socket 기반)   (data-broker)                   │
└─────────────────────────────────────────────────────────────┘
                  │ 모든 노드/연결 메트릭
                  │ 온도/습도/압력 도메인 메트릭
                  ▼
            ┌───────────────────┐
            │   InfluxDB        │
            │  (시계열 적재)    │
            └───────────────────┘

Flow-2 (로컬, BlockingQueue)
┌─────────────────────────────────────────────────────────────┐
│  TimerNode ──(local)──→ HealthChecker ──(local)──→ Logger   │
└─────────────────────────────────────────────────────────────┘
```

#### CLI 운영 시나리오 (시연 스크립트)

```
# 1. 엔진 상태 확인
fbp> stats
fbp> influx status

# 2. 브릿지 플로우 배포
fbp> flow deploy config/smart-building.json
Flow 'smart-building' deployed successfully.

# 3. 로컬 플로우 배포
fbp> flow deploy config/health-check.json
Flow 'health-check' deployed successfully.

# 4. 전체 플로우 시작
fbp> flow start smart-building
fbp> flow start health-check

# 5. 현재 상태 확인
fbp> flow list
fbp> wire list smart-building

# 6. 실시간 모니터링
fbp> monitor node temp-rule
(... 실시간 메시지 확인 ...)
^C

# 7. 도메인 통계 확인
fbp> sensor stats temperature --window 1h --range 6h
fbp> sensor stats humidity --window 1d --range 7d

# 8. 동적 변경 — 압력 룰 추가
fbp> flow patch smart-building patches/add-pressure-rule.json
fbp> flow history smart-building

# 9. 통계 확인
fbp> node stats temp-rule
fbp> wire stats w-1
fbp> stats

# 10. 브로커/Influx 상태
fbp> broker status
fbp> influx status

# 11. 플로우 제어
fbp> flow stop smart-building
fbp> flow status smart-building
fbp> flow restart smart-building

# 12. 정리
fbp> flow remove health-check
fbp> flow remove smart-building
fbp> exit
```

---

### Part H — 도전 과제: 다중 엔진 분산 플로우 (Cross-Engine Flow)

#### 배경

지금까지의 MQTT 브릿지는 **하나의 엔진 안에서** 노드 간 메시지를 시스템 브로커로 보내는 구조였다. 하지만 시스템 브로커는 본질적으로 외부에 있으므로, **다른 프로세스 / 다른 머신의 엔진**도 같은 토픽을 구독할 수 있다.

이 도전 과제에서는 **2개 이상의 FBP 엔진 인스턴스**를 동시에 실행하고, 시스템 브로커를 통해 한 엔진의 노드 출력이 다른 엔진의 노드 입력으로 자연스럽게 연결되는 분산 플로우를 구성한다. 노드 코드는 여전히 변경하지 않는다 — 노드는 자신이 단일 엔진에 속하는지, 분산된 한 조각인지 알지 못한다.

#### 목표

1. 동일 호스트(다른 포트) 또는 다른 호스트에서 엔진 2대를 독립적으로 실행
2. 엔진별로 자신의 플로우 조각(segment)만 배포 — 같은 시스템 브로커 공유
3. 한 엔진의 출력 노드가 발행한 메시지를 다른 엔진의 입력 노드가 동일한 토픽에서 수신
4. 분산 환경에서도 **동일한 노드/연결/도메인 통계**가 InfluxDB에 누적
5. 한 엔진이 다운되어도 다른 엔진은 계속 동작, 복구 시 메시지 흐름 자동 재개

#### 분산 구조

```
                  ┌────────────────────────────────────────┐
                  │  System Broker (tcp://system-broker:1884) │
                  │                                        │
                  │   Topic: fbp/distributed-flow/         │
                  │          collector.out-classifier.in   │
                  │                                        │
                  │   Topic: fbp/distributed-flow/         │
                  │          classifier.out-storage.in     │
                  └────────┬───────────────────────┬───────┘
                           │ pub                   │ sub
                           │                       │
            ┌──────────────▼──────┐   ┌────────────▼───────────┐
            │   Engine A (host-1) │   │   Engine B (host-2)    │
            │                     │   │                        │
            │  ┌─────────────┐    │   │   ┌──────────────┐     │
            │  │MqttSubscriber│   │   │   │  Classifier  │     │
            │  │(data-broker) │   │   │   │ (Threshold + │     │
            │  └──────┬──────┘    │   │   │   Router)    │     │
            │         │           │   │   └──────┬───────┘     │
            │         ▼           │   │          ▼             │
            │  ┌─────────────┐    │   │   ┌──────────────┐     │
            │  │  Collector  │    │   │   │   Storage    │     │
            │  │  (Buffer +  │    │   │   │  (InfluxDB   │     │
            │  │   Batch)    │    │   │   │   Writer)    │     │
            │  └──────┬──────┘    │   │   └──────────────┘     │
            │         │ out       │   │                        │
            │         ▼           │   │                        │
            │   [bridge to Engine B] │   │                        │
            └─────────────────────┘   └────────────────────────┘
                      │                            │
                      │ 각자의 메트릭 (서로 다른 노드들)
                      ▼                            ▼
                ┌──────────────────────────────────────┐
                │              InfluxDB                │
                │  measurement에 engine_id tag 추가     │
                │   tags={engine_id, flow_id, node_id} │
                └──────────────────────────────────────┘
```

#### 플로우 정의 분할 방식

**방식 1: Segment 정의 (각 엔진이 독립 플로우 정의)**

같은 `flow-id`와 토픽 규칙을 공유하되, 엔진은 자기에게 속한 노드와 연결만 정의한다. 엔진 간 경계 노드는 외부와 연결됨을 명시하는 `external` 플래그로 구분한다.

```json
// engine-a/distributed-flow.json
{
  "id": "distributed-flow",
  "engine_id": "engine-a",
  "transport": {"type": "mqtt", "broker": "tcp://system-broker:1884", "qos": 1},
  "nodes": [
    {"id": "subscriber", "type": "MqttSubscriber", "config": {...}},
    {"id": "collector",  "type": "BufferBatch",    "config": {...}}
  ],
  "connections": [
    {"from": "subscriber:out", "to": "collector:in"},
    {"from": "collector:out",  "to": "external", "topic": "fbp/distributed-flow/collector.out-classifier.in"}
  ]
}
```

```json
// engine-b/distributed-flow.json
{
  "id": "distributed-flow",
  "engine_id": "engine-b",
  "transport": {"type": "mqtt", "broker": "tcp://system-broker:1884", "qos": 1},
  "nodes": [
    {"id": "classifier", "type": "ThresholdRouter", "config": {...}},
    {"id": "storage",    "type": "InfluxWriter",   "config": {...}}
  ],
  "connections": [
    {"from": "external", "to": "classifier:in", "topic": "fbp/distributed-flow/collector.out-classifier.in"},
    {"from": "classifier:out", "to": "storage:in"}
  ]
}
```

핵심 규칙:
- 두 엔진의 `flow.id`가 동일해야 토픽 규칙이 일치
- `external` keyword로 엔진 경계를 표시 — 해당 끝은 토픽만 명시되고 노드는 다른 엔진 소유
- 토픽 문자열은 두 정의에서 **정확히 같아야** 한다. `TopicResolver`가 자동 생성할 수도, 수동 지정할 수도 있다.

**방식 2: 마스터 정의 + 엔진 매핑 (선택)**

전체 플로우를 한 파일에 정의하고, 노드별 `engine` 필드로 어느 엔진이 소유할지 표시. 각 엔진은 자기 노드만 활성화하고 경계는 자동으로 외부 토픽으로 처리.

```json
{
  "id": "distributed-flow",
  "engines": {
    "engine-a": "tcp://host-1:1884",
    "engine-b": "tcp://host-2:1884"
  },
  "transport": {"type": "mqtt", "broker": "tcp://system-broker:1884"},
  "nodes": [
    {"id": "subscriber", "engine": "engine-a", "type": "MqttSubscriber", "config": {...}},
    {"id": "collector",  "engine": "engine-a", "type": "BufferBatch",    "config": {...}},
    {"id": "classifier", "engine": "engine-b", "type": "ThresholdRouter","config": {...}},
    {"id": "storage",    "engine": "engine-b", "type": "InfluxWriter",   "config": {...}}
  ],
  "connections": [
    {"from": "subscriber:out", "to": "collector:in"},
    {"from": "collector:out",  "to": "classifier:in"},
    {"from": "classifier:out", "to": "storage:in"}
  ]
}
```

이 경우 엔진은 `--engine-id engine-a`로 자신의 ID를 알리고, 정의를 읽어 본인이 소유한 노드만 활성화. 엔진 경계 연결은 자동으로 MQTT 토픽 브릿지로 변환.

#### 엔진 간 핸드셰이크 (선택 구현)

엔진들이 서로의 존재를 인식하면 더 풍부한 운영이 가능하다.

```
시스템 브로커의 관리 토픽:
  fbp/_engines/announce       — 엔진이 시작 시 자기 정보 publish (retained)
  fbp/_engines/heartbeat      — 주기적 헬스 신호
  fbp/_engines/{engine-id}/cmd — 특정 엔진에 명령 전달
  fbp/_flows/{flow-id}/topology — 플로우 전체 토폴로지 (각 엔진이 자기 조각 publish)
```

엔진 A가 시작하면 `fbp/_engines/announce`에 `{engine_id, host, started_at, owned_flows: [...]}` 발행. 엔진 B는 이를 구독하여 다른 엔진의 존재와 소유 플로우를 알게 됨. CLI는 이 정보를 모아 분산 플로우 토폴로지를 시각화.

#### CLI 확장

```
fbp(engine-a)> engine list
ENGINE-ID    HOST              UPTIME      FLOWS    STATUS
engine-a     host-1            2h 15m      3        SELF
engine-b     host-2            1h 47m      2        REMOTE
engine-c     host-3            0h 23m      1        REMOTE

fbp(engine-a)> flow topology distributed-flow
Flow: distributed-flow (DISTRIBUTED across 2 engines)

  [engine-a]                          [engine-b]
   subscriber → collector ──┐  ┌──── classifier → storage
                            │  │
                            ▼  ▼
                   fbp/distributed-flow/
                   collector.out-classifier.in
                   (system-broker:1884)

  Status:    HEALTHY (both engines online)
  Throughput: 1,247 msg/s end-to-end
  Cross-engine latency (avg/p99): 3.2ms / 18ms

fbp(engine-a)> flow status distributed-flow
Flow: distributed-flow
  Status:    RUNNING (this engine's segment)
  Owned Nodes:  subscriber, collector
  External Outputs: 1 (collector:out → fbp/distributed-flow/collector.out-classifier.in)
  Peer Engines: [engine-b: HEALTHY, last heartbeat 2s ago]
```

#### InfluxDB 스키마 확장

엔진을 식별할 수 있도록 모든 measurement에 `engine_id` 태그를 추가한다.

```
node_stats,engine_id=engine-a,flow_id=distributed-flow,node_id=collector,node_type=BufferBatch in_count=12345i,out_count=12345i,...
node_stats,engine_id=engine-b,flow_id=distributed-flow,node_id=classifier,node_type=ThresholdRouter in_count=12340i,out_count=8732i,...

cross_engine_stats,flow_id=distributed-flow,from_engine=engine-a,to_engine=engine-b,topic=fbp/distributed-flow/collector.out-classifier.in published=12345i,received=12340i,lost=5i,rtt_avg_ms=3.2,rtt_p99_ms=18.4
```

새로운 measurement `cross_engine_stats`는 엔진 경계 토픽의 통계를 별도 집계. 이를 통해 분산 손실(엔진 A가 publish한 수와 엔진 B가 receive한 수의 차이)을 추적할 수 있다.

#### 운영 시나리오 (시연 스크립트)

```bash
# 터미널 1: 엔진 A 실행
$ ./fbp-engine --engine-id engine-a --port 8081
fbp(engine-a)>

# 터미널 2: 엔진 B 실행
$ ./fbp-engine --engine-id engine-b --port 8082
fbp(engine-b)>

# 터미널 1: 엔진 A에 segment 배포
fbp(engine-a)> flow deploy config/distributed-flow.engine-a.json
fbp(engine-a)> flow start distributed-flow

# 터미널 2: 엔진 B에 segment 배포
fbp(engine-b)> flow deploy config/distributed-flow.engine-b.json
fbp(engine-b)> flow start distributed-flow

# 터미널 1: 엔진 발견 확인
fbp(engine-a)> engine list
fbp(engine-a)> flow topology distributed-flow

# 데이터 흐름 시작 (외부에서 데이터 브로커에 발행)
$ mosquitto_pub -h localhost -p 1883 -t sensor/temp -m '{"value":31.5}'

# 양쪽 엔진에서 통계 확인
fbp(engine-a)> node stats collector
fbp(engine-b)> node stats classifier

# 엔진 B 강제 종료 → 엔진 A는 계속 publish, 시스템 브로커 큐잉
$ kill <engine-b-pid>
fbp(engine-a)> flow topology distributed-flow
# Peer Engines: [engine-b: OFFLINE, last heartbeat 45s ago]
# Status: DEGRADED (downstream segment offline)

# 엔진 B 재시작 → 자동 복구, retained / queued 메시지 처리
$ ./fbp-engine --engine-id engine-b --port 8082
fbp(engine-b)> flow deploy config/distributed-flow.engine-b.json
fbp(engine-b)> flow start distributed-flow
# 엔진 A의 collector가 발행한 메시지가 다시 흐름
```

#### 스스로 답해야 할 질문

- 엔진 B가 다운된 동안 엔진 A의 `collector`가 발행한 메시지를 잃지 않으려면? (QoS 1 + 시스템 브로커의 큐잉, 또는 엔진 A의 로컬 outbox 패턴)
- 두 엔진이 같은 `flow-id`로 같은 segment를 동시에 배포하면? (충돌 감지, announce 토픽으로 소유권 확인)
- 엔진 A가 보낸 메시지를 엔진 B 외에 엔진 C도 구독하면? (의도된 fan-out인지 사고인지 — 토픽 ACL 또는 정책 필요)
- 분산 플로우의 "전체 처리량"은 어디서 집계? (각 엔진 메트릭의 합인가, 입출력 노드만 보면 되는가?)
- 한 엔진에서 `flow stop distributed-flow`하면 다른 엔진은? (각자 독립인가, 마스터 엔진이 조정하는가?)
- 시간 동기화가 안 맞는 두 엔진의 latency 측정은 어떻게? (메시지에 발신 ts 포함, NTP 가정?)
- 엔진 발견(discovery)이 retained 메시지에 의존할 때 stale entry 문제는?
- segment 정의 변경(Part C의 동적 패치)이 한 엔진에서 일어나면 다른 엔진은 어떻게 알아채는가?

#### 평가 시 추가 고려 사항

| 항목 | 검증 |
|------|------|
| 무중단 분산 | 한 엔진 다운 시 나머지 엔진은 계속 동작 |
| 메시지 보존 | QoS 1 + retained / 시스템 브로커 큐잉으로 다운 중 메시지 손실 최소화 |
| 통계 일관성 | InfluxDB에서 `engine_id` 태그로 분리 조회 가능, 합산 결과 일치 |
| 운영 가시성 | CLI로 다른 엔진의 상태와 분산 플로우 토폴로지 파악 가능 |
| 확장성 | 엔진 3대 이상으로 늘려도 동일 원리로 동작 |

---

## 4. 설계 참고 사항

### Connection 인터페이스 리팩토링 체크리스트

기존 `Connection` 클래스를 인터페이스로 변경할 때, 기존 코드가 깨지지 않도록 아래 순서를 따른다:

1. `Connection` 인터페이스 정의 (`deliver`, `poll`, `getBufferSize`, `getId`, `close`)
2. 기존 `Connection` 클래스를 `LocalConnection`으로 리네이밍, `Connection` 인터페이스 구현
3. 기존 코드에서 `new Connection(...)` → `new LocalConnection(...)` 변경
4. 노드 코드에서 `Connection` 타입 참조는 이미 인터페이스이므로 변경 불필요
5. 기존 테스트 모두 실행 → 전부 통과 확인
6. `MqttBridgeConnection` 구현

### MQTT 클라이언트 풀링

`MqttBridgeConnection`마다 별도의 `MqttClient`를 만들면 연결 수가 폭발한다. 해결 방안:

```
MqttClientPool
 ├── getPublisher(brokerUrl) → 공유 MqttClient (모든 Connection이 publish에 사용)
 ├── getSubscriber(brokerUrl, topic, callback) → 공유 MqttClient에 topic 구독 추가
 └── release(brokerUrl) → 참조 카운트 0이면 연결 종료
```

하나의 시스템 브로커에 대해 publish용 1개, subscribe용 1개 클라이언트만 유지하면, 연결 10개인 플로우도 MqttClient 2개로 처리할 수 있다.

### 메시지 직렬화 포맷

```json
{
  "v": 1,
  "ts": 1717401782345,
  "src": "sensor",
  "port": "out",
  "payload": {
    "topic": "sensor/temp",
    "value": 31.2,
    "unit": "celsius"
  }
}
```

- `v`: 프로토콜 버전 (향후 포맷 변경 시 하위 호환)
- `ts`: 직렬화 시점 타임스탬프
- `src`, `port`: 디버깅/모니터링용 메타데이터
- `payload`: 원본 `Message`의 페이로드

### 동적 변경의 트랜잭션 경계

플로우 패치는 여러 단위 작업의 묶음이다. 중간 실패에 대비해야 한다.

```
flow patch
 ├── 1. 새 정의 검증 (스키마, 노드 타입, 순환 참조 등)
 ├── 2. diff 계산 → 작업 큐 생성
 ├── 3. 스냅샷 저장 (롤백용)
 ├── 4. 작업 큐 순차 실행
 │    └── 실패 시 → 스냅샷으로 롤백
 └── 5. 변경 이력에 revision 추가, InfluxDB의 flow_events에 기록
```

### 메트릭 수집의 비침습성

메트릭 수집 코드가 메인 처리 경로의 성능을 떨어뜨리면 본말전도다.

- `LongAdder` 등 락 없는 카운터 사용
- 시간 측정은 `System.nanoTime()` 1회만, 계산은 비동기 워커에서
- InfluxDB 쓰기는 별도 스레드의 배치 워커가 담당
- 메인 처리 스레드는 메트릭을 큐에 넣기만 하고 즉시 반환

### InfluxDB 연동 안정성

- 쓰기 실패는 재시도하되, 메인 처리에 영향을 주지 않아야 한다
- 로컬 버퍼가 가득 차면 가장 오래된 데이터부터 폐기 (단, `flow_events` 같은 중요 이벤트는 별도 영속 저장 고려)
- 애플리케이션 종료 시 버퍼 flush
- 시간 동기화 — 모든 타임스탬프는 UTC ns

---

## 5. 평가 기준

| 항목 | 비중 | 세부 |
|------|:----:|------|
| **Connection 추상화** | 15% | 인터페이스 설계, LocalConnection 호환성, 기존 테스트 통과 |
| **MqttBridgeConnection** | 15% | 브로커 경유 메시지 전달, 직렬화, 재연결, 리소스 관리 |
| **동적 플로우 관리** | 15% | 무중단 변경, 변경 이력, 롤백, 트랜잭션 경계 |
| **통계 수집** | 15% | 다층 메트릭 수집의 정확성과 비침습성 |
| **InfluxDB 연동** | 10% | 스키마 설계, 배치 쓰기, 장애 복구, 보존 정책 |
| **CLI 완성도** | 15% | 명령어 커버리지, 출력 품질, 에러 처리, 모니터링 |
| **통합 시나리오** | 10% | E2E 시나리오, 장애 복구 |
| **설계 문서** | 5% | 아키텍처 다이어그램, 설계 결정 근거, 토픽/스키마 규칙 문서화 |

---

## 6. 환경 구성: Docker Compose로 MQTT 브로커 2대 + InfluxDB 실행

종합 과제를 수행하려면 **데이터 브로커**(포트 1883), **시스템 브로커**(포트 1884), **InfluxDB**(포트 8086)가 필요하다. Docker Compose로 한 번에 실행한다.

### 사전 준비

- Docker 및 Docker Compose 설치: [docs.docker.com/get-docker](https://docs.docker.com/get-docker/)
- 설치 확인:
  ```bash
  docker --version
  docker compose version
  ```

### 디렉토리 구조

프로젝트 루트에 아래 파일들을 생성한다.

```
project-root/
├── docker/
│   ├── docker-compose.yml
│   ├── mosquitto-data/
│   │   └── mosquitto.conf
│   ├── mosquitto-system/
│   │   └── mosquitto.conf
│   └── influxdb/
│       └── (자동 생성되는 데이터 볼륨)
├── src/
└── pom.xml
```

### docker-compose.yml

```yaml
version: "3.8"

services:
  # ─────────────────────────────────────────────
  # 데이터 브로커: IoT 센서 데이터 수집용
  # MqttSubscriberNode, MqttPublisherNode가 연결하는 브로커
  # ─────────────────────────────────────────────
  mqtt-data-broker:
    image: eclipse-mosquitto:2.0
    container_name: fbp-data-broker
    ports:
      - "1883:1883"
    volumes:
      - ./mosquitto-data/mosquitto.conf:/mosquitto/config/mosquitto.conf
      - mqtt-data-data:/mosquitto/data
      - mqtt-data-log:/mosquitto/log
    restart: unless-stopped
    networks:
      - fbp-network

  # ─────────────────────────────────────────────
  # 시스템 브로커: FBP 엔진 내부 노드 간 메시지 전달용
  # MqttBridgeConnection이 사용하는 브로커
  # ─────────────────────────────────────────────
  mqtt-system-broker:
    image: eclipse-mosquitto:2.0
    container_name: fbp-system-broker
    ports:
      - "1884:1883"
    volumes:
      - ./mosquitto-system/mosquitto.conf:/mosquitto/config/mosquitto.conf
      - mqtt-system-data:/mosquitto/data
      - mqtt-system-log:/mosquitto/log
    restart: unless-stopped
    networks:
      - fbp-network

  # ─────────────────────────────────────────────
  # InfluxDB: 시계열 메트릭/도메인 통계 저장
  # ─────────────────────────────────────────────
  influxdb:
    image: influxdb:2.7
    container_name: fbp-influxdb
    ports:
      - "8086:8086"
    environment:
      DOCKER_INFLUXDB_INIT_MODE: setup
      DOCKER_INFLUXDB_INIT_USERNAME: admin
      DOCKER_INFLUXDB_INIT_PASSWORD: admin12345
      DOCKER_INFLUXDB_INIT_ORG: fbp
      DOCKER_INFLUXDB_INIT_BUCKET: fbp-metrics
      DOCKER_INFLUXDB_INIT_RETENTION: 30d
      DOCKER_INFLUXDB_INIT_ADMIN_TOKEN: fbp-admin-token-please-change
    volumes:
      - influxdb-data:/var/lib/influxdb2
      - influxdb-config:/etc/influxdb2
    restart: unless-stopped
    networks:
      - fbp-network

volumes:
  mqtt-data-data:
  mqtt-data-log:
  mqtt-system-data:
  mqtt-system-log:
  influxdb-data:
  influxdb-config:

networks:
  fbp-network:
    driver: bridge
```

### mosquitto-data/mosquitto.conf (데이터 브로커)

```
# 데이터 브로커 설정 — 포트 1883
listener 1883
allow_anonymous true
persistence true
persistence_location /mosquitto/data/
log_dest file /mosquitto/log/mosquitto.log
log_type all
```

### mosquitto-system/mosquitto.conf (시스템 브로커)

```
# 시스템 브로커 설정 — 컨테이너 내부 포트 1883, 호스트에서는 1884로 접근
listener 1883
allow_anonymous true
persistence true
persistence_location /mosquitto/data/
log_dest file /mosquitto/log/mosquitto.log
log_type all

# 시스템 브로커는 메시지 크기 제한을 넉넉하게 설정
# (직렬화된 FBP Message가 클 수 있음)
max_packet_size 1048576
```

### 실행 및 관리

```bash
# docker/ 디렉토리로 이동
cd docker

# 모든 서비스 동시 시작 (백그라운드)
docker compose up -d

# 실행 상태 확인
docker compose ps
# NAME                 STATUS    PORTS
# fbp-data-broker      Up        0.0.0.0:1883->1883/tcp
# fbp-system-broker    Up        0.0.0.0:1884->1883/tcp
# fbp-influxdb         Up        0.0.0.0:8086->8086/tcp

# 실시간 로그 (tail -f 방식)
docker compose logs -f
```

### 연결 테스트

브로커가 정상 동작하는지 `mosquitto_pub`/`mosquitto_sub` 명령으로 확인한다.

```bash
# --- 데이터 브로커 (1883) 테스트 ---
mosquitto_sub -h localhost -p 1883 -t "sensor/temp"
mosquitto_pub -h localhost -p 1883 -t "sensor/temp" -m '{"value":25.3}'

# --- 시스템 브로커 (1884) 테스트 ---
mosquitto_sub -h localhost -p 1884 -t "fbp/test/nodeA.out-nodeB.in"
mosquitto_pub -h localhost -p 1884 -t "fbp/test/nodeA.out-nodeB.in" -m '{"v":1,"payload":{"data":"hello"}}'

# --- InfluxDB 헬스체크 ---
curl http://localhost:8086/health
# {"name":"influxdb","message":"ready for queries and writes","status":"pass",...}

# --- InfluxDB UI ---
# 브라우저: http://localhost:8086
# 로그인: admin / admin12345
```

### Java 코드에서 접속 정보

```java
// 데이터 브로커 — MqttSubscriberNode, MqttPublisherNode 용
String dataBrokerUrl = "tcp://localhost:1883";

// 시스템 브로커 — MqttBridgeConnection 용
String systemBrokerUrl = "tcp://localhost:1884";

// InfluxDB — MetricsCollector 적재용
String influxUrl = "http://localhost:8086";
String influxToken = "fbp-admin-token-please-change";
String influxOrg = "fbp";
String influxBucket = "fbp-metrics";
```

플로우 정의 JSON에서의 사용:

```json
{
  "id": "temperature-monitoring",
  "transport": {
    "type": "mqtt",
    "broker": "tcp://localhost:1884",
    "qos": 1
  },
  "metrics": {
    "domain": [
      {"name": "temperature", "source": {"node": "sensor", "port": "out"}, "field": "value", "windows": ["1m", "1h", "1d"]}
    ]
  },
  "nodes": [
    {
      "id": "sensor",
      "type": "MqttSubscriber",
      "config": {
        "broker": "tcp://localhost:1883",
        "topic": "sensor/temp"
      }
    }
  ],
  "connections": [...]
}
```

`transport.broker`(시스템 브로커, 1884)와 노드 config의 `broker`(데이터 브로커, 1883)가 **서로 다른 주소**임에 주의한다.

### 종료 및 정리

```bash
# 모든 서비스 정지
docker compose down

# 볼륨까지 포함하여 완전 삭제 (로그, 데이터, InfluxDB 데이터 포함)
docker compose down -v
```

---

## 7. 참고 자료

### MQTT

- Eclipse Paho MQTT v5 Client: [eclipse.org/paho](https://www.eclipse.org/paho/)
- MQTT 사양 v5.0: [docs.oasis-open.org](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)
- MQTT 토픽 네이밍 규칙: [HiveMQ Blog](https://www.hivemq.com/blog/mqtt-essentials-part-5-mqtt-topics-best-practices/)

### 설계 패턴

- Strategy Pattern — GoF *Design Patterns*
- Abstract Factory Pattern — 연결 타입에 따른 Connection 생성
- Observer Pattern — CLI 모니터링 (메시지 이벤트 구독)
- Command Pattern — 동적 변경 작업 큐와 롤백

### CLI 구현

- JLine 3: [github.com/jline/jline3](https://github.com/jline/jline3)
- Picocli (CLI 파서): [picocli.info](https://picocli.info/)

### 메시지 직렬화

- Jackson Databind: [github.com/FasterXML/jackson-databind](https://github.com/FasterXML/jackson-databind)
- MessagePack (고성능 바이너리): [msgpack.org](https://msgpack.org/)

### 시계열/메트릭

- InfluxDB v2 Java Client: [github.com/influxdata/influxdb-client-java](https://github.com/influxdata/influxdb-client-java)
- InfluxDB Line Protocol: [docs.influxdata.com](https://docs.influxdata.com/influxdb/v2/reference/syntax/line-protocol/)
- HdrHistogram (백분위수 근사): [github.com/HdrHistogram/HdrHistogram](https://github.com/HdrHistogram/HdrHistogram)
- Grafana 대시보드: [grafana.com](https://grafana.com/)