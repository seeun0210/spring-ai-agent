# loop-play-spring-ai-agent

## 실행 환경

- Java 17
- Spring Boot 3.4.1
- Spring AI 1.0.0
- Ollama `qwen3:4b` (`4B`는 약 40억 개 파라미터 모델이라는 뜻이에요)
- 모델 비교 실험: Ollama `qwen3:8b` (`8B`는 약 80억 개 파라미터 모델이라는 뜻이에요)
- temperature: `0.3`

```bash
./gradlew bootRun
```

주요 호출 endpoint:

```text
POST /api/v1/assistant
POST /api/v1/support
GET /api/v1/session/{sessionId}/messages
GET /api/v1/session/ids
DELETE /api/v1/session/{sessionId}
```

curl 예시는 같은 서버와 mock 고객을 기준으로 재현하기 위해 아래 변수를 사용해요.

```bash
export BASE_URL="http://localhost:8080"
export CUSTOMER_ID="customer-1"
export SESSION_ID="cust-A"
```

`/api/v1/assistant`와 `/api/v1/support`는 주문 소유자 검증과 Memory 세션 분리를 위해 `X-Customer-Id`, `X-Session-Id` 헤더를 사용해요.
실제 운영 코드라면 `X-Customer-Id`를 직접 신뢰하지 않고 인증 컨텍스트에서 검증된 사용자 ID를 읽어야 해요.

## 구현 범위

- `ChatMemoryConfig`에서 `ChatMemoryRepository`, `MessageWindowChatMemory`, `MessageChatMemoryAdvisor` 구성
- `MAX_MESSAGES = 20`으로 conversation id별 최근 20 messages 유지
- `ConversationIdResolver`로 `X-Customer-Id`와 `X-Session-Id`를 묶어 고객별 conversation id 생성
- `AssistantService`, `SupportService`에서 `ChatMemory.CONVERSATION_ID`를 ChatClient 호출에 전달
- `SessionController`로 현재 고객의 세션 메시지 조회, 세션 목록 조회, 세션 삭제 지원
- `application-jdbc.yml`에 JDBC Chat Memory 전환 예시 추가
- Memory 설정/세션 API/서비스 conversation id 전달 테스트 추가

## Round 3에서 구현한 Chat Memory

Round 3에서는 `/api/v1/assistant`와 `/api/v1/support`에 세션 단위 Chat Memory를 붙였어요.
클라이언트가 `X-Session-Id` 헤더를 보내면 같은 세션의 최근 대화를 다음 LLM 호출에 다시 넣고, 헤더가 없으면 `default` 세션을 사용해요.
다만 `X-Session-Id`만 conversation id로 쓰면 다른 고객이 같은 세션 ID를 재사용했을 때 이전 대화 문맥이 프롬프트에 섞일 수 있어요.
그래서 현재 구현은 `ConversationIdResolver`에서 `X-Customer-Id`와 `X-Session-Id`를 묶어 `customer-1:cust-A` 같은 conversation id를 만들어요.

```java
String conversationId = conversationIdResolver.resolve(sessionId);

chatClient.prompt()
    .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
    .user(request.message())
    .call()
    .content();
```

메모리 구현은 `MessageWindowChatMemory`를 사용했고, 기준값은 `MAX_MESSAGES = 20`으로 잡았어요.
이 값은 전체 서버 기준 20개가 아니라 conversation id마다 최근 20 messages를 뜻하고, USER/ASSISTANT 왕복 기준으로는 약 10턴을 유지해요.

20을 운영 최적값이라고 확정한 것은 아니고, Round 3 실험의 기준값으로 선택했어요.
배달 상담은 보통 "주문번호 제시 -> 상태 확인 -> 다른 주문 전환 -> 취소 가능 여부 확인 -> 취소 실행/재확인"처럼 3~6턴 안에서 끝난다고 가정했어요.
그 기준에서는 20 messages가 평균 상담보다 여유가 있고, 과제의 10턴 시나리오를 잘라내지 않고 관찰할 수 있는 최소 기준선이 돼요.
반대로 무제한으로 두면 상담이 길어질수록 매 요청 입력 토큰과 응답 시간이 선형으로 늘어나고, 오래된 주문 상태가 최신 사실처럼 섞일 수 있어요.
그래서 이번 구현에서는 "충분히 긴 기준선이지만 무한 누적은 막는 값"으로 20을 선택했고, 이후 `2 / 20 / Integer.MAX_VALUE` 비교 실험으로 이 값이 너무 작거나 큰지 확인하는 방식이 맞다고 봤어요.

요약 메모리도 가능하지만, 주문 취소/배달 상태처럼 현재성이 중요한 도메인에서는 요약이 오래된 상태를 더 그럴듯하게 남길 수 있으므로 먼저 짧은 윈도우로 시작하는 편이 낫다고 봤어요.

Spring AI 1.0의 구현체를 직접 확인해 보니 `MessageWindowChatMemory`의 핵심 흐름은 아래처럼 `find -> process -> saveAll` 구조였어요.

```java
public void add(String conversationId, List<Message> newMessages) {
    List<Message> oldMessages = repository.findByConversationId(conversationId);
    List<Message> processed = process(oldMessages, newMessages);
    repository.saveAll(conversationId, processed);
}
```

`process`에서는 기존 메시지 뒤에 새 메시지를 붙이고, `maxMessages`를 넘으면 앞쪽의 오래된 일반 메시지부터 제거해요.
`SystemMessage`는 특별 취급해서 새 SystemMessage가 들어오면 기존 SystemMessage를 제거하고, window trim 때도 가능한 한 보존해요.
`ChatMemoryConfigTest`에서 이 동작도 확인했어요.
`old system`, `first user`가 있는 세션에 `new system`을 추가하면 결과는 `first user`, `new system`만 남았고, `maxMessages = 3`인 세션에서 `system prompt`, `old user`, `old assistant`, `new user`가 되면 일반 메시지인 `old user`가 먼저 제거되어 `system prompt`, `old assistant`, `new user`가 남았어요.

기본 저장소인 `InMemoryChatMemoryRepository`는 내부적으로 conversation id별 메시지 목록을 들고 있어요.

```java
Map<String, List<Message>> chatMemoryStore = new ConcurrentHashMap<>();
```

따라서 저장 구조는 아래와 같아요.

```text
customer-1:cust-A -> 최근 20 messages
customer-1:cust-B -> 최근 20 messages
customer-2:cust-A -> 최근 20 messages
```

여기서 `ConcurrentHashMap`은 Map 접근을 안전하게 해주지만, `MessageWindowChatMemory.add()` 전체를 원자적으로 만들어 주지는 않아요.
같은 conversation id에 두 요청이 동시에 들어오면 둘 다 같은 old list를 읽고 각자 `saveAll`을 할 수 있어서, 나중 저장이 먼저 저장된 메시지를 덮어쓸 수 있어요.
즉 항상 하나만 남는다는 뜻은 아니고, 타이밍에 따라 결과가 달라져요.
두 번째 요청이 첫 번째 저장 이후에 읽으면 둘 다 남을 수 있지만, 두 요청이 모두 저장 전의 같은 스냅샷을 읽으면 마지막 저장만 남아 새 메시지 하나가 유실될 수 있어요.
실험용 동시성 테스트는 이 두 번째 경우를 강제로 만들어, `message-a`, `message-b` 둘 다 남는다는 보장이 없음을 확인해요.
운영에서는 같은 세션에 대해 응답 생성 중 추가 요청을 막는 in-flight guard, 세션별 큐잉, Redis list append, DB optimistic locking 같은 별도 정책이 필요해요.

이 부분은 2단계의 `MAX_MESSAGES` 크기 실험이나 3단계의 JDBC 저장소 선택만으로 자연스럽게 해결되지 않아요.
2단계는 "얼마나 많은 메시지를 프롬프트에 다시 넣을 것인가"를 보고, 3단계는 "메시지를 어디에 저장할 것인가"를 봐요.
반면 동시 요청 문제는 "같은 conversation id에 대한 read-modify-write를 어떤 순서로 직렬화할 것인가"의 문제예요.
그래서 `ChatMemoryConfigTest.concurrentAddsToSameConversationCanLoseOneMessage`에서 두 요청이 같은 스냅샷을 읽은 뒤 각각 저장하는 상황을 재현했어요.
결과적으로 마지막 `saveAll`만 남아 메시지 하나가 유실될 수 있음을 테스트로 확인했어요.
실제 `InMemoryChatMemoryRepository`로도 탐색용 race probe를 돌려 봤어요.
이 테스트는 타이밍 의존성이 있어서 기본 테스트에서는 제외하고, `RACE_PROBE=true`를 줄 때만 실행해요.

```bash
RACE_PROBE=true LOG_DIR=/private/tmp/baedal-round3-logs \
  ./gradlew test --tests com.baedal.support.memory.ChatMemoryConfigTest.realInMemoryRepositoryCanLoseMessagesUnderConcurrentAdds
```

관찰 결과:

```text
Observed ChatMemory lost update: attempt=1, requested=200, stored=26
```

200개 메시지를 같은 conversation id에 동시에 add했지만 최종 Memory에는 26개만 남았어요.
이 숫자는 실행 타이밍에 따라 달라질 수 있고, 매번 26개가 된다는 뜻은 아니에요.
중요한 점은 실제 Spring AI InMemory 구현에서도 동시 `add()`가 append 보장을 제공하지 않는다는 거예요.

예를 들어 같은 세션에서 사용자가 화면을 빠르게 두 번 누르거나 앱이 요청을 재시도해서 아래 두 요청이 거의 동시에 들어왔다고 가정할 수 있어요.

```text
요청 A: 2024-1234 취소 가능해요?
요청 B: 2024-1235 배달 어디쯤이에요?
```

둘 다 같은 이전 Memory를 읽은 뒤 각자 응답을 만들고 `saveAll`을 호출하면, 마지막에 저장한 쪽만 남을 수 있어요.
사용자는 두 응답을 모두 받았지만 서버 Memory에는 한 턴만 남는 상태가 돼요.

```text
사용자가 실제로 본 응답:
  A 응답: 2024-1234는 이미 배달 중이라 취소할 수 없어요.
  B 응답: 2024-1235는 아직 조리 전이에요.

Memory에 남은 내용:
  USER: 2024-1235 배달 어디쯤이에요?
  ASSISTANT: 2024-1235는 아직 조리 전이에요.
```

이후 사용자가 "그거 취소해주세요"라고 말하면, 사용자는 A의 `2024-1234`를 떠올렸을 수도 있지만 LLM은 Memory에 남은 B의 `2024-1235`를 더 강하게 참조할 수 있어요.
반대로 A만 남고 B가 유실되면 "방금 본 1235 상태"를 이어 묻는 질문에서 맥락을 잃을 수 있어요.
즉 동시 요청 문제는 단순히 로그 한 줄이 사라지는 문제가 아니라, 다음 Tool 호출의 orderId 추론을 흔들 수 있는 문제예요.

Advisor 순서는 `MessageChatMemoryAdvisor`를 `order(10)`으로 두고, 기존 `PerformanceLoggingAdvisor`보다 먼저 실행되게 했어요.
그래야 성능 로그에는 Memory가 주입된 뒤의 실제 프롬프트와 토큰 사용량이 잡혀요.

