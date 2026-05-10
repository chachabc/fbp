# FBP IoT Rule Engine — 3단계 학습 자료

### 목표
- 사용자가 JSON/YAML 설정만으로 플로우를 자유롭게 구성하고, 커스텀 노드를 플러그인 형태로 추가할 수 있는 확장형 FBP 엔진을 설계·구현한다.

### 전제
- 1단계(기본 엔진) + 2단계(MQTT, MODBUS, Rule 처리)를 완료한 상태에서 시작한다.

### 학습 방식
- 목표만 제시한다.
- 설계, 구현, 검증의 전 과정을 자율적으로 수행한다.
- 어떤 클래스를 만들어야 하는지, 어떤 패턴을 사용해야 하는지 스스로 결정한다.
- 각 Step에서 **달성해야 할 목표**, **설계 시 스스로 답해야 할 질문**, **검증해야 할 테스트 항목**을 제시한다.

---

## 시작 전 점검

3단계를 시작하기 전에 아래 항목을 점검한다. 하나라도 미달이면 이전 단계를 보완한 후 진행한다.

| 점검 항목 | 확인 |
|-----------|:----:|
| 기본 FBP 엔진이 동작한다 (Node, Port, Connection, Message, Flow, FlowEngine) | ☐ |
| `AbstractNode`를 상속한 커스텀 노드를 자유롭게 추가할 수 있다 | ☐ |
| MQTT 노드(Subscriber/Publisher)가 Broker와 정상 통신한다 | ☐ |
| MODBUS TCP 노드가 Socket 기반으로 동작한다 (외부 라이브러리 없음) | ☐ |
| RuleNode가 조건에 따라 메시지를 필터링/분기한다 | ☐ |
| `mvn test`로 1~2단계의 모든 단위 테스트가 통과한다 | ☐ |
| Git 저장소에 이전 단계의 코드가 정리되어 커밋되어 있다 | ☐ |

---

## Step 1~3 — 기본 엔진 고속 구현 + 확장 아키텍처 설계

### 목표

1. 1단계 Step 1~10 분량의 기본 엔진을 **3일 내에 구현 완료**한다
2. 이후 Step 4~10에서 필요한 **확장 포인트를 미리 설계**한다
3. 아래 세 가지 핵심 아키텍처 요소의 인터페이스와 초기 구현을 완성한다

### 핵심 설계 과제

**A. 노드 레지스트리 (NodeRegistry)**

노드 타입 이름(`"ThresholdFilter"`, `"MqttSubscriber"` 등)으로 노드 인스턴스를 생성할 수 있는 중앙 등록소. 이후 FlowParser가 JSON에서 `"type": "ThresholdFilter"`를 읽었을 때, 이 레지스트리를 통해 노드를 생성한다.

```
NodeRegistry
 ├── register(String typeName, NodeFactory factory)
 ├── create(String typeName, Map<String, Object> config) → Node
 ├── getRegisteredTypes() → Set<String>
 └── isRegistered(String typeName) → boolean
```

**B. 플로우 정의 포맷 설계**

JSON 또는 YAML로 플로우를 선언적으로 정의하는 포맷. 다음 요소를 포함해야 한다:
- 플로우 ID, 이름, 설명
- 노드 목록: 각 노드의 ID, 타입, 설정(config)
- 연결 목록: 소스 노드:포트 → 타겟 노드:포트

**C. 플러그인 아키텍처 개념 설계**

Step 6~7에서 구현할 플러그인 시스템의 기본 구조를 미리 잡아둔다. `ServiceLoader`를 사용할 것인지, 커스텀 ClassLoader를 사용할 것인지, 두 가지를 조합할 것인지 방향을 정한다.

### 스스로 답해야 할 질문

- `NodeFactory`는 함수형 인터페이스(`Function<Map, Node>`)로 충분한가, 별도 인터페이스가 필요한가?
- 한 타입에 대해 여러 Factory를 허용할 것인가? 중복 등록 시 어떻게 처리할 것인가?
- 플로우 정의에서 노드 설정(config)의 타입은 `Map<String, Object>`로 충분한가, 별도 `NodeConfig` 클래스가 필요한가?
- 노드가 여러 개의 입력 포트, 여러 개의 출력 포트를 가질 때 연결 정의는 어떻게 표현할 것인가?

### 구현할 클래스 (예시 — 팀 내 설계에 따라 변경 가능)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `registry/` | `NodeFactory` | 노드 생성 함수형 인터페이스 |
| `registry/` | `NodeRegistry` | 타입명 → Factory 매핑, 노드 인스턴스 생성 |
| `registry/` | `NodeRegistryException` | 등록/조회/생성 실패 시 예외 |

> **테스트 항목 — NodeRegistry**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | register + create | 팩토리 등록 후 `create()`로 노드 인스턴스 생성 |
> | 2 | 미등록 타입 create | 등록되지 않은 타입으로 `create()` 호출 시 `NodeRegistryException` |
> | 3 | 중복 등록 처리 | 동일 타입명으로 두 번 등록 시 정책에 맞게 동작 (덮어쓰기 또는 예외) |
> | 4 | getRegisteredTypes | 등록된 타입 목록이 정확히 반환됨 |
> | 5 | config 전달 | create 시 전달한 config가 노드에 올바르게 적용됨 |
> | 6 | isRegistered | 등록된 타입 → true, 미등록 → false |
> | 7 | null/빈 타입명 | null 또는 빈 문자열로 등록/조회 시 적절한 예외 |

