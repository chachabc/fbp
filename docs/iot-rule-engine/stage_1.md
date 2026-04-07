# FBP IoT Rule Engine — 1단계 학습 자료

### 목표
- Java로 FBP 기본 엔진(노드 생성, 포트 연결, 메시지 흐름)을 직접 구현할 수 있다.

### 전제
- 기본 문법(변수, 조건문, 반복문, 배열), 클래스, 생성자, 메서드 개념은 이미 알고 있다.

### 학습 방식
- 개념 설명 → 최소한의 예제 → 단계별 과제. 과제는 이전 단계의 결과물 위에 쌓아가며, 최종적으로 완성된 FBP 엔진이 된다.

---

## Step 1 — 개발 환경 구축 & FBP 개념 도입

### 1.1 개발 환경

JDK 21과 Maven을 사용한다. 아래 명령으로 프로젝트를 생성한다.

```bash
mvn archetype:generate \
  -DgroupId=com.fbp.engine \
  -DartifactId=fbp-engine \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.5 \
  -DinteractiveMode=false
```

`pom.xml`에서 Java 21과 JUnit 5를 설정한다.

```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>

<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 1.2 Flow-based Programming이란?

일반적인 프로그래밍은 함수가 함수를 호출하는 **제어 흐름** 방식이다. FBP는 다르다. 독립적인 처리 단위(Node) 사이를 메시지(Information Packet)가 흘러가는 **데이터 흐름**으로 프로그램을 구성한다.

공장의 조립 라인에 비유하면:

```
[부품 공급] → 컨베이어 벨트 → [조립] → 컨베이어 벨트 → [검수] → 컨베이어 벨트 → [포장]
```

각 작업대(Node)는 자기 할 일만 한다. 컨베이어 벨트(Connection)가 물건(Message)을 다음 작업대로 옮긴다. 작업대를 교체하거나 순서를 바꾸기 쉽다.

#### FBP의 5가지 핵심 요소

| 요소 | 역할 | 비유 |
|------|------|------|
| **Node** (Component) | 데이터를 처리하는 독립 단위 | 작업대 |
| **Port** (In/Out) | 노드의 입구와 출구 | 작업대의 입출력 선반 |
| **Connection** (Edge) | 포트 사이를 연결하는 통로 | 컨베이어 벨트 |
| **Message** (IP) | 노드 사이를 이동하는 데이터 | 벨트 위의 부품 |
| **Flow** (Network) | 노드와 연결의 전체 구성도 | 공장 배치도 |

IoT 환경에서 이를 적용하면:

```
[온도센서] →(측정값)→ [임계값 필터] →(초과분)→ [알림 전송]
                          ↓(정상)
                     [로그 저장]
```

각 노드가 독립적이므로, "알림 전송"을 "장비 제어"로 바꾸거나 중간에 "데이터 변환"을 끼워 넣기가 쉽다.

#### 우리가 만들 엔진의 전체 구조

```
┌─────────────────────────────────────────────┐
│                  FlowEngine                 │
│  ┌───────────────────────────────────────┐  │
│  │              Flow                     │  │
│  │  [Node A]──Connection──[Node B]       │  │
│  │      │                    │           │  │
│  │  OutPort              InPort          │  │
│  │  [Node B]──Connection──[Node C]       │  │
│  └───────────────────────────────────────┘  │
│  - Register, start, and stop Flows          │
│  - Run each Node in a separate thread       │
└─────────────────────────────────────────────┘
```

### 1.3 프로젝트 패키지 구조

```
src/main/java/com/fbp/engine/
├── core/       ← 엔진 핵심 (Node, Port, Connection, Flow, FlowEngine)
├── node/       ← 구체적 노드 구현체
├── message/    ← 메시지(IP) 정의
└── runner/     ← 엔진 실행 진입점
```

### 과제

> **과제 1-1**: Maven 프로젝트를 생성하고 `mvn clean compile`이 성공하는 것을 확인하라. 위 패키지 구조를 생성하라.
>
> **과제 1-2**: FBP 엔진에 필요한 핵심 클래스 목록을 작성하라. 클래스 이름, 소속 패키지, 역할(한 줄)을 표로 정리하라.
>
> **과제 1-3**: 본인만의 FBP 엔진 아키텍처 다이어그램을 그려라. 다음 질문에 답하면서 그린다.
> - 노드와 노드 사이에 데이터는 어떤 경로로 전달되는가?
> - 노드가 동시에 동작하려면 무엇이 필요한가?
> - 플로우를 "실행"한다는 것은 구체적으로 무엇을 의미하는가?

---

## Step 2 — 인터페이스·제네릭 복습 & Node/Message 설계

### 2.1 인터페이스

인터페이스는 "이 타입은 반드시 이런 기능을 제공해야 한다"는 **계약(contract)** 이다. FBP 엔진에서 다양한 종류의 노드(센서, 필터, 알림 등)를 엔진이 동일한 방식으로 관리하려면, 모든 노드가 같은 인터페이스를 구현해야 한다.

```java
// 예시: 인터페이스와 다형성
public interface Processable {
    void process(String data);
}

// 어떤 구현이든 Processable 타입으로 다룰 수 있음
Processable p = new UpperCaseProcessor();
p.process("hello");  // 구현에 따라 동작이 달라짐
```

### 2.2 제네릭

제네릭은 타입을 파라미터로 받아 여러 타입에 대해 동작하도록 한다. FBP Message의 페이로드에는 온도(Double), 장비 ID(String), 상태(Boolean) 등이 섞여 있다. `Map<String, Object>`에 담되, 꺼낼 때 캐스팅을 편하게 하는 제네릭 메서드가 유용하다.

```java
// 제네릭 메서드 예시
@SuppressWarnings("unchecked")
public <T> T get(String key) {
    return (T) map.get(key);
}

// 사용: 캐스팅 코드 불필요
Double temp = message.get("temperature");
```

### 2.3 설계 가이드

**Node 인터페이스** — 모든 노드가 구현할 최소한의 계약:
- 노드의 고유 ID를 반환하는 메서드
- 메시지를 처리하는 메서드

**Message 클래스** — 노드 간 전달되는 데이터 패킷:
- 고유 ID (UUID)
- 페이로드 (`Map<String, Object>`)
- 타임스탬프
- **불변(immutable)**으로 설계한다 — 나중에 여러 스레드가 동시에 같은 메시지를 읽어도 안전하도록

불변 설계의 핵심: 생성자에서 `Collections.unmodifiableMap(new HashMap<>(payload))`로 복사본을 만들어 외부 수정을 차단한다. 데이터를 추가해야 하면 기존 메시지를 복사하여 **새 메시지를 생성**한다.

### 과제

> **과제 2-1**: `core` 패키지에 `Node` 인터페이스를 작성하라.
> - `String getId()`
> - `void process(Message message)`
>
> **과제 2-2**: `message` 패키지에 `Message` 클래스를 작성하라.
> - 필드: `id`(UUID), `payload`(Map<String, Object>), `timestamp`
> - 생성자에서 ID 자동 생성, 페이로드 불변 복사, 타임스탬프 자동 기록
> - `getPayload()`, `getTimestamp()`, `getId()` 메서드
> - 제네릭 메서드 `<T> T get(String key)` — 페이로드에서 키로 값을 꺼냄
> - `toString()` 오버라이드
>
> **과제 2-3**: `Message`에 다음 메서드를 추가하라.
> - `Message withEntry(String key, Object value)` — 기존 페이로드에 항목을 추가한 **새 Message** 반환 (원본 불변)
> - `boolean hasKey(String key)` — 키 존재 여부 확인
> - `Message withoutKey(String key)` — 특정 키를 제거한 새 Message 반환
>
> **과제 2-4**: `node` 패키지에 `PrintNode`를 구현하라.
>
> **기능 명세 — PrintNode**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `node` |
> | 구현 | `Node` 인터페이스 |
> | 생성자 | `PrintNode(String id)` — 노드 ID를 받아 저장 |
> | 필드 | `id`(String) |
> | `getId()` | 생성 시 지정한 ID를 반환 |
> | `process(Message)` | `[노드ID] 메시지내용` 형식으로 콘솔 출력. 예: `[printer-1] {temperature=25.5}` |
>
> **과제 2-5**: `main` 메서드에서 `Message`를 생성하고, `PrintNode`로 처리하는 코드를 작성하여 동작을 확인하라.
>
> **과제 2-6**: `MessageTest` 클래스를 작성하여 아래 테스트 항목을 모두 검증하라.
>
> **테스트 항목 — Message**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 생성 시 ID 자동 할당 | `getId()`가 null이 아니고 빈 문자열이 아님 |
> | 2 | 생성 시 timestamp 자동 기록 | `getTimestamp()`가 0보다 큼 |
> | 3 | 페이로드 조회 | 생성 시 넣은 key-value를 `get()`으로 꺼낼 수 있음 |
> | 4 | 제네릭 get 타입 캐스팅 | `get("temperature")`의 반환 타입이 Double로 사용 가능 |
> | 5 | 존재하지 않는 키 조회 | `get("없는키")`가 null 반환 |
> | 6 | 페이로드 불변 — 외부 수정 차단 | `getPayload().put()`하면 `UnsupportedOperationException` 발생 |
> | 7 | 페이로드 불변 — 원본 Map 수정 무영향 | Message 생성에 사용한 원본 Map을 수정해도 Message 내용은 변하지 않음 |
> | 8 | withEntry — 새 객체 반환 | `withEntry()`가 반환한 Message와 원본은 서로 다른 객체 |
> | 9 | withEntry — 원본 불변 | `withEntry()` 후 원본 Message에 새 키가 없음 |
> | 10 | withEntry — 새 메시지에 값 존재 | 새 Message에서 추가한 키의 값을 조회할 수 있음 |
> | 11 | hasKey — 존재하는 키 | `hasKey("temperature")`가 true |
> | 12 | hasKey — 없는 키 | `hasKey("없는키")`가 false |
> | 13 | withoutKey — 키 제거 확인 | 반환된 Message에서 해당 키가 없음 |
> | 14 | withoutKey — 원본 불변 | 원본 Message에는 해당 키가 여전히 있음 |
> | 15 | toString 포맷 | `toString()`이 null이 아니고, payload 내용을 포함 |
>
> **과제 2-7**: `PrintNodeTest` 클래스를 작성하여 아래 테스트 항목을 모두 검증하라.
>
> **테스트 항목 — PrintNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | getId 반환 | 생성 시 지정한 ID가 `getId()`로 반환됨 |
> | 2 | process 정상 동작 | `process()` 호출 시 예외가 발생하지 않음 |
> | 3 | Node 인터페이스 구현 | PrintNode 인스턴스를 `Node` 타입 변수에 대입 가능 |

---

## Step 3 — Port와 Connection 설계

### 3.1 Port가 필요한 이유

Step 2에서는 `printer.process(msg)`처럼 노드를 직접 호출했다. 하지만 FBP에서 노드는 서로를 직접 알지 못한다. **Port**라는 입출력 지점을 통해 간접적으로 연결된다.

```
직접 호출:   NodeA 내부에서 NodeB.process() 호출 → NodeA가 NodeB를 알아야 함
Port 사용:   NodeA → OutputPort → Connection → InputPort → NodeB → NodeA는 NodeB를 모름
```

이렇게 하면 NodeB를 NodeC로 교체해도 NodeA의 코드를 바꿀 필요가 없다.

### 3.2 설계 가이드

**InputPort** 인터페이스:
- `getName()` — 포트 이름 (예: "in", "trigger")
- `receive(Message)` — Connection으로부터 메시지를 수신

**OutputPort** 인터페이스:
- `getName()` — 포트 이름 (예: "out", "error")
- `connect(Connection)` — Connection을 연결
- `send(Message)` — 연결된 모든 Connection으로 메시지 전송

**Connection** 클래스:
- 내부에 `Queue<Message>` 버퍼를 가짐
- `deliver(Message)` — OutputPort가 호출. 버퍼에 넣고 대상 InputPort에 전달
- `setTarget(InputPort)` — 도착지 설정

**DefaultOutputPort**: 하나의 포트에 여러 Connection을 연결할 수 있다 (1:N 전송). `send()`에서 모든 Connection의 `deliver()`를 호출한다.

**DefaultInputPort**: 소속 노드(`owner`)를 생성 시 받는다. `receive()`에서 `owner.process(message)`를 호출한다.

### 3.3 연결 구조

```
GeneratorNode                                      PrintNode
  ┌──────────┐     Connection      ┌──────────┐
  │ OutputPort│──→ [Queue] ──→   │ InputPort │ → process()
  └──────────┘                     └──────────┘