```java
.defaultAdvisors(
    messageChatMemoryAdvisor,
    assistantPerformanceLoggingAdvisor()
)
```

기본 저장소는 `InMemoryChatMemoryRepository`예요.
로컬 실험에서는 실행이 가볍고 테스트가 단순하다는 장점이 있지만, 서버 재시작 시 대화가 사라지고 인스턴스가 여러 대면 세션이 분산되는 한계가 있어요.
또 `MAX_MESSAGES`는 세션별 메시지 수만 제한하므로 세션 수가 계속 늘어나는 문제는 막지 못해요.
예를 들어 1,000개 세션이면 최대 20,000 messages가 JVM에 남을 수 있어 TTL이나 삭제 정책이 별도로 필요해요.
그래서 `jdbc` 프로필 예시를 `application-jdbc.yml`에 따로 남겼고, 실제 운영에서는 Spring AI JDBC Chat Memory 의존성과 테이블을 추가한 뒤 JDBC 저장소로 바꾸는 편이 맞아요.

세션 확인용 API도 추가했어요.

```bash
curl -X POST http://localhost:8080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: session-a' \
  -d '{"message":"주문번호 2024-1234 배달 어디쯤이에요?"}'

curl http://localhost:8080/api/v1/session/session-a/messages \
  -H 'X-Customer-Id: customer-1'

curl http://localhost:8080/api/v1/session/ids \
  -H 'X-Customer-Id: customer-1'

curl -X DELETE http://localhost:8080/api/v1/session/session-a \
  -H 'X-Customer-Id: customer-1'
```

## 1단계 검증 - 지시 대명사와 세션 분리

아래 실험은 `./gradlew bootRun --args='--server.port=18080 --baedal.chat-memory.max-messages=20'`으로 서버를 띄우고 실행했어요.
시나리오 2에서 `2024-1234`는 seed 데이터상 `DELIVERING` 상태라 첫 취소 시도는 `NOT_CANCELABLE`로 끝나고 실제 취소 side effect가 발생하지 않아요.
따라서 "그거 말고 2024-1235"로 전환했을 때 1235만 새로 취소되는지 보기 좋은 케이스예요.

### 시나리오 1 - "그거"로 같은 주문 배송 재조회

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s1-cust-A' \
  -d '{"message":"2024-1234 어디쯤 있어요?"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s1-cust-A' \
  -d '{"message":"그거 언제 도착해요?"}'
```

응답:

```text
1턴: 주문번호 2024-1234는 현재 배달 중이에요. 라이더는 역삼역 사거리에 있어요.
2턴: 주문번호 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요.
```

주요 로그:

```text
1턴: messageCount=2, messages=SYSTEM(chars=1297), USER(chars=18)
[Tool] getDeliveryStatus(orderId=2024-1234)
LLM call completed. elapsedMs=37357, promptTokens=2355

2턴: messageCount=4, messages=USER(chars=18), ASSISTANT(chars=65), SYSTEM(chars=1297), USER(chars=11)
[Tool] getDeliveryStatus(orderId=2024-1234)
LLM call completed. elapsedMs=34551, promptTokens=2471
```

Memory 상태:

```json
[
  {"type":"USER","content":"2024-1234 어디쯤 있어요?"},
  {"type":"ASSISTANT","content":"주문번호 2024-1234는 현재 배달 중이에요. 라이더는 역삼역 사거리에 있어요. 추가 정보가 필요하지 않습니다."},
  {"type":"USER","content":"그거 언제 도착해요?"},
  {"type":"ASSISTANT","content":"주문번호 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요. 정확한 도착 시간은 라이더의 배달 진행 상황에 따라 변할 수 있으니, 주문 상태를 다시 확인해 주세요."}
]
```

### 시나리오 2 - 취소 대상 전환

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s2-cust-A' \
  -d '{"message":"2024-1234 취소해주세요"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s2-cust-A' \
  -d '{"message":"아, 그거 말고 2024-1235 취소해주세요"}'
```

응답:

```text
1턴: 주문 번호 2024-1234는 현재 배달 중이므로 취소할 수 없어요.
2턴: 주문 번호 2024-1235가 성공적으로 취소되었습니다.
```

주요 로그:

```text
1턴: [Tool] cancelOrder(orderId=2024-1234, reason=고객 요청)
1턴: [Tool] cancelOrder result(orderId=2024-1234, cancelId=null, outcome=NOT_CANCELABLE, status=DELIVERING, ...)

2턴: messageCount=4, messages=USER(chars=16), ASSISTANT(chars=93), SYSTEM(chars=1297), USER(chars=25)
2턴: [Tool] cancelOrder(orderId=2024-1235, reason=고객 요청)
2턴: [Tool] cancelOrder result(orderId=2024-1235, cancelId=a9a2c7b9-590d-42a7-b18f-6f4e35bfd0f8, outcome=CANCELED, status=CANCELED, ...)
```

Memory 상태:

```json
[
  {"type":"USER","content":"2024-1234 취소해주세요"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문 번호 2024-1234는 현재 배달 중이므로 취소할 수 없어요. 주문이 이미 라이더에게 전달되었습니다. 주문을 기다리거나 고객센터에 문의해 주세요."},
  {"type":"USER","content":"아, 그거 말고 2024-1235 취소해주세요"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문 번호 2024-1235가 성공적으로 취소되었습니다. 라이더가 주문을 받지 못했으므로 추가 조치가 필요하지 않습니다. 다른 주문 취소나 도움이 필요하면 알려주세요."}
]
```

### 시나리오 3 - "아까 물어본 그 주문"

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s3-cust-A' \
  -d '{"message":"2024-1234 배달 상황 알려주세요"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s3-cust-A' \
  -d '{"message":"아까 물어본 그 주문 언제 도착해요?"}'
```

응답:

```text
1턴: 주문 번호 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요.
2턴: 주문 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요.
```

주요 로그:

```text
2턴: messageCount=4, messages=USER(chars=21), ASSISTANT(chars=73), SYSTEM(chars=1297), USER(chars=20)
2턴: [Tool] getDeliveryStatus(orderId=2024-1234)
2턴: LLM call completed. elapsedMs=58291, promptTokens=2499, completionTokens=3471
```

Memory 상태:

```json
[
  {"type":"USER","content":"2024-1234 배달 상황 알려주세요"},
  {"type":"ASSISTANT","content":"주문 번호 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요. 추가로 도움이 필요하시면 알려주세요."},
  {"type":"USER","content":"아까 물어본 그 주문 언제 도착해요?"},
  {"type":"ASSISTANT","content":"주문 2024-1234는 현재 배달 중이에요. 라이더가 역삼역 사거리에 위치해 있어요. 도착 예정 시간은 아직 확인할 수 없어요. 추가로 도움이 필요하시면 알려주세요."}
]
```

### 시나리오 4 - 세션 오염 방지

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s4-A' \
  -d '{"message":"2024-1234 지금 어디쯤이에요?"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s4-B' \
  -d '{"message":"그 주문 어디쯤이에요?"}'
```

응답:

```text
s4-A: 주문번호 2024-1234는 현재 배달 중이에요. 라이더는 역삼역 사거리에 위치해 있어요.
s4-B: 주문 상태를 확인하려면 주문번호를 알려주세요.
```

주요 로그:

```text
s4-A: conversationId=customer-1:s4-A, messageCount=2
s4-A: [Tool] getDeliveryStatus(orderId=2024-1234)

s4-B: conversationId=customer-1:s4-B, messageCount=2
s4-B: LLM call completed. promptTokens=1134
```

`s4-B`에서는 `getDeliveryStatus` Tool이 호출되지 않았어요.
`s4-A`와 `s4-B`의 Memory도 서로 분리되어 있어요.

```json
// s4-A
[
  {"type":"USER","content":"2024-1234 지금 어디쯤이에요?"},
  {"type":"ASSISTANT","content":"주문번호 2024-1234는 현재 배달 중이에요. 라이더는 역삼역 사거리에 위치해 있어요. 추가로 궁금한 사항이 있으면 알려주세요."}
]

// s4-B
[
  {"type":"USER","content":"그 주문 어디쯤이에요?"},
  {"type":"ASSISTANT","content":"주문 상태를 확인하려면 주문번호를 알려주세요. 주문번호가 없으면 배달 상태를 확인할 수 없어요. 다음 단계는 주문번호를 입력해 주세요."}
]
```

세션 목록도 현재 고객 기준으로만 조회돼요.

```json
["s1-cust-A","s2-cust-A","s3-cust-A","s4-B","s4-A"]
```

### 시나리오 5 - Memory 삭제 후 맥락 소실

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s5-A' \
  -d '{"message":"2024-1234 배달 어디쯤이에요?"}'

curl http://localhost:18080/api/v1/session/s5-A/messages \
  -H 'X-Customer-Id: customer-1'

curl -X DELETE http://localhost:18080/api/v1/session/s5-A \
  -H 'X-Customer-Id: customer-1'

curl http://localhost:18080/api/v1/session/s5-A/messages \
  -H 'X-Customer-Id: customer-1'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: s5-A' \
  -d '{"message":"그거 언제 도착해요?"}'
```

삭제 전 Memory:

```json
[
  {"type":"USER","content":"2024-1234 배달 어디쯤이에요?"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문번호 2024-1234의 배달 상태는 배달 중이에요. 라이더는 현재 역삼역 사거리에 위치해 있어요. 주문이 정확히 배달 중이니 추가 확인이 필요하시면 다시 문의해 주세요."}
]
```

삭제 직후 Memory:

```json
[]
```

삭제 후 "그거" 응답:

```text
핵심 답변: 주문 번호가 필요해요. 배달 상태를 확인하기 위해 주문 번호를 알려주세요.
```

주요 로그:

```text
[Session] clear sessionId=s5-A
삭제 후 요청: messageCount=2, messages=SYSTEM(chars=1297), USER(chars=11)
삭제 후 요청: LLM call completed. promptTokens=1133
```

삭제 이후에는 이전 `2024-1234`가 프롬프트에 포함되지 않았고, Tool도 호출되지 않았어요.

### 추가 실험 - 여러 seed 주문을 섞었을 때 대명사 해석

1단계 기본 시나리오는 한 번에 한 주문만 강하게 다루는 편이라, 여러 주문이 같은 Memory 안에 있을 때 대명사가 어느 주문으로 붙는지도 따로 확인했어요.
사용한 seed는 `2024-1234`와 `2024-1237`이에요.

```text
2024-1234 = DELIVERING, 허니콤보/콜라, riderLocation=역삼역 사거리
2024-1237 = COOKING, 불고기버거/감자튀김, riderLocation=null
```

실행 흐름:

```bash
curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: multi-seed-pronoun' \
  -d '{"message":"2024-1234 배달 상황 알려주세요"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: multi-seed-pronoun' \
  -d '{"message":"2024-1237 주문 메뉴 뭐였죠?"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: multi-seed-pronoun' \
  -d '{"message":"그 버거 세트 지금 어디쯤이에요?"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: multi-seed-pronoun' \
  -d '{"message":"아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘"}'

curl -X POST http://localhost:18080/api/v1/assistant \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: multi-seed-pronoun' \
  -d '{"message":"처음에 물어본 그 주문 라이더 위치 다시 확인해줘"}'
```

관찰 결과:

| 턴 | 사용자 표현 | 기대 orderId | 실제 Tool 호출 | 결과 |
| --- | --- | --- | --- | --- |
| 1 | `2024-1234 배달 상황` | `2024-1234` | `getDeliveryStatus(2024-1234)` | 성공 |
| 2 | `2024-1237 주문 메뉴` | `2024-1237` | `getOrderDetail(2024-1237)` | 성공 |
| 3 | `그 버거 세트` | `2024-1237` | `getDeliveryStatus(2024-1237)` | 성공 |
| 4 | `아까 배달 중이던 그 주문` | `2024-1234` | `getDeliveryStatus(2024-1237)` | 실패 |
| 5 | `처음에 물어본 그 주문` | `2024-1234` | `getDeliveryStatus(2024-1237)` | 실패 |

실패 로그:

```text
[Assistant] sessionId=multi-seed-pronoun, conversationId=customer-1:multi-seed-pronoun, message=아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘
LLM request prompt. endpoint=assistant, messageCount=8, ...
[Tool] getDeliveryStatus(orderId=2024-1237)
[Tool] getDeliveryStatus result(... status=COOKING, riderLocation=null, message=매장에서 조리 중이에요.)
```

```text
[Assistant] sessionId=multi-seed-pronoun, conversationId=customer-1:multi-seed-pronoun, message=처음에 물어본 그 주문 라이더 위치 다시 확인해줘
LLM request prompt. endpoint=assistant, messageCount=10, ...
[Tool] getDeliveryStatus(orderId=2024-1237)
[Tool] getDeliveryStatus result(... status=COOKING, riderLocation=null, message=매장에서 조리 중이에요.)
```

Memory에는 `2024-1234`와 `2024-1237`이 모두 남아 있었지만, 모델은 "배달 중이던", "처음에 물어본" 같은 단서보다 최근의 `2024-1237` 문맥을 더 강하게 사용했어요.
즉 Chat Memory가 있다고 해서 다중 주문 참조가 항상 정확해지는 것은 아니에요.
주문이 여러 개 등장하는 상담에서는 "마지막 주문번호" 규칙만으로 부족하고, 모델이 헷갈릴 수 있는 표현이면 주문번호를 다시 확인하거나, 서버 쪽에서 "현재 활성 주문 후보"를 구조화해서 관리하는 정책이 필요해요.

추가로 음식명처럼 Assistant 응답 본문에 명확히 남은 단서도 따로 확인했어요.
처음에는 `2024-1237 주문 메뉴 뭐였죠?` 바로 다음에 `아까 감자튀김 들어있던 주문 배달 어디쯤이에요?`를 물었고, 이 케이스도 의미 있는 검증이라고 생각했어요.
하지만 바로 직전 응답에 "감자튀김"이 있었기 때문에, 나중에 다시 보니 검증으로 약했어요.
게다가 모델이 `2024-1237`은 맞췄지만 실제 Tool call이 아니라 아래 JSON을 응답 본문으로 출력했어요.

```json
{
  "tool": "getDeliveryStatus",
  "arguments": {
    "orderId": "2024-1237"
  }
}
```

그래서 `food-reference-gap` 세션에서 중간에 다른 주문들을 끼워 다시 실험했어요.

```text
1) 2024-1237 주문 메뉴 뭐였죠?              -> 감자튀김 포함
2) 2024-1234 배달 상황도 알려주세요         -> 배달 중 / 역삼역 사거리
3) 2024-1239 주문 메뉴도 알려주세요         -> 초밥 세트 / 미소장국
4) 아까 감자튀김 들어있던 주문 배달 어디쯤이에요?
```

이번에는 마지막 질문에서 실제 Tool call이 실행됐고, `2024-1237`을 정확히 선택했어요.

```text
[Assistant] sessionId=food-reference-gap, conversationId=customer-1:food-reference-gap, message=아까 감자튀김 들어있던 주문 배달 어디쯤이에요?
LLM request prompt. endpoint=assistant, messageCount=8, ...
[Tool] getDeliveryStatus(orderId=2024-1237)
[Tool] getDeliveryStatus result(... status=COOKING, riderLocation=null, message=매장에서 조리 중입니다.)
```

Memory 상태도 네 번째 질문 직전에 `감자튀김`, `2024-1234`, `초밥 세트`가 모두 들어 있는 상태였어요.

```json
[
  {
    "type": "USER",
    "content": "2024-1237 주문 메뉴 뭐였죠?"
  },
  {
    "type": "ASSISTANT",
    "content": "핵심 답변: 주문하신 메뉴는 2개의 불고기버거와 1개의 감자튀김입니다. 현재 주문 상태는 요리 중입니다."
  },
  {
    "type": "USER",
    "content": "2024-1234 배달 상황도 알려주세요"
  },
  {
    "type": "ASSISTANT",
    "content": "핵심 답변: 주문 2024-1234는 현재 배달 중입니다. 라이더가 역삼역 사거리에 위치해 있습니다."
  },
  {
    "type": "USER",
    "content": "2024-1239 주문 메뉴도 알려주세요"
  },
  {
    "type": "ASSISTANT",
    "content": "핵심 답변: 주문하신 메뉴는 1개의 초밥 세트와 1개의 미소장국입니다."
  },
  {
    "type": "USER",
    "content": "아까 감자튀김 들어있던 주문 배달 어디쯤이에요?"
  },
  {
    "type": "ASSISTANT",
    "content": "핵심 답변: 주문 2024-1237은 현재 매장에서 조리 중입니다. 라이더가 아직 배달을 시작하지 않은 상태입니다."
  }
]
```

이 관찰을 보면 모델은 "배달 중이던" 같은 상태 조건보다 "감자튀김" 같은 고유 메뉴명을 더 잘 따라갔어요.
다만 이것도 신뢰할 수 있는 서버 정책은 아니에요.
메뉴명이 중복되거나 Assistant 응답에 메뉴명이 빠지면 다시 흔들릴 수 있기 때문에, read-only 질문은 어느 정도 허용하되 command Tool은 아래처럼 서버 정책으로 막는 편이 맞다고 생각했어요.

`DELIVERING`처럼 상태 코드를 직접 말하면 나아지는지도 확인했어요.
이번에는 첫 응답에 `DELIVERING`, 두 번째 응답에 `COOKING`이 그대로 남도록 유도한 뒤, 세 번째 질문에서 `DELIVERING인 주문`을 찾게 했어요.

```text
1) 2024-1234 배달 상황 알려주세요. 상태 코드는 영어 enum으로도 같이 알려주세요
   -> 주문 번호 2024-1234의 배달 상태는 DELIVERING입니다.
2) 2024-1237 주문 메뉴 뭐였죠?
   -> 현재 주문 상태는 COOKING입니다.