> **테스트 항목 — NodeFactory**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 정상 생성 | config를 받아 올바른 노드 인스턴스 반환 |
> | 2 | 잘못된 config | 필수 설정이 누락된 config 전달 시 예외 |
> | 3 | 람다 구현 | 함수형 인터페이스로 람다 기반 팩토리 등록 가능 |

---

## Step 4~5 — 설정 기반 플로우 엔진

### 목표

1. JSON(필수) 또는 YAML(선택) 파일에서 플로우 정의를 읽어 **자동으로 노드를 생성하고 연결**하는 `FlowParser` 구현
2. 파싱한 결과로 `Flow` 객체를 생성하고 `FlowEngine`에 등록하여 실행
3. **런타임에 플로우를 추가/수정/삭제**할 수 있는 관리 인터페이스 제공

### 플로우 정의 예시 (JSON)

```json
{
  "id": "temperature-monitoring",
  "name": "온도 모니터링 플로우",
  "description": "MQTT 센서 데이터를 수신하여 임계값 초과 시 알림",
  "nodes": [
    {
      "id": "sensor",
      "type": "MqttSubscriber",
      "config": {
        "broker": "tcp://localhost:1883",
        "topic": "sensor/temp",
        "qos": 1
      }
    },
    {
      "id": "rule",
      "type": "ThresholdFilter",
      "config": {
        "field": "value",
        "operator": ">",
        "threshold": 30
      }
    },
    {
      "id": "alert",
      "type": "MqttPublisher",
      "config": {
        "broker": "tcp://localhost:1883",
        "topic": "alert/temp"
      }
    }
  ],
  "connections": [
    { "from": "sensor:out", "to": "rule:in" },
    { "from": "rule:out", "to": "alert:in" }
  ]
}
```

### 스스로 답해야 할 질문

- JSON 파싱에 Jackson을 사용할 것인가, Gson을 사용할 것인가? 이미 2단계에서 Jackson을 사용했다면 일관성을 유지하는 것이 좋은가?
- 플로우 정의의 유효성을 언제 검증할 것인가? 파싱 시점? `Flow` 생성 시점? 실행 시점?
- 연결 정의에서 `"sensor:out"`의 파싱 전략은? 단순 `split(":")`이면 충분한가, 포트 이름에 `:`이 포함될 수 있는가?
- 순환 연결(cycle)을 허용할 것인가? DAG 검증이 필요한가?
- 런타임 중 플로우를 수정할 때 실행 중인 메시지는 어떻게 처리할 것인가?
- YAML 지원을 추가한다면, 파서를 어떻게 추상화할 것인가? (`FlowParser` 인터페이스 + `JsonFlowParser`, `YamlFlowParser`)

### 구현할 클래스 (예시)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `parser/` | `FlowParser` | 플로우 정의 파싱 인터페이스 |
| `parser/` | `JsonFlowParser` | JSON → FlowDefinition 변환 |
| `parser/` | `FlowDefinition` | 플로우 정의 데이터 객체 (노드 목록, 연결 목록) |
| `parser/` | `NodeDefinition` | 노드 정의 (id, type, config) |
| `parser/` | `ConnectionDefinition` | 연결 정의 (from node:port, to node:port) |
| `parser/` | `FlowParserException` | 파싱 실패 시 예외 |
| `engine/` | `FlowManager` | 플로우 CRUD — 런타임 추가/조회/수정/삭제 |

### 핵심 동작 흐름

```
JSON 파일
   │
   ▼
FlowParser.parse(InputStream) → FlowDefinition
   │
   ▼
FlowDefinition + NodeRegistry → Flow 객체 생성
   │  (NodeRegistry로 각 노드 인스턴스 생성, Connection으로 연결)
   ▼
FlowManager.deploy(Flow) → FlowEngine에 등록 및 실행
```

> **테스트 항목 — JsonFlowParser**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 정상 파싱 | 유효한 JSON → FlowDefinition 정상 변환 |
> | 2 | 노드 목록 | 파싱된 FlowDefinition의 노드 수와 각 노드의 id, type, config 일치 |
> | 3 | 연결 목록 | 파싱된 연결의 from/to 정보가 JSON과 일치 |
> | 4 | 필수 필드 누락 — id | 플로우 id가 없으면 `FlowParserException` |
> | 5 | 필수 필드 누락 — nodes | nodes 배열이 없으면 예외 |
> | 6 | 빈 노드 목록 | nodes가 빈 배열이면 예외 또는 경고 |
> | 7 | 잘못된 JSON 형식 | 문법 오류 JSON → 적절한 예외 |
> | 8 | 연결의 포트 파싱 | `"sensor:out"` → sourceNode="sensor", sourcePort="out" |
> | 9 | 잘못된 연결 형식 | `"sensor"` (포트 없음) → 예외 |
> | 10 | 존재하지 않는 노드 참조 | 연결에서 정의되지 않은 노드 id 참조 시 예외 |
> | 11 | 중복 노드 id | 같은 id의 노드가 두 개 이상이면 예외 |
> | 12 | config 타입 보존 | config의 숫자, 문자열, boolean 값이 타입을 유지한 채 파싱됨 |