```

### 과제

> **과제 3-1**: `core` 패키지에 `InputPort`, `OutputPort` 인터페이스를 작성하라.
>
> **과제 3-2**: `Connection` 클래스를 구현하라.
> - `id`(String), `buffer(Queue<Message>)`, `target`(InputPort) 필드
> - `deliver(Message)` — 버퍼에 넣고 target이 있으면 꺼내서 `target.receive()` 호출
> - `setTarget(InputPort)` — 도착지 설정
> - `getBufferSize()` — 현재 버퍼 크기
>
> **과제 3-3**: `DefaultOutputPort`를 구현하라.
> - `List<Connection>`을 내부에 관리
> - `connect(Connection)` — 리스트에 추가
> - `send(Message)` — 모든 Connection의 `deliver()` 호출
>
> **과제 3-4**: `DefaultInputPort`를 구현하라.
> - 생성 시 소속 `Node`(owner)를 받음
> - `receive(Message)` — `owner.process(message)` 호출
>
> **과제 3-5**: `GeneratorNode`를 구현하라.
>
> **기능 명세 — GeneratorNode**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `node` |
> | 구현 | `Node` 인터페이스 |
> | 생성자 | `GeneratorNode(String id)` — 노드 ID를 받아 저장 |
> | 필드 | `id`(String), `outputPort`(OutputPort) |
> | `getId()` | 생성 시 지정한 ID를 반환 |
> | `process(Message)` | GeneratorNode는 외부 메시지를 처리하지 않으므로 빈 구현 |
> | `generate(String key, Object value)` | 주어진 key-value로 새 Message를 생성하고 OutputPort의 `send()`로 전송 |
> | `getOutputPort()` | 내부 OutputPort 인스턴스를 반환 |
>
> **과제 3-6**: `PrintNode`를 수정하여 InputPort를 가지도록 하라.
>
> **기능 명세 — PrintNode (Port 추가 버전)**
>
> | 항목 | 내용 |
> |------|------|
> | 변경 사항 | Step 2의 PrintNode에 InputPort를 추가 |
> | 추가 필드 | `inputPort`(InputPort) — `DefaultInputPort("in", this)`로 생성 |
> | `getInputPort()` | 내부 InputPort 인스턴스를 반환 |
> | 동작 변경 | Connection → InputPort → `receive()` → `process()` 흐름으로 메시지를 수신하여 출력. 기존 `process()` 로직(콘솔 출력)은 동일 |
>
> **과제 3-7**: `main`에서 아래 연결을 구성하고 메시지가 흘러가는지 확인하라.
> ```
> GeneratorNode → Connection → PrintNode
> ```
> `generator.generate("temperature", 25.5)` 호출 시 PrintNode에서 출력되어야 한다.
>
> **과제 3-8**: 1:N 연결을 테스트하라. 하나의 GeneratorNode에 두 개의 Connection을 연결하고, 각각 다른 PrintNode로 메시지가 전달되는지 확인한다.
>
> **과제 3-9**: `FilterNode`를 구현하라.
>
> **기능 명세 — FilterNode**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `node` |
> | 구현 | `Node` 인터페이스 |
> | 생성자 | `FilterNode(String id, String key, double threshold)` |
> | 필드 | `id`(String), `key`(String), `threshold`(double), `inputPort`(InputPort), `outputPort`(OutputPort) |
> | `getId()` | 생성 시 지정한 ID를 반환 |
> | `process(Message)` | 메시지에서 `key`에 해당하는 값을 꺼내 `threshold` 이상이면 OutputPort로 전달, 미만이면 무시. 키가 없으면 무시 |
> | `getInputPort()` | 내부 InputPort 반환 |
> | `getOutputPort()` | 내부 OutputPort 반환 |
>
> **과제 3-10**: 아래 3단 연결을 구성하고 동작을 확인하라.
> ```
> GeneratorNode → Connection → FilterNode(threshold=30) → Connection → PrintNode
> ```
> 온도 25.0 → PrintNode에 도달하지 않음, 온도 35.0 → PrintNode에서 출력
>
> **과제 3-11**: Step 3에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — Connection**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | deliver 후 target 수신 | deliver()한 메시지가 target InputPort의 receive()를 통해 노드에 전달됨 |
> | 2 | target 미설정 시 동작 | target이 null인 상태에서 deliver()해도 예외가 발생하지 않음 |
> | 3 | 버퍼 크기 확인 | deliver() 후 getBufferSize()가 예상값과 일치 |
> | 4 | 다수 메시지 순서 보장 | 여러 메시지를 deliver()하면 전달 순서가 보장됨 |
>
> **테스트 항목 — DefaultOutputPort**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 단일 Connection 전달 | send()하면 연결된 Connection에 메시지가 전달됨 |
> | 2 | 다중 Connection 전달 (1:N) | 2개의 Connection을 연결하고 send()하면 양쪽 모두 메시지를 수신 |
> | 3 | Connection 미연결 시 | connect()하지 않고 send()해도 예외가 발생하지 않음 |
>
> **테스트 항목 — DefaultInputPort**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | receive 시 owner 호출 | receive()하면 소속 노드의 process()가 호출됨 |
> | 2 | 포트 이름 확인 | getName()이 생성 시 지정한 이름을 반환 |
>
> **테스트 항목 — FilterNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 조건 만족 시 통과 | threshold 이상인 값을 가진 메시지가 OutputPort로 전달됨 |
> | 2 | 조건 미달 시 차단 | threshold 미만인 값을 가진 메시지가 OutputPort로 전달되지 않음 |
> | 3 | 경계값 처리 | threshold와 정확히 같은 값의 동작 확인 (이상 조건이므로 통과) |
> | 4 | 키 없는 메시지 | 필터링 대상 키가 없는 메시지가 들어왔을 때 예외 없이 처리됨 |
>
> **테스트 항목 — GeneratorNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | generate 메시지 생성 | `generate("key", "value")` 호출 시 OutputPort로 메시지가 전달됨 |
> | 2 | 메시지 내용 확인 | 전달된 메시지의 페이로드에 지정한 key-value가 포함됨 |
> | 3 | OutputPort 조회 | `getOutputPort()`가 null이 아님 |
> | 4 | 다수 generate 호출 | 3번 호출하면 3개의 메시지가 순서대로 전달됨 |
>
> **테스트 항목 — PrintNode (Port 추가 버전)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | InputPort 조회 | `getInputPort()`가 null이 아님 |
> | 2 | InputPort를 통한 수신 | InputPort의 `receive()`를 호출하면 process()가 실행됨 |

---

## Step 4 — 스레드 기초와 동시성

### 4.1 왜 스레드가 필요한가?

Step 3까지는 `generator.generate()`를 호출하면 Connection → PrintNode까지 **한 번에** 실행되었다. 모든 것이 main 스레드 하나에서 순차 실행된다.

실제 FBP에서는 각 노드가 독립적으로 동시에 동작해야 한다. TimerNode가 1초 대기하는 동안 다른 노드들도 동작해야 하기 때문이다. 이를 위해 **스레드**를 사용한다.

### 4.2 Thread와 Runnable

```java
Thread t = new Thread(() -> {
    for (int i = 0; i < 5; i++) {
        System.out.println("[작업] " + i);
        try { Thread.sleep(500); }
        catch (InterruptedException e) { break; }
    }
});
t.start();  // 별도 스레드에서 실행
```

### 4.3 공유 자원 문제

두 스레드가 같은 변수를 동시에 수정하면 결과가 예측불가능하다. `count++`는 실제로 읽기→증가→쓰기 3단계인데, 중간에 다른 스레드가 끼어들 수 있다. `synchronized`로 한 번에 한 스레드만 접근하게 제어한다.

### 4.4 BlockingQueue

FBP Connection에 가장 중요한 도구다.

- `put(item)` — 큐가 가득 차면 빈자리가 생길 때까지 **대기**
- `take()` — 큐가 비어 있으면 데이터가 올 때까지 **대기**

이 특성이 FBP의 Connection에 딱 맞는다. 이전 노드가 메시지를 보내지 않으면, 다음 노드는 자연스럽게 대기한다.

```java
BlockingQueue<String> queue = new LinkedBlockingQueue<>(10);