3) DELIVERING인 주문 라이더 위치 다시 확인해줘
   -> 주문 번호를 알려주세요.
```

실제 Memory에도 `DELIVERING`과 `COOKING`이 모두 남아 있었지만, 모델은 `2024-1234`를 선택하지 못했어요.

```json
[
  {
    "type": "ASSISTANT",
    "content": "주문 번호 2024-1234의 배달 상태는 DELIVERING입니다. 라이더는 역삼역 사거리에 위치하고 있습니다."
  },
  {
    "type": "ASSISTANT",
    "content": "주문 번호 2024-1237의 주문 메뉴는 불고기버거 2개와 감자튀김 1개입니다. 총 금액은 17,500원이며, 현재 주문 상태는 COOKING입니다."
  },
  {
    "type": "USER",
    "content": "DELIVERING인 주문 라이더 위치 다시 확인해줘"
  },
  {
    "type": "ASSISTANT",
    "content": "주문 번호를 알려주세요. DELIVERING 상태의 주문 라이더 위치를 다시 확인하려면 주문 번호가 필요합니다."
  }
]
```

이 결과를 보면 실패 원인은 `DELIVERING`이라는 문자열이 Memory에 없어서만은 아니었어요.
문자열이 있어도 모델이 자연어 대화 로그를 `status == DELIVERING` 조건으로 안정적으로 검색하지 못했어요.
즉 상태 기반 참조는 Memory보다 구조화된 상태 저장소나 상태 검색 Tool로 처리하는 편이 낫다고 생각했어요.

이 실패는 프롬프트를 더 세게 쓰는 방식만으로 안정적으로 해결하기 어렵다고 판단했어요.
ChatMemory는 대화 transcript를 LLM에 다시 넣어 주는 장치이지, 주문 식별의 authoritative state가 아니기 때문이에요.
운영에서는 `ChatMemory`와 별도로 `ConversationOrderState` 같은 대화 상태를 두고, 주문 참조 상태를 명시적으로 관리하는 편이 안전해요.

```text
ChatMemory:
  이전 USER / ASSISTANT 자연어 대화

ConversationOrderState:
  activeOrderId
  recentMentionedOrderIds
  lastExplicitOrderId
  source = EXPLICIT_USER_MESSAGE | TOOL_RESULT | UI_SELECTION
```

정책도 보수적으로 두는 것이 맞아요.

| 상황 | 처리 |
| --- | --- |
| 사용자가 주문번호를 명시함 | 그 orderId를 사용하고 `activeOrderId` 갱신 |
| 주문 후보가 1개뿐이고 read-only 조회 | 해당 orderId로 Tool 호출 가능 |
| 주문 후보가 2개 이상인데 "그거", "그 주문"만 말함 | Tool 호출하지 말고 주문번호를 다시 질문 |
| `cancelOrder` 같은 상태 변경 Tool | 명시적 orderId 또는 확정된 active order 없으면 호출 금지 |
| UI에서 주문 카드를 선택함 | 선택된 주문을 `activeOrderId`로 갱신 |

즉 Round 3의 결론은 "ChatMemory로 모든 지시대명사를 해결한다"가 아니라, "ChatMemory는 문맥 보조이고 주문 참조의 최종 책임은 별도 상태 모델과 보수적 Tool 호출 정책에 둔다"는 거예요.
특히 취소처럼 side effect가 있는 Tool은 애매한 대명사만으로 실행하지 않는 것이 맞아요.

다만 문자열 파싱으로 `그 주문` 같은 표현을 막는 Guard를 두는 방식은 현실적인 해결책으로 보지 않았어요.
한국어 대명사, 메뉴명, 시간 표현, 취소 의도까지 문자열 규칙으로 늘리기 시작하면 금방 깨지고, 실제로는 LLM이 이해해야 하는 일을 서버 정규식으로 다시 구현하는 꼴이 돼요.

이번 구현에서는 agent를 둘로 나누지 않고, 실제 위험 지점인 Tool 실행 직전에 서버 정책을 걸었어요.
LLM은 여전히 하나이고, `cancelOrder` Tool 호출을 제안할 수 있어요.
다만 `GuardedToolCallback`이 실제 `OrderTools.cancelOrder` 실행 전에 `ToolExecutionPolicy`를 먼저 확인해요.

```text
Assistant LLM
  -> cancelOrder({"orderId":"2024-1237"}) 제안
  -> GuardedToolCallback
  -> ToolExecutionPolicy 검사
  -> 허용: 실제 OrderTools.cancelOrder 실행
  -> 차단: CONFIRMATION_REQUIRED Tool 결과 반환