> **테스트 항목 — FlowDefinition**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 불변성 | 생성 후 노드/연결 목록 수정 불가 (unmodifiable) |
> | 2 | 노드 조회 | `getNode(id)`로 특정 노드 정의 조회 |
> | 3 | 연결 유효성 | 모든 연결이 존재하는 노드를 참조하는지 검증 |

> **테스트 항목 — FlowManager**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | deploy | FlowDefinition으로 플로우 배포 → 실행 상태 확인 |
> | 2 | list | 배포된 플로우 목록 조회 |
> | 3 | getStatus | 특정 플로우의 상태(RUNNING, STOPPED) 조회 |
> | 4 | stop | 실행 중인 플로우 정지 |
> | 5 | restart | 정지된 플로우 재시작 |
> | 6 | remove | 플로우 삭제 — 정지 후 제거 |
> | 7 | 실행 중 삭제 | RUNNING 상태의 플로우 삭제 시 자동 정지 후 삭제 |
> | 8 | 존재하지 않는 id 조작 | 없는 id로 stop/restart/remove 시 예외 |
> | 9 | 중복 id 배포 | 이미 존재하는 id의 플로우 배포 시 정책에 맞게 동작 |
> | 10 | 미등록 노드 타입 | FlowDefinition에 NodeRegistry에 없는 타입이 있으면 배포 실패 |

---

## Step 6~7 — 플러그인 시스템과 커스텀 노드

### 목표

1. Java `ServiceLoader`를 활용한 **플러그인 메커니즘** 구현
2. 외부 JAR 파일로 **커스텀 노드를 추가**할 수 있는 구조
3. 노드 개발자가 인터페이스만 구현하면 엔진에 등록할 수 있는 **Node SDK** 정의

### ServiceLoader 기반 플러그인 구조

```
engine.jar (메인 엔진)
 └── META-INF/services/
       └── com.example.fbp.plugin.NodeProvider  ← SPI 파일

custom-nodes.jar (플러그인)
 └── META-INF/services/
       └── com.example.fbp.plugin.NodeProvider  ← SPI 파일
 └── com/example/custom/
       └── MyCustomNodeProvider.class
```

`NodeProvider` 인터페이스:
```java
public interface NodeProvider {
    /** 이 플러그인이 제공하는 노드 타입 목록 */
    List<NodeDescriptor> getNodeDescriptors();
}

public record NodeDescriptor(
    String typeName,
    String description,
    Class<? extends Node> nodeClass,
    NodeFactory factory
) {}
```

### 스스로 답해야 할 질문

- `ServiceLoader`만으로 충분한가, 런타임에 JAR를 추가하려면 `URLClassLoader`가 필요하지 않은가?
- 플러그인 JAR를 특정 디렉토리(`plugins/`)에 두면 자동 로드하는 방식은 어떻게 구현할 것인가?
- 플러그인 간 의존성 충돌은 어떻게 방지할 것인가?
- 플러그인이 제공하는 노드가 기존 내장 노드와 타입명이 충돌하면 어떻게 할 것인가?
- 플러그인을 런타임에 추가/제거할 수 있어야 하는가? (hot reload)
- Node SDK를 별도 모듈(JAR)로 분리하여 플러그인 개발자에게 최소 의존성만 제공할 수 있는가?

### 구현할 클래스 (예시)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `plugin/` | `NodeProvider` (SPI) | 플러그인이 구현하는 서비스 제공자 인터페이스 |
| `plugin/` | `NodeDescriptor` | 노드 타입 설명 레코드 (typeName, description, class, factory) |
| `plugin/` | `PluginManager` | 플러그인 검색, 로드, NodeRegistry 자동 등록 |
| `plugin/` | `PluginScanner` | plugins/ 디렉토리 스캔, JAR 파일 탐색 |
| `plugin/` | `PluginClassLoader` | URLClassLoader 확장 — 외부 JAR 로드 |
| `plugin/` | `PluginException` | 플러그인 로드/등록 실패 시 예외 |

### 플러그인 로드 흐름

```
엔진 시작
  │
  ▼
PluginManager.loadPlugins()
  │
  ├── (1) ClassPath 내 ServiceLoader<NodeProvider> 스캔
  │
  ├── (2) plugins/ 디렉토리의 JAR 파일 스캔
  │       └── URLClassLoader로 JAR 로드
  │       └── ServiceLoader<NodeProvider> 스캔
  │
  └── (3) 모든 NodeProvider의 NodeDescriptor를 NodeRegistry에 등록
```

> **테스트 항목 — NodeProvider (SPI)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | getNodeDescriptors | 구현체가 올바른 NodeDescriptor 목록 반환 |
> | 2 | 빈 목록 | 노드를 제공하지 않는 Provider → 빈 리스트 반환 |
> | 3 | descriptor 정합성 | 반환된 descriptor의 typeName, factory가 null이 아님 |