// 생산자: queue.put(item)  → 큐가 가득 차면 대기
// 소비자: queue.take()     → 큐가 비면 대기
```

### 4.5 Connection 개선 방향

Step 3의 Connection은 `LinkedList` 기반이라 스레드 안전하지 않다. `LinkedBlockingQueue`로 교체하면:
- `deliver()` — `buffer.put(message)` (버퍼에 넣기만 함, target 즉시 호출 안 함)
- `poll()` — `buffer.take()` (메시지가 올 때까지 대기하여 꺼냄)

이 분리가 핵심이다. **생산(deliver)과 소비(poll)를 서로 다른 스레드에서 독립적으로 수행**할 수 있다.

### 과제

> **과제 4-1**: `ArrayList`를 공유 버퍼로 사용하는 생산자-소비자를 구현하고, 문제를 직접 확인하라.
> - 공유 버퍼: `ArrayList<String> buffer = new ArrayList<>();`
> - 생산자 스레드: 0.1초 간격으로 "메시지-0" ~ "메시지-99"를 `buffer.add()`
> - 소비자 스레드: `while` 루프에서 `buffer.isEmpty()`를 검사하고, 비어있지 않으면 `buffer.remove(0)`으로 꺼내서 출력
> - **관찰할 문제들**:
    >   - `ConcurrentModificationException` 또는 `IndexOutOfBoundsException`이 발생하는가?
>   - 소비자가 같은 메시지를 두 번 꺼내거나, 메시지가 유실되는 경우가 있는가?
>   - 소비자의 `while(!buffer.isEmpty())` 루프가 CPU를 100% 점유하는가? (busy-waiting)
>   - 생산자가 끝난 뒤, 소비자가 종료 시점을 어떻게 알 수 있는가?
> - 위 문제들을 기록하고, 왜 발생하는지 분석하라.
>
> **과제 4-2**: 과제 4-1의 문제를 `synchronized`와 `wait()`/`notify()`로 해결한 뒤, `BlockingQueue`로 대체하여 비교하라.
> - **단계 A** — `synchronized` 적용:
    >   - `buffer.add()`와 `buffer.remove(0)`을 `synchronized(buffer)` 블록으로 감싸라
>   - 소비자의 busy-waiting을 `buffer.wait()`으로, 생산자의 추가 후 `buffer.notify()`로 변경하라
>   - 종료 시점 처리를 위해 "END" 종료 신호를 사용하라
>   - 예외가 사라지고, CPU 점유율이 내려가는지 확인하라
> - **단계 B** — `BlockingQueue` 대체:
    >   - 단계 A의 코드를 `LinkedBlockingQueue<String>`로 교체하라
>   - `synchronized`, `wait()`, `notify()`가 모두 사라지는 것을 확인하라
>   - `put()`/`take()`만으로 동일한 동작이 되는 이유를 정리하라
> - **비교 정리**: 세 가지 방식(ArrayList, synchronized, BlockingQueue)의 코드 라인 수, 예외 처리 복잡도, CPU 사용률을 표로 비교하라
>
> **과제 4-3**: `Connection` 클래스를 `BlockingQueue` 기반으로 개선하라.
> - `buffer`를 `LinkedBlockingQueue<Message>`로 변경
> - `deliver(Message)` — `buffer.put(message)` (넣기만 함)
> - `poll()` — `buffer.take()` (꺼내기, 대기 가능)
> - 생성자에서 버퍼 크기를 지정할 수 있도록 오버로딩 (기본값 100)
>
> **과제 4-4**: 두 노드를 별도 스레드에서 실행하라.
> - 생산자 스레드: GeneratorNode가 1초 간격으로 메시지 5개 생성 → Connection에 deliver
> - 소비자 스레드: Connection에서 `poll()`로 메시지를 꺼내 PrintNode.process() 호출
> - 두 스레드가 독립적으로 동작하는지 확인
>
> **과제 4-5**: 3노드 스레드 파이프라인을 구성하라.
> ```
> [Thread-1: GeneratorNode] → Connection-1 → [Thread-2: FilterNode] → Connection-2 → [Thread-3: PrintNode]
> ```
> - Thread-2(FilterNode)는 Connection-1에서 `poll()`로 메시지를 꺼내고, 조건 통과 시 Connection-2에 `deliver()`
> - Thread-3(PrintNode)는 Connection-2에서 `poll()`로 메시지를 꺼내 출력
> - 힌트: 각 스레드는 `while(running)` 루프로 반복 처리. 종료 플래그(`volatile boolean running`)를 사용
>
> **과제 4-6**: 과제 4-5에서 생산 속도와 소비 속도를 다르게 설정해보라.
> - 생산자가 0.1초 간격, 소비자가 1초 간격인 경우 → Connection 버퍼에 메시지가 쌓이는지 `getBufferSize()`로 확인
> - 반대의 경우 → 소비자가 대기하는지 확인
>
> **과제 4-7**: BlockingQueue 기반 Connection에 대한 테스트를 작성하라.
>
> **테스트 항목 — Connection (BlockingQueue 버전)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | deliver-poll 기본 동작 | deliver()한 메시지를 poll()로 꺼낼 수 있음 |
> | 2 | 메시지 순서 보장 | 3개 메시지를 deliver()하면 poll() 순서가 FIFO |
> | 3 | 멀티스레드 deliver-poll | 별도 스레드에서 deliver하고, 다른 스레드에서 poll하여 수신 성공 (`CountDownLatch` 활용) |
> | 4 | poll 대기 동작 | deliver 전에 poll()을 호출한 스레드가 메시지 도착까지 블로킹됨 (타임아웃 내 수신 확인) |
> | 5 | 버퍼 크기 제한 | 버퍼 크기 2로 생성한 Connection에 3개 deliver 시도 → 3번째가 블로킹됨 (별도 스레드에서 확인) |
> | 6 | 버퍼 크기 조회 | deliver() 후 getBufferSize()가 예상 값과 일치 |

---

## Step 5 — 추상 클래스·설계 패턴과 AbstractNode

### 5.1 왜 추상 클래스가 필요한가?

Step 2~4에서 만든 노드들(PrintNode, GeneratorNode, FilterNode)에 공통 코드가 반복된다.
- 모든 노드에 `id` 필드와 `getId()`가 있음
- 많은 노드에 InputPort/OutputPort 관리 코드가 있음
- 노드 시작/종료 시 자원 정리가 필요함

이 공통 로직을 **추상 클래스**로 추출하면, 새 노드마다 반복 코드를 작성하지 않아도 된다.

- **인터페이스**: "무엇을 할 수 있는가" (계약)
- **추상 클래스**: "공통적으로 어떻게 하는가" (구현 포함)

### 5.2 생명주기

노드의 생명주기는 **initialize → (process 반복) → shutdown** 이다.
- `initialize()` — 엔진이 플로우를 시작할 때 호출. 자원 할당.
- `process(Message)` — 메시지가 올 때마다 호출. 핵심 로직.
- `shutdown()` — 엔진이 플로우를 정지할 때 호출. 자원 해제.

### 5.3 Template Method 패턴

AbstractNode의 `process()`가 공통 흐름(전처리 → 핵심 로직 → 후처리)을 정의하고, 핵심 로직(`onProcess()`)만 하위 클래스에 위임한다. 나중에 로깅이나 성능 측정 등 공통 기능을 `process()`에 추가하면 모든 노드에 자동 적용된다.

```
AbstractNode
├── process(message)        ← 공통 흐름 (전처리 → onProcess → 후처리)
│   └── onProcess(message)  ← 하위 클래스가 구현
├── initialize()            ← 오버라이드 가능
└── shutdown()              ← 오버라이드 가능
```

### 5.4 AbstractNode 설계 가이드

- `id` 필드와 `getId()` — 공통
- `Map<String, InputPort> inputPorts`, `Map<String, OutputPort> outputPorts` — 포트 관리
- `addInputPort(String name)` — DefaultInputPort를 생성하여 맵에 등록 (protected)
- `addOutputPort(String name)` — DefaultOutputPort를 생성하여 맵에 등록 (protected)
- `getInputPort(String name)`, `getOutputPort(String name)` — 포트 조회
- `send(String portName, Message message)` — 출력 포트로 메시지 전송 (protected)
- `abstract void onProcess(Message message)` — 하위 클래스가 구현할 핵심 로직

### 과제

> **과제 5-1**: `Node` 인터페이스에 `initialize()`와 `shutdown()` 메서드를 추가하라.
>
> **과제 5-2**: `AbstractNode` 추상 클래스를 구현하라.
> - 위 설계 가이드의 모든 기능을 포함
> - `process()`에서 `onProcess()` 호출 전후에 간단한 로그를 출력하라 (예: `"[nodeId] processing message..."`)
> - `onProcess(Message message)`는 abstract로 선언
>
> **과제 5-3**: Step 2의 `PrintNode`를 `AbstractNode`를 상속하도록 리팩토링하라.
>
> **기능 명세 — PrintNode (AbstractNode 기반)**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `PrintNode(String id)` — `super(id)` 호출 후 `addInputPort("in")` |
> | 포트 | InputPort `"in"` 1개 |
> | `onProcess(Message)` | `[노드ID] 메시지내용` 형식으로 콘솔 출력 (Step 2와 동일 로직) |
> | 제거 대상 | 직접 관리하던 `id` 필드, `getId()`, InputPort 필드 — 모두 AbstractNode가 제공 |
>
> 기존 코드 대비 얼마나 간결해졌는지 비교하라.
>
> **과제 5-4**: Step 3의 `FilterNode`를 `AbstractNode`를 상속하도록 리팩토링하라.
>
> **기능 명세 — FilterNode (AbstractNode 기반)**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `FilterNode(String id, String key, double threshold)` — `super(id)` 호출 후 `addInputPort("in")`, `addOutputPort("out")` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `key`(String), `threshold`(double) — 필터 조건 |
> | `onProcess(Message)` | 메시지에서 `key` 값을 꺼내 `threshold` 이상이면 `send("out", message)`, 미만이면 무시 |
> | 제거 대상 | 직접 관리하던 포트 필드, `getId()` — AbstractNode가 제공 |
>
> **과제 5-5**: `TimerNode`를 `AbstractNode`를 상속하여 구현하라.
>
> **기능 명세 — TimerNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `TimerNode(String id, long intervalMs)` — 노드 ID와 발생 주기(밀리초)를 받음 |
> | 포트 | OutputPort `"out"` 1개 |
> | 필드 | `intervalMs`(long), `tickCount`(int, 0부터 시작), `scheduler`(ScheduledExecutorService) |
> | `initialize()` | `ScheduledExecutorService`를 생성하고 `scheduleAtFixedRate()`로 주기적 메시지 생성 시작 |
> | 주기 동작 | 매 주기마다 페이로드 `{"tick": tickCount, "timestamp": System.currentTimeMillis()}`인 Message를 생성하여 `send("out", msg)` 호출. `tickCount`는 매번 1 증가 |
> | `shutdown()` | 스케줄러를 종료 (`scheduler.shutdown()`) |
> | `onProcess(Message)` | 빈 구현 — TimerNode는 외부 메시지를 처리하지 않음 |
>
> **과제 5-6**: TimerNode(0.5초 주기) → FilterNode(tick >= 3) → PrintNode 파이프라인을 구성하라.
> - 모든 노드에 `initialize()` 호출
> - 3초 후 모든 노드에 `shutdown()` 호출
> - tick 0, 1, 2는 필터링되고, tick 3 이상만 PrintNode에 출력되는지 확인
>
> **과제 5-7**: `LogNode`를 `AbstractNode`를 상속하여 구현하라.
>
> **기능 명세 — LogNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `LogNode(String id)` — `super(id)` 호출 후 포트 등록 |
> | 포트 | InputPort `"in"` 1개, OutputPort `"out"` 1개 |
> | `onProcess(Message)` | ① `[HH:mm:ss.SSS][노드ID] 메시지내용` 형식으로 콘솔 출력 ② `send("out", message)`로 원본 메시지를 다음 노드에 전달 |
> | 시간 포맷 | `java.time.LocalTime.now()` → `DateTimeFormatter.ofPattern("HH:mm:ss.SSS")` |
> | PrintNode와 차이 | PrintNode는 출력만 하고 끝(종단 노드). LogNode는 출력 후 메시지를 **그대로 전달**하므로 체인 중간에 삽입 가능 |
>
> **과제 5-8**: Step 5에서 구현한 클래스들에 대한 테스트를 작성하라.
>
> **테스트 항목 — AbstractNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | getId 반환 | 생성 시 지정한 ID가 반환됨 |
> | 2 | addInputPort 등록 | `addInputPort("in")` 후 `getInputPort("in")`이 null이 아님 |
> | 3 | addOutputPort 등록 | `addOutputPort("out")` 후 `getOutputPort("out")`이 null이 아님 |
> | 4 | 미등록 포트 조회 | `getInputPort("없는포트")`가 null 반환 |
> | 5 | process → onProcess 호출 | `process()` 호출 시 하위 클래스의 `onProcess()`가 실행됨 (간단한 테스트용 하위 클래스 작성) |
> | 6 | send로 메시지 전달 | OutputPort에 Connection을 연결 후 `send()`하면 상대측에서 수신 |
>
> **테스트 항목 — TimerNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | initialize 후 메시지 생성 | initialize() 호출 후 일정 시간 대기하면 OutputPort로 메시지가 전송됨 (CollectorNode 또는 Connection의 poll로 확인) |
> | 2 | tick 증가 | 수신한 메시지들의 tick 값이 0, 1, 2, ... 순서로 증가 |
> | 3 | shutdown 후 정지 | shutdown() 호출 후에는 더 이상 메시지가 생성되지 않음 |
> | 4 | 주기 확인 | 500ms 주기로 설정 시 2초간 대략 4개 메시지가 생성됨 (오차 허용) |
>
> **테스트 항목 — PrintNode (AbstractNode 기반 리팩토링 후)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 포트 구성 확인 | `getInputPort("in")`이 null이 아님 |
> | 2 | process 정상 동작 | 메시지를 process()하면 예외 없이 처리됨 |
> | 3 | AbstractNode 상속 확인 | PrintNode가 AbstractNode의 인스턴스임 (`instanceof`) |
>
> **테스트 항목 — FilterNode (AbstractNode 기반 리팩토링 후)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 조건 만족 → send 호출 | threshold 이상인 메시지가 OutputPort로 전달됨 |
> | 2 | 조건 미달 → 차단 | threshold 미만인 메시지가 OutputPort로 전달되지 않음 |
> | 3 | 포트 구성 확인 | `getInputPort("in")`과 `getOutputPort("out")`이 null이 아님 |
>
> **테스트 항목 — LogNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 메시지 통과 전달 | 수신한 메시지가 그대로 OutputPort로 전달됨 (내용 동일) |
> | 2 | 중간 삽입 가능 | A → LogNode → B 연결에서 A가 보낸 메시지를 B가 수신 |

---

## Step 6 — 노드 라이브러리 확장 및 다중 노드 플로우

### 6.1 다양한 노드 만들기

FBP 엔진의 유용성은 얼마나 다양한 노드를 조합할 수 있느냐에 달려 있다. 이제까지 만든 노드(PrintNode, FilterNode, TimerNode, LogNode)에 더해 유틸리티 노드들을 추가하여 엔진의 활용도를 높인다.

### 6.2 TransformNode 설계 가이드

데이터 변환을 수행하는 범용 노드다. 변환 로직을 생성 시 `Function<Message, Message>` 으로 주입받으면, 변환마다 새 클래스를 만들 필요 없이 람다식으로 간결하게 정의할 수 있다.

```java
// 사용 예시 (구현은 직접 하라)
TransformNode f2c = new TransformNode("f2c", msg -> {
    Double fahrenheit = msg.get("temperature");
    double celsius = (fahrenheit - 32) * 5.0 / 9.0;
    return msg.withEntry("temperature", celsius);
});
```

### 6.3 다중 노드 연결 시 주의점

노드가 4~5개로 늘어나면 연결 코드가 복잡해진다. 각 연결이 정확한 OutputPort와 InputPort를 잇고 있는지 확인해야 한다. 이것이 Step 7에서 Flow 클래스를 만드는 이유이기도 하다.

### 과제

> **과제 6-1**: `TransformNode`를 `AbstractNode`를 상속하여 구현하라.
>
> **기능 명세 — TransformNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `TransformNode(String id, Function<Message, Message> transformer)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `transformer`(Function<Message, Message>) — 변환 로직 |
> | `onProcess(Message)` | `transformer.apply(message)` 호출. 결과가 null이 아니면 `send("out", result)`, null이면 무시(메시지 필터링 효과) |
> | 특징 | 변환 로직을 람다식으로 주입하므로 클래스를 새로 만들지 않고 다양한 변환을 정의 가능 |
>
> **과제 6-2**: TransformNode를 활용하여 "화씨→섭씨 변환" 플로우를 구성하라.
> ```
> GeneratorNode(화씨 온도 생성) → TransformNode(F→C 변환) → PrintNode
> ```
>
> **과제 6-3**: `SplitNode`를 구현하라.
>
> **기능 명세 — SplitNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `SplitNode(String id, String key, double threshold)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"match"` 1개, OutputPort `"mismatch"` 1개 |
> | 필드 | `key`(String) — 비교 대상 필드명, `threshold`(double) — 분기 기준값 |
> | `onProcess(Message)` | 메시지에서 `key` 값을 꺼내 `threshold` 이상이면 `send("match", message)`, 미만이면 `send("mismatch", message)` |
> | FilterNode와 차이 | FilterNode는 조건 미달 시 메시지를 버리지만, SplitNode는 `"mismatch"` 포트로 분기하여 전달 |
>
> **과제 6-4**: SplitNode를 사용한 분기 플로우를 구성하라.
> ```
>                          ┌→ match    → PrintNode("경고")
> TimerNode → SplitNode ──┤
>                          └→ mismatch → PrintNode("정상")
> ```
> - SplitNode는 tick 값이 3 이상이면 match, 미만이면 mismatch로 분기
> - 양쪽 PrintNode에서 각각 출력되는지 확인
>
> **과제 6-5**: 4노드 파이프라인을 구성하고 동작을 확인하라.
> ```
> TimerNode(1초) → LogNode → FilterNode(tick >= 3) → PrintNode
> ```
> - 모든 노드에 initialize() 호출, 7초 후 shutdown()
> - LogNode에서는 모든 메시지가 출력되고, PrintNode에서는 tick >= 3만 출력되는지 확인
>
> **과제 6-6**: `CounterNode`를 구현하라.
>
> **기능 명세 — CounterNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `CounterNode(String id)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `count`(int, 0부터 시작) — 수신 메시지 누적 카운트 |
> | `onProcess(Message)` | ① `count`를 1 증가 ② `message.withEntry("count", count)`로 카운트가 추가된 **새 Message**를 생성 ③ `send("out", newMessage)` |
> | `shutdown()` | `[노드ID] 총 처리 메시지: N건` 형식으로 최종 카운트를 콘솔에 출력 |
> | 주의 | 원본 메시지를 직접 수정하지 않고 `withEntry()`로 새 메시지를 생성하여 전달 |
>
> **과제 6-7**: `DelayNode`를 구현하라.
>
> **기능 명세 — DelayNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `DelayNode(String id, long delayMs)` — 노드 ID와 지연 시간(밀리초)을 받음 |
> | 포트 | InputPort `"in"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `delayMs`(long) — 지연 시간 |
> | `onProcess(Message)` | `Thread.sleep(delayMs)`로 지정 시간만큼 대기한 후 `send("out", message)`로 메시지를 그대로 전달 |
> | `InterruptedException` | sleep 중 인터럽트 발생 시 `Thread.currentThread().interrupt()` 호출 후 메시지를 전달하지 않음 |
>
> 이 노드를 파이프라인 중간에 넣으면 메시지 흐름이 느려지는지 확인하라.
>
> **과제 6-8**: Step 6에서 구현한 노드들에 대한 테스트를 작성하라.
>
> **테스트 항목 — TransformNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 변환 정상 동작 | 입력 메시지가 transformer 함수에 의해 변환되어 OutputPort로 전달됨 |
> | 2 | null 반환 시 미전달 | transformer가 null을 반환하면 OutputPort로 전달되지 않음 |
> | 3 | 원본 메시지 불변 | 변환 후에도 원본 메시지의 내용은 변하지 않음 |
>
> **테스트 항목 — SplitNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 조건 만족 → match 포트 | threshold 이상인 메시지가 "match" OutputPort로 전달됨 |
> | 2 | 조건 미달 → mismatch 포트 | threshold 미만인 메시지가 "mismatch" OutputPort로 전달됨 |
> | 3 | 양쪽 동시 확인 | 만족/미달 메시지를 각각 보내면 양쪽 포트에서 각각 수신됨 |
> | 4 | 경계값 처리 | threshold와 같은 값의 분기 방향 확인 |
>
> **테스트 항목 — CounterNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | count 키 추가 | 첫 번째 메시지 전달 후 "count" 키 값이 1 |
> | 2 | count 누적 | 3개 메시지 전달 후 마지막 메시지의 "count" 값이 3 |
> | 3 | 원본 키 유지 | 원본 메시지의 다른 키-값이 변환 후에도 유지됨 |
>
> **테스트 항목 — DelayNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 지연 후 전달 | 메시지가 지정된 지연 시간 이후에 OutputPort로 전달됨 (시간 측정) |
> | 2 | 메시지 내용 보존 | 지연 후에도 메시지 내용이 동일함 |