```

`AssistantService`는 현재 사용자 메시지에서 명시적으로 등장한 주문번호만 추출해서 ToolContext에 넣어요.
여기서 `OrderIdExtractor`는 `"그 주문"` 같은 자연어를 해석하지 않고, `2024-1234` 형식의 명시 ID만 추출해요.
이 정도의 포맷 추출은 LLM이 해야 할 자연어 이해를 서버 정규식으로 대체하는 것이 아니라, 서버가 검증 가능한 식별자를 수집하는 단계예요.

```java
List<String> explicitOrderIds = OrderIdExtractor.extract(message);
ConversationOrderState orderState =
        orderStateRepository.rememberExplicitOrderIds(conversationId, explicitOrderIds);

chatClient.prompt()
        .toolContext(Map.of(
                "conversationId", conversationId,
                "explicitOrderIds", explicitOrderIds,
                "recentOrderIds", orderState.recentOrderIds(),
                "activeOrderId", orderState.activeOrderId()
        ));
```

`ToolExecutionPolicy`는 `cancelOrder`만 보수적으로 막아요.
현재 요청에 명시된 orderId와 LLM이 제안한 orderId가 일치하면 허용하고, 그렇지 않으면 실제 취소를 실행하지 않아요.

```java
if (toolName.equals("cancelOrder")) {
    String proposedOrderId = readOrderId(toolInput);

    if (explicitOrderIds.contains(proposedOrderId)) {
        return ALLOW;
    }

    return BLOCK_CONFIRMATION_REQUIRED;
}
```

차단 시에는 예외를 던지지 않고 Tool 결과를 반환해요.
그러면 LLM은 이 Tool 결과를 보고 사용자에게 주문번호 확인을 요청하는 최종 응답을 만들 수 있어요.

```json
{
  "outcome": "CONFIRMATION_REQUIRED",
  "orderId": "2024-1237",
  "candidates": ["2024-1234", "2024-1237"],
  "message": "취소할 주문번호를 다시 확인해 주세요."
}
```

이 방식의 장점은 LLM 호출 수를 늘리지 않으면서, 취소 같은 상태 변경 Tool의 최종 실행권을 서버가 가진다는 점이에요.
반대로 read-only Tool인 `getOrderDetail`, `getDeliveryStatus`는 기존처럼 LLM이 호출할 수 있게 두었어요.
운영에서 더 확장하려면 `confirmedOrderId`를 UI 확인 버튼이나 서버 발급 confirmation token으로 ToolContext에 넣고, 그 값과 일치할 때만 취소를 허용하면 돼요.

이렇게 설계한 근거는 read-only Tool과 command Tool의 실패 비용이 다르기 때문이에요.
`getDeliveryStatus`나 `getOrderDetail`이 잘못된 주문번호로 호출되면 고객에게 잘못된 안내를 할 수는 있지만, 서버 상태가 바뀌지는 않아요.
반면 `cancelOrder`가 잘못된 주문번호로 호출되면 실제 주문 상태가 `CANCELED`로 바뀌고, 결제/조리/운영 흐름까지 영향을 줄 수 있어요.
즉 같은 "그 주문" 해석 실패라도 조회 실패와 취소 실패의 blast radius가 달라요.

| Tool 종류 | 예시 | 잘못된 orderId 추론 시 영향 | 이번 구현의 정책 |
| --- | --- | --- | --- |
| read-only query | `getOrderDetail`, `getDeliveryStatus` | 잘못된 안내, 고객 혼란. 서버 상태 변경 없음 | LLM 추론 허용. 필요하면 응답에서 재확인 |
| command / side effect | `cancelOrder` | 주문 상태 변경, 취소 이력 생성, 결제/운영 영향 | 현재 발화에 명시 orderId가 없으면 실행 직전 차단 |

따라서 이번 정책은 모든 대명사 실패를 없애는 장치가 아니에요.
예전 실패 시나리오인 `"아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘"`처럼 read-only 질문은 여전히 모델이 되묻거나 잘못 판단할 수 있어요.
실제로 한 번의 live 검증에서는 `2024-1237`로 잘못 붙었고, 다른 검증에서는 주문번호를 다시 물었어요.
3단계 JDBC 재검증 중 단순 순서에서는 같은 취지의 질문을 `2024-1234`로 맞춘 적도 있었어요.
하지만 실패했던 순서와 같은 문장으로 다시 비교하니 JDBC와 InMemory가 모두 같은 방식으로 실패했어요.
즉 차이는 저장소 때문이 아니라, 직전에 `그 버거 세트`로 `2024-1237` 문맥을 강하게 만든 실험 순서 때문이라고 보는 게 맞아요.

```text
공통 실험 순서:
1) 2024-1234 배달 상황 알려주세요
2) 2024-1237 주문 메뉴 뭐였죠?
3) 그 버거 세트 지금 어디쯤이에요?
4) 아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘
5) 처음에 물어본 그 주문 라이더 위치 다시 확인해줘
```

| 저장소 | 4턴 기대 | 4턴 실제 | 5턴 기대 | 5턴 실제 |
| --- | --- | --- | --- | --- |
| JDBC file | `2024-1234` | `2024-1237` | `2024-1234` | `2024-1237` |
| InMemory | `2024-1234` | `2024-1237` | `2024-1234` | `2024-1237` |

JDBC와 InMemory 모두 Memory에는 `2024-1234`와 `2024-1237`이 남아 있었지만, 모델은 "배달 중이던", "처음에 물어본"보다 최근의 버거 세트 문맥을 더 강하게 따라갔어요.
저장소를 JDBC로 바꿔도 상태 조건 참조가 구조적으로 해결되는 것은 아니고, 모델이 자연어 Memory를 그때그때 어떻게 읽는지에 따라 결과가 흔들릴 수 있어요.
하지만 `"그 주문 취소해주세요"`처럼 command로 이어진 경우에는 모델이 `cancelOrder(2024-1237)`을 제안해도 `ToolExecutionPolicy`가 아래처럼 차단했어요.

```text
[ToolPolicy] blocked cancelOrder.
conversationId=customer-1:tool-policy-live,
proposedOrderId=2024-1237,
explicitOrderIds=[],
recentOrderIds=[2024-1237]
```

반대로 현재 발화에 `2024-1235 취소해주세요`처럼 주문번호가 명시되어 있으면 같은 policy가 허용하고 실제 Tool이 실행돼요.

```text
[Tool] cancelOrder(orderId=2024-1235, reason=고객 요청)
[Tool] cancelOrder result(... outcome=CANCELED ...)
```

결론적으로 이 설계의 목적은 "AI가 항상 올바른 주문을 고르게 만든다"가 아니라, "AI가 틀릴 수 있음을 전제로 상태 변경 Tool의 최종 실행권을 서버가 가진다"는 거예요.

## 1단계 설계 결정

### MAX_MESSAGES = 20

`MessageWindowChatMemory`의 `maxMessages`는 전체 서버가 아니라 conversation id별 메시지 개수예요.
`USER`와 `ASSISTANT`를 합쳐 20개이므로 일반적인 왕복 기준 약 10턴을 유지해요.
배달 상담은 주문 상태 확인, 취소 가능 여부, 주문 전환, 재확인까지 보통 3~6턴 안에서 끝난다고 가정하면 10턴은 기준 실험값으로 충분해요.
다만 위 실험에서도 1턴 `promptTokens=2355`에서 2턴 `promptTokens=2471~2499`처럼 Memory가 붙은 만큼 입력 토큰이 증가했어요.
따라서 20은 운영 최적값이라기보다 Round 3 기준선이고, 실제 기준은 2단계의 `2 / 20 / Integer.MAX_VALUE` 토큰 비교로 다시 정해야 해요.

### `default` 폴백 위험

| 위험 시나리오 | 영향 | 대응 |
| --- | --- | --- |
| 구버전 앱이 `X-Session-Id`를 보내지 않음 | 여러 사용자가 `default` conversation에 섞여 다른 고객의 주문번호가 프롬프트에 들어갈 수 있음 | 운영에서는 헤더 누락 시 400 응답, 또는 서버가 세션 ID 발급 |
| 어뷰저가 의도적으로 헤더를 제거함 | 공용 `default` 세션을 오염시켜 다른 사용자의 대화 품질과 개인정보를 훼손할 수 있음 | 인증된 사용자 ID와 서버 발급 session id를 함께 검증 |
| 테스트/운영 클라이언트가 같은 기본값 사용 | 스테이지 환경의 대화가 운영과 다른 재현 불가능한 상태를 만들 수 있음 | 환경별 세션 namespace 분리 |

이번 실습에서는 과제 편의를 위해 `defaultValue = "default"`를 남겼지만, 운영에서는 개발용 폴백으로만 봐야 해요.

### 세션 식별 방식 비교

| 방식 | 장점 | 배달 상담 도메인 단점 |
| --- | --- | --- |
| 쿠키 / HTTP Session | 웹 채팅 UI에서는 자동 전송되고 구현이 단순함 | 앱/API 클라이언트에서는 다루기 번거롭고, 멀티 인스턴스에서는 sticky session이나 외부 세션 저장소가 필요 |
| JWT 클레임 | 인증된 고객 ID와 세션 scope를 함께 검증할 수 있음 | 토큰 만료, 재발급, 세션 회전 정책을 설계해야 함 |
| URL 경로 | `/session/{id}/chat`처럼 명시적이고 디버깅이 쉬움 | URL 로그에 세션 ID가 남기 쉬워 민감 정보가 될 수 있음 |
| HTTP 헤더 | 앱/웹/API 모두에서 쓰기 쉽고 프레임워크 의존이 낮음 | 클라이언트가 임의 값을 보낼 수 있으므로 인증 컨텍스트와 결합하지 않으면 위험 |

이번 구현은 HTTP 헤더를 사용하되, `X-Session-Id`만 쓰지 않고 `X-Customer-Id`와 묶어 `customer-1:s1-cust-A` 형태로 conversation id를 만들었어요.
실제 운영에서는 `X-Customer-Id`도 헤더에서 신뢰하지 말고 JWT나 서버 세션에서 검증된 고객 ID를 읽어야 해요.

### 클라이언트 지정 세션 ID의 보안 리스크

클라이언트가 세션 ID를 직접 정하면 다른 사용자의 세션 ID를 추측하거나, 같은 세션 ID를 재사용해 프롬프트 오염을 만들 수 있어요.
이를 막으려면 서버가 UUID 기반 session id를 발급하고, 세션 소유자를 인증된 customer id와 함께 저장해야 해요.
또는 JWT에 서명된 conversation claim을 넣어 위변조를 막고, 세션 조회/삭제 API는 항상 현재 인증 사용자 범위로만 동작해야 해요.

### 취소 의도와 Memory 실험

Round 3에서 가장 중요하게 본 시나리오는 "주문 취소하고 싶어"처럼 의도만 먼저 말하고, 다음 턴에 주문번호만 주는 경우예요.
같은 세션 `cancel-flow`에서 아래 순서로 실행했어요.

```bash
curl -X POST "$BASE_URL/api/v1/assistant" \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: cancel-flow' \
  -d '{"message":"주문 취소하고 싶어"}'