> **테스트 항목 — PluginManager**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | ClassPath 플러그인 로드 | ServiceLoader로 ClassPath 내 NodeProvider 자동 발견 및 등록 |
> | 2 | 외부 JAR 로드 | plugins/ 디렉토리의 JAR에서 NodeProvider 발견 및 등록 |
> | 3 | NodeRegistry 자동 등록 | 로드된 플러그인의 노드 타입이 NodeRegistry에 등록됨 |
> | 4 | 타입 충돌 처리 | 내장 노드와 동일한 typeName의 플러그인 노드 → 정책에 맞게 처리 |
> | 5 | 잘못된 JAR | 유효하지 않은 JAR 파일 → 예외 후 나머지 플러그인은 정상 로드 |
> | 6 | plugins 디렉토리 없음 | 디렉토리가 없으면 스캔 건너뜀 (예외 아님) |
> | 7 | 빈 plugins 디렉토리 | 디렉토리는 있지만 JAR가 없으면 정상 (플러그인 0개) |
> | 8 | 플러그인 수 확인 | 복수 JAR 로드 시 전체 등록된 노드 타입 수가 예상과 일치 |

> **테스트 항목 — PluginClassLoader**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | JAR 로드 | 외부 JAR의 클래스를 정상적으로 로드 |
> | 2 | 클래스 격리 | 플러그인 클래스가 엔진의 내부 클래스에 영향을 주지 않음 |
> | 3 | 리소스 해제 | close() 호출 시 JAR 파일 핸들 해제 |
> | 4 | 존재하지 않는 JAR | 없는 경로의 JAR → 예외 |

---

## Step 8 — 모니터링과 관리 API

### 목표

1. 엔진 상태 모니터링: **노드별 처리량, 큐 크기, 에러 수** 등 메트릭 수집
2. `com.sun.net.httpserver.HttpServer` 기반의 **REST API**를 통한 엔진 제어
3. 외부 라이브러리 없이 JDK 내장 HttpServer만 사용

### REST API 엔드포인트

| Method | Path | 설명 | 요청 본문 | 응답 |
|--------|------|------|----------|------|
| `GET` | `/flows` | 실행 중인 플로우 목록 | — | `[{id, name, status}]` |
| `POST` | `/flows` | 새 플로우 배포 | 플로우 정의 JSON | `{id, status}` |
| `DELETE` | `/flows/{id}` | 플로우 중지 및 삭제 | — | `{message}` |
| `GET` | `/flows/{id}/metrics` | 플로우 메트릭 조회 | — | `{nodes: [{id, processed, errors, avgTime}]}` |
| `GET` | `/nodes/{id}/stats` | 노드 상세 통계 | — | `{processed, errors, avgTime, queueSize}` |
| `GET` | `/health` | 엔진 상태 | — | `{status, uptime, flowCount}` |

### 스스로 답해야 할 질문

- 메트릭 수집은 각 노드가 직접 수행할 것인가, 별도 `MetricsCollector`가 AOP(프록시) 방식으로 수집할 것인가?
- 메트릭 저장은 인메모리(ConcurrentHashMap)로 충분한가, 시계열 데이터 보관이 필요한가?
- HTTP 요청 처리 스레드와 FBP 엔진 스레드 사이의 동기화는 어떻게 할 것인가?
- API 인증/인가가 필요한가? (교육 과정에서는 생략할 수 있지만, 프로덕션에서는?)
- `/flows/{id}`에서 `{id}` 경로 변수를 어떻게 추출할 것인가? (`HttpServer`는 경로 변수를 지원하지 않으므로 직접 파싱)
- JSON 응답 직렬화에 Jackson을 재사용할 것인가?

### 구현할 클래스 (예시)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `metrics/` | `MetricsCollector` | 노드/플로우 메트릭 수집 및 조회 |
| `metrics/` | `NodeMetrics` | 노드별 메트릭 데이터 (처리 건수, 에러 수, 평균 처리 시간) |
| `metrics/` | `FlowMetrics` | 플로우 전체 메트릭 집계 |
| `api/` | `HttpApiServer` | HttpServer 기반 REST API 서버 |
| `api/` | `FlowHandler` | `/flows` 엔드포인트 핸들러 |
| `api/` | `MetricsHandler` | `/flows/{id}/metrics`, `/nodes/{id}/stats` 핸들러 |
| `api/` | `HealthHandler` | `/health` 엔드포인트 핸들러 |
| `api/` | `ApiResponse` | HTTP 응답 유틸리티 (상태 코드, JSON 직렬화) |

### 메트릭 수집 구조

```
[Node.process()]
     │
     ▼
MetricsCollector.recordProcessing(nodeId, startTime, success/failure)
     │
     ▼
NodeMetrics (AtomicLong counters)
     │
     ▼
GET /flows/{id}/metrics → FlowMetrics 집계 → JSON 응답
```

> **테스트 항목 — MetricsCollector**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 처리 건수 기록 | recordProcessing 호출 후 처리 건수 증가 |
> | 2 | 에러 건수 기록 | 실패로 기록 시 에러 카운트 증가 |
> | 3 | 평균 처리 시간 | 여러 번 기록 후 평균 처리 시간 계산이 정확함 |
> | 4 | 멀티스레드 안전성 | 10개 스레드에서 동시에 기록해도 카운트가 정확함 |
> | 5 | 노드별 분리 | 서로 다른 노드의 메트릭이 독립적으로 관리됨 |
> | 6 | 리셋 | 메트릭 초기화 후 카운트가 0 |
> | 7 | 존재하지 않는 노드 | 미등록 노드 id로 조회 시 빈 메트릭 또는 null |