---

## Step 7 — Flow 클래스와 그래프 구조

### 7.1 왜 Flow 클래스가 필요한가?

Step 6의 main 메서드를 보면, 노드 생성 → Connection 생성 → 포트 연결 → initialize → sleep → shutdown을 모두 직접 관리하고 있다. 노드가 10개, 20개로 늘어나면 이 코드가 매우 복잡해진다.

Flow 클래스는 노드와 연결을 하나의 단위로 묶어 관리한다. 전자 회로의 회로도와 같다.

### 7.2 Flow 설계 가이드

```java
Flow flow = new Flow("temperature-monitoring");

// 노드 등록
flow.addNode(new TimerNode("timer", 1000))
    .addNode(new FilterNode("filter", "tick", 3))
    .addNode(new PrintNode("printer"));

// 연결 정의 — "소스노드ID:포트이름" → "대상노드ID:포트이름"
flow.connect("timer", "out", "filter", "in")
    .connect("filter", "out", "printer", "in");

// 실행
flow.initialize();   // 모든 노드 초기화
// ... 실행 ...
flow.shutdown();     // 모든 노드 종료
```

내부적으로 Flow는:
- `Map<String, AbstractNode> nodes` — 등록된 노드
- `List<Connection> connections` — 생성된 연결
- `connect()`에서 소스 노드의 OutputPort와 대상 노드의 InputPort를 찾아 Connection으로 연결
- 존재하지 않는 노드 ID나 포트 이름이면 예외를 발생시킴