curl -X POST "$BASE_URL/api/v1/assistant" \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: cancel-flow' \
  -d '{"message":"2024-1235"}'
```

1턴 응답은 주문번호를 요구했어요.

```text
핵심 답변: 주문 취소를 요청하셨습니다. 주문번호가 필요해요.
필요한 정보: 주문번호를 알려주세요.
다음 액션: 주문번호를 입력해 주시면 즉시 취소 절차를 진행해 드리겠습니다.
```

2턴에서는 사용자가 주문번호만 보냈지만, Memory에 남아 있던 취소 의도를 이어받아 `cancelOrder` Tool을 호출했어요.

```text
[Assistant] sessionId=cancel-flow, conversationId=customer-1:cancel-flow, message=2024-1235
LLM request prompt. endpoint=assistant, messageCount=4, ...
[Tool] cancelOrder(orderId=2024-1235, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1235, cancelId=0f6f8235-54d7-4496-91cc-14cf83a1db41, outcome=CANCELED, status=CANCELED, canceledReason=고객 요청, ...)
LLM call completed. endpoint=assistant, elapsedMs=28274, promptTokens=2575, completionTokens=1727, totalTokens=4302
```

Memory 상태도 USER/ASSISTANT 2왕복으로 남았어요.

```json
[
  {"type":"USER","content":"주문 취소하고 싶어"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문 취소를 요청하셨습니다. 주문번호가 필요해요.\n\n필요한 정보: 주문번호를 알려주세요.\n\n다음 액션: 주문번호를 입력해 주시면 즉시 취소 절차를 진행해 드리겠습니다."},
  {"type":"USER","content":"2024-1235"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문번호 2024-1235의 주문이 성공적으로 취소되었습니다. 취소 사유는 \"고객 요청\"이에요. 추가 도움이 필요하시면 언제든지 연락해 주세요."}
]
```

같은 `X-Session-Id: cancel-flow`를 `customer-2`가 사용하면 conversation id가 `customer-2:cancel-flow`로 분리되어 `customer-1`의 취소 의도가 섞이지 않았어요.
`customer-2`가 주문번호만 보낸 경우에는 Tool을 호출하지 않고 원하는 작업을 다시 물었어요.

```text
[Assistant] sessionId=cancel-flow, conversationId=customer-2:cancel-flow, message=2024-1235
LLM request prompt. endpoint=assistant, messageCount=2, ...
```

응답:

```text
핵심 답변: 주문번호 2024-1235가 제공되었습니다. 주문 상태, 배달 정보, 취소, 환불 등 어떤 부분을 확인하시고자 하는지 알려주세요.
```

또 `customer-2`가 명시적으로 `2024-1235 취소해주세요`라고 요청하면 Tool은 실행되지만, `findByIdForCustomer(orderId, customerId)` 소유권 검증 때문에 결과가 `NOT_FOUND`로 막혔어요.

```text
[Assistant] sessionId=cancel-not-owner, conversationId=customer-2:cancel-not-owner, message=2024-1235 취소해주세요
[Tool] cancelOrder(orderId=2024-1235, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1235, cancelId=null, outcome=NOT_FOUND, status=null, canceledReason=null, canceledAt=null)
```

이 실험으로 Memory는 취소 의도와 주문번호를 이어 붙이는 데 도움이 되지만, 실제 command Tool에서는 반드시 현재 고객 소유권과 최신 주문 상태를 다시 확인해야 한다는 점을 확인했어요.

### 재취소 멱등성과 Memory 실험

Round 2에서 만든 취소 멱등성도 Memory와 같이 확인했어요.
목표는 첫 턴에서 `2024-1239`를 취소한 뒤, 두 번째 턴에서 주문번호 없이 "방금 그거 다시 취소해줘"라고 말했을 때 동작을 보는 것이었어요.

```bash
curl -X POST "$BASE_URL/api/v1/assistant" \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: idempotency-flow' \
  -d '{"message":"2024-1239 취소해주세요"}'

curl -X POST "$BASE_URL/api/v1/assistant" \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: customer-1' \
  -H 'X-Session-Id: idempotency-flow' \
  -d '{"message":"방금 그거 다시 취소해줘"}'
```

1턴 응답:

```text
주문번호 2024-1239가 성공적으로 취소되었습니다. 취소 사유는 고객 요청이에요.
```

1턴 Tool 로그:

```text
[Assistant] sessionId=idempotency-flow, conversationId=customer-1:idempotency-flow, message=2024-1239 취소해주세요
[Tool] cancelOrder(orderId=2024-1239, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=4fac69c1-95e8-419a-a9e4-6604f4ea29db, outcome=CANCELED, status=CANCELED, canceledReason=고객 요청, canceledAt=2026-05-31T01:04:03.015453+09:00)
LLM call completed. endpoint=assistant, elapsedMs=33995, promptTokens=2421, completionTokens=2090, totalTokens=4511
```

2턴에서는 사용자가 주문번호를 다시 말하지 않았지만, Memory가 1턴의 `2024-1239`를 프롬프트에 넣었고 LLM이 같은 주문으로 `cancelOrder`를 다시 호출했어요.
다만 도메인 계층은 이미 취소된 주문을 새로 취소하지 않고 `ALREADY_CANCELED`로 응답했고, 첫 취소의 `cancelId`를 그대로 재사용했어요.

```text
[Assistant] sessionId=idempotency-flow, conversationId=customer-1:idempotency-flow, message=방금 그거 다시 취소해줘
LLM request prompt. endpoint=assistant, messageCount=4, toolCount=3, messages=USER(chars=16), ASSISTANT(chars=128), SYSTEM(chars=1297), USER(chars=13), tools=getOrderDetail, getDeliveryStatus, cancelOrder
[Tool] cancelOrder(orderId=2024-1239, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=4fac69c1-95e8-419a-a9e4-6604f4ea29db, outcome=ALREADY_CANCELED, status=CANCELED, canceledReason=고객 요청, canceledAt=2026-05-31T01:04:03.015453+09:00)
LLM call completed. endpoint=assistant, elapsedMs=27803, promptTokens=2617, completionTokens=1706, totalTokens=4323
```

2턴 응답:

```text
핵심 답변: 주문 2024-1239는 이미 취소되었습니다. 다시 취소할 수 없어요.
```

Memory 상태:

```json
[
  {"type":"USER","content":"2024-1239 취소해주세요"},
  {"type":"ASSISTANT","content":"주문번호 2024-1239가 성공적으로 취소되었습니다. 취소 사유는 고객 요청이에요. 주문이 취소되었으므로 음식은 제공되지 않습니다.  \n필요한 추가 정보: 없음  \n다음에 취할 액션: 추가 문의사항이 있으면 언제든지 알려주세요."},
  {"type":"USER","content":"방금 그거 다시 취소해줘"},
  {"type":"ASSISTANT","content":"핵심 답변: 주문 2024-1239는 이미 취소되었습니다. 다시 취소할 수 없어요. 새로운 주문을 진행하거나 고객센터에 문의해 주세요.  \n필요한 추가 정보: 없음  \n다음에 취할 액션: 새 주문을 생성하거나 고객센터로 연락해 주세요."}
]
```

구현상 멱등성은 Memory가 아니라 `OrderCancelService`와 `Order`가 책임져요.
`OrderCancelService.cancel()`은 `findByIdForCustomer(orderId, customerId)`로 소유권을 먼저 확인하고, 같은 주문 객체에 대해 `synchronized (order)`로 취소 전이를 직렬화해요.
첫 취소는 `CancelHistoryService.record()`로 이력을 만들고, 반복 취소는 `findLatestCanceled(orderId, customerId)`로 기존 이력을 찾아 같은 `cancelId`, `canceledReason`, `canceledAt`을 돌려줘요.

자동 테스트도 이 경계를 확인해요.
`OrderToolsTest.cancelOrderReusesFirstCancelHistoryForRepeatedCancel`는 순차 재취소에서 이력이 1개만 남고 같은 `cancelId`를 재사용하는지 검증하고, `cancelOrderCreatesSingleCancelHistoryForConcurrentRepeatedCancel`는 8개 동시 취소 요청에서 `CANCELED` 1개와 `ALREADY_CANCELED` 7개만 나오는지 확인해요.
즉 Memory는 "방금 그거"를 `2024-1239`로 해석하게 돕지만, 멱등성과 취소 이력의 정합성은 Tool 뒤쪽의 도메인 코드가 보장해요.

정리하면 `CancelHistory`의 한계를 Memory가 완전히 해결한 것은 아니에요.
두 기능은 서로 다른 문제를 나눠 맡아요.
`CancelHistory`는 "이 주문이 이미 취소됐는가", "첫 취소 이력은 무엇인가", "반복 요청에서 같은 취소 결과를 돌려줄 수 있는가" 같은 도메인 사실을 보장해요.
반면 Chat Memory는 "사용자가 말한 방금 그거가 어떤 주문인가", "이전 턴의 취소 의도가 아직 이어지는가" 같은 대화 문맥을 보완해요.

```text
Chat Memory:
  "방금 그거 다시 취소해줘" -> 이전 대화에서 2024-1239를 찾음

Tool / Domain:
  cancelOrder(2024-1239)
  -> 이미 CANCELED 상태
  -> 기존 CancelHistory 재사용
  -> ALREADY_CANCELED 반환
```

그래서 이번 라운드의 결론은 "Memory가 History를 대체한다"가 아니라, "Memory는 문맥 힌트이고 History는 도메인 사실이다"에 가까워요.
LLM이 Memory를 보고 주문번호를 잘 추론하더라도, 최종 실행 단계에서는 항상 Tool이 현재 고객 소유권, 최신 주문 상태, 취소 이력을 다시 확인해야 해요.

다만 `X-Session-Id`를 그대로 신뢰하는 방식은 운영용 인증 모델이 아니에요.
실제 서비스에서는 쿠키, JWT, 인증된 사용자 ID, 서버가 발급한 conversation id 중 하나로 세션을 묶고, 다른 고객이 임의 세션 ID를 추측해 대화 기록을 조회하지 못하게 막아야 해요.

이번 구현에서 Memory는 어디까지나 문맥 힌트예요.
사용자가 "그 주문 취소해줘"라고 말했을 때 이전 대화의 주문번호를 해석하는 데 도움을 줄 수는 있지만, command Tool을 실행할 때는 여전히 현재 고객 소유 주문인지 확인하고 최신 주문 상태를 Tool로 다시 조회해야 해요.
즉 "기억은 문맥, 실행은 Tool, 최종 판단은 현재 상태"로 두는 게 맞다고 봤어요.

자동 테스트는 실제 LLM을 호출하지 않고 Memory 경계만 검증했어요.
모델 응답은 느리고 비결정적이라 단위 테스트에 넣기 어렵기 때문에, `ChatMemoryConfigTest`에서는 20개 윈도우와 Advisor order를 확인하고 `SessionControllerTest`에서는 저장된 메시지 조회, 세션 ID 목록, 삭제 동작을 검증했어요.

## 2단계 검증 - Memory 크기 실험

같은 10턴 시나리오를 `MAX_MESSAGES = 20`, `2`, `Integer.MAX_VALUE`로 각각 실행했어요.
서버는 매번 새로 띄웠고, 실험 세션도 분리했어요.
토큰과 시간은 `PerformanceLoggingAdvisor`의 `LLM call completed` 로그에서 10턴을 읽어 평균을 냈어요.

```text
1) 2024-1234 배달 상황 알려주세요
2) 그거 몇 분 남았어요?
3) 2024-1235 주문도 있는데 메뉴 뭐였죠?
4) 아 그 버거 세트
5) 2024-1234 취소 가능해요?
6) 그럼 1235는 취소되죠?
7) 그거 취소해주세요
8) 아까 1234는 언제 도착해요?
9) 그 주문 라이더 위치 다시 확인
10) 요약해 주세요 지금까지 제가 뭘 물어봤는지
```

### 정량 비교

| 실험 | MAX_MESSAGES | 10턴 평균 입력 토큰 | 10턴 평균 출력 토큰 | 10턴 평균 응답 시간 | 지시/참조 해결 성공 |
| --- | ---: | ---: | ---: | ---: | ---: |
| A | 20 | 2,493.1 | 2,166.4 | 37,618.7ms | 3 / 5 |
| B | 2 | 2,042.9 | 1,718.8 | 27,501.0ms | 1 / 5 |
| C | Integer.MAX_VALUE | 2,572.7 | 1,885.4 | 32,189.5ms | 2 / 5 |

이 표에서 가장 중요한 의미는 "토큰이 적다고 좋은 Memory 설정은 아니다"예요.
`MAX_MESSAGES = 2`는 평균 입력 토큰과 응답 시간이 가장 낮았지만, 실제 상담 품질은 가장 많이 깨졌어요.
즉 비용과 속도만 보면 2가 좋아 보일 수 있지만, 이전 주문번호를 복원하지 못해 잘못된 Tool 파라미터를 만들었기 때문에 상담 에이전트로는 위험했어요.

반대로 `Integer.MAX_VALUE`는 10턴 평균만 보면 `20`보다 압도적으로 나쁘게 보이지 않았어요.
이건 10턴 시점의 대화가 아직 20 messages 근처라서, `MAX_MESSAGES = 20`과 `Integer.MAX_VALUE`가 프롬프트에 넣는 Memory 범위가 거의 같았기 때문이에요.
따라서 이 숫자만 보고 "무제한도 괜찮다"고 판단하면 안 된다고 생각했어요.
무제한의 문제는 10턴보다 30턴, 50턴, 100턴처럼 장기 상담에서 입력 토큰과 응답 시간이 계속 누적된다는 점이에요.

또 평균 입력 토큰이 매 턴 완전히 단조 증가하지는 않았어요.
Tool 호출 여부, 모델이 만든 응답 길이, Tool 결과 재호출 여부에 따라 `promptTokens`가 흔들렸어요.
그래서 이 실험의 해석은 "정확히 몇 토큰이 정답인가"가 아니라, `MAX_MESSAGES`가 작으면 문맥 품질이 깨지고, 너무 크면 장기적으로 비용과 지연이 커진다는 경향을 확인하는 데 있어요.

결론적으로 `20`은 최종 운영값이라기보다, 이번 도메인에서 10턴 정도의 상담을 보존하면서도 무제한 누적을 막는 기준선으로 의미가 있었어요.
운영에서는 이 값을 고정 상수로 믿기보다 실제 상담 로그의 평균/상위 95% 턴 수, 입력 토큰 예산, 실패율을 같이 보고 조정해야 해요.

지시/참조 해결 성공은 아래 5개 턴만 봤어요.

| 턴 | 기대 |
| --- | --- |
| 2 | `그거`를 `2024-1234`로 해석 |
| 4 | `그 버거 세트`가 모호하거나 잘못된 참조임을 처리 |
| 7 | `그거 취소`가 직전의 `2024-1235` 취소 의도를 이어받는지 |
| 8 | `아까 1234`를 `2024-1234`로 해석 |
| 9 | `그 주문`을 8턴의 `2024-1234`로 해석 |

`MAX_MESSAGES = 20`은 2, 8, 9턴은 맞췄지만 4턴은 `2024-1235`를 다시 설명했고, 7턴은 `cancelOrder(2024-1235)`를 제안했지만 command guard가 실행 직전에 차단했어요.
따라서 read-only 문맥은 일부 성공했지만 command는 별도 확인이 필요하다고 봤어요.

```text
7턴 로그:
[ToolPolicy] blocked cancelOrder.
conversationId=customer-1:round2-size-20-r2,
proposedOrderId=2024-1235,
explicitOrderIds=[],
recentOrderIds=[2024-1234, 2024-1235]
```

`MAX_MESSAGES = 2`는 실패가 가장 뚜렷했어요.
메모리에 직전 USER/ASSISTANT 한 쌍만 남기 때문에, 10턴이 끝난 뒤 Memory는 마지막 요약 요청과 응답만 남았어요.

```json
[
  {
    "type": "USER",
    "content": "요약해 주세요 지금까지 제가 뭘 물어봤는지"
  },
  {
    "type": "ASSISTANT",
    "content": "핵심 답변: 지금까지 고객님께서 물어보신 내용은 \"그 주문 라이더 위치 다시 확인\"입니다."
  }
]
```

실패 예시는 6~9턴에서 잘 보였어요.
사용자는 `1235`, `1234`를 앞의 `2024-1235`, `2024-1234`의 축약처럼 말했지만, 모델은 이전 긴 주문번호를 복원하지 못하고 `1235`, `1234` 그대로 Tool에 넘겼어요.

```text
6턴: 그럼 1235는 취소되죠?
[Tool] getOrderDetail(orderId=1235)
[Tool] getOrderDetail result(null)