> **테스트 항목 — NodeMetrics**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 초기값 | 생성 직후 모든 카운터가 0 |
> | 2 | increment | 처리 건수, 에러 건수 증가 |
> | 3 | 평균 계산 | 처리 시간 합계 / 처리 건수 = 평균 |
> | 4 | 스냅샷 | 현재 메트릭의 불변 스냅샷 반환 |

> **테스트 항목 — HttpApiServer**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 서버 시작/정지 | start() → 포트 바인딩 확인, stop() → 정상 종료 |
> | 2 | GET /health | 200 OK, status 필드 포함 |
> | 3 | GET /flows | 200 OK, 배포된 플로우 목록 반환 |
> | 4 | POST /flows | 유효한 JSON → 201 Created, 플로우 배포 확인 |
> | 5 | POST /flows 잘못된 JSON | 400 Bad Request |
> | 6 | DELETE /flows/{id} | 존재하는 플로우 삭제 → 200 OK |
> | 7 | DELETE /flows/{id} 없는 id | 404 Not Found |
> | 8 | GET /flows/{id}/metrics | 배포된 플로우의 메트릭 JSON 반환 |
> | 9 | 존재하지 않는 경로 | 404 Not Found |
> | 10 | 잘못된 HTTP 메서드 | 405 Method Not Allowed |
> | 11 | 포트 충돌 | 이미 사용 중인 포트로 시작 시 예외 |
> | 12 | Content-Type | 응답 헤더에 `application/json` 포함 |

---

## Step 9 — 고급 플로우 패턴

### 목표

1. **서브플로우(Sub-flow)**: 플로우 안에 플로우를 내포하여 재사용 가능한 단위로 관리
2. **동적 라우팅**: 메시지 내용에 따라 런타임에 출력 경로를 결정
3. **에러 핸들링 플로우**: 노드에서 에러 발생 시 별도 플로우로 분기하여 처리
4. **백프레셔(Backpressure)**: 소비자의 처리 속도가 생산자보다 느릴 때 흐름을 제어

### 각 패턴 개요

**A. 서브플로우 (SubFlow)**

하나의 노드처럼 동작하지만 내부에 별도의 플로우를 포함한다. 복잡한 처리 로직을 캡슐화하여 재사용할 수 있다.

```
[MainFlow]
  sensor → [SubFlow: 데이터 정제] → rule → alert
              │
              └── 내부: parser → validator → normalizer
```

**B. 동적 라우팅 (Dynamic Routing)**

메시지의 내용(필드 값, 타입 등)에 따라 런타임에 어떤 출력 포트로 보낼지 결정한다.

```
             ┌── portA → [온도 처리]
sensor → router ── portB → [습도 처리]
             └── portC → [기압 처리]
```

**C. 에러 핸들링 플로우 (Error Handling)**

노드의 `process()`에서 예외가 발생하면 메시지를 에러 전용 포트로 보내 별도의 에러 처리 플로우에서 처리한다.

```
sensor → transform ──(정상)──→ output
              │
              └──(에러)──→ errorHandler → logger → deadLetterQueue
```

**D. 백프레셔 (Backpressure)**

`Connection`의 큐가 가득 찼을 때 생산자 노드를 일시 정지시키거나 메시지를 드롭/버퍼링하는 전략.

| 전략 | 동작 |
|------|------|
| Block | 큐가 가득 차면 생산자 스레드가 대기 (기본 BlockingQueue 동작) |
| Drop Oldest | 큐가 가득 차면 가장 오래된 메시지 제거 |
| Drop Newest | 큐가 가득 차면 새 메시지 버림 |
| Overflow Buffer | 임시 버퍼(디스크 등)에 저장 |

### 스스로 답해야 할 질문

- 서브플로우의 입출력 포트는 어떻게 외부 플로우의 포트와 연결할 것인가?
- 서브플로우의 수명주기(시작/정지)는 부모 플로우와 어떻게 동기화할 것인가?
- 동적 라우팅에서 라우팅 규칙을 어떻게 정의할 것인가? 코드? 설정 파일?
- 에러 포트를 연결하지 않은 노드에서 에러가 발생하면 어떻게 할 것인가? (기본 에러 핸들러?)
- 백프레셔 전략을 Connection 단위로 설정할 수 있어야 하는가? 플로우 단위?
- 여러 전략을 런타임에 전환할 수 있어야 하는가?

### 구현할 클래스 (예시)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `flow/` | `SubFlowNode` | 내부에 Flow를 포함하는 복합 노드 |
| `node/` | `DynamicRouterNode` | 메시지 기반 동적 라우팅 |
| `node/` | `RoutingRule` | 라우팅 조건 정의 (필드, 연산자, 값 → 포트명) |
| `core/` | `ErrorPort` | 에러 전용 출력 포트 |
| `node/` | `ErrorHandlerNode` | 에러 메시지 수신 및 처리 (로깅, 재시도 등) |
| `node/` | `DeadLetterNode` | 처리 불가 메시지 최종 저장 |
| `core/` | `BackpressureStrategy` | 백프레셔 전략 인터페이스 |
| `core/` | `BackpressureConnection` | 전략 적용 Connection 구현체 |
| `core/` | `DropOldestStrategy` | 가장 오래된 메시지 제거 전략 |
| `core/` | `DropNewestStrategy` | 새 메시지 버림 전략 |