### 7.3 플로우 유효성 검증

플로우를 실행하기 전에 검증하면 런타임 오류를 예방할 수 있다.
- 노드가 1개 이상 있는가?
- 연결되지 않은 포트가 있는가? (경고 수준)
- 순환 참조가 있는가? (무한 루프 위험)

### 과제

> **과제 7-1**: `Flow` 클래스를 구현하라.
> - `id`(String), `nodes`(Map<String, AbstractNode>), `connections`(List<Connection>)
> - `addNode(AbstractNode node)` — 노드 등록. `return this`로 메서드 체이닝 지원
> - `connect(sourceNodeId, sourcePort, targetNodeId, targetPort)` — 연결 생성. `return this`
    >   - 노드/포트가 없으면 `IllegalArgumentException` 발생
>   - Connection ID는 `"소스ID:포트->대상ID:포트"` 형식으로 자동 생성
> - `initialize()` — 모든 노드의 initialize() 호출
> - `shutdown()` — 모든 노드의 shutdown() 호출
> - `getNodes()`, `getConnections()`
>
> **과제 7-2**: Step 6 과제의 4노드 파이프라인을 `Flow`를 사용하여 재구성하라. 코드가 얼마나 간결해지는지 비교하라.
>
> **과제 7-3**: Step 6 과제의 SplitNode 분기 플로우를 `Flow`를 사용하여 재구성하라.
>
> **과제 7-4**: `Flow`에 `validate()` 메서드를 구현하라. `List<String>`으로 에러 메시지 목록을 반환한다.
> - 노드가 0개이면 에러
> - 잘못된 노드 ID로 connect() 시 에러 (이미 예외가 발생하겠지만, validate()에서도 재확인)
>
> **과제 7-5**: `validate()`에 순환 참조 탐지를 추가하라.
> - 연결 정보를 바탕으로 방향 그래프를 구성한다
> - 깊이 우선 탐색(DFS)으로 그래프를 순회하면서, 방문 중인 노드를 다시 방문하면 순환이 있는 것이다
> - 힌트: 각 노드의 상태를 `UNVISITED`, `VISITING`, `VISITED` 3가지로 관리
>
> **과제 7-6**: Flow에 대한 테스트를 작성하라.
>
> **테스트 항목 — Flow**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 노드 등록 | addNode() 후 getNodes()에 해당 노드가 포함됨 |
> | 2 | 메서드 체이닝 | addNode().addNode().connect()가 예외 없이 동작 |
> | 3 | 정상 연결 | connect() 후 getConnections()의 크기가 증가 |
> | 4 | 존재하지 않는 소스 노드 ID | connect()에 잘못된 sourceNodeId → `IllegalArgumentException` |
> | 5 | 존재하지 않는 대상 노드 ID | connect()에 잘못된 targetNodeId → `IllegalArgumentException` |
> | 6 | 존재하지 않는 소스 포트 | connect()에 잘못된 sourcePort → `IllegalArgumentException` |
> | 7 | 존재하지 않는 대상 포트 | connect()에 잘못된 targetPort → `IllegalArgumentException` |
> | 8 | validate — 빈 Flow | 노드가 없는 Flow의 validate()가 에러 메시지 포함 |
> | 9 | validate — 정상 Flow | 유효한 Flow의 validate()가 빈 리스트 반환 |
> | 10 | initialize — 전체 호출 | initialize() 시 모든 노드의 initialize()가 호출됨 (TimerNode 등의 동작으로 간접 확인) |
> | 11 | shutdown — 전체 호출 | shutdown() 시 모든 노드의 shutdown()이 호출됨 |
> | 12 | 순환 참조 탐지 (도전) | 순환 연결이 있는 Flow의 validate()가 에러 메시지 포함 |

