# FBP IoT Rule Engine — 2단계 학습 자료

### 목표
- 1단계에서 구현한 기본 FBP 엔진에 MQTT, MODBUS 등 외부 통신 프로토콜을 연결하여 실제 IoT 장비와 통신할 수 있는 Rule Engine을 구현한다.

### 전제
- 1단계(Step 1~10)를 완료하여 기본 FBP 엔진(Node, Port, Connection, Flow, FlowEngine)이 동작하는 상태에서 시작한다.

### 학습 방식
- 주제와 참고 자료를 제시한다.
- 1단계처럼 단계별 코드를 안내하지 않으며, 구현 방향은 팀 내에서 자율적으로 결정한다.
- 각 Step에서 달성해야 할 목표와 설계 시 고려해야 할 사항을 제시하고, 팀 토의 주제를 통해 스스로 판단하고 결정하는 역량을 기른다.

---

## 시작 전 점검

2단계를 시작하기 전에 아래 항목을 점검한다. 하나라도 미달이면 1단계를 보완한 후 진행한다.

| 점검 항목 | 확인 |
|-----------|:----:|
| `Message` 클래스가 불변(immutable)으로 동작하며, `withEntry()`, `withoutKey()`가 새 객체를 반환한다 | ☐ |
| `AbstractNode`를 상속한 노드가 5종 이상 구현되어 있다 | ☐ |
| `Connection`이 `BlockingQueue` 기반으로 동작하며, 멀티스레드에서 안전하다 | ☐ |
| `Flow` 클래스로 노드를 등록하고 연결할 수 있다 | ☐ |
| `FlowEngine`으로 플로우를 등록/시작/정지할 수 있다 | ☐ |
| `mvn test`로 모든 단위 테스트가 통과한다 | ☐ |

---

## Step 1 — 네트워크 프로그래밍 기초와 프로토콜 노드 설계

### 1.1 주제

외부 시스템과 통신하는 노드를 만들려면 네트워크 프로그래밍의 기본 개념을 이해해야 한다. 1단계의 노드들은 JVM 안에서만 메시지를 주고받았지만, 2단계부터는 네트워크 너머의 장비(MQTT Broker, MODBUS 디바이스)와 데이터를 교환한다.

### 1.2 학습 내용

**Java 네트워크 기초**

- `java.net.Socket` — TCP 연결의 기본. 클라이언트가 서버에 접속하여 바이트 스트림을 주고받는 구조.
- `java.net.ServerSocket` — 서버 측에서 클라이언트 연결을 대기하는 구조.
- `InputStream`/`OutputStream` — 소켓 위에서 데이터를 읽고 쓰는 스트림.

```java
// TCP 클라이언트 기본 구조
try (Socket socket = new Socket("localhost", 1883)) {
    OutputStream out = socket.getOutputStream();
    InputStream in = socket.getInputStream();
    // 데이터 송수신...
}
```

**비동기 I/O와 Java NIO**

전통적인 `Socket`은 블로킹 방식이다. `read()`를 호출하면 데이터가 올 때까지 스레드가 멈춘다. 연결이 많아지면 스레드도 많아져 비효율적이다.

Java NIO(`java.nio`)는 비블로킹 I/O를 지원한다.

- `Channel` — 양방향 데이터 통로 (Socket의 발전형)
- `Buffer` — 데이터를 담는 컨테이너 (읽기/쓰기 모드 전환)
- `Selector` — 하나의 스레드로 여러 채널의 이벤트를 감시

2단계에서 직접 NIO를 구현하지는 않지만, MQTT/MODBUS 라이브러리가 내부적으로 NIO를 사용한다는 점을 이해하면 디버깅과 설계에 도움이 된다.

**콜백과 리스너 패턴**

외부 프로토콜 라이브러리는 대부분 콜백 방식으로 동작한다. MQTT 메시지가 도착하면 라이브러리가 우리가 등록한 콜백을 호출하는 식이다.

```java
// 콜백 패턴의 기본 구조
interface MessageListener {
    void onMessage(String topic, byte[] payload);
    void onConnectionLost(Throwable cause);
}
```

이 패턴은 FBP 엔진과 자연스럽게 연결된다. 콜백에서 수신한 외부 데이터를 FBP `Message`로 변환하여 OutputPort로 전송하면 된다.

### 1.3 프로토콜 노드의 공통 설계

외부 통신 노드는 1단계의 순수 처리 노드와 다른 특성을 가진다.

| 특성 | 1단계 노드 (내부 처리) | 2단계 노드 (외부 통신) |
|------|----------------------|----------------------|
| 데이터 흐름 | 동기적 — process() 호출 시 즉시 처리 | 비동기적 — 외부에서 데이터가 언제 올지 모름 |
| 자원 관리 | 없음 또는 단순 | 네트워크 연결, 세션, 재연결 필요 |
| 에러 처리 | 간단한 예외 처리 | 연결 끊김, 타임아웃, 재시도 등 복잡 |
| 생명주기 | initialize()에서 큰 작업 없음 | initialize()에서 연결 수립, shutdown()에서 연결 해제 |
| 스레드 | 엔진이 관리 | 라이브러리 자체 스레드 + 엔진 스레드 병행 |

이 차이를 고려하여, 외부 통신 노드의 공통 구조를 먼저 설계하는 것이 좋다.

**설계 고려사항: ProtocolNode 추상 클래스**

프로토콜 노드에 공통적으로 필요한 기능을 생각해 보라.

- 연결 상태 관리 (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `ERROR`)
- 자동 재연결 로직 (연결이 끊기면 일정 주기로 재시도)
- 연결 이벤트 알림 (연결 성공/실패/끊김을 로그 또는 메시지로 전파)
- 설정(Configuration) 관리 (호스트, 포트, 인증 정보 등)

```
AbstractNode
   └── ProtocolNode (abstract)          ← 연결 상태 관리, 재연결, 설정 공통
         ├── MqttSubscriberNode
         ├── MqttPublisherNode
         ├── ModbusReaderNode
         └── ModbusWriterNode
```

### 1.4 참고 자료