> **테스트 항목 — SubFlowNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 메시지 전달 | 외부 입력 → 서브플로우 내부 → 외부 출력 정상 전달 |
> | 2 | 내부 플로우 실행 | 서브플로우 내부 노드들이 올바른 순서로 처리 |
> | 3 | 수명주기 — 시작 | SubFlowNode 시작 시 내부 플로우도 시작 |
> | 4 | 수명주기 — 정지 | SubFlowNode 정지 시 내부 플로우도 정지 |
> | 5 | 재사용 | 같은 서브플로우 정의를 여러 곳에서 인스턴스화 |
> | 6 | 내부 에러 전파 | 서브플로우 내부에서 에러 발생 시 외부 에러 포트로 전파 |
> | 7 | JSON 정의 | 플로우 JSON에서 서브플로우를 정의하고 파싱 가능 |

> **테스트 항목 — DynamicRouterNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 조건 매칭 | 메시지 필드 값에 따라 올바른 출력 포트로 전달 |
> | 2 | 다중 규칙 | 여러 RoutingRule 중 첫 매칭 규칙의 포트로 전달 |
> | 3 | 기본 포트 | 어떤 규칙도 매칭되지 않으면 default 포트로 전달 |
> | 4 | 규칙 없음 | 규칙이 비어 있으면 모든 메시지가 default로 전달 |
> | 5 | null 필드 | 라우팅 필드가 메시지에 없으면 default 포트 |
> | 6 | 런타임 규칙 변경 | 실행 중 규칙 추가/제거 가능 |
> | 7 | 성능 | 100개 규칙에서도 지연 시간이 허용 범위 내 |

> **테스트 항목 — ErrorPort / ErrorHandlerNode**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 에러 발생 시 분기 | process()에서 예외 → 에러 포트로 메시지 전달 |
> | 2 | 에러 메시지 내용 | 에러 메시지에 원본 메시지, 예외 정보, 노드 id 포함 |
> | 3 | 에러 포트 미연결 | 에러 포트가 연결되지 않았으면 로그 기록 후 계속 |
> | 4 | 정상 처리 시 | 예외 없으면 에러 포트에 메시지 전달하지 않음 |
> | 5 | ErrorHandlerNode 수신 | ErrorHandlerNode가 에러 메시지를 수신하고 처리 |
> | 6 | 재시도 로직 | ErrorHandlerNode에서 재시도 설정 시 원래 노드로 재전달 |
> | 7 | DeadLetterNode | 재시도 초과 시 DeadLetterNode로 전달 |

> **테스트 항목 — BackpressureConnection**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | Block 전략 | 큐 가득 참 → send()가 블로킹됨 (타임아웃으로 확인) |
> | 2 | DropOldest 전략 | 큐 가득 참 + 새 메시지 → 가장 오래된 메시지 제거 확인 |
> | 3 | DropNewest 전략 | 큐 가득 참 + 새 메시지 → 새 메시지가 버려짐 |
> | 4 | 전략 변경 | 런타임에 전략 변경 후 새 전략이 적용됨 |
> | 5 | 큐 크기 설정 | 생성 시 지정한 큐 용량이 적용됨 |
> | 6 | 드롭 카운트 | DropOldest/DropNewest 전략에서 드롭된 메시지 수 메트릭 |
> | 7 | 멀티스레드 | 여러 생산자 스레드에서 동시 전송 시 데이터 손실 없음 |

---

## Step 10 — 통합 검증 및 성능 최적화

### 목표

1. **복합 시나리오 통합 테스트**: MQTT 입력 → 다중 규칙 분기 → MODBUS 출력 + 알림
2. **부하 테스트**: 초당 1,000건 이상의 메시지 처리 성능 검증
3. **메모리 누수 점검**: 장시간 실행 시 메모리 사용량 안정성 확인
4. **스레드 풀 최적화**: 노드 수 대비 최적 스레드 풀 크기 탐색
5. **아키텍처 문서화**: 전체 시스템 구조를 문서로 정리

### 복합 시나리오

```
MQTT Subscriber ─→ JSON Parser ─→ DynamicRouter
                                       │
                   ┌───────────────────┼───────────────────┐
                   ▼                   ▼                   ▼
            ThresholdRule        TimeWindowRule       CompositeRule
                   │                   │                   │
                   ▼                   ▼                   ▼
            ModbusWriter         MqttPublisher        SubFlow[알림]
                   │                                       │
                   ▼                                       ▼
              ErrorHandler                           AlertNode → FileWriter
```

### 부하 테스트 기준

| 항목 | 기준 |
|------|------|
| 처리량(Throughput) | 초당 1,000건 이상 (단순 pass-through 기준) |
| 응답 시간(Latency) | 평균 < 10ms, 99th percentile < 50ms |
| 에러율 | < 0.1% |
| 메모리 | 10분 실행 시 GC 후 힙 사용량이 단조 증가하지 않음 |
| 스레드 | 노드 수 × 2 이하의 스레드로 처리 가능 |