---

## Step 8 — FlowEngine 구현

### 8.1 FlowEngine의 역할

FlowEngine은 FBP 엔진의 최상위 관리자다.

1. Flow를 등록하고 관리한다.
2. Flow를 시작하면 내부의 모든 노드를 실행한다.
3. Flow를 정지하면 모든 노드를 안전하게 종료한다.
4. 엔진 전체의 상태를 관리한다.

### 8.2 ExecutorService

`ExecutorService`는 Java가 제공하는 스레드 풀이다. 직접 `new Thread()`보다 효율적이다.

```java
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> { /* 작업 */ });
executor.shutdown();  // 모든 작업 완료 후 종료
```

### 8.3 FlowEngine 설계 가이드

- `enum State { INITIALIZED, RUNNING, STOPPED }`
- `Map<String, Flow> flows` — 등록된 플로우
- `register(Flow flow)` — 플로우 등록
- `startFlow(String flowId)` — 유효성 검증 후 flow.initialize()
- `stopFlow(String flowId)` — flow.shutdown()
- `shutdown()` — 모든 플로우 정지
- `getState()`, `getFlows()`

### 과제

> **과제 8-1**: `FlowEngine` 클래스를 구현하라.
>
> **기능 명세 — FlowEngine**
>
> | 항목 | 내용 |
> |------|------|
> | 패키지 | `core` |
> | 생성자 | `FlowEngine()` — 상태를 `INITIALIZED`로 설정, 빈 flows 맵 초기화 |
> | 필드 | `flows`(Map&lt;String, Flow&gt;), `state`(State enum: `INITIALIZED`, `RUNNING`, `STOPPED`) |
> | `register(Flow)` | 플로우를 `flows` 맵에 등록. `[Engine] 플로우 'flowId' 등록됨` 로그 출력 |
> | `startFlow(String flowId)` | ① flows에서 flowId 조회 — 없으면 `IllegalArgumentException` ② `flow.validate()` 호출 — 에러 있으면 `IllegalStateException` ③ `flow.initialize()` 호출 ④ state를 `RUNNING`으로 변경 ⑤ `[Engine] 플로우 'flowId' 시작됨` 로그 출력 |
> | `stopFlow(String flowId)` | 해당 플로우의 `shutdown()` 호출. `[Engine] 플로우 'flowId' 정지됨` 로그 출력 |
> | `shutdown()` | 모든 플로우를 `shutdown()`, state를 `STOPPED`로 변경 |
> | `getState()` | 현재 엔진 상태 반환 |
> | `getFlows()` | 등록된 플로우 맵 반환 |
>
> **과제 8-2**: FlowEngine을 사용하여 Step 7의 플로우를 실행하라.
> ```java
> FlowEngine engine = new FlowEngine();
> engine.register(flow);
> engine.startFlow("monitoring");
> Thread.sleep(5000);
> engine.shutdown();
> ```
>
> **과제 8-3**: FlowEngine에 두 개의 플로우를 동시에 등록하고 실행하라.
> - 플로우 A: TimerNode(0.5초) → PrintNode("A")
> - 플로우 B: TimerNode(1초) → PrintNode("B")
> - 두 플로우의 출력이 각각 독립적으로 동작하는지 확인
>
> **과제 8-4**: 각 Flow에 개별 상태(RUNNING/STOPPED)를 관리하도록 확장하라.
> - `listFlows()` 메서드를 추가하여 모든 플로우의 ID와 상태를 출력
> - 특정 플로우만 stop/start하면 해당 플로우만 상태가 변경되는지 확인
>
> **과제 8-5**: FlowEngine에 간단한 CLI를 추가하라. `Scanner`로 사용자 입력을 받아 처리한다.
> - `list` — 등록된 플로우 목록과 상태 출력
> - `start <id>` — 플로우 시작
> - `stop <id>` — 플로우 정지
> - `exit` — 엔진 종료
> - 예시:
    >   ```
>   fbp> list
>   [1] monitoring  STOPPED
>   fbp> start monitoring
>   [Engine] 플로우 'monitoring' 시작됨
>   fbp> stop monitoring
>   [Engine] 플로우 'monitoring' 정지됨
>   fbp> exit
>   [Engine] 엔진 종료됨
>   ```
>
> **과제 8-6**: FlowEngine에 대한 테스트를 작성하라.
>
> **테스트 항목 — FlowEngine**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 초기 상태 | 생성 직후 getState()가 `INITIALIZED` |
> | 2 | 플로우 등록 | register() 후 getFlows()에 해당 플로우가 포함됨 |
> | 3 | startFlow 정상 | startFlow() 후 state가 `RUNNING` |
> | 4 | startFlow — 없는 ID | 존재하지 않는 flowId → `IllegalArgumentException` |
> | 5 | startFlow — 유효성 실패 | validate() 에러가 있는 Flow → `IllegalStateException` |
> | 6 | stopFlow 정상 | startFlow() 후 stopFlow() → 해당 플로우가 정지됨 |
> | 7 | shutdown 전체 | shutdown() 후 state가 `STOPPED` |
> | 8 | 다중 플로우 독립 동작 | 2개 플로우 등록 후 하나만 stop해도 나머지는 영향 없음 |
> | 9 | listFlows 출력 | 등록된 모든 플로우의 ID와 상태가 조회됨 |

---

## Step 9 — IoT 시나리오 적용 및 추가 노드 구현

### 9.1 IoT 전용 노드

FBP 엔진을 IoT에 적용하려면 센서 데이터 생성, 임계값 판단, 알림 전송 등 도메인 특화 노드가 필요하다.

### 9.2 노드 설계 가이드

**TemperatureSensorNode** — 온도 센서 시뮬레이터:
- InputPort("trigger") — 트리거 메시지를 받으면 온도를 생성
- OutputPort("out")
- 생성 시 min/max 온도 범위를 받음
- 페이로드: `{sensorId, temperature, unit, timestamp}`

**ThresholdFilterNode** — 임계값 기반 분기:
- InputPort("in")
- OutputPort("alert") — 임계값 초과
- OutputPort("normal") — 정상 범위
- 생성 시 대상 필드명과 임계값을 받음

**AlertNode** — 경고 출력:
- InputPort("in")
- 수신한 메시지에서 sensorId와 temperature를 꺼내 경고 메시지 출력

### 과제