8턴: 아까 1234는 언제 도착해요?
[Tool] getDeliveryStatus(orderId=1234)
[Tool] getDeliveryStatus result(null)
```

응답도 실제 주문을 잇지 못했어요.

```text
주문번호 1235는 시스템에서 찾을 수 없습니다.
주문번호 1234는 시스템에서 찾을 수 없습니다.
```

왜 2가 부족한지도 여기서 확인돼요.
`MessageWindowChatMemory`는 message 단위로 자르기 때문에 `maxMessages = 2`면 사실상 직전 USER/ASSISTANT 한 쌍만 남아요.
따라서 1턴의 `2024-1234`, 3턴의 `2024-1235` 같은 기준 정보가 몇 턴 뒤에는 사라지고, 모델은 축약 주문번호를 완성할 근거를 잃어요.

`Integer.MAX_VALUE`는 10턴 기준으로는 `20`과 큰 차이가 나지 않았어요.
10턴이면 USER/ASSISTANT가 20 messages라서 `MAX_MESSAGES = 20`도 아직 거의 같은 범위를 담을 수 있기 때문이에요.
다만 로그의 messageCount는 턴이 진행될수록 2, 4, 6, ... 20으로 계속 증가했고, 윈도우 제한이 없으면 11턴부터도 계속 늘어나는 구조예요.

```text
MAX_VALUE request messageCount:
1턴=2
2턴=4
3턴=6
4턴=8
5턴=10
6턴=12
7턴=14
8턴=16
9턴=18
10턴=20
```

이번 실험에서는 발열 때문에 10턴에서 멈췄어요.
하지만 `MAX_MESSAGES = 20`은 10턴 시점에 이미 20 messages에 도달하므로 다음 턴부터 오래된 일반 메시지가 잘려요.
반면 `Integer.MAX_VALUE`는 11턴, 12턴, 30턴까지도 계속 누적돼 입력 토큰과 응답 시간이 늘어날 수밖에 없어요.
즉 `MAX_VALUE`의 위험은 10턴 평균보다 장기 상담에서 더 크게 드러나요.

### 설계 판단

| 전략 | 맞는 시나리오 | 장점 | 단점 |
| --- | --- | --- | --- |
| Sliding window | 짧은 상담, 최근 주문 상태가 중요한 배달 상담 | 구현이 단순하고 오래된 맥락을 자동 제거 | 오래된 주문 재문의나 장기 상담 요약에는 약함 |
| Summarization | 긴 상담, 상담원 인계, 다건 문의 요약 | 오래된 대화를 압축해 보존 가능 | 요약이 잘못되면 주문 상태나 취소 이력을 그럴듯하게 왜곡할 수 있음 |

배달 상담에서 지시 대명사 해결은 command Tool 기준으로 더 엄격하게 봐야 해요.
조회 질문은 일부 실패해도 되묻거나 정정할 수 있지만, 취소는 실제 상태 변경이 일어나요.
그래서 프로덕션 기준은 read-only 참조 성공률보다 command 안전성을 우선해야 하고, `cancelOrder`는 현재 발화에 명시 주문번호가 없으면 실행 직전에 차단하는 정책을 유지하는 편이 맞다고 생각했어요.

오래된 대화가 의미 있는 경우도 있어요.
예를 들어 3시간 전 배달 완료 건의 환불 재문의, 어제 취소한 주문의 결제 취소 확인, 상담원 인계 후 같은 고객이 다시 돌아온 경우예요.
이런 경우를 세션 Memory만으로 해결하려고 하면 세션 길이와 개인정보 보관 리스크가 커져요.
운영에서는 ChatMemory와 별개로 주문/취소 이력 같은 도메인 저장소를 조회하고, Memory는 "어떤 주문을 말하는지"를 좁히는 힌트로만 쓰는 편이 낫다고 봤어요.

세션이 아니라 고객 단위로 Memory를 영속 유지하면 "어제 문의한 주문", "지난번 취소 건" 같은 UX는 좋아질 수 있어요.
하지만 고객의 주소, 전화번호, 주문 습관, 결제 관련 발화가 장기간 쌓이므로 개인정보 저장소가 돼요.
따라서 고객 단위 영속 Memory는 TTL, 마스킹, 접근 제어, 삭제 요청 처리, 감사 로그를 같이 설계해야 해요.

## 3단계 검증 - InMemory vs JdbcChatMemory

3단계에서는 Chat Memory 저장소를 기본 InMemory에서 JDBC로 바꿔 보고, 같은 기능이 유지되는지와 서버 재시작 후 Memory가 살아남는지를 확인했어요.
중요한 결론은 저장소 전환이 "대화 해석 능력"을 올리는 것이 아니라, "대화 기록을 어디에 얼마나 오래 저장할 것인가"를 바꾸는 결정이라는 점이에요.

### 구현 변경

`build.gradle`에 JDBC Chat Memory starter와 H2 드라이버를 추가했어요.

```groovy
implementation 'org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc'
runtimeOnly 'com.h2database:h2'
```

`ChatMemoryConfig`의 InMemory repository Bean은 `jdbc` 프로필이 아닐 때만 등록되게 했어요.
그래야 `jdbc` 프로필에서는 Spring AI가 자동 구성한 `JdbcChatMemoryRepository`가 `MessageWindowChatMemory`에 주입돼요.

```java
@Bean
@Profile("!jdbc")
public ChatMemoryRepository chatMemoryRepository() {
    return new InMemoryChatMemoryRepository();
}
```

JDBC starter를 추가하면 기본 프로필에서도 Spring Boot가 H2 DataSource를 자동 구성할 수 있어요.
이때 JDBC Chat Memory schema initializer가 같이 실행되면 기본 InMemory 실행이 깨질 수 있어서, 기본 `application.yml`에서는 JDBC schema init을 꺼뒀어요.
`jdbc` 프로필에서만 다시 켜요.

```yaml
# application.yml
spring:
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: never
```

```yaml
# application-jdbc.yml
spring:
  datasource:
    url: jdbc:h2:mem:baedal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: true
      path: /h2-console
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: embedded
            platform: postgresql