### 스스로 답해야 할 질문

- 부하 테스트는 어떤 도구로 수행할 것인가? 자체 `LoadTester` 클래스? JMH(Java Microbenchmark Harness)?
- 메모리 누수는 어떻게 탐지할 것인가? `Runtime.getRuntime().totalMemory()` 주기적 기록? VisualVM?
- 스레드 풀 크기를 동적으로 조정하는 것이 가능한가? `ThreadPoolExecutor`의 `setCorePoolSize()`?
- 병목 지점을 식별하기 위해 노드별 처리 시간을 프로파일링하는 방법은?
- 아키텍처 문서에는 어떤 내용이 포함되어야 하는가? (구성 요소, 의존 관계, 데이터 흐름, 설계 결정 이유)

### 구현할 클래스 (예시)

| 패키지 | 클래스/인터페이스 | 역할 |
|--------|------------------|------|
| `test/` | `LoadTester` | 대량 메시지 생성 및 처리량 측정 |
| `test/` | `PerformanceResult` | 부하 테스트 결과 데이터 (throughput, latency, errors) |
| `test/` | `MemoryMonitor` | 주기적 메모리 사용량 기록 |
| `engine/` | `ThreadPoolConfig` | 스레드 풀 설정 (core size, max size, queue capacity) |

> **테스트 항목 — 복합 시나리오 통합 테스트 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | JSON 플로우 배포 | JSON 파일에서 플로우 정의 읽기 → 배포 → RUNNING 상태 |
> | 2 | MQTT→Rule→MQTT | MQTT 수신 → 규칙 적용 → 조건 충족 메시지만 MQTT 발행 |
> | 3 | 동적 라우팅 | 센서 타입별로 서로 다른 처리 경로로 분기 |
> | 4 | 에러 핸들링 | 처리 중 에러 발생 → 에러 플로우로 분기 → 로그 기록 |
> | 5 | 서브플로우 | 서브플로우를 포함한 플로우가 정상 동작 |
> | 6 | 백프레셔 | 느린 소비자에서 큐 적체 → 백프레셔 전략 동작 확인 |
> | 7 | MODBUS 연동 | 규칙 충족 시 MODBUS TCP 레지스터에 값 기록 (Socket 기반) |
> | 8 | REST API 연동 | POST /flows로 플로우 배포, GET /flows로 확인, DELETE로 삭제 |
> | 9 | 메트릭 수집 | 플로우 실행 후 GET /flows/{id}/metrics에서 처리량 확인 |
> | 10 | 다중 플로우 | 3개 이상의 플로우를 동시에 배포 및 실행 |