> **과제 9-1**: `TemperatureSensorNode`를 구현하라.
>
> **기능 명세 — TemperatureSensorNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `TemperatureSensorNode(String id, double min, double max)` |
> | 포트 | InputPort `"trigger"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `min`(double), `max`(double) — 온도 생성 범위 |
> | `onProcess(Message)` | ① `min + Math.random() * (max - min)`으로 랜덤 온도 생성 (소수점 1자리로 반올림) ② 페이로드 `{"sensorId": getId(), "temperature": 값, "unit": "°C", "timestamp": System.currentTimeMillis()}`인 새 Message 생성 ③ `send("out", msg)` |
> | 트리거 방식 | 외부에서 "trigger" 포트로 메시지를 받을 때마다 센서 데이터를 1건 생성. TimerNode와 연결하여 주기적 생성 가능 |
>
> **과제 9-2**: `ThresholdFilterNode`를 구현하라.
>
> **기능 명세 — ThresholdFilterNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `ThresholdFilterNode(String id, String fieldName, double threshold)` |
> | 포트 | InputPort `"in"` 1개, OutputPort `"alert"` 1개, OutputPort `"normal"` 1개 |
> | 필드 | `fieldName`(String) — 비교 대상 필드명, `threshold`(double) — 임계값 |
> | `onProcess(Message)` | 메시지에서 `fieldName` 값을 꺼내 `threshold`를 **초과**하면 `send("alert", message)`, 이하이면 `send("normal", message)`. 키가 없으면 무시 |
> | SplitNode와 차이 | SplitNode는 범용(이상/미만), ThresholdFilterNode는 IoT 특화(초과/이하)이며 포트 이름이 도메인을 반영 |
>
> **과제 9-3**: `AlertNode`를 구현하라.
>
> **기능 명세 — AlertNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `AlertNode(String id)` |
> | 포트 | InputPort `"in"` 1개 |
> | `onProcess(Message)` | 메시지에서 `"sensorId"`와 `"temperature"`를 꺼내 `[경고] 센서 {sensorId} 온도 {temperature}°C — 임계값 초과!` 형식으로 콘솔 출력. 키가 없으면 `[경고] 알 수 없는 센서 데이터` 출력 |
> | 특징 | 종단 노드 — OutputPort 없음. 경고 출력만 수행 |
>
> **과제 9-4**: 위 노드들을 조합하여 온도 모니터링 플로우를 구성하라.
> ```
> [TimerNode(1초)] → [TemperatureSensorNode(15~45도)]
>                         ↓
>                 [ThresholdFilterNode(30도)]
>                   ↓alert           ↓normal
>              [AlertNode]        [LogNode]
> ```
> FlowEngine으로 등록/시작하고, 10초간 실행하라.
>
> **과제 9-5**: `HumiditySensorNode`를 구현하고, 온도 모니터링 플로우에 습도 모니터링을 추가하라.
>
> **기능 명세 — HumiditySensorNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `HumiditySensorNode(String id, double min, double max)` — 예: `min=30, max=90` |
> | 포트 | InputPort `"trigger"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `min`(double), `max`(double) — 습도 생성 범위 |
> | `onProcess(Message)` | ① `min + Math.random() * (max - min)`으로 랜덤 습도 생성 (소수점 1자리) ② 페이로드 `{"sensorId": getId(), "humidity": 값, "unit": "%", "timestamp": System.currentTimeMillis()}`인 새 Message 생성 ③ `send("out", msg)` |
> | 트리거 방식 | TemperatureSensorNode와 동일 — "trigger" 포트로 메시지를 받을 때마다 습도 데이터 1건 생성 |
>
> 습도가 70% 이상이면 별도의 알림을 출력하도록 ThresholdFilterNode과 AlertNode를 연결하라.
>
> **과제 9-6**: `FileWriterNode`를 구현하라.
>
> **기능 명세 — FileWriterNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `FileWriterNode(String id, String filePath)` — 노드 ID와 출력 파일 경로를 받음 |
> | 포트 | InputPort `"in"` 1개 |
> | 필드 | `filePath`(String), `writer`(BufferedWriter) |
> | `initialize()` | `new BufferedWriter(new FileWriter(filePath, true))`로 파일 열기 (append 모드) |
> | `onProcess(Message)` | 메시지의 `toString()` 결과를 `writer.write()` + `writer.newLine()` + `writer.flush()`로 한 줄 기록 |
> | `shutdown()` | `writer.close()`로 파일 핸들 닫기. null 체크 필요 |
> | 특징 | 종단 노드 — OutputPort 없음. 파일 I/O 자원을 생명주기(initialize/shutdown)로 관리하는 예시 |
>
> 온도 모니터링 플로우의 "normal" 경로에 FileWriterNode를 연결하여, 정상 온도 데이터를 파일로 저장하라.
>
> **과제 9-7**: `HumiditySensorNode`에 대한 테스트를 포함하여 작성하라.
>
> **테스트 항목 — HumiditySensorNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 습도 범위 확인 | 생성된 습도가 min~max 범위(30~90) 이내 |
> | 2 | 필수 키 포함 | 출력 메시지에 "sensorId", "humidity", "unit" 키가 존재 |
> | 3 | sensorId 일치 | 메시지의 "sensorId"가 노드 ID와 일치 |
> | 4 | 트리거마다 생성 | 트리거 메시지를 3번 보내면 3개의 출력 메시지 생성 |
>
> **과제 9-8** (도전): `MergeNode`를 설계하고 구현하라.
>
> **기능 명세 — MergeNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `MergeNode(String id)` |
> | 포트 | InputPort `"in-1"` 1개, InputPort `"in-2"` 1개, OutputPort `"out"` 1개 |
> | 필드 | `pending1`(Message), `pending2`(Message) — 각 입력에서 대기 중인 메시지 |
> | `onProcess(Message)` | ① 메시지가 어느 포트로 들어왔는지 판별 (힌트: 메시지에 출처 정보를 추가하거나, InputPort별 process 분기) ② 해당 쪽 pending에 저장 ③ 양쪽 모두 도착했으면 두 페이로드를 합친 새 Message를 생성하여 `send("out", merged)`, pending 초기화 |
> | 매칭 방식 | 순서 기반 — in-1의 N번째 메시지와 in-2의 N번째 메시지를 매칭 |
> | 고민할 점 | 두 입력의 도착 시점이 다를 수 있다. 한쪽만 도착하면 나머지를 기다려야 하며, 스레드 안전성도 고려 필요 |
>
> **테스트 항목 — MergeNode (도전)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 양쪽 입력 수신 | in-1과 in-2 양쪽에서 메시지를 수신할 수 있음 |
> | 2 | 합쳐진 메시지 출력 | 두 입력의 데이터가 하나의 메시지에 합쳐져 OutputPort로 전달됨 |
> | 3 | 한쪽만 도착 시 대기 | 한쪽 입력만 도착하면 출력이 즉시 발생하지 않음 (매칭 대기) |
> | 4 | 포트 구성 확인 | `getInputPort("in-1")`, `getInputPort("in-2")`, `getOutputPort("out")`이 모두 null이 아님 |
>
> **과제 9-9**: Step 9에서 구현한 IoT 노드들에 대한 테스트를 작성하라.
>
> **테스트 항목 — TemperatureSensorNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 온도 범위 확인 | 생성된 온도가 min~max 범위 이내 |
> | 2 | 필수 키 포함 | 출력 메시지에 "sensorId", "temperature", "unit", "timestamp" 키가 모두 존재 |
> | 3 | sensorId 일치 | 메시지의 "sensorId"가 노드 ID와 일치 |
> | 4 | 트리거마다 생성 | 트리거 메시지를 3번 보내면 3개의 출력 메시지 생성 |
>
> **테스트 항목 — ThresholdFilterNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 초과 → alert 포트 | 임계값을 초과하는 메시지가 "alert" 포트로 전달됨 |
> | 2 | 이하 → normal 포트 | 임계값 이하인 메시지가 "normal" 포트로 전달됨 |
> | 3 | 경계값 (정확히 같은 값) | 임계값과 같은 값의 분기 방향 확인 ("초과"이므로 normal) |
> | 4 | 키 없는 메시지 | 대상 필드가 없는 메시지 수신 시 예외 없이 처리 |
> | 5 | 양쪽 동시 검증 | alert와 normal 양쪽에 CollectorNode를 연결하여, 각각 올바른 메시지만 수신되는지 확인 |
>
> **테스트 항목 — AlertNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 정상 처리 | sensorId와 temperature가 포함된 메시지 수신 시 예외 없이 동작 |
> | 2 | 키 누락 시 처리 | "temperature" 키가 없는 메시지 수신 시 예외가 발생하지 않음 |
>
> **테스트 항목 — FileWriterNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 파일 생성 | initialize() 후 지정 경로에 파일이 생성됨 |
> | 2 | 내용 기록 | 메시지 3개를 보낸 후 shutdown(), 파일에 3줄이 기록되어 있음 |
> | 3 | shutdown 후 파일 닫힘 | shutdown() 후 추가 메시지를 보내도 기록되지 않거나 예외 발생 |
>
> **테스트 항목 — 온도 모니터링 통합 플로우**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | alert 경로 검증 | alert 경로의 CollectorNode에 수집된 메시지의 temperature가 모두 임계값 초과 |
> | 2 | normal 경로 검증 | normal 경로의 CollectorNode에 수집된 메시지의 temperature가 모두 임계값 이하 |
> | 3 | 전체 메시지 수 | alert + normal 수집 수의 합이 TimerNode의 tick 수와 일치 |

---

## Step 10 — 통합 테스트와 리팩토링

### 10.1 JUnit 5