- Oracle Java Networking Tutorial: [docs.oracle.com/javase/tutorial/networking](https://docs.oracle.com/javase/tutorial/networking/)
- Java NIO Tutorial — Baeldung: [baeldung.com/java-nio](https://www.baeldung.com/java-nio-selector)
- Netty 프레임워크 개요: [netty.io](https://netty.io/) — 직접 사용하지는 않지만, 구조를 이해하면 도움이 된다

### 과제

> **과제 1-1**: TCP 에코 서버와 클라이언트를 작성하라. 서버는 수신한 메시지를 그대로 돌려보내고, 클라이언트는 "Hello FBP"를 전송한 후 응답을 출력한다.
>
> **과제 1-2**: 콜백 기반 메시지 수신 구조를 설계하라. `MessageListener` 인터페이스를 정의하고, 수신 시 FBP `Message`로 변환하는 흐름을 코드로 작성하라.
>
> **과제 1-3**: `ProtocolNode` 추상 클래스를 설계하고 구현하라.
>
> **기능 명세 — ProtocolNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 추상 클래스 | 직접 인스턴스화 불가 |
> | 생성자 | `ProtocolNode(String id, Map<String, Object> config)` |
> | 필드 | `config`(Map<String, Object>), `connectionState`(ConnectionState enum), `reconnectIntervalMs`(long, 기본 5000) |
> | enum | `ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }` |
> | `initialize()` | `connectionState`를 `CONNECTING`으로 변경 → `connect()` 호출 → 성공 시 `CONNECTED`, 실패 시 재연결 스케줄러 시작 |
> | `shutdown()` | 재연결 스케줄러 중지 → `disconnect()` 호출 → `DISCONNECTED`로 변경 |
> | `abstract void connect()` | 하위 클래스가 실제 연결 로직 구현 (예외 발생 가능) |
> | `abstract void disconnect()` | 하위 클래스가 실제 연결 해제 로직 구현 |
> | `reconnect()` | 연결 끊김 시 호출. `reconnectIntervalMs` 간격으로 `connect()` 재시도. 최대 재시도 횟수를 config에서 읽음 (기본 10회) |
> | `getConnectionState()` | 현재 연결 상태 반환 |
> | `getConfig(String key)` | config에서 값 조회 |
> | `isConnected()` | `connectionState == CONNECTED` |
>
> **과제 1-4**: `ProtocolNode`를 상속하는 간단한 `EchoProtocolNode`를 작성하여, `connect()`에서 TCP 소켓 연결, `disconnect()`에서 소켓 해제를 수행하도록 구현하라. 과제 1-1의 에코 서버와 연동하여 동작을 확인한다.
>
> **팀 토의**
> - 프로토콜 노드의 연결 실패를 엔진에 어떻게 알릴 것인가? (에러 OutputPort? 이벤트 버스?)
> - 재연결 시 기존 플로우의 다른 노드는 어떻게 동작해야 하는가? (대기? 메시지 버림? 큐에 쌓기?)
> - config 정보를 코드에 하드코딩하지 않고 외부에서 주입하는 방법은?
>
> **과제 1-5**: Step 1에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — ProtocolNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 초기 상태 | 생성 직후 `getConnectionState()`가 `DISCONNECTED` |
> | 2 | config 조회 | 생성 시 전달한 config의 값을 `getConfig()`로 조회 가능 |
> | 3 | initialize → CONNECTED | connect()가 성공하면 상태가 `CONNECTED`로 변경됨 |
> | 4 | initialize → 연결 실패 시 상태 | connect()에서 예외 발생 시 `ERROR` 또는 재연결 스케줄러가 시작됨 |
> | 5 | shutdown → DISCONNECTED | shutdown() 후 상태가 `DISCONNECTED` |
> | 6 | isConnected 반환값 | `CONNECTED` 상태에서 `isConnected()` → true, 그 외 → false |
> | 7 | 재연결 시도 | connect() 실패 후 재연결 스케줄러가 지정 간격으로 재시도함 (간접 확인) |

---

## Step 2 — MQTT 프로토콜 이해와 연결

### 2.1 주제

MQTT(Message Queuing Telemetry Transport)는 IoT에서 가장 널리 사용되는 경량 메시징 프로토콜이다. 센서가 데이터를 **발행(Publish)** 하면, 관심 있는 시스템이 해당 주제를 **구독(Subscribe)** 하여 수신하는 구조다.

### 2.2 MQTT 핵심 개념

```
                          ┌─────────────┐
  [Sensor A] ──publish──→ │             │ ──→ [Subscriber 1]
                          │ MQTT Broker │
  [Sensor B] ──publish──→ │ (Mosquitto) │ ──→ [Subscriber 2]
                          │             │
                          └─────────────┘
```

**Broker**: 메시지 중개자. 발행자와 구독자를 연결한다. 발행자와 구독자는 서로를 직접 알지 못한다.

**Topic**: 메시지의 주소. 계층 구조를 가진다.
- `sensor/temperature` — 온도 센서 데이터
- `sensor/humidity` — 습도 센서 데이터
- `sensor/+` — 와일드카드. `sensor/` 아래 모든 하위 토픽
- `sensor/#` — 다중 레벨 와일드카드. `sensor/` 아래 모든 레벨

**QoS (Quality of Service)**: 메시지 전달 보장 수준
- QoS 0 — At most once. 최대 한 번 전달. 유실 가능. 가장 빠름.
- QoS 1 — At least once. 최소 한 번 전달. 중복 가능.
- QoS 2 — Exactly once. 정확히 한 번 전달. 가장 느림.

**Retained Message**: Broker가 토픽의 마지막 메시지를 저장. 새 구독자가 연결하면 즉시 최신 값을 받을 수 있다.

**Last Will and Testament (LWT)**: 클라이언트가 비정상 종료되면 Broker가 미리 등록된 "유언" 메시지를 발행한다. 장비 오프라인 감지에 유용하다.

### 2.3 Eclipse Paho MQTT Client

Java에서 가장 널리 사용되는 MQTT 클라이언트 라이브러리다.

**Maven 의존성**:
```xml
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
    <version>1.2.5</version>
</dependency>
```

**기본 사용 패턴**:

```java
// 연결
MqttClient client = new MqttClient("tcp://localhost:1883", "client-id");
MqttConnectionOptions options = new MqttConnectionOptions();
options.setAutomaticReconnect(true);
options.setCleanStart(true);
client.connect(options);

// 구독
client.subscribe("sensor/temperature", 1, (topic, msg) -> {
    String payload = new String(msg.getPayload());
    System.out.println("수신: " + topic + " → " + payload);
});

// 발행
MqttMessage message = new MqttMessage("25.5".getBytes());
message.setQos(1);
client.publish("sensor/temperature", message);

// 연결 해제
client.disconnect();
client.close();
```

**주의사항**:
- Client ID는 Broker 내에서 고유해야 한다. 같은 ID로 두 클라이언트가 연결하면 하나가 강제 해제된다.
- 콜백은 Paho의 내부 스레드에서 호출된다. FBP 엔진의 스레드와 다르므로 동기화에 주의한다.
- `connect()`는 네트워크 호출이므로 실패할 수 있다. 예외 처리를 반드시 포함한다.

### 2.4 MqttSubscriberNode 설계 가이드

MQTT Broker에서 메시지를 수신하여 FBP 플로우에 주입하는 **소스 노드**다.

**동작 구조**:
```
MQTT Broker                   FBP 플로우
                                   ┌──────────────────────┐
 topic: sensor/temp                │  MqttSubscriberNode  │
  ──(MQTT message)──→ callback ──→ │  "out" OutputPort    │──→ [next node]
                                   └──────────────────────┘
```

**설계 시 고려사항**:
- MQTT 메시지(바이트 배열)를 FBP `Message`로 변환하는 로직이 필요하다
- MQTT 메시지의 payload가 JSON 형식이라면 파싱하여 Map으로 변환한다
- 토픽 정보도 FBP Message의 페이로드에 포함시켜야 다음 노드가 토픽별 분기를 할 수 있다
- 콜백 스레드에서 `send()`를 호출하므로, OutputPort와 Connection이 스레드 안전한지 확인한다
- 구독할 토픽은 config에서 읽는다

**페이로드 변환 예시**:
```
MQTT 수신: topic="sensor/temp", payload={"value": 28.5, "unit": "°C"}

FBP Message 페이로드:
{
    "topic": "sensor/temp",
    "value": 28.5,
    "unit": "°C",
    "timestamp": 1717401782000
}
```

### 2.5 MqttPublisherNode 설계 가이드

FBP 플로우에서 처리된 결과를 MQTT Broker로 발행하는 **싱크 노드**다.

**동작 구조**:
```
FBP Flow                                 MQTT Broker
                  ┌────────────────────┐
[Previous Node]──→│  MqttPublisherNode │
                  │  "in" InputPort    │──(MQTT Publish)──→ topic: alert/temp
                  └────────────────────┘
```

**설계 시 고려사항**:
- FBP `Message`의 어떤 필드를 MQTT 페이로드로 변환할 것인가?
- 발행할 토픽을 고정(config)할 것인가, 메시지에서 동적으로 결정할 것인가?
- QoS와 Retained 설정을 config에서 관리한다
- 발행 실패 시 재시도 로직이 필요한가?

### 2.6 JSON 파싱

MQTT 메시지의 페이로드는 대부분 JSON 형식이다. Java에서 JSON을 다루려면 라이브러리가 필요하다.

**Jackson (권장)**:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

```java
ObjectMapper mapper = new ObjectMapper();

// JSON 문자열 → Map
String json = "{\"value\": 28.5, \"unit\": \"°C\"}";
Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});

// Map → JSON 문자열
String output = mapper.writeValueAsString(map);
```

**Gson (대안)**:
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

팀에서 어떤 라이브러리를 사용할지 협의하여 결정한다.

### 2.7 로컬 MQTT Broker 설치

테스트를 위해 로컬에서 Mosquitto Broker를 실행한다.

**Docker를 이용한 설치 (권장)**:
```bash
docker run -d --name mosquitto \
  -p 1883:1883 \
  -p 9001:9001 \
  eclipse-mosquitto:2
```

Mosquitto 설정 파일(`mosquitto.conf`)에 아래를 추가해야 외부 접속이 가능하다:
```
listener 1883
allow_anonymous true
```

**CLI로 테스트**:
```bash
# 터미널 1: 구독
mosquitto_sub -t "sensor/temp" -v

# 터미널 2: 발행
mosquitto_pub -t "sensor/temp" -m '{"value": 28.5}'
```

### 2.8 참고 자료

- MQTT 공식 사양: [mqtt.org](https://mqtt.org/)
- MQTT 5.0 Features: [docs.oasis-open.org/mqtt/mqtt/v5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)
- Eclipse Paho Java Client: [eclipse.org/paho](https://www.eclipse.org/paho/index.php?page=clients/java/index.php)
- Paho API Javadoc: [javadoc.io/doc/org.eclipse.paho](https://javadoc.io/doc/org.eclipse.paho/org.eclipse.paho.mqttv5.client/latest/index.html)
- Mosquitto Docker 이미지: [hub.docker.com/_/eclipse-mosquitto](https://hub.docker.com/_/eclipse-mosquitto)
- Jackson JSON: [github.com/FasterXML/jackson-databind](https://github.com/FasterXML/jackson-databind)

### 과제

> **과제 2-1**: Maven에 Eclipse Paho MQTT v5 클라이언트와 JSON 라이브러리 의존성을 추가하라.
>
> **과제 2-2**: Paho 라이브러리를 직접 사용하여 MQTT Broker에 연결하고, 토픽을 구독/발행하는 독립 테스트 프로그램을 작성하라. FBP 엔진과 무관하게 라이브러리 사용법만 익히는 것이 목적이다.
>
> **과제 2-3**: `MqttSubscriberNode`를 구현하라.
>
> **기능 명세 — MqttSubscriberNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `ProtocolNode` |
> | 생성자 | `MqttSubscriberNode(String id, Map<String, Object> config)` |
> | config 키 | `brokerUrl`(String, 예: "tcp://localhost:1883"), `clientId`(String), `topic`(String), `qos`(int, 기본 1) |
> | 포트 | OutputPort `"out"` 1개 |
> | 필드 | `client`(MqttClient), `objectMapper`(ObjectMapper) |
> | `connect()` | MqttClient 생성 → 연결 옵션 설정(cleanStart, autoReconnect) → `client.connect()` → `client.subscribe(topic, qos, callback)` |
> | 콜백 | MQTT 메시지 수신 시: ① payload를 JSON 파싱하여 `Map<String, Object>`로 변환 ② `"topic"` 키에 수신 토픽 추가 ③ `"mqttTimestamp"` 키에 수신 시각 추가 ④ FBP `Message`를 생성하여 `send("out", message)` |
> | `disconnect()` | `client.disconnect()` → `client.close()` |
> | 에러 처리 | JSON 파싱 실패 시 원본 payload를 `"rawPayload"` 키에 문자열로 넣어 전송 |
>
> **과제 2-4**: `MqttPublisherNode`를 구현하라.
>
> **기능 명세 — MqttPublisherNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `ProtocolNode` |
> | 생성자 | `MqttPublisherNode(String id, Map<String, Object> config)` |
> | config 키 | `brokerUrl`(String), `clientId`(String), `topic`(String, 기본 발행 토픽), `qos`(int, 기본 1), `retained`(boolean, 기본 false) |
> | 포트 | InputPort `"in"` 1개 |
> | 필드 | `client`(MqttClient), `objectMapper`(ObjectMapper) |
> | `connect()` | MqttClient 생성 → 연결 → 연결 상태 로그 출력 |
> | `onProcess(Message)` | ① FBP Message 페이로드를 JSON 문자열로 변환 ② 발행 토픽 결정: 메시지에 `"topic"` 키가 있으면 그 값 사용, 없으면 config의 기본 토픽 사용 ③ `MqttMessage` 생성(QoS, retained 설정) ④ `client.publish(topic, mqttMessage)` |
> | `disconnect()` | `client.disconnect()` → `client.close()` |
> | 에러 처리 | 발행 실패 시 로그 출력, 예외를 삼키지 않고 에러 카운트 관리 |
>
> **과제 2-5**: `MqttSubscriberNode` → `PrintNode` 플로우를 구성하고, 외부에서 `mosquitto_pub`로 메시지를 발행하면 PrintNode에서 출력되는지 확인하라.
>
> **과제 2-6**: `MqttPublisherNode`를 사용하여, `GeneratorNode`가 생성한 메시지를 MQTT Broker에 발행하고, `mosquitto_sub`로 수신되는지 확인하라.
>
> **과제 2-7**: 양방향 MQTT 플로우를 구성하라.
> ```
> [MqttSubscriberNode("sensor/temp")] → [FilterNode(temperature > 30)]
>                                            ↓
>                                     [MqttPublisherNode("alert/temp")]
> ```
> `mosquitto_pub`로 온도 데이터를 발행하면, 30도 초과 시 `alert/temp` 토픽에 메시지가 발행되는지 확인한다.
>
> **팀 토의**
> - MqttSubscriberNode와 MqttPublisherNode가 같은 Broker를 사용할 때, client ID 충돌을 어떻게 방지하는가?
> - 여러 토픽을 구독해야 할 때, 노드 하나로 처리할 것인가 토픽마다 노드를 만들 것인가?
> - QoS 레벨은 상황에 따라 어떻게 선택해야 하는가?
> - 연결이 끊겼다 재연결될 때, 구독은 자동으로 복원되는가?
>
> **과제 2-8**: Step 2에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — MqttSubscriberNode**
>
> 외부 Broker 의존성이 있으므로, 단위 테스트와 통합 테스트를 분리한다.
>
> *단위 테스트 (Broker 불필요)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 포트 구성 | `getOutputPort("out")`이 null이 아님 |
> | 2 | 초기 상태 | 생성 직후 `isConnected()`가 false |
> | 3 | config 조회 | `getConfig("brokerUrl")`가 설정한 값과 일치 |
> | 4 | JSON → Message 변환 | JSON 문자열을 수동으로 변환하는 내부 메서드가 올바른 Map을 반환 (리플렉션 또는 protected 메서드 테스트) |
> | 5 | JSON 파싱 실패 처리 | 잘못된 JSON이 들어왔을 때 "rawPayload" 키로 원본 문자열이 전달됨 |
>
> *통합 테스트 (Broker 필요 — `@Tag("integration")`)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 6 | Broker 연결 성공 | initialize() 후 `isConnected()`가 true |
> | 7 | 메시지 수신 | Broker에 publish 후 CollectorNode에서 메시지가 수신됨 |
> | 8 | 토픽 정보 포함 | 수신한 FBP Message에 "topic" 키가 포함됨 |
> | 9 | shutdown 후 연결 해제 | shutdown() 후 `isConnected()`가 false |
>
> **테스트 항목 — MqttPublisherNode**
>
> *단위 테스트 (Broker 불필요)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 포트 구성 | `getInputPort("in")`이 null이 아님 |
> | 2 | 초기 상태 | 생성 직후 `isConnected()`가 false |
> | 3 | config 기본 토픽 조회 | `getConfig("topic")`가 설정 값과 일치 |
>
> *통합 테스트 (Broker 필요 — `@Tag("integration")`)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 4 | Broker 연결 성공 | initialize() 후 `isConnected()`가 true |
> | 5 | 메시지 발행 | FBP Message를 process()로 보내면 Broker에서 수신됨 (별도 subscriber로 확인) |
> | 6 | 동적 토픽 | 메시지에 "topic" 키가 있으면 해당 토픽으로 발행됨 |
> | 7 | shutdown 후 연결 해제 | shutdown() 후 `isConnected()`가 false |

---

## Step 3 — MODBUS TCP 프로토콜 이해와 소켓 직접 구현

### 3.1 주제

MODBUS는 산업 자동화에서 가장 오래되고 널리 사용되는 통신 프로토콜이다. PLC(Programmable Logic Controller), 센서, 액추에이터 등 산업 장비가 데이터를 주고받는 표준이다.

이 Step에서는 외부 라이브러리를 사용하지 않고, **Java Socket으로 MODBUS TCP 프로토콜을 직접 구현**한다. 프로토콜의 프레임 구조를 바이트 단위로 이해하고, 직접 요청을 조립하고 응답을 파싱하는 과정을 통해 산업 프로토콜의 동작 원리를 깊이 있게 학습한다.

### 3.2 MODBUS 핵심 개념

MQTT와 달리 MODBUS는 **마스터-슬레이브** 구조다. 마스터(우리 엔진)가 슬레이브(장비)에게 요청을 보내면 슬레이브가 응답하는 방식이다. 슬레이브는 먼저 데이터를 보내지 않는다.

```
[FBP 엔진 (마스터)] ──요청──→ [MODBUS 장비 (슬레이브)]
                 ←──응답──
```

**전송 방식**:
- **MODBUS RTU**: 시리얼(RS-485) 통신. 바이너리 프레임. 산업 현장에서 주로 사용.
- **MODBUS TCP**: TCP/IP 위에서 동작. 이더넷 연결. 2단계에서는 이 방식을 직접 구현한다.

**데이터 모델 — 레지스터**:

MODBUS 장비의 데이터는 "레지스터"라는 메모리 공간에 저장된다.

| 레지스터 타입 | 읽기/쓰기 | 크기 | 주소 범위 | 용도 예시 |
|-------------|----------|------|----------|----------|
| Coil | 읽기/쓰기 | 1 bit | 0x0000~0xFFFF | 릴레이 ON/OFF, 모터 제어 |
| Discrete Input | 읽기 전용 | 1 bit | 0x0000~0xFFFF | 스위치 상태, 센서 접점 |
| Holding Register | 읽기/쓰기 | 16 bit | 0x0000~0xFFFF | 설정값, 제어 명령 |
| Input Register | 읽기 전용 | 16 bit | 0x0000~0xFFFF | 온도, 습도, 전류 등 측정값 |

**주요 Function Code**:
- FC 03: Read Holding Registers — 이 Step에서 반드시 구현
- FC 06: Write Single Register — 이 Step에서 반드시 구현
- FC 04: Read Input Registers — 선택 구현
- FC 16: Write Multiple Registers — 선택 구현

### 3.3 MODBUS TCP 프레임 구조

MODBUS TCP는 TCP 소켓 위에서 바이너리 프레임을 주고받는다. 프레임은 **MBAP 헤더**와 **PDU(Protocol Data Unit)** 로 구성된다.

#### MBAP 헤더 (7 바이트)

MODBUS TCP 고유의 헤더다. 모든 요청과 응답에 포함된다.

```
바이트 위치:  [0][1]     [2][3]    [4][5]          [6]
           ──────     ──────    ──────          ───
의미:       트랜잭션 ID  프로토콜 ID  길이(이후 바이트수) 유닛 ID
           (2byte)    (2byte)   (2byte)         (1byte)
```

| 필드 | 크기 | 설명 |
|------|------|------|
| Transaction ID | 2 바이트 | 요청/응답 쌍을 식별. 요청에서 보낸 값이 응답에 그대로 돌아옴 |
| Protocol ID | 2 바이트 | 항상 `0x0000` (MODBUS 프로토콜) |
| Length | 2 바이트 | 이 필드 이후의 바이트 수 (Unit ID + PDU 길이) |
| Unit ID | 1 바이트 | 슬레이브 ID. TCP에서는 보통 `0x01` 또는 `0xFF` |

#### PDU — FC 03: Read Holding Registers

**요청 PDU** (5 바이트):
```
[Function Code: 0x03] [시작 주소: 2byte] [레지스터 개수: 2byte]
```

**전체 요청 프레임** (12 바이트 = MBAP 7 + PDU 5):
```
예: 슬레이브 1, 주소 0번부터 3개 레지스터 읽기

[00 01]  Transaction ID = 1
[00 00]  Protocol ID = 0
[00 06]  Length = 6 (Unit ID 1 + PDU 5)
[01]     Unit ID = 1
[03]     Function Code = Read Holding Registers
[00 00]  Start Address = 0
[00 03]  Quantity = 3
```

**응답 PDU**:
```
[Function Code: 0x03] [바이트 수: 1byte] [레지스터 값들: N×2byte]
```

**전체 응답 프레임** 예 (3개 레지스터, 각 16비트):
```
[00 01]  Transaction ID = 1 (요청과 동일)
[00 00]  Protocol ID = 0
[00 09]  Length = 9 (Unit ID 1 + FC 1 + ByteCount 1 + Data 6)
[01]     Unit ID = 1
[03]     Function Code = 0x03
[06]     Byte Count = 6 (3 레지스터 × 2 바이트)
[00 FA]  Register 0 = 250
[02 58]  Register 1 = 600
[00 01]  Register 2 = 1
```

#### PDU — FC 06: Write Single Register

**요청 PDU** (5 바이트):
```
[Function Code: 0x06] [레지스터 주소: 2byte] [기록할 값: 2byte]
```

**전체 요청 프레임** (12 바이트):
```
예: 슬레이브 1, 주소 2번에 값 100 기록

[00 02]  Transaction ID = 2
[00 00]  Protocol ID = 0
[00 06]  Length = 6
[01]     Unit ID = 1
[06]     Function Code = Write Single Register
[00 02]  Register Address = 2
[00 64]  Value = 100
```

**응답 프레임**: 요청과 동일한 내용을 그대로 에코백한다 (성공 시).

#### 에러 응답

슬레이브가 요청을 처리할 수 없으면 에러 응답을 보낸다. Function Code의 최상위 비트가 1로 설정된다.

```
[Function Code | 0x80] [Exception Code: 1byte]
```

| Exception Code | 의미 |
|:--------------:|------|
| 0x01 | Illegal Function — 지원하지 않는 Function Code |
| 0x02 | Illegal Data Address — 존재하지 않는 레지스터 주소 |
| 0x03 | Illegal Data Value — 값이 허용 범위를 벗어남 |
| 0x04 | Slave Device Failure — 장비 내부 오류 |

### 3.4 ModbusTcpClient 설계 가이드

MODBUS TCP 프로토콜을 소켓으로 직접 구현하는 클라이언트 클래스다. 이 클래스가 프로토콜의 바이트 조립/파싱을 담당하며, 노드(ModbusReaderNode, ModbusWriterNode)는 이 클라이언트를 사용한다.

**클래스 구조**:
```
ModbusTcpClient                ← MODBUS TCP 프로토콜 구현 (소켓 기반)
  ├── connect(host, port)      ← TCP 소켓 연결
  ├── disconnect()             ← 소켓 종료
  ├── readHoldingRegisters()   ← FC 03 요청/응답
  ├── writeSingleRegister()    ← FC 06 요청/응답
  └── isConnected()            ← 연결 상태 확인

ModbusReaderNode / ModbusWriterNode
  └── 내부에서 ModbusTcpClient를 사용
```

**구현 핵심 — 바이트 배열 조립과 파싱**:

Java에서 바이트 배열을 다루는 핵심 도구:

```java
// 바이트 배열 조립 — DataOutputStream 사용
ByteArrayOutputStream baos = new ByteArrayOutputStream();
DataOutputStream dos = new DataOutputStream(baos);
dos.writeShort(transactionId);  // 2바이트 Big-Endian
dos.writeShort(0x0000);         // Protocol ID
dos.writeShort(length);         // Length
dos.writeByte(unitId);          // Unit ID
dos.writeByte(functionCode);    // Function Code
byte[] frame = baos.toByteArray();

// 소켓으로 전송
socket.getOutputStream().write(frame);
socket.getOutputStream().flush();

// 응답 수신 — DataInputStream 사용
DataInputStream dis = new DataInputStream(socket.getInputStream());
int respTransactionId = dis.readUnsignedShort();
int respProtocolId = dis.readUnsignedShort();
int respLength = dis.readUnsignedShort();
int respUnitId = dis.readUnsignedByte();
int respFunctionCode = dis.readUnsignedByte();
```

**주의사항**:
- MODBUS TCP는 **Big-Endian** (네트워크 바이트 순서)이다. Java의 `DataOutputStream`/`DataInputStream`은 기본이 Big-Endian이므로 별도 변환이 필요 없다.
- 16비트 레지스터 값은 `readUnsignedShort()`로 읽어야 부호 없는 0~65535 범위를 올바르게 처리한다.
- 응답을 읽을 때 먼저 MBAP 헤더(7바이트)를 읽고, Length 필드를 확인한 후 나머지를 읽는 것이 안전하다.
- 소켓 타임아웃(`socket.setSoTimeout()`)을 설정하여 장비가 응답하지 않는 경우를 처리한다.
- Transaction ID는 요청마다 1씩 증가시켜 요청/응답 쌍을 식별한다.

### 3.5 MODBUS TCP 슬레이브 시뮬레이터 설계

테스트를 위해 슬레이브 시뮬레이터도 직접 구현한다. `ServerSocket`으로 연결을 받고, 요청 프레임을 파싱하여 레지스터 값을 돌려주는 프로그램이다.

**동작 구조**:
```
ModbusTcpSimulator
  ├── ServerSocket(port)          ← 클라이언트 연결 대기
  ├── registers (int[])           ← 레지스터 저장소
  ├── handleClient(Socket)        ← 연결된 클라이언트 처리 (별도 스레드)
  │     ├── 요청 프레임 수신/파싱
  │     ├── FC 03 → 레지스터 읽기 → 응답 조립/전송
  │     └── FC 06 → 레지스터 쓰기 → 에코 응답 전송
  ├── setRegister(address, value) ← 외부에서 레지스터 값 설정
  └── getRegister(address)        ← 외부에서 레지스터 값 조회 (테스트 검증용)
```

```java
// 시뮬레이터 사용 예시
ModbusTcpSimulator simulator = new ModbusTcpSimulator(5020, 100); // 포트 5020, 레지스터 100개
simulator.setRegister(0, 250);  // 주소 0: 온도 25.0 × 10
simulator.setRegister(1, 600);  // 주소 1: 습도 60.0 × 10
simulator.setRegister(2, 0);    // 주소 2: 상태
simulator.start();

// ... 테스트 수행 ...

simulator.stop();
```

이 시뮬레이터는 테스트에서도 활용된다. `@BeforeEach`에서 시뮬레이터를 시작하고, `@AfterEach`에서 종료하면 외부 도구 없이 통합 테스트를 수행할 수 있다.

### 3.6 ModbusReaderNode 설계 가이드

MODBUS 장비에서 레지스터 값을 읽어 FBP 플로우에 주입하는 노드다.

**동작 구조**:
```
MODBUS 장비                        FBP 플로우
                 ┌──────────────────────┐
 레지스터        │   ModbusReaderNode    │
 ←─(TCP 요청)──│ "trigger" InputPort    │←── [TimerNode]
 ──(TCP 응답)──→│ "out" OutputPort      │──→ [다음 노드]
                 └──────────────────────┘
```

MODBUS는 마스터가 먼저 요청해야 한다. 따라서 ModbusReaderNode는 "trigger" 포트로 메시지를 받을 때마다 레지스터를 읽는다. TimerNode와 연결하면 주기적 폴링이 된다.

**설계 시 고려사항**:
- 내부에서 `ModbusTcpClient`를 사용하여 통신한다
- 읽을 레지스터의 시작 주소, 개수, 슬레이브 ID를 config에서 설정
- 레지스터 값의 의미 해석: 원시값(정수)을 물리량(온도, 습도)으로 변환하는 방법
- 여러 레지스터를 한 번에 읽어 하나의 FBP Message로 묶을 것인가, 레지스터마다 메시지를 만들 것인가
- 폴링 주기가 너무 짧으면 장비에 부하, 너무 길면 데이터 지연

**페이로드 변환 예시**:
```
MODBUS 읽기: 슬레이브 1, 주소 0~2 → [250, 600, 1]

FBP Message 페이로드:
{
    "slaveId": 1,
    "registers": {
        "0": 250,
        "1": 600,
        "2": 1
    },
    "timestamp": 1717401782000
}
```

또는 매핑 정보를 config에 포함하여 의미 있는 키를 부여할 수도 있다:
```json
"registerMapping": {
    "0": {"name": "temperature", "scale": 0.1},
    "1": {"name": "humidity", "scale": 0.1},
    "2": {"name": "status"}
}
```

→ FBP Message: `{"temperature": 25.0, "humidity": 60.0, "status": 1}`

### 3.7 ModbusWriterNode 설계 가이드

FBP 플로우에서 처리된 결과를 MODBUS 장비의 레지스터에 기록하는 노드다.

**동작 구조**:
```
FBP 플로우                              MODBUS 장비
                 ┌──────────────────────┐
[이전 노드]──→   │   ModbusWriterNode    │ ──(TCP 요청)──→ 레지스터
                 │   "in" InputPort      │
                 └──────────────────────┘
```

**설계 시 고려사항**:
- 내부에서 `ModbusTcpClient`를 사용하여 통신한다
- FBP Message에서 어떤 필드를 레지스터에 쓸 것인가
- 쓰기 대상 레지스터 주소와 값의 매핑 방법
- 쓰기 성공/실패 결과를 FBP 플로우에 피드백할 것인가 (결과 OutputPort)
- 16비트 정수로 변환 시 스케일 처리 (예: 온도 25.5 → 255)
- FC 06 응답의 에코백 값이 요청과 일치하는지 검증하여 쓰기 성공을 확인

### 3.8 참고 자료

- MODBUS 프로토콜 사양: [modbus.org](https://modbus.org/)
- MODBUS TCP/IP Implementation Guide: [modbus.org/docs/Modbus_Messaging_Implementation_Guide_V1_0b.pdf](https://modbus.org/docs/Modbus_Messaging_Implementation_Guide_V1_0b.pdf)
- MODBUS TCP 설명: [simplymodbus.ca](https://www.simplymodbus.ca/)
- Java DataInputStream/DataOutputStream: [Oracle Docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/DataInputStream.html)
- MODBUS 시뮬레이터 도구 (동작 비교 참고용): ModRSsim2, diagslave

### 과제

> **과제 3-1**: MODBUS TCP 프레임을 수작업으로 조립하고 해석하는 연습을 하라.
> - 아래 시나리오의 요청/응답 프레임을 바이트 배열로 작성한다 (종이 또는 코드).
    >   - 슬레이브 1, 주소 10번부터 Holding Register 5개 읽기 (FC 03)
>   - 슬레이브 1, 주소 5번에 값 1234 쓰기 (FC 06)
>   - 읽기 응답: 레지스터 5개 값이 [100, 200, 300, 400, 500]
> - 각 바이트의 의미를 주석으로 표기하라.
>
> **과제 3-2**: `ModbusTcpClient` 클래스를 구현하라.
>
> **기능 명세 — ModbusTcpClient**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `protocol` (또는 `modbus`) |
> | 생성자 | `ModbusTcpClient(String host, int port)` — 연결 대상 호스트와 포트를 저장 (생성자에서 연결하지 않음) |
> | 필드 | `socket`(Socket), `out`(DataOutputStream), `in`(DataInputStream), `transactionId`(int, 0부터 시작), `host`(String), `port`(int) |
> | `connect()` | ① `new Socket(host, port)`로 TCP 연결 ② `socket.setSoTimeout(3000)` — 3초 타임아웃 ③ `DataOutputStream`/`DataInputStream` 생성 |
> | `disconnect()` | 소켓, 스트림 닫기. null 체크 필요 |
> | `isConnected()` | `socket != null && socket.isConnected() && !socket.isClosed()` |
> | `readHoldingRegisters(int unitId, int startAddress, int quantity)` | ① FC 03 요청 프레임 조립 (MBAP 헤더 + PDU) ② 소켓으로 전송 ③ 응답 프레임 수신 및 파싱 ④ 에러 응답이면 `ModbusException` 발생 ⑤ `int[]` 배열로 레지스터 값 반환 |
> | `writeSingleRegister(int unitId, int address, int value)` | ① FC 06 요청 프레임 조립 ② 소켓으로 전송 ③ 응답 프레임 수신 ④ 에코백 검증 (주소, 값 일치 확인) ⑤ 불일치 시 `ModbusException` 발생 |
> | 내부 메서드 | `buildMbapHeader(int transactionId, int length, int unitId)` — MBAP 헤더 7바이트 생성 |
> | 내부 메서드 | `readMbapHeader()` — 응답에서 MBAP 헤더 7바이트를 읽어 파싱 |
> | `transactionId` | 매 요청마다 1 증가. 응답의 Transaction ID와 대조하여 요청/응답 쌍 확인 |
>
> **과제 3-3**: `ModbusException` 클래스를 구현하라.
>
> **기능 명세 — ModbusException**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `Exception` |
> | 생성자 | `ModbusException(int functionCode, int exceptionCode)` |
> | 필드 | `functionCode`(int), `exceptionCode`(int) |
> | `getMessage()` | `"MODBUS 에러 — FC: 0x%02X, Exception: 0x%02X (%s)"` 형식. exceptionCode에 따라 설명 포함 |
> | `getExceptionCode()` | exceptionCode 반환 |
> | 정적 상수 | `ILLEGAL_FUNCTION = 0x01`, `ILLEGAL_DATA_ADDRESS = 0x02`, `ILLEGAL_DATA_VALUE = 0x03`, `SLAVE_DEVICE_FAILURE = 0x04` |
>
> **과제 3-4**: `ModbusTcpSimulator` 클래스를 구현하라.
>
> **기능 명세 — ModbusTcpSimulator**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `protocol` (또는 `modbus`) |
> | 생성자 | `ModbusTcpSimulator(int port, int registerCount)` — 수신 포트와 레지스터 개수 |
> | 필드 | `serverSocket`(ServerSocket), `registers`(int[]), `running`(volatile boolean) |
> | `start()` | 별도 스레드에서 `serverSocket.accept()`로 클라이언트 연결 대기. 연결 시 `handleClient()` 호출 |
> | `stop()` | `running = false`, `serverSocket.close()` |
> | `handleClient(Socket)` | ① MBAP 헤더 수신 ② Function Code에 따라 분기 ③ FC 03: 요청된 주소/개수만큼 registers에서 읽어 응답 조립/전송 ④ FC 06: 요청된 주소에 값 기록, 에코백 응답 전송 ⑤ 잘못된 주소면 에러 응답(Exception Code 0x02) 전송 |
> | `setRegister(int address, int value)` | 외부에서 레지스터 값 설정 |
> | `getRegister(int address)` | 외부에서 레지스터 값 조회 (테스트 검증용) |
> | 다중 클라이언트 | 각 클라이언트를 별도 스레드에서 처리하여 동시 접속 지원 |
>
> **과제 3-5**: 과제 3-2의 `ModbusTcpClient`와 과제 3-4의 `ModbusTcpSimulator`를 조합하여 독립 테스트 프로그램을 작성하라.
> - 시뮬레이터: 포트 5020에서 동작. 레지스터 10개. 초기값: [250, 600, 1, 0, 0, 0, 0, 0, 0, 0]
> - 클라이언트: 시뮬레이터에 연결하여 주소 0~2 레지스터 읽기 → 출력 → 주소 2에 값 100 쓰기 → 다시 읽어서 변경 확인
>
> **과제 3-6**: `ModbusReaderNode`를 구현하라.
>
> **기능 명세 — ModbusReaderNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `ProtocolNode` |
> | 생성자 | `ModbusReaderNode(String id, Map<String, Object> config)` |
> | config 키 | `host`(String), `port`(int, 기본 502), `slaveId`(int), `startAddress`(int), `count`(int), `registerMapping`(Map<String, Object>, 선택) |
> | 포트 | InputPort `"trigger"` 1개, OutputPort `"out"` 1개, OutputPort `"error"` 1개 |
> | 필드 | `client`(ModbusTcpClient) |
> | `connect()` | `ModbusTcpClient` 생성 → `client.connect()` |
> | `onProcess(Message)` | ① trigger 수신 시 `client.readHoldingRegisters(slaveId, startAddress, count)` 호출 ② `int[]` 결과를 FBP Message로 변환 (registerMapping이 있으면 매핑 적용) ③ `send("out", message)` ④ `ModbusException` 또는 `IOException` 발생 시 에러 메시지를 `send("error", errorMsg)` |
> | `disconnect()` | `client.disconnect()` |
>
> **과제 3-7**: `ModbusWriterNode`를 구현하라.
>
> **기능 명세 — ModbusWriterNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `ProtocolNode` |
> | 생성자 | `ModbusWriterNode(String id, Map<String, Object> config)` |
> | config 키 | `host`(String), `port`(int, 기본 502), `slaveId`(int), `registerAddress`(int), `valueField`(String — FBP Message에서 값을 읽을 키), `scale`(double, 기본 1.0) |
> | 포트 | InputPort `"in"` 1개, OutputPort `"result"` 1개 (쓰기 결과 전달, 선택) |
> | 필드 | `client`(ModbusTcpClient) |
> | `connect()` | `ModbusTcpClient` 생성 → `client.connect()` |
> | `onProcess(Message)` | ① 메시지에서 `valueField` 키의 값을 꺼냄 ② `scale`을 곱하여 정수로 변환 ③ `client.writeSingleRegister(slaveId, registerAddress, 값)` ④ 성공 시 "result" 포트로 결과 메시지 전달 ⑤ 예외 발생 시 에러 로그 |
> | `disconnect()` | `client.disconnect()` |
>
> **과제 3-8**: TimerNode → ModbusReaderNode → PrintNode 플로우를 구성하고, 시뮬레이터에서 레지스터 값을 주기적으로 읽어 출력하라.
>
> **과제 3-9**: ModbusReaderNode → ThresholdFilterNode → ModbusWriterNode 플로우를 구성하라.
> - ReaderNode는 온도 레지스터(주소 0)를 읽고
> - ThresholdFilterNode는 임계값 이상이면 통과시키고
> - WriterNode는 제어 레지스터(주소 2)에 알림 코드(1)를 기록
> - 시뮬레이터에서 주소 2의 값이 변경되었는지 `getRegister(2)`로 확인
>
> **팀 토의**
> - MODBUS 폴링 주기를 어떻게 결정해야 하는가? 장비 응답 속도, 네트워크 지연을 고려하라.
> - 레지스터 값의 스케일 변환을 노드 안에서 할 것인가, 별도 TransformNode에서 할 것인가?
> - MODBUS 통신 에러(타임아웃, 응답 없음) 시 어떻게 처리해야 하는가?
> - 여러 레지스터를 읽을 때, 하나의 요청으로 묶어 읽는 것과 개별 요청의 장단점은?
> - 소켓을 매 요청마다 열고 닫을 것인가, 연결을 유지할 것인가? 각각의 장단점은?
> - Transaction ID를 관리하는 이유는 무엇인가? 생략하면 어떤 문제가 생기는가?
>
> **과제 3-10**: Step 3에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — ModbusTcpClient**
>
> *단위 테스트 — 프레임 조립/파싱*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | FC 03 요청 프레임 조립 | `readHoldingRegisters()`가 생성하는 바이트 배열이 MODBUS 사양과 일치 (unitId, startAddress, quantity가 올바른 위치에 올바른 값) |
> | 2 | FC 06 요청 프레임 조립 | `writeSingleRegister()`가 생성하는 바이트 배열이 MODBUS 사양과 일치 |
> | 3 | MBAP 헤더 구조 | Transaction ID, Protocol ID(0x0000), Length, Unit ID가 올바르게 조립됨 |
> | 4 | Transaction ID 증가 | 연속 요청 시 Transaction ID가 1씩 증가 |
> | 5 | 초기 상태 | 생성 직후 `isConnected()`가 false |
>
> *통합 테스트 (시뮬레이터 사용)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 6 | 연결/해제 | `connect()` 후 `isConnected()` true, `disconnect()` 후 false |
> | 7 | Holding Register 읽기 | 시뮬레이터에 설정한 값과 `readHoldingRegisters()` 반환값이 일치 |
> | 8 | 다수 레지스터 읽기 | 5개 레지스터를 한 번에 읽어 배열 크기와 값이 모두 일치 |
> | 9 | Single Register 쓰기 | `writeSingleRegister()` 후 시뮬레이터의 `getRegister()`로 값 변경 확인 |
> | 10 | 쓰기 후 읽기 | 쓰기 → 읽기 순서로 값이 일관되게 반영됨 |
> | 11 | 에러 응답 처리 | 존재하지 않는 주소를 읽으면 `ModbusException` 발생, exceptionCode가 `ILLEGAL_DATA_ADDRESS` |
> | 12 | 소켓 타임아웃 | 시뮬레이터를 중지한 상태에서 요청 시 `SocketTimeoutException` 또는 `IOException` 발생 |
>
> **테스트 항목 — ModbusException**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | getMessage 포맷 | 생성 시 지정한 functionCode와 exceptionCode가 메시지에 포함 |
> | 2 | getExceptionCode | 생성 시 지정한 exceptionCode가 반환됨 |
> | 3 | 상수 값 | `ILLEGAL_FUNCTION`이 0x01, `ILLEGAL_DATA_ADDRESS`가 0x02 등 |
>
> **테스트 항목 — ModbusTcpSimulator**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 시작/종료 | `start()` 후 포트가 열리고, `stop()` 후 닫힘 |
> | 2 | 레지스터 초기값 | `setRegister()` 후 `getRegister()`로 설정 값 확인 |
> | 3 | FC 03 응답 | ModbusTcpClient로 읽기 요청 시 설정된 레지스터 값이 응답됨 |
> | 4 | FC 06 응답 | ModbusTcpClient로 쓰기 요청 시 레지스터 값이 변경되고 에코백 응답 |
> | 5 | 잘못된 주소 에러 | 범위를 벗어난 주소 요청 시 Exception Code 0x02 에러 응답 |
> | 6 | 다중 클라이언트 | 2개 클라이언트가 동시 접속하여 독립적으로 요청/응답 가능 |
>
> **테스트 항목 — ModbusReaderNode**
>
> *단위 테스트*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 포트 구성 | `getInputPort("trigger")`, `getOutputPort("out")`, `getOutputPort("error")`가 null이 아님 |
> | 2 | 초기 상태 | 생성 직후 `isConnected()`가 false |
> | 3 | config 확인 | `getConfig("host")`, `getConfig("slaveId")` 등이 설정 값과 일치 |
>
> *통합 테스트 (시뮬레이터 사용)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 4 | 연결 성공 | initialize() 후 `isConnected()`가 true |
> | 5 | 레지스터 읽기 | trigger 메시지 전송 후 CollectorNode에서 레지스터 값이 포함된 메시지 수신 |
> | 6 | registerMapping 적용 | 매핑이 설정된 경우 의미 있는 키(temperature, humidity)로 변환됨 |
> | 7 | 읽기 실패 시 에러 포트 | 존재하지 않는 주소를 읽으면 "error" 포트로 에러 메시지 전달 |
> | 8 | shutdown 후 연결 해제 | shutdown() 후 `isConnected()`가 false |
>
> **테스트 항목 — ModbusWriterNode**
>
> *단위 테스트*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 포트 구성 | `getInputPort("in")`이 null이 아님 |
> | 2 | 초기 상태 | 생성 직후 `isConnected()`가 false |
> | 3 | config 확인 | `getConfig("registerAddress")` 등이 설정 값과 일치 |
>
> *통합 테스트 (시뮬레이터 사용)*
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 4 | 연결 성공 | initialize() 후 `isConnected()`가 true |
> | 5 | 레지스터 쓰기 | FBP Message를 process()로 보낸 후, 시뮬레이터의 `getRegister()`로 값 변경 확인 |
> | 6 | 스케일 변환 | scale=10.0 설정 시 25.5 → 255로 변환되어 기록됨 |
> | 7 | shutdown 후 연결 해제 | shutdown() 후 `isConnected()`가 false |

---

## Step 4 — 프로토콜 노드 통합 및 Rule 처리

### 4.1 주제

Step 2~3에서 구현한 MQTT/MODBUS 노드를 기존 FBP 엔진에 통합하고, 규칙(Rule) 기반 처리 플로우를 구성한다. 여기서 Rule Engine의 핵심인 `RuleNode`를 설계한다.

### 4.2 RuleNode 설계 가이드

`RuleNode`는 조건식을 평가하여 메시지의 경로를 결정하는 노드다. 1단계의 `ThresholdFilterNode`가 단일 숫자 비교만 할 수 있었다면, `RuleNode`는 더 복잡한 조건을 처리한다.

**기본 동작**:
```
입력 메시지 → [조건 평가] → 조건 만족: "match" 포트
                         → 조건 불만족: "mismatch" 포트
```

**규칙 표현 방식 — 팀이 선택할 3가지 옵션**:

**옵션 A: Java Predicate 기반 (코드 내 정의)**
```java
RuleNode rule = new RuleNode("temp-rule", msg -> {
    Double temp = msg.get("temperature");
    return temp != null && temp > 30.0;
});
```
- 장점: 구현 간단, 타입 안전, IDE 지원
- 단점: 규칙 변경 시 코드 수정·재컴파일 필요

**옵션 B: 문자열 기반 조건식**
```java
RuleNode rule = new RuleNode("temp-rule", "temperature > 30.0");
```
- 장점: 설정 파일에서 규칙 정의 가능, 런타임 변경 가능
- 단점: 조건식 파서 구현 필요, 타입 안전성 낮음

**옵션 C: 복합 규칙 (AND/OR)**
```java
CompositeRule rule = new CompositeRule("complex", CompositeRule.Operator.AND);
rule.addCondition("temperature", ">", 30.0);
rule.addCondition("humidity", ">", 70.0);
```
- 장점: 다중 조건 조합 가능
- 단점: 구현 복잡도 높음

팀 내에서 토의하여 옵션을 선택하되, 최소한 옵션 A는 구현하고 가능하면 B나 C로 확장하라.

### 4.3 간단한 조건식 파서 (옵션 B 선택 시)

문자열 조건식 `"temperature > 30.0"`을 해석하려면 간단한 파서가 필요하다.

**지원할 연산자**: `>`, `>=`, `<`, `<=`, `==`, `!=`

**파싱 전략**:
1. 조건식을 공백 기준으로 분리: `["temperature", ">", "30.0"]`
2. 첫 번째 토큰 → 메시지 페이로드 키
3. 두 번째 토큰 → 연산자
4. 세 번째 토큰 → 비교 값 (숫자 또는 문자열)

```java
// 파서 구현의 뼈대
public class RuleExpression {
    private String field;
    private String operator;
    private Object value;

    public static RuleExpression parse(String expression) {
        String[] parts = expression.trim().split("\\s+");
        // parts[0] = field, parts[1] = operator, parts[2] = value
        // ...
    }

    public boolean evaluate(Message message) {
        Object fieldValue = message.get(field);
        // operator에 따라 비교 수행
        // ...
    }
}
```

### 4.4 통합 플로우 설계

MQTT와 MODBUS를 결합한 실용적인 플로우를 구성한다.

**시나리오: 센서 데이터 수집 → 규칙 평가 → 장비 제어 + 알림**

```
[MqttSubscriberNode]          ← MQTT Broker에서 센서 데이터 수신
        ↓
[RuleNode: temperature > 30]  ← 규칙 평가
   ↓match              ↓mismatch
[MqttPublisherNode]    [LogNode]
   (알림 발행)            (정상 기록)
   ↓
[ModbusWriterNode]     ← MODBUS 장비에 제어 명령 전송
   (팬 가동 명령)
```

이 플로우가 동작하면:
1. 센서가 MQTT로 온도 데이터를 발행
2. MqttSubscriberNode가 수신하여 FBP 메시지로 변환
3. RuleNode가 30도 초과 여부를 판단
4. 초과 시: MQTT로 알림 발행 + MODBUS로 팬 가동 명령
5. 정상 시: 로그 기록

### 4.5 참고 자료

- Java `Predicate<T>` 함수형 인터페이스: [docs.oracle.com](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/Predicate.html)
- MVEL (Expression Language): [github.com/mvel/mvel](https://github.com/mvel/mvel) — 옵션 B를 고도화할 때 참고
- Spring Expression Language (SpEL): [docs.spring.io](https://docs.spring.io/spring-framework/reference/core/expressions.html) — 기업용 표현식 엔진 참고

### 과제

> **과제 4-1**: `RuleNode`를 구현하라. 최소한 Predicate 기반(옵션 A)을 구현한다.
>
> **기능 명세 — RuleNode (옵션 A: Predicate 기반)**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `RuleNode(String id, Predicate<Message> condition)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"match"` 1개, OutputPort `"mismatch"` 1개 |
> | 필드 | `condition`(Predicate<Message>) |
> | `onProcess(Message)` | `condition.test(message)`가 true이면 `send("match", message)`, false이면 `send("mismatch", message)` |
>
> **과제 4-2** (확장): `RuleExpression` 클래스를 구현하고, 문자열 조건식을 지원하는 `RuleNode` 오버로딩 생성자를 추가하라.
>
> **기능 명세 — RuleExpression**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `core` (또는 `rule`) |
> | 정적 메서드 | `static RuleExpression parse(String expression)` — 문자열 조건식을 파싱 |
> | 메서드 | `boolean evaluate(Message message)` — 메시지에 대해 조건을 평가하여 결과 반환 |
> | 지원 연산자 | `>`, `>=`, `<`, `<=`, `==`, `!=` |
> | 지원 타입 | 숫자(Double), 문자열(String) 비교 |
>
> **과제 4-3** (도전): `CompositeRuleNode`를 구현하라. AND/OR로 여러 조건을 결합할 수 있다.
>
> **기능 명세 — CompositeRuleNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `CompositeRuleNode(String id, Operator operator)` — `Operator`는 `AND` 또는 `OR` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"match"` 1개, OutputPort `"mismatch"` 1개 |
> | 필드 | `conditions`(List<Predicate<Message>>), `operator`(Operator enum) |
> | `addCondition(Predicate<Message>)` | 조건 추가 |
> | `addCondition(String field, String op, Object value)` | 문자열 기반 조건 추가 (내부에서 RuleExpression으로 변환) |
> | `onProcess(Message)` | AND: 모든 조건이 true여야 match. OR: 하나라도 true면 match |
>
> **과제 4-4**: 4.4절의 통합 플로우를 구현하고, MQTT Broker와 MODBUS 시뮬레이터를 사용하여 End-to-End 동작을 확인하라.
>
> **과제 4-5**: 시간 기반 규칙을 구현하라. "5분 이내에 온도가 3번 이상 30도를 초과하면 알림"과 같은 규칙을 처리하는 `TimeWindowRuleNode`를 설계하라.
>
> **기능 명세 — TimeWindowRuleNode (도전)**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `TimeWindowRuleNode(String id, Predicate<Message> condition, long windowMs, int threshold)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"alert"` 1개, OutputPort `"pass"` 1개 |
> | 필드 | `condition`(Predicate<Message>), `windowMs`(long — 시간 창 크기), `threshold`(int — 발생 횟수 기준), `events`(Queue — 시간 창 내 조건 만족 이벤트 기록) |
> | `onProcess(Message)` | ① 조건 평가 ② 만족 시 현재 시각을 events에 추가 ③ windowMs보다 오래된 이벤트 제거 ④ events 크기 >= threshold이면 `send("alert", message)`, 아니면 `send("pass", message)` |
>
> **팀 토의**
> - 규칙 표현 방식으로 어떤 옵션을 선택했는가? 그 이유는?
> - 규칙이 복잡해지면 노드 하나에 넣을 것인가, 여러 노드를 조합할 것인가?
> - 시간 기반 규칙에서 메시지 누락(네트워크 지연 등)은 어떻게 고려하는가?
> - 규칙을 외부 파일(JSON/YAML)로 정의하고 런타임에 로드하는 방법은?
>
> **과제 4-6**: Step 4에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — RuleNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 조건 만족 → match | 조건을 만족하는 메시지가 "match" 포트로 전달됨 |
> | 2 | 조건 불만족 → mismatch | 조건을 만족하지 않는 메시지가 "mismatch" 포트로 전달됨 |
> | 3 | 포트 구성 | "in", "match", "mismatch" 포트가 모두 존재 |
> | 4 | null 필드 처리 | 조건에 사용되는 필드가 없는 메시지가 예외 없이 처리됨 |
> | 5 | 다수 메시지 분기 | 혼합된 메시지를 연속 처리 시 각각 올바른 포트로 분기됨 |
>
> **테스트 항목 — RuleExpression (옵션 B 선택 시)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 파싱 — 숫자 비교 | `"temperature > 30.0"` 파싱 후 evaluate()가 올바른 결과 반환 |
> | 2 | 파싱 — 문자열 비교 | `"status == ON"` 파싱 후 evaluate()가 올바른 결과 반환 |
> | 3 | 모든 연산자 | `>`, `>=`, `<`, `<=`, `==`, `!=` 각각에 대해 올바른 비교 |
> | 4 | 잘못된 표현식 | 파싱 불가한 문자열에 대해 적절한 예외 발생 |
> | 5 | 필드 없음 | 메시지에 해당 필드가 없을 때의 동작 확인 |
>
> **테스트 항목 — CompositeRuleNode (도전)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | AND — 모두 만족 | 두 조건 모두 true일 때 match |
> | 2 | AND — 하나 불만족 | 하나라도 false이면 mismatch |
> | 3 | OR — 하나 만족 | 하나만 true여도 match |
> | 4 | OR — 모두 불만족 | 모두 false이면 mismatch |
> | 5 | 빈 조건 | 조건이 없을 때의 기본 동작 (AND: match, OR: mismatch) |
>
> **테스트 항목 — TimeWindowRuleNode (도전)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 기준 미달 → pass | 시간 창 내 조건 만족 횟수가 threshold 미만이면 pass |
> | 2 | 기준 도달 → alert | 시간 창 내 조건 만족 횟수가 threshold 이상이면 alert |
> | 3 | 시간 창 만료 | windowMs 이전의 이벤트는 카운트에서 제외됨 |
> | 4 | 조건 불만족 메시지 | 조건 불만족 메시지는 이벤트로 기록되지 않음 |
>
> **테스트 항목 — MQTT↔MODBUS 통합 플로우 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | MQTT 수신 → Rule 분기 | MQTT로 발행한 메시지가 RuleNode를 통해 올바르게 분기됨 |
> | 2 | Rule match → MODBUS 쓰기 | 규칙 만족 시 MODBUS 레지스터에 값이 기록됨 |
> | 3 | Rule match → MQTT 알림 | 규칙 만족 시 알림 토픽에 메시지가 발행됨 |
> | 4 | End-to-End 흐름 | 전체 파이프라인이 중단 없이 동작 |

---

## Step 5 — 통합 테스트 및 시뮬레이션

### 5.1 주제

Step 1~4에서 구현한 모든 프로토콜 노드와 Rule 처리를 통합하여, 실제 IoT 환경을 시뮬레이션하고 End-to-End 테스트를 수행한다.

### 5.2 테스트 환경 구성

```
┌──────────────┐     ┌──────────────────────────────────┐     ┌──────────────────┐
│  Mosquitto   │     │         FBP Rule Engine           │     │ MODBUS 시뮬레이터  │
│  MQTT Broker │←──→│                                    │←──→│                   │
│  (localhost   │     │  MqttSub → Rule → MqttPub        │     │  Slave ID 1       │
│   :1883)     │     │                 → ModbusWriter    │     │  Port 5020        │
│              │     │  TimerNode → ModbusReader          │     │                   │
└──────────────┘     └──────────────────────────────────┘     └──────────────────┘
```

**Mosquitto Broker 설정** (`mosquitto.conf`):
```
listener 1883
allow_anonymous true
log_type all
```

**MODBUS 시뮬레이터**: Step 3에서 구현한 `ModbusTcpSimulator`를 사용한다. 테스트 시작 시 시뮬레이터를 구동하고, 종료 시 정리한다.

### 5.3 테스트 시나리오

**시나리오 1: MQTT → Rule → MQTT (순수 MQTT)**

1. `mosquitto_pub`로 `sensor/temp`에 `{"value": 35.0}` 발행
2. MqttSubscriberNode가 수신
3. RuleNode(`value > 30`)가 match로 분기
4. MqttPublisherNode가 `alert/temp`에 발행
5. `mosquitto_sub`로 알림 수신 확인

**시나리오 2: Timer → MODBUS Reader → Rule → MODBUS Writer**

1. TimerNode가 1초마다 트리거
2. ModbusReaderNode가 시뮬레이터의 온도 레지스터를 읽음
3. RuleNode가 임계값 평가
4. ModbusWriterNode가 제어 레지스터에 명령 기록
5. 시뮬레이터에서 제어 레지스터 값 변경 확인

**시나리오 3: MQTT → Rule → MODBUS (크로스 프로토콜)**

1. MQTT로 센서 데이터 수신
2. Rule 평가
3. MODBUS 장비에 제어 명령 전송

### 5.4 에러 시뮬레이션

정상 동작뿐 아니라 에러 상황도 테스트해야 한다.

| 에러 상황 | 시뮬레이션 방법 | 확인 사항 |
|----------|--------------|----------|
| MQTT Broker 연결 끊김 | Broker 프로세스를 종료 후 재시작 | 자동 재연결 동작, 재연결 후 구독 복원 |
| MODBUS 장비 응답 없음 | 시뮬레이터 중지 | 타임아웃 처리, 에러 포트로 메시지 전달 |
| 잘못된 데이터 수신 | 유효하지 않은 JSON 발행 | JSON 파싱 실패 시 rawPayload 처리 |
| 높은 메시지 빈도 | 0.01초 간격으로 대량 발행 | 메시지 유실 여부, 큐 적체, 메모리 사용량 |
| 네트워크 지연 | Thread.sleep 주입 또는 tc 명령 | 메시지 순서 보장 여부, 타임아웃 설정 |

### 5.5 성능 측정

**측정 항목**:
- 초당 처리 메시지 수 (throughput)
- 메시지 처리 지연 시간 (latency) — 수신부터 최종 출력까지
- 메모리 사용량
- Connection 큐 적체량

**간단한 성능 측정 방법**:
```java
long start = System.nanoTime();
// 메시지 N개 처리
long elapsed = System.nanoTime() - start;
double throughput = N / (elapsed / 1_000_000_000.0);
System.out.println("처리량: " + throughput + " msg/s");
```

### 5.6 참고 자료

- Mosquitto CLI 도구: `mosquitto_pub`, `mosquitto_sub`
- JUnit 5 Tag 기반 테스트 분리: [junit.org/junit5/docs](https://junit.org/junit5/docs/current/user-guide/#running-tests-tags)
- Testcontainers (Docker 기반 통합 테스트): [testcontainers.com](https://testcontainers.com/) — Mosquitto를 테스트 시 자동 구동하는 데 활용 가능

### 과제

> **과제 5-1**: 5.3절의 시나리오 1, 2, 3을 각각 구현하고 동작을 확인하라.
>
> **과제 5-2**: 5.4절의 에러 시뮬레이션을 최소 3가지 이상 수행하라. 각 에러 상황에서의 엔진 동작을 기록하고, 개선이 필요한 부분을 정리하라.
>
> **과제 5-3**: 5.5절의 성능 측정을 수행하라. 초당 100건, 500건, 1000건의 메시지를 처리할 때 각각의 throughput과 latency를 측정한다.
>
> **과제 5-4**: 통합 테스트 클래스를 작성하라.
>
> **테스트 항목 — MQTT 통합 테스트 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | Subscriber → Publisher 파이프라인 | MQTT 수신 메시지가 처리되어 다른 토픽에 발행됨 |
> | 2 | 다중 토픽 구독 | 와일드카드 토픽(`sensor/+`) 구독 시 여러 하위 토픽의 메시지를 수신 |
> | 3 | QoS 1 전달 보장 | QoS 1로 발행한 메시지가 누락 없이 수신됨 |
> | 4 | 재연결 테스트 | Broker 재시작 후 SubscriberNode가 자동 재연결하여 메시지를 수신 |
>
> **테스트 항목 — MODBUS 통합 테스트 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | Reader → 레지스터 읽기 | 시뮬레이터의 레지스터 값이 정확히 읽힘 |
> | 2 | Writer → 레지스터 쓰기 | 기록한 값이 시뮬레이터에서 확인됨 |
> | 3 | Reader → Writer 파이프라인 | 읽은 값을 기반으로 다른 레지스터에 쓰기 성공 |
> | 4 | 연결 끊김 처리 | 시뮬레이터 중지 시 에러 포트로 에러 메시지 전달 |
>
> **테스트 항목 — 크로스 프로토콜 통합 테스트 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | MQTT → Rule → MODBUS | MQTT 수신 → 규칙 평가 → MODBUS 쓰기 전체 경로 동작 |
> | 2 | MODBUS → Rule → MQTT | MODBUS 읽기 → 규칙 평가 → MQTT 발행 전체 경로 동작 |
> | 3 | 복합 플로우 안정성 | 5분간 연속 실행 시 에러 없이 동작 (장기 실행 테스트) |
>
> **과제 5-5**: 전체 테스트 현황을 정리하고, 모든 테스트가 통과하는지 확인하라.

---

## 전체 클래스 구성도

2단계 학습을 마치면 1단계 클래스에 더해 아래와 같은 클래스가 추가된다.

```
com.fbp.engine
├── core/
│   ├── (1단계 클래스들)
│   ├── ProtocolNode.java              (Step 1 — 추상 클래스)
│   ├── ConnectionState.java           (Step 1 — enum)
│   ├── RuleExpression.java            (Step 4 — 조건식 파서, 선택)
│   └── ...
├── message/
│   └── (1단계 클래스들)
├── protocol/
│   ├── ModbusTcpClient.java           (Step 3 — 소켓 기반 MODBUS TCP 클라이언트)
│   ├── ModbusTcpSimulator.java        (Step 3 — 테스트용 슬레이브 시뮬레이터)
│   └── ModbusException.java           (Step 3 — MODBUS 에러)
├── node/
│   ├── (1단계 클래스들)
│   ├── MqttSubscriberNode.java        (Step 2)
│   ├── MqttPublisherNode.java         (Step 2)
│   ├── ModbusReaderNode.java          (Step 3)
│   ├── ModbusWriterNode.java          (Step 3)
│   ├── RuleNode.java                  (Step 4)
│   ├── CompositeRuleNode.java         (Step 4 — 도전)
│   └── TimeWindowRuleNode.java        (Step 4 — 도전)
└── runner/
    └── Main.java
```

---

## 전체 테스트 현황 요약

### 단위 테스트 (외부 시스템 불필요)

| # | 테스트 클래스 | Step | 테스트 수 | 주요 검증 내용 |
|---|-------------|------|:---------:|--------------|
| 1 | `ProtocolNodeTest` | 1 | 7 | 연결 상태 관리, config, 생명주기 |
| 2 | `MqttSubscriberNodeTest` (단위) | 2 | 5 | 포트, 초기 상태, JSON 변환 |
| 3 | `MqttPublisherNodeTest` (단위) | 2 | 3 | 포트, 초기 상태, config |
| 4 | `ModbusTcpClientTest` (프레임) | 3 | 5 | 프레임 조립, MBAP 헤더, Transaction ID |
| 5 | `ModbusExceptionTest` | 3 | 3 | 메시지 포맷, exceptionCode, 상수 |
| 6 | `ModbusReaderNodeTest` (단위) | 3 | 3 | 포트, 초기 상태, config |
| 7 | `ModbusWriterNodeTest` (단위) | 3 | 3 | 포트, 초기 상태, config |
| 8 | `RuleNodeTest` | 4 | 5 | 분기, 포트, null 처리 |
| 9 | `RuleExpressionTest` (선택) | 4 | 5 | 파싱, 연산자, 타입, 에러 |
| 10 | `CompositeRuleNodeTest` (도전) | 4 | 5 | AND/OR 로직 |
| 11 | `TimeWindowRuleNodeTest` (도전) | 4 | 4 | 시간 창, 이벤트 관리 |
| | **단위 테스트 소계** | | **약 48개** | |

### 통합 테스트 (Broker / 시뮬레이터 필요)

MODBUS 통합 테스트는 직접 구현한 `ModbusTcpSimulator`를 `@BeforeEach`에서 시작하므로, 외부 도구 설치 없이 실행 가능하다. MQTT 통합 테스트는 Mosquitto Broker가 필요하므로 `@Tag("integration")`으로 분리한다.

| # | 테스트 클래스 | Step | 테스트 수 | 주요 검증 내용 |
|---|-------------|------|:---------:|--------------|
| 12 | `MqttSubscriberNodeIntTest` | 2 | 4 | Broker 연결, 메시지 수신 |
| 13 | `MqttPublisherNodeIntTest` | 2 | 4 | Broker 연결, 메시지 발행 |
| 14 | `ModbusTcpClientIntTest` | 3 | 7 | 시뮬레이터 연결, 읽기/쓰기, 에러, 타임아웃 |
| 15 | `ModbusTcpSimulatorTest` | 3 | 6 | 시작/종료, FC 03/06 응답, 에러 응답, 다중 클라이언트 |
| 16 | `ModbusReaderNodeIntTest` | 3 | 5 | 시뮬레이터 연결, 레지스터 읽기, 매핑 |
| 17 | `ModbusWriterNodeIntTest` | 3 | 4 | 시뮬레이터 연결, 레지스터 쓰기, 스케일 |
| 18 | `MqttModbusIntegrationTest` | 4 | 4 | 크로스 프로토콜 플로우 |
| 19 | `MqttIntegrationTest` | 5 | 4 | MQTT End-to-End |
| 20 | `ModbusIntegrationTest` | 5 | 4 | MODBUS End-to-End |
| 21 | `CrossProtocolIntegrationTest` | 5 | 3 | MQTT↔MODBUS 복합 |
| | **통합 테스트 소계** | | **약 45개** | |

| | **2단계 전체 합계** | | **약 93개** | |

### 테스트 실행 방법

```bash
# 단위 테스트만 실행 (외부 시스템 불필요)
mvn test -DexcludedGroups=integration

# 통합 테스트 포함 (Broker + 시뮬레이터 실행 필요)
mvn test

# 통합 테스트만 실행
mvn test -Dgroups=integration
```

**pom.xml에 Tag 필터 설정**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <!-- 기본 실행 시 통합 테스트 제외 -->
        <excludedGroups>integration</excludedGroups>
    </configuration>
</plugin>
```

---

## 부록: Maven 의존성 전체

2단계에서 추가되는 의존성을 정리한다.

```xml
<!-- MQTT -->
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
    <version>1.2.5</version>
</dependency>

<!-- MODBUS: 외부 라이브러리 없음 — java.net.Socket으로 직접 구현 -->

<!-- JSON (Jackson) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>

<!-- 테스트 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers (선택 — Docker 기반 통합 테스트) -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```