```

여기서 `platform: postgresql`을 둔 이유는 Spring AI 1.0.0 JDBC memory jar 안에 H2용 schema 파일이 없고, PostgreSQL schema가 제공되기 때문이에요.
H2를 `MODE=PostgreSQL`로 실행해서 PostgreSQL schema를 적용했어요.

### JDBC 프로필 실행

```bash
LOG_DIR=/private/tmp/baedal-round3-jdbc-mem \
  ./gradlew bootRun \
  --args='--server.port=18080 --spring.profiles.active=jdbc --baedal.chat-memory.max-messages=20'
```

실행 로그에서 JDBC profile과 H2 Console이 정상으로 뜨는 것을 확인했어요.

```text
The following 1 profile is active: "jdbc"
HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:baedal user=SA
H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:baedal'
```

### JDBC 프로필에서 1단계 시나리오 재검증

JDBC 프로필에서도 1단계 5종 시나리오를 같은 방식으로 다시 돌렸어요.
저장소만 바뀌었고 `MessageWindowChatMemory`, `MessageChatMemoryAdvisor`, `ChatMemory.CONVERSATION_ID` 연결은 그대로라서 응답 흐름은 InMemory와 같았어요.

| 시나리오 | 기대 | JDBC 결과 |
| --- | --- | --- |
| `2024-1234 어디쯤?` -> `그거 언제 도착해요?` | 2턴에서 `2024-1234` 재참조 | `2024-1234` 배달 중 / 역삼역 사거리로 응답 |
| `2024-1234 취소` -> `그거 말고 2024-1235 취소` | 대상이 `1235`로 전환 | `2024-1234`는 취소 불가, `2024-1235`는 취소 성공 |
| `2024-1234 배달 상황` -> `아까 물어본 그 주문` | 이전 orderId 추출 | `2024-1234`로 재조회 |
| 세션 A에서 `2024-1234`, 세션 B에서 `그 주문` | B에는 맥락 없음 | B는 주문번호를 다시 물음 |
| Memory 삭제 후 `그거` | 맥락 소실 | 주문번호를 다시 물음 |

삭제 시나리오의 JDBC Memory 상태도 확인했어요.

```json
// 삭제 전
[
  {"type":"USER","content":"2024-1234 배달 어디쯤이에요?"},
  {"type":"ASSISTANT","content":"2024-1234번 주문의 배달 상태는 '배달 중입니다'입니다. 라이더는 현재 역삼역 사거리에 있습니다. 추가 정보가 필요하시면 알려주세요."}
]

// DELETE /api/v1/session/j5 이후
[]
```

삭제 후 같은 세션에서 `그거 언제 도착해요?`를 보내면 아래처럼 주문번호를 다시 물었어요.

```text
핵심 답변: 주문 번호가 필요해요. 배달 상태를 확인하기 위해 주문 번호를 알려주세요.
```

### H2 Console 테이블 확인

H2 Console에 접속해서 아래 쿼리를 실행했어요.

```sql
SELECT conversation_id, type, LEFT(content, 80) AS content_preview, "timestamp"
FROM SPRING_AI_CHAT_MEMORY
ORDER BY "timestamp";
```

실제 테이블에는 conversation id별 USER/ASSISTANT 메시지가 저장됐어요.
이번 실행에서는 `MessageChatMemoryAdvisor`가 저장한 USER/ASSISTANT rows만 보였고, Tool 요청/응답은 Chat Memory 테이블에 남지 않았어요.

```text
CONVERSATION_ID | TYPE      | CONTENT_PREVIEW                         | timestamp
customer-1:j1   | USER      | 2024-1234 어디쯤 있어요?                 | 2026-05-31 11:51:52.278
customer-1:j1   | ASSISTANT | 주문번호 2024-1234의 배달 상태는 현재... | 2026-05-31 11:51:52.279
customer-1:j1   | USER      | 그거 언제 도착해요?                      | 2026-05-31 11:51:52.280
customer-1:j1   | ASSISTANT | 주문번호 2024-1234는 현재 '배달 중'...   | 2026-05-31 11:51:52.281
customer-1:j2   | USER      | 2024-1234 취소해주세요                   | 2026-05-31 11:53:26.834
customer-1:j2   | ASSISTANT | 핵심 답변: 주문 번호 2024-1234는...      | 2026-05-31 11:53:26.835
customer-1:j2   | USER      | 아, 그거 말고 2024-1235 취소해주세요     | 2026-05-31 11:53:26.836
customer-1:j2   | ASSISTANT | 핵심 답변: 주문 번호 2024-1235가...      | 2026-05-31 11:53:26.837
...
(18 rows)
```

이 결과로 JDBC 저장소가 실제 DB 테이블에 conversation id, message type, content, timestamp를 남긴다는 것을 확인했어요.
다만 Tool 결과 JSON 전체가 Memory에 쌓이지 않는다는 점도 같이 확인했어요.
이건 토큰 비용 관점에서는 유리하지만, 나중에 `"DELIVERING인 주문"`처럼 Tool 결과의 구조화된 값을 검색하고 싶을 때는 Chat Memory만으로 부족하다는 뜻이에요.

### 재시작 실험

같은 세션에서 먼저 `2024-1234 배달 상황 알려주세요`를 보낸 뒤 서버를 내리고 다시 띄웠어요.
재시작 후 같은 세션에 `그거 언제 도착해요?`를 보냈을 때, `2024-1234`를 기억하는지 확인했어요.

| 저장소 설정 | 재시작 후 Memory 유지? | 관찰 |
| --- | --- | --- |
| InMemory 기본 | 아니요 | 재시작 후 `그거`에 대해 주문번호를 다시 물었어요 |
| `jdbc:h2:mem:baedal` | 아니요 | JDBC여도 H2 mem은 JVM 재시작 후 사라져서 주문번호를 다시 물었어요 |
| `jdbc:h2:file:/private/tmp/baedal-round3-h2/baedal-stage3-file2` | 예 | 재시작 후 `그거`를 `2024-1234`로 해석해 배달 중 / 역삼역 사거리로 응답했어요 |

`jdbc:h2:mem` 재시작 후 응답:

```text
핵심 답변: 주문번호가 필요합니다. 주문번호를 알려주시면 배달 상태를 확인해 드리겠습니다.
```

`jdbc:h2:file` 재시작 후 응답:

```text
핵심 답변: 주문 번호 2024-1234는 현재 배달 중입니다.
라이더가 역삼역 사거리에 위치해 있으며, 정확한 도착 시간은 라이더의 현재 진행 상황에 따라 달라집니다.
```

파일 기반 H2 실험에서는 첫 실행 때 schema 생성을 명시적으로 켰어요.
`initialize-schema=embedded`만으로는 file URL에서 테이블이 만들어지지 않아 `SPRING_AI_CHAT_MEMORY not found`가 발생했기 때문이에요.

```bash
# 첫 실행: schema 생성
LOG_DIR=/private/tmp/baedal-round3-jdbc-file \
  ./gradlew bootRun \
  --args='--server.port=18080 --spring.profiles.active=jdbc \
  --spring.datasource.url=jdbc:h2:file:/private/tmp/baedal-round3-h2/baedal-stage3-file2;MODE=PostgreSQL \
  --spring.ai.chat.memory.repository.jdbc.initialize-schema=always \
  --baedal.chat-memory.max-messages=20'

# 재시작: 기존 테이블 사용
LOG_DIR=/private/tmp/baedal-round3-jdbc-file \
  ./gradlew bootRun \
  --args='--server.port=18080 --spring.profiles.active=jdbc \
  --spring.datasource.url=jdbc:h2:file:/private/tmp/baedal-round3-h2/baedal-stage3-file2;MODE=PostgreSQL \
  --spring.ai.chat.memory.repository.jdbc.initialize-schema=never \
  --baedal.chat-memory.max-messages=20'
```

### 이전 실패 시나리오는 JDBC로 해결되는가

JDBC가 상태 조건 참조를 해결하는지 보려고, InMemory에서 실패했던 순서를 JDBC와 InMemory에서 똑같이 다시 실행했어요.

```text
1) 2024-1234 배달 상황 알려주세요
2) 2024-1237 주문 메뉴 뭐였죠?
3) 그 버거 세트 지금 어디쯤이에요?
4) 아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘
5) 처음에 물어본 그 주문 라이더 위치 다시 확인해줘
```

결과는 둘 다 실패였어요.

| 저장소 | 4턴 기대 | 4턴 실제 | 5턴 기대 | 5턴 실제 |
| --- | --- | --- | --- | --- |
| JDBC file | `2024-1234` | `2024-1237` | `2024-1234` | `2024-1237` |
| InMemory | `2024-1234` | `2024-1237` | `2024-1234` | `2024-1237` |

따라서 JDBC 전환은 상태 조건 참조를 구조적으로 해결하지 못해요.
JDBC는 Memory 보존성과 멀티 인스턴스 공유 문제를 해결하는 쪽이고, `"배달 중이던 주문"`처럼 과거 Tool 결과를 조건으로 찾는 문제는 별도의 구조화 상태나 검색 Tool이 필요해요.

### 모델을 키우면 해결되는가

여기서 `4B`, `8B`는 모델의 파라미터 수를 뜻해요.
`qwen3:4b`는 약 40억 개 파라미터, `qwen3:8b`는 약 80억 개 파라미터를 가진 모델이에요.
파라미터가 많을수록 대화 안의 단서와 조건을 더 잘 조합할 가능성이 있지만, 모델 파일 크기, 메모리 사용량, CPU/GPU 연산량도 같이 늘어요.
그래서 로컬 실행에서는 정확도만 보지 말고 응답 시간, 발열, 메모리 사용량까지 같이 봐야 해요.

같은 5턴 순서를 `qwen3:8b`로도 다시 실행했어요.
실행은 아래처럼 모델만 바꿔서 했고, 저장소는 기본 InMemory, `MAX_MESSAGES = 20`으로 유지했어요.

```bash
LOG_DIR=/private/tmp/baedal-round3-qwen3-8b \
  ./gradlew bootRun \
  --args='--server.port=18080 --spring.ai.ollama.chat.model=qwen3:8b --baedal.chat-memory.max-messages=20'