테스트는 `src/test/java` 아래에 작성한다. `mvn test`로 실행한다.

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Test
void example() {
    assertEquals(4, 2 + 2);
    assertNotNull(obj);
    assertThrows(Exception.class, () -> { /* 예외 발생 코드 */ });
}
```

테스트에서 노드 출력을 검증하려면, 수신한 메시지를 리스트에 저장하는 **CollectorNode**를 만들면 편리하다. PrintNode처럼 출력하는 대신 `List<Message>`에 쌓아두어, 나중에 `assertEquals`로 검증한다.

### 10.2 전체 테스트 현황 점검

Step 2~9에서 각 클래스별 테스트 항목을 제시했다. 아래는 전체 테스트 항목을 정리한 체크리스트다. **모든 항목이 구현되고 통과하는지 확인하라.**

| 테스트 클래스 | 작성 시점 | 테스트 항목 수 | 상태 |
|-------------|----------|:----------:|------|
| `MessageTest` | Step 2 과제 2-6 | 15개 | ☐ |
| `PrintNodeTest` (Step 2 기본) | Step 2 과제 2-6 | 3개 | ☐ |
| `ConnectionTest` (Queue 버전) | Step 3 과제 3-11 | 4개 | ☐ |
| `DefaultOutputPortTest` | Step 3 과제 3-11 | 3개 | ☐ |
| `DefaultInputPortTest` | Step 3 과제 3-11 | 2개 | ☐ |
| `FilterNodeTest` (Step 3 버전) | Step 3 과제 3-11 | 4개 | ☐ |
| `GeneratorNodeTest` | Step 3 과제 3-11 | 4개 | ☐ |
| `PrintNodeTest` (Step 3 Port 버전) | Step 3 과제 3-11 | 2개 | ☐ |
| `ConnectionTest` (BlockingQueue 버전) | Step 4 과제 4-7 | 6개 | ☐ |
| `AbstractNodeTest` | Step 5 과제 5-8 | 6개 | ☐ |
| `TimerNodeTest` | Step 5 과제 5-8 | 4개 | ☐ |
| `PrintNodeTest` (Step 5 리팩토링 후) | Step 5 과제 5-8 | 3개 | ☐ |
| `FilterNodeTest` (리팩토링 후) | Step 5 과제 5-8 | 3개 | ☐ |
| `LogNodeTest` | Step 5 과제 5-8 | 2개 | ☐ |
| `TransformNodeTest` | Step 6 과제 6-8 | 3개 | ☐ |
| `SplitNodeTest` | Step 6 과제 6-8 | 4개 | ☐ |
| `CounterNodeTest` | Step 6 과제 6-8 | 3개 | ☐ |
| `DelayNodeTest` | Step 6 과제 6-8 | 2개 | ☐ |
| `FlowTest` | Step 7 과제 7-6 | 12개 | ☐ |
| `FlowEngineTest` | Step 8 과제 8-6 | 9개 | ☐ |
| `TemperatureSensorNodeTest` | Step 9 과제 9-8 | 4개 | ☐ |
| `ThresholdFilterNodeTest` | Step 9 과제 9-8 | 5개 | ☐ |
| `AlertNodeTest` | Step 9 과제 9-8 | 2개 | ☐ |
| `FileWriterNodeTest` | Step 9 과제 9-8 | 3개 | ☐ |
| `HumiditySensorNodeTest` | Step 9 과제 9-8 | 4개 | ☐ |
| `MergeNodeTest` | Step 9 과제 9-8 | 4개 | ☐ |
| 온도 모니터링 통합 테스트 | Step 9 과제 9-8 | 3개 | ☐ |
| `CollectorNodeTest` | Step 10 과제 10-1 | 5개 | ☐ |
| 최종 종합 통합 테스트 | Step 10 과제 10-6 | 7개 | ☐ |
| **합계** | | **약 125개** | |

### 10.3 리팩토링 체크리스트

- **네이밍**: 클래스명, 메서드명이 역할을 명확히 드러내는가?
- **중복 제거**: 여러 노드에 반복되는 코드가 AbstractNode에 잘 추출되었는가?
- **에러 처리**: null 체크, 예외 처리가 적절한가?
- **문서화**: public 클래스와 메서드에 Javadoc이 있는가?

### 과제

> **과제 10-1**: 테스트용 `CollectorNode`를 구현하라.
>
> **기능 명세 — CollectorNode**
>
> | 항목 | 내용 |
> |------|------|
> | 상속 | `AbstractNode` |
> | 생성자 | `CollectorNode(String id)` — `super(id)` 호출 후 `addInputPort("in")` |
> | 포트 | InputPort `"in"` 1개 |
> | 필드 | `collected`(List&lt;Message&gt;) — 수신한 메시지를 순서대로 저장하는 리스트. `new ArrayList<>()` 로 초기화 |
> | `onProcess(Message)` | 수신한 메시지를 `collected` 리스트에 `add()` |
> | `getCollected()` | `collected` 리스트를 반환 — 테스트에서 `assertEquals`, `assertTrue` 등으로 검증에 사용 |
> | 용도 | PrintNode 대신 파이프라인 끝에 연결하면, 콘솔 출력 없이 프로그램적으로 메시지 수신 결과를 검증 가능 |
> | 특징 | 종단 노드 — OutputPort 없음. 오직 테스트 목적으로 사용 |
>
> **테스트 항목 — CollectorNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 메시지 수집 | 메시지를 전송하면 `getCollected()` 리스트에 저장됨 |
> | 2 | 수집 순서 보존 | 여러 메시지를 순서대로 전송하면 리스트에 전송 순서대로 저장됨 |
> | 3 | 초기 상태 빈 리스트 | 생성 직후 `getCollected()`가 빈 리스트를 반환 |
> | 4 | InputPort 존재 | "in" 포트가 정상적으로 등록되어 있음 |
> | 5 | 파이프라인 연결 검증 | GeneratorNode → CollectorNode 연결 시, Generator가 보낸 모든 메시지가 Collector에 수집됨 |
>
> **과제 10-2**: 위 전체 테스트 현황 표를 점검하고, 아직 작성하지 못한 테스트를 모두 작성하라. 각 Step의 테스트 항목 표를 참고하여 테스트 메서드를 구현한다.
>
> **과제 10-3**: 모든 테스트를 실행하고 통과하는지 확인하라.
> ```bash
> mvn test
> ```
> 실패하는 테스트가 있다면 원인을 분석하고, 구현 코드 또는 테스트 코드를 수정한다.
>
> **과제 10-4**: 리팩토링을 수행하라.
> - 위 체크리스트를 기준으로 모든 클래스를 점검
> - 리팩토링 후 `mvn test`로 모든 테스트가 여전히 통과하는지 확인
>
> **과제 10-5** (최종 종합): 아래 전체 시나리오를 하나의 Flow로 구성하고 FlowEngine으로 실행하라.
> ```
> [TimerNode(1초)] → [TemperatureSensorNode(15~45도)]
>                          ↓
>                  [ThresholdFilterNode(30도)]
>                    ↓alert           ↓normal
>               [AlertNode]      [LogNode] → [FileWriterNode]
> ```
> 동작 요구사항:
> - FlowEngine으로 플로우를 등록/시작
> - 10초 실행 후 종료
> - 30도 초과 시 콘솔에 경고 출력, 정상 온도는 파일에 기록
>
> **과제 10-6**: 최종 종합 시나리오에 대한 통합 테스트를 작성하라.
>
> **테스트 항목 — 최종 종합 통합 테스트**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 엔진 시작/종료 | FlowEngine이 플로우를 정상 시작하고, shutdown 후 STOPPED 상태 |
> | 2 | alert 경로 정확성 | alert 경로의 CollectorNode에 수집된 메시지의 temperature가 **모두** 30도 초과 |
> | 3 | normal 경로 정확성 | normal 경로의 CollectorNode에 수집된 메시지의 temperature가 **모두** 30도 이하 |
> | 4 | 전체 분기 완전성 | alert 수집 수 + normal 수집 수 = 전체 센서 생성 수 (누락 없음) |
> | 5 | 파일 기록 검증 | FileWriterNode가 기록한 파일의 줄 수 = normal 경로 메시지 수 |
> | 6 | 센서 데이터 형식 | 모든 수집 메시지에 "sensorId", "temperature", "unit" 키가 존재 |
> | 7 | 온도 범위 | 모든 수집 메시지의 temperature가 15.0~45.0 범위 이내 |

---

## 부록: 전체 클래스 구성도

2주간의 학습을 마치면 아래와 같은 클래스 구조가 완성된다.

```
com.fbp.engine
├── core/
│   ├── Node.java                  (인터페이스 — Step 2, 5)
│   ├── AbstractNode.java          (추상 클래스 — Step 5)
│   ├── InputPort.java             (인터페이스 — Step 3)
│   ├── OutputPort.java            (인터페이스 — Step 3)
│   ├── DefaultInputPort.java      (구현 — Step 3)
│   ├── DefaultOutputPort.java     (구현 — Step 3)
│   ├── Connection.java            (Step 3 → Step 4에서 BlockingQueue로 개선)
│   ├── Flow.java                  (Step 7)
│   └── FlowEngine.java           (Step 8)
├── message/
│   └── Message.java               (Step 2)
├── node/
│   ├── PrintNode.java             (Step 2 → Step 5에서 리팩토링)
│   ├── GeneratorNode.java         (Step 3)
│   ├── FilterNode.java            (Step 3 → Step 5에서 리팩토링)
│   ├── TimerNode.java             (Step 5)
│   ├── LogNode.java               (Step 5)
│   ├── TransformNode.java         (Step 6)
│   ├── SplitNode.java             (Step 6)
│   ├── CounterNode.java           (Step 6)
│   ├── DelayNode.java             (Step 6)
│   ├── TemperatureSensorNode.java (Step 9)
│   ├── HumiditySensorNode.java    (Step 9)
│   ├── ThresholdFilterNode.java   (Step 9)
│   ├── AlertNode.java             (Step 9)
│   ├── FileWriterNode.java        (Step 9)
│   ├── MergeNode.java             (Step 9 도전)
│   └── CollectorNode.java         (Step 10 테스트용)
└── runner/
    └── Main.java
```