> **테스트 항목 — 부하 테스트 (`@Tag("performance")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 처리량 기준 | 10,000건 메시지 전송 후 초당 처리량 ≥ 1,000건 |
> | 2 | 지연 시간 | 메시지 입력~출력 간 지연 시간 평균 < 10ms |
> | 3 | 에러율 | 10,000건 중 에러 < 0.1% |
> | 4 | 장시간 실행 | 5분 연속 실행 후 메모리 사용량 안정성 (단조 증가 아님) |
> | 5 | 스레드 효율 | 20개 노드 기준 활성 스레드 수 ≤ 40 |
> | 6 | 큐 적체 | 부하 상황에서 Connection 큐 크기가 상한선을 초과하지 않음 |

> **테스트 항목 — REST API 통합 테스트 (`@Tag("integration")`)**
>
> | # | 테스트명 | 검증 내용 |
> |---|---------|----------|
> | 1 | 플로우 CRUD | POST → GET → DELETE 전체 흐름 |
> | 2 | 배포 후 실행 확인 | POST /flows 후 실제로 플로우가 메시지를 처리하는지 확인 |
> | 3 | 메트릭 정확성 | 알려진 수의 메시지를 보낸 후 메트릭의 처리 건수가 일치 |
> | 4 | 동시 요청 | 여러 HTTP 클라이언트가 동시에 API 호출 시 정상 동작 |
> | 5 | 대용량 플로우 정의 | 50개 이상의 노드를 포함한 플로우 배포 |

---

## 전체 테스트 현황

### 패키지 구조

```
src/test/java/com/example/fbp/
├── registry/
│   ├── NodeRegistryTest.java
│   └── NodeFactoryTest.java
├── parser/
│   ├── JsonFlowParserTest.java
│   └── FlowDefinitionTest.java
├── engine/
│   └── FlowManagerTest.java
├── plugin/
│   ├── PluginManagerTest.java
│   ├── PluginClassLoaderTest.java
│   └── NodeProviderTest.java
├── metrics/
│   ├── MetricsCollectorTest.java
│   └── NodeMetricsTest.java
├── api/
│   └── HttpApiServerTest.java
├── flow/
│   └── SubFlowNodeTest.java
├── node/
│   ├── DynamicRouterNodeTest.java
│   ├── ErrorHandlerNodeTest.java
│   └── DeadLetterNodeTest.java
├── core/
│   └── BackpressureConnectionTest.java
└── integration/
    ├── ComplexScenarioTest.java
    ├── LoadTest.java
    └── RestApiIntegrationTest.java
```

### 테스트 요약

| Step | 테스트 클래스 | 단위 테스트 | 통합 테스트 |
|------|-------------|:---------:|:---------:|
| 1~3 | NodeRegistryTest, NodeFactoryTest | 10 | — |
| 4~5 | JsonFlowParserTest, FlowDefinitionTest, FlowManagerTest | 25 | — |
| 6~7 | PluginManagerTest, PluginClassLoaderTest, NodeProviderTest | 15 | — |
| 8 | MetricsCollectorTest, NodeMetricsTest, HttpApiServerTest | 23 | — |
| 9 | SubFlowNodeTest, DynamicRouterNodeTest, ErrorHandlerNodeTest, BackpressureConnectionTest | 28 | — |
| 10 | ComplexScenarioTest, LoadTest, RestApiIntegrationTest | — | 21 |
| **합계** | **17개 클래스** | **101** | **21** |
| | | **총 122개 테스트** | |

---

## 부록 A: Maven 의존성

3단계에서 추가되는 의존성을 정리한다. 1~2단계 의존성은 기존 그대로 유지한다.

```xml
<!-- 1~2단계에서 이미 포함된 의존성 -->
<!-- Eclipse Paho MQTT v5, Jackson, JUnit 5, Testcontainers -->

<!-- YAML 파서 (선택 — YAML 플로우 정의 지원 시) -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.17.0</version>
</dependency>

<!-- JLine (선택 — 고급 CLI 구현 시) -->
<dependency>
    <groupId>org.jline</groupId>
    <artifactId>jline</artifactId>
    <version>3.26.1</version>
</dependency>

<!-- HTTP 서버: com.sun.net.httpserver.HttpServer — JDK 내장, 별도 의존성 불필요 -->

<!-- 플러그인 시스템: java.util.ServiceLoader — JDK 내장, 별도 의존성 불필요 -->

<!-- MODBUS: 외부 라이브러리 없음 — java.net.Socket으로 직접 구현 (2단계에서 구현 완료) -->
```

---

## 부록 B: 전체 패키지 구조

```
src/main/java/com/example/fbp/
├── core/           ← 1단계 (Node, Port, Connection, Flow, FlowEngine)
│   ├── ErrorPort
│   ├── BackpressureStrategy
│   ├── BackpressureConnection
│   ├── DropOldestStrategy
│   └── DropNewestStrategy
├── message/        ← 1단계 (Message)
├── node/           ← 1~3단계 (각종 노드)
│   ├── DynamicRouterNode
│   ├── RoutingRule
│   ├── ErrorHandlerNode
│   └── DeadLetterNode
├── protocol/       ← 2단계 (MQTT, MODBUS)
├── rule/           ← 2단계 (RuleNode, RuleExpression)
├── registry/       ← 3단계 신규
│   ├── NodeFactory
│   ├── NodeRegistry
│   └── NodeRegistryException
├── parser/         ← 3단계 신규
│   ├── FlowParser
│   ├── JsonFlowParser
│   ├── FlowDefinition
│   ├── NodeDefinition
│   ├── ConnectionDefinition
│   └── FlowParserException
├── plugin/         ← 3단계 신규
│   ├── NodeProvider (SPI)
│   ├── NodeDescriptor
│   ├── PluginManager
│   ├── PluginScanner
│   ├── PluginClassLoader
│   └── PluginException
├── engine/         ← 3단계 신규
│   ├── FlowManager
│   └── ThreadPoolConfig
├── metrics/        ← 3단계 신규
│   ├── MetricsCollector
│   ├── NodeMetrics
│   └── FlowMetrics
├── api/            ← 3단계 신규
│   ├── HttpApiServer
│   ├── FlowHandler
│   ├── MetricsHandler
│   ├── HealthHandler
│   └── ApiResponse
└── flow/           ← 3단계 신규
    └── SubFlowNode
```

---

## 부록 C: 참고 자료

### 플러그인 시스템

- Oracle Java Documentation: [ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)
- Baeldung: [Java Service Provider Interface (SPI)](https://www.baeldung.com/java-spi)
- Baeldung: [URLClassLoader](https://www.baeldung.com/java-classloaders)

### REST API (JDK HttpServer)

- Oracle: [com.sun.net.httpserver](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html)
- Baeldung: [Simple HTTP Server in Java](https://www.baeldung.com/java-http-server)

### JSON/YAML 파싱

- Jackson Databind: [GitHub](https://github.com/FasterXML/jackson-databind)
- Jackson YAML: [GitHub](https://github.com/FasterXML/jackson-dataformat-yaml)

### 성능 테스트

- Baeldung: [Java Microbenchmark Harness (JMH)](https://www.baeldung.com/java-microbenchmark-harness)
- Oracle: [ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)

### FBP 참고

- J. Paul Morrison, *Flow-Based Programming* (2nd Edition)
- [NoFlo.js](https://noflojs.org/) — JavaScript FBP 구현 참고
- [Node-RED](https://nodered.org/) — JSON 기반 플로우 정의 참고

### 설계 패턴

- Registry Pattern — Martin Fowler, *Patterns of Enterprise Application Architecture*
- Plugin Architecture — [OSGI Alliance](https://www.osgi.org/)
- Backpressure — Reactive Streams Specification: [reactive-streams.org](https://www.reactive-streams.org/)