```

이번 실행에서는 `qwen3:8b`가 `qwen3:4b`보다 상태/순서 단서를 더 잘 따라갔어요.

| 모델 | 의미 | 로컬 모델 크기 | 4턴 `아까 배달 중이던 그 주문` | 5턴 `처음에 물어본 그 주문` | 관찰 |
| --- | --- | --- | --- | --- | --- |
| `qwen3:4b` | 약 40억 개 파라미터 | 약 2.5GB | `2024-1237`로 오판 | `2024-1237`로 오판 | 최근 버거 세트 문맥을 더 강하게 따라갔어요 |
| `qwen3:8b` | 약 80억 개 파라미터 | 약 5.2GB | `2024-1234`로 Tool 호출 | `2024-1234`로 Tool 호출 | 이번 실행에서는 상태/순서 참조를 맞췄어요 |

8B 실행 로그:

```text
4턴: [Tool] getDeliveryStatus(orderId=2024-1234)
     elapsedMs=16323, promptTokens=2877, completionTokens=566, totalTokens=3443
5턴: [Tool] getDeliveryStatus(orderId=2024-1234)
     elapsedMs=24497, promptTokens=3067, completionTokens=844, totalTokens=3911
```

그래서 "모델을 키우면 나아질 수는 있다"고 봤어요.
다만 이건 해결책이라기보다 정확도 개선에 가까워요.
모델이 좋아져도 `cancelOrder` 같은 command Tool을 바로 실행해도 된다는 뜻은 아니고, 모호한 참조나 위험한 실행은 서버 정책에서 확인/차단해야 해요.
또 8B는 같은 5턴 실험에서도 마지막 응답이 약 24초 걸려서 4B보다 무거웠어요.
이번 로컬에서는 발열이 감당 가능했지만, 운영 기준으로는 정확도 개선과 지연 시간/비용을 같이 봐야 해요.

### 의사결정 트리

| 운영 조건 | Yes/No | 선택 |
| --- | --- | --- |
| 서비스가 로드밸런서 뒤 멀티 인스턴스로 뜨는가? | Yes | JDBC 같은 외부 공유 저장소가 필요해요 |
| 서버 재시작 후에도 고객 대화가 이어져야 하는가? | Yes | InMemory는 탈락하고 JDBC/file DB/운영 DB가 필요해요 |
| 법적/감사 이유로 상담 이력을 N년 보관해야 하는가? | Yes | JDBC만으로도 부족하고 보존 정책, 접근 제어, 감사 로그가 필요해요 |
| 단일 인스턴스 + 세션이 분 단위로 짧은가? | Yes | InMemory로도 충분할 수 있어요 |

InMemory로 충분한 조건은 아래 3가지라고 봤어요.

- 단일 인스턴스에서만 실행되고 로드밸런서로 요청이 분산되지 않아요.
- 서버 재시작 시 대화가 사라져도 UX나 운영상 문제가 없어요.
- 상담 세션이 짧고 세션 수가 제한되어 JVM heap 증가를 관리할 수 있어요.

JDBC가 필요한 조건은 아래 3가지라고 생각했어요.

- 여러 서버 인스턴스가 같은 conversation memory를 공유해야 해요.
- 배포나 장애 재시작 후에도 고객 대화가 이어져야 해요.
- 운영팀이 상담 이력을 DB에서 조회하거나 감사/분쟁 대응을 해야 해요.

배달 실제 운영이라면 InMemory보다는 외부 저장소를 우선 검토할 것 같아요.
상담 이력은 conversation id와 timestamp 기준 조회가 많고, 보존 기간, 백업, 접근 제어 같은 운영 요구사항이 같이 따라오기 때문이에요.
구체적인 DB 제품은 팀의 기존 운영 환경, 보관 기간, 조회 패턴, 개인정보 처리 정책을 기준으로 선택하는 편이 맞아요.

JDBC 저장소를 도입할 때 같이 봐야 할 비기능 요구사항은 아래예요.

- TTL / 보존 기간: 오래된 상담 Memory를 언제 삭제할지 정해야 해요.
- 개인정보 마스킹 / 암호화: 주소, 전화번호, 결제 관련 발화가 평문으로 남을 수 있어요.
- 접근 제어: 상담원 조회와 시스템 쓰기 계정을 분리하고, read-only 뷰를 고려해야 해요.
- 인덱스 / 파티셔닝: `conversation_id`, `timestamp` 기준 조회가 많으므로 장기 보관 시 테이블 성능을 설계해야 해요.
- 백업 / 복구: Memory를 운영 데이터로 본다면 장애 복구와 삭제 요청 처리도 같이 설계해야 해요.

## 학습 기록

### 내가 배운 것

처음에는 Chat Memory를 붙이면 `"그거"`, `"아까 그 주문"` 같은 표현이 꽤 자연스럽게 해결될 거라고 생각했어요.
그런데 curl로 같은 시나리오를 반복해서 돌려 보니, Memory는 정답을 보장하는 저장소가 아니라 이전 대화 문장을 프롬프트 앞에 다시 붙여 주는 장치에 가까웠어요.
그래서 Memory 안에 `2024-1234`가 남아 있어도 모델이 항상 그 주문을 고르는 것은 아니었고, 최근에 말한 `2024-1237` 문맥을 더 강하게 따라가는 경우가 있었어요.

가장 인상적이었던 실패는 `"아까 배달 중이던 그 주문 라이더 위치 다시 확인해줘"`였어요.
Memory에는 `2024-1234`가 배달 중이라는 응답이 남아 있었는데도 `qwen3:4b`는 `2024-1237`을 다시 조회했어요.
저장소를 JDBC로 바꿔도 같은 순서에서는 실패했기 때문에, 이 문제는 InMemory/JDBC의 차이가 아니라 모델이 자연어 대화 로그를 조건 검색처럼 안정적으로 읽지 못하는 문제라고 봤어요.
반대로 `qwen3:8b`로 바꾸니 이번 실행에서는 `2024-1234`를 맞췄는데, 이것도 "모델을 키우면 나아질 수 있다"는 의미이지 운영에서 안전장치를 없애도 된다는 의미는 아니라고 생각했어요.

`MAX_MESSAGES = 2` 실험도 생각보다 의미가 있었어요.
단순히 메시지를 적게 넣으면 조금 덜 똑똑해지는 정도가 아니라, `1235`, `1234`처럼 사용자가 축약해서 말한 주문번호를 원래의 `2024-1235`, `2024-1234`로 복원하지 못했어요.
2개면 직전 USER/ASSISTANT 한 쌍만 남기 때문에, 조금만 앞에서 말한 기준 정보가 바로 사라진다는 것을 확인했어요.
그래서 `MAX_MESSAGES = 20`은 정답이라기보다 이번 과제의 10턴 시나리오를 온전히 관찰하기 위한 기준선이라고 봤어요.

세션 분리도 단순 기능이 아니라 보안 경계에 가깝다고 느꼈어요.
처음에는 `X-Session-Id`만으로 conversation id를 나누면 된다고 생각했지만, 사용자가 임의로 같은 세션 ID를 보내면 다른 사람의 Memory와 섞일 수 있어요.
그래서 `X-Customer-Id`와 `X-Session-Id`를 묶어서 `customerId:sessionId` 형태로 만들었고, 운영에서는 이 고객 ID도 헤더가 아니라 인증 컨텍스트에서 가져와야 한다고 정리했어요.

취소 같은 command Tool은 특히 Memory만 믿으면 안 된다고 봤어요.
조회 질문에서 orderId를 한 번 잘못 고르는 것도 문제지만, `cancelOrder`에서 잘못 고르면 실제 상태 변경이 일어나요.
그래서 이번에는 모델이 Tool 호출을 제안하더라도 `ToolExecutionPolicy`가 실행 직전에 명시 주문번호와 확인 여부를 보고 막는 구조를 넣었어요.
오늘 실험을 해보면서 Chat Memory는 상담 UX를 돕는 장치이고, 실행 안전성은 서버 정책으로 잡아야 한다는 쪽으로 생각이 바뀌었어요.

### 의문점

아직 제일 궁금한 것은 `"배달 중이던 주문"` 같은 표현을 어디까지 LLM에게 맡기고, 어디부터 서버가 구조화해서 처리해야 하는가예요.
오늘 실험에서는 메뉴명처럼 `"감자튀김"`이라는 구체적인 단서가 있을 때는 비교적 잘 찾았지만, `"배달 중"` 같은 상태 조건은 4B에서 흔들렸어요.
그렇다면 Tool 결과에서 `orderId`, `status`, `menu`, `lastMentionedAt` 정도만 별도 상태로 쌓아 두고, LLM은 조건을 추출하고 서버가 후보를 찾는 방식이 더 현실적인지 궁금해졌어요.

Tool 응답을 Memory에 남길지 말지도 고민돼요.
현재는 USER/ASSISTANT 중심으로 남아서 토큰 비용은 덜하지만, Tool 응답의 구조화된 값은 다음 턴에서 직접 검색하기 어려웠어요.
그렇다고 Tool JSON 전체를 Memory에 넣으면 토큰도 늘고 주소나 결제 관련 정보가 섞일 위험도 커져요.
어떤 값은 Memory가 아니라 별도 session state에 남기는 편이 맞아 보이는데, 그 경계를 어떻게 잡을지는 더 실험해 봐야 해요.

동시 요청 문제도 더 봐야 할 것 같아요.
`MessageWindowChatMemory.add()` 구현을 보니 같은 conversation id에 대해 read-modify-write가 한 번에 원자적으로 보장되는 구조는 아니었고, race probe에서도 메시지 유실 가능성을 확인했어요.
실제 앱에서 사용자가 버튼을 두 번 누르거나 네트워크 재시도로 같은 세션에 요청이 겹치면, Memory가 사용자가 실제로 본 대화와 달라질 수 있어요.
이걸 세션별 큐로 직렬화할지, in-flight 요청을 막을지, DB optimistic locking으로 해결할지 아직 판단이 필요해요.

### Round 4에 시도하고 싶은 것

Round 4에서는 Memory와 RAG를 한 프롬프트에 같이 붙이는 것보다, 둘의 책임을 먼저 분리해서 보고 싶어요.
Memory는 `"그 주문"`, `"아까 취소하려던 주문"`처럼 지금 세션 안에서만 의미가 있는 말을 풀기 위한 장치로 보고, RAG는 `"비 오는 날 지연 보상"`, `"취소 가능 시간"`, `"환불 기준"`처럼 정책 문서가 필요한 질문을 처리하는 장치로 보고 싶어요.

예를 들어 `"아까 그 주문, 비 와서 늦으면 보상돼요?"`라는 질문이 들어오면 한 번에 답하게 하기보다 세 부분으로 나누는 흐름을 실험해 보고 싶어요.
Memory나 session state로 `그 주문`이 어떤 orderId인지 확인하고, Tool로 최신 주문 상태를 다시 조회하고, RAG로 보상 정책을 찾아 최종 답변을 만드는 방식이에요.
이렇게 나누면 모델이 기억, 최신 상태, 정책 지식을 한 번에 추측하는 부담이 줄어들 것 같아요.

또 Advisor 순서도 실제로 비교해 보고 싶어요.
Memory가 먼저 들어간 뒤 RAG 검색 질의가 만들어지는 경우와, RAG 문서가 먼저 들어가고 Memory가 뒤에 붙는 경우가 답변 품질이나 토큰 수에 어떤 차이를 만드는지 궁금해요.
이번 Round 3에서 토큰 수가 생각보다 크게 흔들렸기 때문에, Round 4에서는 답변 품질뿐 아니라 입력 토큰과 응답 시간도 같이 기록해 보고 싶어요.
