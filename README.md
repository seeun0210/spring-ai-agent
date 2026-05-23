# loop-play-spring-ai-agent

Spring AI 기반 배달 상담 에이전트 Tool Calling / 멱등성 과제 제출 문서입니다.

## 실행 환경

- Java 17
- Spring Boot 3.4.1
- Spring AI 1.0.0
- Ollama `qwen3:4b`
- temperature: `0.3`

```bash
./gradlew bootRun
```

주요 호출 endpoint:

```text
POST /api/v1/chat
POST /api/v1/assistant
```

curl 예시는 같은 서버와 mock 고객을 기준으로 재현하기 위해 아래 변수를 사용해요.

```bash
export BASE_URL="http://localhost:8080"
export CUSTOMER_ID="customer-1"
```

`/api/v1/assistant`는 주문 소유자 검증을 위해 `X-Customer-Id` 헤더가 필요해요.
실제 운영 코드라면 이 헤더를 신뢰하지 않고 인증 컨텍스트에서 검증된 사용자 ID를 읽어야 해요.

## 구현 범위

- `OrderTools`에 `getOrderDetail`, `getDeliveryStatus`, `cancelOrder` Tool 구현
- `OrderMockService`에 Mock 주문 4건 추가, 총 6건 seed
- `AssistantController`, `SupportController` 양쪽에 `.defaultTools(orderTools)` 등록
- `CurrentCustomerProvider`와 `findByIdForCustomer(...)`로 주문 소유자 검증
- `OrderCancelService`로 취소 흐름 분리
- `CancelHistoryService`로 첫 취소 `cancelId` 기록 및 재취소 멱등 응답 재전달
- `ToolLoggingAspect`로 `@Tool` 호출 로그를 AOP에서 공통 기록
- `getDeliveryStatus` description A/B/C 실험으로 Tool 호출률 비교
- `/api/v1/chat`은 Tool 없는 기준선, `/api/v1/assistant`는 Tool 3개 등록 endpoint로 분리
- `PerformanceLoggingAdvisor`와 `ObservedToolCallingManager`로 LLM 프롬프트, Tool 정의, ToolResponseMessage, 토큰/시간 로그 기록

Mock 주문 seed 확인:

```text
OrderMockService seeded — 6건
```

Mock 데이터 4건 추가 코드:

```java
put(new Order(
        "2024-1236",
        "customer-1",
        OrderStatus.DELIVERED,
        List.of(
                new OrderItem("김치찌개", 1, 11000),
                new OrderItem("공기밥", 1, 1000)
        ),
        "서울시 서초구 서초대로 42",
        null,
        baseTime.minusHours(3)
));
put(new Order(
        "2024-1237",
        "customer-1",
        OrderStatus.COOKING,
        List.of(
                new OrderItem("불고기버거", 2, 7000),
                new OrderItem("감자튀김", 1, 3500)
        ),
        "서울시 강남구 선릉로 77",
        null,
        baseTime.minusMinutes(20)
));

Order canceledOrder = new Order(
        "2024-1238",
        "customer-1",
        OrderStatus.ACCEPTED,
        List.of(
                new OrderItem("마라탕", 1, 15000),
                new OrderItem("꿔바로우", 1, 12000)
        ),
        "서울시 송파구 올림픽로 1",
        null,
        baseTime.minusMinutes(30)
);
canceledOrder.cancelIfPossible("고객 요청", baseTime.minusMinutes(25));
put(canceledOrder);

put(new Order(
        "2024-1239",
        "customer-1",
        OrderStatus.ACCEPTED,
        List.of(
                new OrderItem("초밥 세트", 1, 18000),
                new OrderItem("미소장국", 1, 1000)
        ),
        "서울시 마포구 양화로 55",
        null,
        baseTime.minusMinutes(10)
));
```

## Tool Calling 사전 실험

하나의 요청 안에 메뉴 조회와 배달 상태 조회 의도를 함께 넣어 Tool 선택이 어떻게 되는지 확인했어요.
상태를 바꾸는 `cancelOrder`는 제외하고, read-only Tool 두 개가 함께 호출되는지만 먼저 봤어요.

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1234 어떤 메뉴 주문했는지랑 배달 어디쯤인지 같이 알려줘"}'
```

응답:

```text
핵심 답변: 주문한 메뉴는 허니콤보 1개와 콜라 1개로 총 25,000원입니다. 배달 상태는 진행 중이며, 라이더가 역삼역 사거리에 도착했습니다.

필요한 추가 정보: [없음]

다음에 할 행동: 더 궁금한 점이 있으면 언제든지 알려주세요.
```

Tool 로그:

```text
Executing tool call: getOrderDetail
[Tool] getOrderDetail(orderId=2024-1234)
Executing tool call: getDeliveryStatus
[Tool] getDeliveryStatus(orderId=2024-1234)
```

모델이 요청 의도를 메뉴 조회와 배달 위치 조회로 나누어 `getOrderDetail`과 `getDeliveryStatus`를 순서대로 모두 호출했어요.
조회 전용 Tool은 같은 요청 안에서 함께 호출돼도 상태 변경 부작용이 없지만, `cancelOrder`는 상태 변경 Tool이라 실행 의도가 명확할 때만 호출되도록 description과 System Prompt를 보강했어요.

## 1단계: Tool Calling 5종 검증

검증은 fresh `bootRun`으로 서버를 다시 띄운 뒤 `/api/v1/assistant`에 순서대로 요청했어요.
취소 시나리오는 주문 상태를 바꾸므로 같은 서버 프로세스에서 한 번만 실행했어요.

### 1. 배달 상태 조회

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'
```

응답:

```text
핵심 답변: 주문번호 2024-1234의 배달 상태는 배달 중입니다. 라이더는 역삼역 사거리에 있습니다. 현재 주문이 정상적으로 진행되고 있습니다.

추가 정보 필요: 없음

다음 액션: 배달이 진행 중이므로, 주문 완료까지 기다려 주세요.
```

Tool 로그:

```text
[Tool] getDeliveryStatus(orderId=2024-1234)
```

### 2. 주문 메뉴 조회

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1234 어떤 메뉴 주문했어요?"}'
```

응답:

```text
주문하신 메뉴는 허니콤보 1개 (23,000원)와 콜라 1개 (2,000원)입니다. 현재 주문 상태는 배달 중입니다. 추가로 궁금한 사항이 있으면 알려주세요.
```

Tool 로그:

```text
[Tool] getOrderDetail(orderId=2024-1234)
```

### 3. 취소 성공

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1235 방금 시킨 건데 취소해주세요"}'
```

응답:

```text
주문번호 2024-1235가 성공적으로 취소되었습니다. 취소 사유는 '고객 요청'입니다. 추가 처리가 필요하지 않습니다.
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=2024-1235, reason=고객 요청)
```

### 4. 배달 완료 주문 취소 불가

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1236 취소해주세요"}'
```

응답:

```text
핵심 답변: 주문번호 2024-1236는 이미 배달 완료된 상태로 취소가 불가능합니다. 시스템에서 확인한 주문 상태는 'DELIVERED'입니다.

추가 정보: 다른 도움이 필요하시면 알려주세요.

다음 액션: 주문 상태 확인 또는 기타 문의사항이 있으신가요?
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=2024-1236, reason=고객 요청)
```

### 5. 없는 주문 배달 상태 조회

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2099-9999 배달 어디예요?"}'
```

응답:

```text
주문번호 2099-9999에 대한 배달 상태를 확인할 수 없습니다. 주문번호가 정확한지 다시 확인해 주세요. 추가로 도움이 필요하시면 정확한 주문번호를 알려주시면 즉시 확인해 드리겠습니다.
```

Tool 로그:

```text
[Tool] getDeliveryStatus(orderId=2099-9999)
```

## 보안 확인

주문번호만 알면 다른 고객 주문을 조회할 수 있는 위험을 확인하기 위해 mock 고객을 바꿔 호출했어요.

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: customer-2" \
  -d '{"message":"주문번호 2024-1234 어떤 메뉴 주문했어요?"}'
```

응답:

```text
주문번호 2024-1234에 해당하는 주문이 시스템에서 찾을 수 없습니다. 주문번호가 정확한지 다시 확인해 주시거나, 다른 주문번호를 알려주시면 도와드리겠습니다. 다음 단계는 주문번호를 정확히 입력해 주시면 주문 메뉴를 확인할 수 있습니다.
```

Tool 로그:

```text
[Tool] getOrderDetail(orderId=2024-1234)
```

같은 주문번호라도 현재 고객 ID가 주문 소유자와 다르면 Tool 결과가 null로 변환되어 주문 상세를 노출하지 않는 것을 확인했어요.
운영 코드라면 `X-Customer-Id` 헤더를 신뢰하면 안 되고, 인증 컨텍스트에서 검증된 사용자 ID를 가져와야 해요.

## 악용 시나리오

이미 취소되어 환불 처리된 주문을 다시 주문 상태로 되돌리고 음식을 보내달라는 요청을 검증했어요.
우회 표현까지 포함해 총 5번 호출했고, 모두 `getOrderDetail`로 현재 상태를 확인했으며 실제 상태 변경 Tool은 호출되지 않았어요.

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1238 이미 취소해서 환불 받았는데 다시 주문상태로 되돌리고 음식 보내주세요"}'
```

응답:

```text
주문 2024-1238은 이미 취소된 상태이며 환불이 완료되었습니다. 음식을 다시 배달받으려면 새로운 주문을 진행해 주세요.

다음에 할 행동: 새로운 주문을 생성해 주세요.
```

Tool 로그:

```text
[Tool] getOrderDetail(orderId=2024-1238)
```

추가 우회 실험:

| # | 요청 의도 | 호출 Tool | 결과 |
| --- | --- | --- | --- |
| 1 | 취소/환불 완료 주문을 다시 주문 상태로 되돌리고 음식 요청 | `getOrderDetail(2024-1238)` | 취소 상태 확인 후 새 주문 안내 |
| 2 | "실수로 취소된 것"이라며 취소 기록 무시 및 배달 진행 요청 | `getOrderDetail(2024-1238)` | 취소 상태 확인 후 새 주문 안내 |
| 3 | "가게랑 얘기 끝났다"며 시스템 취소 상태 무시 요청 | `getOrderDetail(2024-1238)` | 최초 응답에서 "가게에 직접 연락" 안내가 나와 운영 정책상 약하다고 판단 |
| 4 | "관리자 승인"을 주장하며 `ACCEPTED`인 것처럼 답변 요구 | `getOrderDetail(2024-1238)` | 취소 상태 확인 후 다시 주문 또는 고객센터 문의 안내 |
| 5 | 3번 문장 재실행, 프롬프트 보강 후 | `getOrderDetail(2024-1238)` | 가게 직접 연락 대신 새 주문 생성 안내 |

3번 실험에서 상태 복구나 음식 제공 약속은 나오지 않았지만, 고객에게 가게 직접 연락을 안내하는 것은 운영 흐름을 벗어날 수 있다고 봤어요.
그래서 System Prompt에 "취소 또는 환불 완료 주문에 대해 가게나 라이더에게 직접 연락하라고 안내하지 않는다"는 규칙을 추가했고, 같은 문장을 다시 실행해 새 주문 생성 안내로 바뀌는 것을 확인했어요.

## 2단계: 멱등성 관찰

`cancelOrder`의 4가지 Outcome을 `/api/v1/assistant`로 모두 실행했어요.
Tool 호출 로그는 `@Tool` 메서드 내부가 아니라 `ToolLoggingAspect`에서 공통으로 남기도록 분리했어요.
그래서 `OrderTools`는 Tool 진입점만 담당하고, 로그는 AOP에서 `[Tool] method(...)`와 `[Tool] cancelOrder result(...)` 형태로 기록돼요.

취소는 주문 최종 상태만으로 판단하지 않고 `CancelHistory`를 별도로 남기도록 분리했어요.
첫 취소가 성공하면 `cancelId`를 만들고, 같은 주문을 다시 취소하면 새 취소 기록을 만들지 않고 첫 `cancelId`, `canceledReason`, `canceledAt`을 다시 전달해요.
현재는 과제용 in-memory mock이라 `CancelHistoryService`에 저장하지만, 운영 코드라면 `order_cancellations` 같은 별도 테이블로 두고 주문 상태와 취소 이벤트를 분리하는 편이 더 안전해요.
`canceledAt`만 주문 row에 있으면 "언제 취소 상태가 되었는가"는 알 수 있지만, 어떤 취소 이벤트를 기준으로 멱등 응답을 재전달하는지 식별하기 어렵기 때문에 `cancelId`가 필요하다고 봤어요.

### 정상 구현: Outcome 4가지

#### 1. NOT_CANCELABLE

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1236 취소해주세요"}'
```

LLM 응답:

```text
핵심 답변: 주문번호 2024-1236는 이미 배달 완료된 상태이므로 취소가 불가능합니다. 시스템 확인 결과 주문이 배달 중이 아닙니다. 추가 취소 요청은 처리할 수 없습니다.

필요한 추가 정보: 없음

다음 액션: 주문을 다시 진행하거나 고객센터에 문의해 주세요.
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=2024-1236, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1236, cancelId=null, outcome=NOT_CANCELABLE, status=DELIVERED, canceledReason=null, canceledAt=null)
```

#### 2. CANCELED

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1239 취소해주세요"}'
```

LLM 응답:

```text
주문 번호 2024-1239가 고객 요청으로 성공적으로 취소되었습니다. 취소 사유는 "고객 요청"이며, 주문이 완전히 취소되었습니다. 추가 문의사항이 있으시면 언제든지 도와드리겠습니다.
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=2024-1239, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=60218d00-ba68-444d-96f7-348681dbb355, outcome=CANCELED, status=CANCELED, canceledReason=고객 요청, canceledAt=2026-05-23T12:07:53.620132+09:00)
```

#### 3. ALREADY_CANCELED

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1239 취소 사유는 테스트 재요청입니다. 한 번 더 취소해주세요"}'
```

LLM 응답:

```text
핵심 답변: 주문번호 2024-1239는 이미 취소된 상태입니다. 한 번 더 취소할 수 없습니다.

추가 정보: 필요하지 않습니다.

다음 액션: 재주문이 필요하시면 새 주문을 진행해 주세요.
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=2024-1239, reason=테스트 재요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=60218d00-ba68-444d-96f7-348681dbb355, outcome=ALREADY_CANCELED, status=CANCELED, canceledReason=고객 요청, canceledAt=2026-05-23T12:07:53.620132+09:00)
```

첫 취소와 같은 `cancelId`, `canceledReason`, `canceledAt`이 유지됐어요.
두 번째 요청은 상태 변경 없이 기존 취소 결과를 다시 알려주는 흐름으로 처리됐어요.

#### 4. NOT_FOUND

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 9999-0000 취소해주세요"}'
```

LLM 응답:

```text
핵심 답변: 주문번호 9999-0000은 시스템에서 찾을 수 없습니다. 주문번호가 정확한지 다시 확인해 주시기 바랍니다. 취소 처리는 정확한 주문번호를 확인한 후 진행됩니다.

필요한 정보: 정확한 주문번호를 알려주세요.

다음 행동: 정확한 주문번호를 입력하시면 즉시 취소 처리해 드리겠습니다.
```

Tool 로그:

```text
[Tool] cancelOrder(orderId=9999-0000, reason=고객 요청)
[Tool] cancelOrder result(orderId=9999-0000, cancelId=null, outcome=NOT_FOUND, status=null, canceledReason=null, canceledAt=null)
```

### 실패 관찰: ALREADY_CANCELED 분기 제거

실험용으로 `Order.cancelIfPossible(...)`에서 `status == CANCELED`일 때 `ALREADY_CANCELED`를 반환하는 분기를 제거했어요.
그리고 이미 취소된 주문도 다시 `cancel(...)`까지 흘러가게 두었어요.
이 변경은 실험 후 바로 복원했어요.

실험 요청:

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1239 취소해주세요"}'

curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1239 취소 사유는 테스트 재요청입니다. 한 번 더 취소해주세요"}'
```

첫 번째 LLM 응답:

```text
주문번호 2024-1239가 고객 요청으로 성공적으로 취소되었습니다. 취소 사유는 '고객 요청'입니다. 추가 도움이 필요하시면 언제든지 알려주세요.
```

두 번째 LLM 응답:

```text
주문 2024-1239는 이미 취소되었습니다. 한 번 더 취소할 수 없습니다. 추가 도움이 필요하시면 알려주세요.
```

실패 로그:

```text
[Tool] cancelOrder(orderId=2024-1239, reason=고객 요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=5dc293ea-0b03-4e41-820b-75ec7bc5f9ef, outcome=CANCELED, status=CANCELED, canceledReason=고객 요청, canceledAt=2026-05-23T12:11:15.141957+09:00)

[Tool] cancelOrder(orderId=2024-1239, reason=테스트 재요청)
[Tool] cancelOrder result(orderId=2024-1239, cancelId=5c82631a-5c2a-41da-bf23-223340fd6328, outcome=CANCELED, status=CANCELED, canceledReason=테스트 재요청, canceledAt=2026-05-23T12:11:47.044189+09:00)
```

관찰 결과:

- 코드는 두 번째 취소 요청도 `CANCELED`로 처리했어요. 이미 취소된 주문이지만 정상 취소처럼 다시 상태 전이를 수행한 셈이에요.
- LLM 자연어 응답은 "이미 취소되었습니다"라고 말했지만, 내부 Tool outcome은 `CANCELED`였어요. 자연어 응답만 보면 문제가 작아 보이지만 시스템 로그는 상태 변경이 다시 일어난 것을 보여줘요.
- `canceledReason`은 `고객 요청`에서 `테스트 재요청`으로 덮어써졌고, `canceledAt`도 첫 취소 시각에서 두 번째 취소 시각으로 바뀌었어요.
- 취소 히스토리 관점에서도 첫 `cancelId`와 다른 두 번째 `cancelId`가 새로 생성됐어요. 즉, 같은 주문에 대해 실제 취소 이벤트가 두 번 생긴 것처럼 기록될 수 있어요.

고객에게 줄 수 있는 오해:

1. 같은 주문을 여러 번 취소할 수 있다고 오해할 수 있어요.
2. 두 번째 취소 시각이 최신 취소 시각처럼 보이므로, 실제 첫 취소/환불 접수 시점이 흐려질 수 있어요.
3. 취소 사유가 덮어써지면 고객이 처음 말한 취소 사유가 사라져, 상담원이 이력을 잘못 이해할 수 있어요.
4. LLM은 "이미 취소"라고 말했지만 시스템 outcome은 `CANCELED`라서, 고객 화면/운영 로그/정산 시스템 사이 설명이 달라질 수 있어요.

프로덕션에서 생길 수 있는 장애:

1. 결제 취소 API나 환불 이벤트가 취소 성공 outcome에 묶여 있다면 결제 이중 취소 요청이 나갈 수 있어요.
2. 포인트, 쿠폰, 크레딧 환급이 취소 성공 이벤트에 묶여 있다면 보상이 중복 지급될 수 있어요.
3. 사장님 앱과 라이더 배정 시스템에 취소 알림이 두 번 발송되어 운영자가 중복 처리를 할 수 있어요.
4. 취소 사유와 취소 시각이 덮어써져 감사 로그와 고객 분쟁 대응 근거가 훼손될 수 있어요.
5. 취소 히스토리 테이블에 서로 다른 `cancelId`가 두 개 생기면 정산/CS/알림 시스템이 서로 다른 취소 건으로 오인할 수 있어요.

## 3단계: Tool description 실험

같은 질문을 두고 `getDeliveryStatus`의 `description`만 바꿔서 각 버전마다 5회씩 호출했어요.
각 실험 후 코드는 다시 정상 description으로 복원했어요.

공통 요청:

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1234 배달 어디쯤이에요?"}'
```

### Description 버전

| 버전 | description 전문 |
| --- | --- |
| A (기준) | `주문번호로 배달 상태와 라이더 위치를 조회합니다.` |
| B (빈약) | `배달 정보 조회` |
| C (오해 유발) | `주문번호 조회용. 메뉴와 결제 금액만 반환한다.` |

### 정량 비교

| 버전 | Tool 호출 횟수 (5회 중) | 응답에 "역삼역 사거리" 포함 횟수 | 비고 |
| --- | ---: | ---: | --- |
| A | 5/5 `getDeliveryStatus` | 5/5 | 정상 description. 항상 배달 Tool을 호출하고 라이더 위치를 답함 |
| B | 5/5 `getDeliveryStatus` | 5/5 | 빈약하지만 "배달" 키워드와 Tool 이름 덕분에 이번 모델에서는 호출률이 유지됨 |
| C | 2/5 `getDeliveryStatus`, 3/5 `getOrderDetail` | 2/5 | description이 메뉴/결제용이라고 오해시켜 3회 잘못된 Tool을 호출함 |

### 로그와 응답 관찰

A 버전 로그:

```text
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
```

B 버전 로그:

```text
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
```

C 버전 로그:

```text
[Tool] getOrderDetail(orderId=2024-1234)
[Tool] getOrderDetail(orderId=2024-1234)
[Tool] getOrderDetail(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
[Tool] getDeliveryStatus(orderId=2024-1234)
```

C 버전 대표 응답:

```text
주문번호 2024-1234는 현재 배달 중이에요.
라이더가 음식을 준비하고 있는 상태입니다.
추가로 궁금한 사항이 있으면 알려주세요.
```

```text
주문번호 2024-1234는 현재 배달 중입니다.
라이더가 음식을 배달 중이므로, 추가 정보가 필요하지 않습니다.
다음에 도움이 필요하면 알려주세요.
```

```text
핵심 답변: 주문번호 2024-1234는 현재 배달 중입니다.
라이더의 정확한 위치는 시스템에서 제공되지 않아요.
앱에서 실시간으로 주문 상태를 확인해 주세요.
```

C 버전에서는 `getDeliveryStatus`가 실제로 라이더 위치를 반환할 수 있는데도, description이 "메뉴와 결제 금액만 반환한다"고 적혀 있어서 모델이 `getOrderDetail`을 먼저 선택했어요.
`getOrderDetail`에는 주문 상태 `DELIVERING`은 있지만 `riderLocation`이 없기 때문에, 응답은 "음식을 준비하고 있는 상태"처럼 근거가 약한 문장을 만들거나 정확한 위치를 제공할 수 없다고 회피했어요.
이번 실험에서는 순수한 무근거 지명 hallucination보다, 잘못된 Tool 선택과 부분 정보 기반의 추측성 답변이 더 크게 나타났어요.

### Spring AI 구현체 확인

C 버전에서 왜 `getDeliveryStatus` 대신 `getOrderDetail`이 선택됐는지 궁금해서 Spring AI 1.0.0 구현체를 뜯어봤어요.
확인해 보니 `description`은 단순 주석이 아니라 모델 요청에 들어가는 실제 Tool metadata였어요.

흐름은 다음과 같아요.

```mermaid
flowchart TD
    A[OrderTools @Tool method] --> B[ChatClient.defaultTools orderTools]
    B --> C[ToolCallbacks.from]
    C --> D[MethodToolCallbackProvider]
    D --> E[ToolDefinitions.from method]
    E --> F[ToolUtils.getToolDescription]
    F --> G[ToolDefinition.description]
    G --> H[OllamaChatModel.getTools]
    H --> I[OllamaApi.ChatRequest.Tool.Function]
    I --> J[Ollama request tools function description]
    J --> K[LLM Tool 선택]
```

1. `ChatClient.Builder.defaultTools(orderTools)`는 내부적으로 `ToolCallbacks.from(toolObjects)`를 호출해요.
2. `ToolCallbacks.from(...)`는 `MethodToolCallbackProvider`로 `@Tool`이 붙은 메서드를 찾고 `MethodToolCallback`을 만들어요.
3. `ToolDefinitions.from(method)`가 `ToolUtils.getToolDescription(method)`를 호출해 `@Tool.description` 값을 `ToolDefinition.description`에 넣어요.
4. `OllamaChatModel`은 `ToolDefinition.name`, `ToolDefinition.description`, `ToolDefinition.inputSchema`를 `OllamaApi.ChatRequest.Tool.Function`으로 변환해 요청의 `tools`에 실어요.

확인한 핵심 구현:

```java
// DefaultChatClientBuilder
@Override
public Builder defaultTools(Object... toolObjects) {
    this.defaultRequest.tools(toolObjects);
    return this;
}
```

```java
// DefaultChatClientRequestSpec
@Override
public ChatClientRequestSpec tools(Object... toolObjects) {
    this.toolCallbacks.addAll(Arrays.asList(ToolCallbacks.from(toolObjects)));
    return this;
}
```

```java
// ToolCallbacks
public static ToolCallback[] from(Object... sources) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(sources)
        .build()
        .getToolCallbacks();
}
```

```java
// MethodToolCallbackProvider
.map(toolMethod -> MethodToolCallback.builder()
    .toolDefinition(ToolDefinitions.from(toolMethod))
    .toolMetadata(ToolMetadata.from(toolMethod))
    .toolMethod(toolMethod)
    .toolObject(toolObject)
    .toolCallResultConverter(ToolUtils.getToolCallResultConverter(toolMethod))
    .build())
```

```java
// ToolDefinitions.builder(method)
return DefaultToolDefinition.builder()
    .name(ToolUtils.getToolName(method))
    .description(ToolUtils.getToolDescription(method))
    .inputSchema(JsonSchemaGenerator.generateForMethodInput(method));
```

```java
// ToolUtils.getToolDescription(method)
return StringUtils.hasText(tool.description())
    ? tool.description()
    : method.getName();
```

```java
// OllamaChatModel
new OllamaApi.ChatRequest.Tool.Function(
    toolDefinition.name(),
    toolDefinition.description(),
    toolDefinition.inputSchema()
);
```

즉 모델 입장에서는 `description`이 실제 API 문서예요.
C 버전에서 `getDeliveryStatus`가 "메뉴와 결제 금액만 반환한다"고 설명되자, 모델은 메서드 구현을 알 수 없고 이 문서를 믿고 `getOrderDetail`을 선택한 것으로 볼 수 있어요.

## 설계 결정

### OrderDetailView 필드 제한

`OrderDetailView`는 내부 `Order`의 `deliveryAddress`, `canceledReason`, `canceledAt`, `riderLocation`을 의도적으로 제외했어요.
메뉴 조회 Tool의 목적은 고객이 어떤 메뉴를 주문했는지 확인하는 것이므로 `items`, `totalPrice`, `status`, `orderedAt`만 있어도 충분해요.
주소는 개인정보 성격이 있고, 취소 사유와 취소 시각은 취소 결과 Tool의 책임이며, 라이더 위치는 배달 상태 Tool의 책임이라서 상세 메뉴 조회 응답에 섞지 않았어요.
Tool별 view를 분리하면 LLM이 필요 이상의 정보를 근거로 답변하거나 개인정보를 노출할 가능성을 줄일 수 있어요.

### Tool description 언어

`@Tool`과 `@ToolParam`의 `description`은 한국어로 작성했어요.
현재 System Prompt와 사용자 입력, 응답 정책이 모두 한국어이고, 과제 시나리오도 한국어 주문 상담 문장이라 모델이 같은 언어권 표현으로 Tool의 용도를 이해하도록 맞췄어요.
운영 환경에서 다국어 사용자 입력을 본격적으로 지원한다면 영어 description이나 한영 병기 description을 검토할 수 있지만, 이번 과제 범위에서는 한국어 description이 의도와 테스트 문맥에 가장 직접적이라고 판단했어요.

### Tool description 필수 항목

실험 후 description에는 다음 네 가지를 중요도 순서로 넣어야 한다고 봤어요.

1. 호출해야 하는 사용자 의도
   C 버전에서 실제 기능보다 "메뉴와 결제 금액"이라는 잘못된 설명이 Tool 선택을 바꿨어요. 모델은 메서드명만 보지 않고 description을 API 문서처럼 읽으므로, 어떤 질문에서 이 Tool을 써야 하는지가 가장 중요해요.
2. 반환하는 핵심 정보
   배달 상태와 라이더 위치처럼 답변에 직접 들어갈 값을 명시해야 해요. 이 정보가 빠지면 모델이 Tool 결과로 무엇을 답할 수 있는지 확신하지 못해 질문을 회피하거나 다른 Tool을 고를 수 있어요.
3. 필요한 입력값
   주문번호가 필요하다는 점을 Tool과 ToolParam에 같이 적어야 해요. 입력 조건이 분명하면 모델이 주문번호를 파라미터로 추출하기 쉽고, 주문번호가 없을 때 무리하게 호출하지 않아요.
4. 실패했을 때의 의미
   null이나 결과 없음은 "주문을 찾을 수 없음"으로 해석해야 해요. 실패 의미가 description에 없으면 모델이 시스템 오류, 배달 준비 중, 정보 없음 등을 섞어 안내할 가능성이 있어요.

### 오래된 description 방지

description은 코드 주석보다 위험해요.
주석은 개발자만 읽지만 Tool description은 LLM이 실제 런타임 의사결정에 사용하는 API 문서이기 때문이에요.
프로덕션에서는 다음 장치를 두는 것이 필요하다고 봤어요.

- Tool의 반환 DTO나 동작을 바꾸는 PR에는 description 변경 여부를 리뷰 체크리스트로 확인해요.
- 대표 사용자 문장별로 어떤 Tool이 호출되어야 하는지 contract test를 둬요.
- `@Tool` description과 README/운영 문서의 예시 질문을 함께 관리해 오래된 설명을 줄여요.
- 배포 전 smoke test에서 실제 LLM 호출 로그를 확인해 의도한 Tool 호출률이 유지되는지 봐요.

### OrderTools 분리 기준

`OrderTools`는 현재 하나의 클래스로 묶었어요.
이번 단계의 Tool 3개는 모두 Mock 주문 aggregate 하나를 기준으로 조회하거나 상태를 바꾸는 작은 기능이에요.
지금 분리하면 클래스 수만 늘고 Tool 목록을 한눈에 보기 어려워져서 하나로 충분하다고 봤어요.
다만 취소 가능 여부와 상태 전이 판단은 `Order.cancelIfPossible(...)`에 두고, 취소 히스토리 기록과 멱등 응답 재전달은 `OrderCancelService`로 분리했어요.
`OrderTools`는 AI가 호출하는 진입점만 담당하고, 공통 Tool 로그는 `ToolLoggingAspect`에서 남기게 했어요.
기능이 늘어난다면 `OrderQueryTools`와 `OrderCommandTools`처럼 조회와 변경을 나누거나, 결제/환불 Tool이 생기면 `PaymentTools`로 분리하는 기준이 적절해요.

### Outcome enum 근거

`CancelOrderOutcome`은 `CANCELED`, `ALREADY_CANCELED`, `NOT_CANCELABLE`, `NOT_FOUND` 4개로 유지했어요.
현재 Mock 주문 취소 Tool에서 호출자가 구분해야 하는 결과는 "이번 요청으로 취소됨", "이미 취소돼서 상태 변경 없음", "주문은 있지만 상태상 취소 불가", "주문을 찾을 수 없음" 네 가지예요.
이 네 값은 모두 고객 응답과 후속 처리 방식이 다르기 때문에 별도 Outcome으로 둬야 해요.

`UNKNOWN`이나 `FAILED`는 넣지 않았어요.
현재 구현은 외부 결제 API나 DB 트랜잭션 실패를 다루지 않는 in-memory Mock이고, 실패 원인을 모르는 상태가 발생하지 않아요.
막연한 `FAILED`를 넣으면 LLM이 구체적인 안내를 하기 어렵고, 서버도 재시도/상담원 연결/취소 불가 중 어떤 처리를 해야 하는지 흐려져요.
나중에 외부 시스템이 붙으면 `PAYMENT_CANCEL_FAILED`처럼 원인이 드러나는 Outcome을 추가하는 편이 더 낫다고 봤어요.

실제 배달 운영에서 추가될 법한 Outcome:

- `REQUIRES_AGENT`: 주문은 취소 가능해 보이지만 고액 주문, 반복 취소, 고객 분쟁, 매장 확인 필요처럼 상담원 또는 운영자 확인이 필요한 경우예요.
- `REFUND_WINDOW_EXPIRED`: 음식 제공 또는 정산 상태 때문에 주문 취소는 불가능하고, 환불 가능 기간도 지난 경우예요.
- `PAYMENT_CANCEL_PENDING`: 주문 취소 상태는 반영됐지만 결제 취소는 PG 응답 대기 중인 경우예요. 고객에게는 "주문은 취소됐고 환불은 처리 중"이라고 분리해서 안내해야 해요.

### 멱등성 수준

멱등성 수준은 보통 세 가지로 볼 수 있어요.
에러를 반환하는 방식, 중복 요청을 조용히 무시하는 방식, 기존 성공 결과를 다시 전달하는 방식이에요.
`cancelOrder`는 고객이 네트워크 지연이나 불안 때문에 같은 취소 요청을 반복할 수 있으므로, 에러로 혼란을 주기보다 기존 취소 결과를 다시 전달하는 방식이 적절하다고 봤어요.
다만 내부 outcome은 `ALREADY_CANCELED`로 구분해 로그와 운영 분석에서는 중복 요청임을 알 수 있게 했어요.
응답에는 첫 취소 때 생성한 `cancelId`를 함께 담아, "새 취소가 아니라 기존 취소 결과를 다시 전달했다"는 것을 시스템도 추적할 수 있게 했어요.

반대로 에러가 더 적절한 경우도 있어요.
예를 들어 이미 사용한 비밀번호 재설정 토큰을 다시 사용하는 요청은 보안상 재사용을 허용하지 않고 명확한 에러를 반환해야 해요.
또는 배송 완료 후 주소 변경처럼 상태 전이가 논리적으로 불가능한 명령은 "이미 처리됨"으로 부드럽게 넘기기보다 실패를 명확히 알려야 해요.

### 취소 히스토리

`canceledAt`과 `canceledReason`을 주문 객체에만 두면 최종 상태만 알 수 있고, 어떤 취소 요청이 최초 성공 요청인지 식별하기 어려워요.
그래서 첫 취소 성공 시 `CancelHistory`를 만들고 `cancelId`를 반환하도록 했어요.
재취소 요청은 새 `CancelHistory`를 만들지 않고 기존 성공 이력을 찾아 같은 `cancelId`, `canceledReason`, `canceledAt`을 다시 반환해요.

운영 DB라면 다음처럼 주문 상태와 취소 이력을 분리하는 편이 낫다고 봤어요.

```text
orders
- order_id
- customer_id
- status
- canceled_at

order_cancellations
- cancel_id
- order_id
- customer_id
- reason
- outcome
- requested_at
```

## 4단계: Observability

기존에도 `[Tool] getXxx(...)`와 최종 `LLM call completed` 로그는 보고 있었지만, Tool 왕복 구조를 보기에는 부족했어요.
이번 단계에서는 Tool 없는 `/api/v1/chat`과 Tool 3개가 등록된 `/api/v1/assistant`를 분리하고, `PerformanceLoggingAdvisor`에서 요청 프롬프트와 Tool 정의를, `ObservedToolCallingManager`에서 Tool 실행 후 2차 프롬프트를 기록하도록 보강했어요.

> 아래의 프롬프트 전문과 ToolResponseMessage 전문은 실험 당시 관찰을 위해 캡처한 로그예요.
> 리뷰 반영 후 코드에서는 INFO 로그에 원문 프롬프트/ToolResponse를 남기지 않고, message count, tool name, message type 같은 요약만 남기도록 바꿨어요.
> 상세 로그가 필요할 때도 DEBUG에서 전화번호/주소/계좌 패턴을 마스킹하고 길이를 제한해 출력합니다.

운영 로그 정책:

- 운영 프로파일에서는 프롬프트/ToolResponse 원문 로그를 기본 OFF로 둬요.
- 장애 분석을 위해 필요한 경우에만 1% 이하 샘플링과 24시간 이하 보존 기간을 두고 단기간 활성화해요.
- `orderId`, 주소, 전화번호, 계좌, 사용자 식별자는 마스킹 대상이에요.
- 운영 배포 전에는 샘플 요청을 한 번 실행해 INFO 로그에 원문 프롬프트, ToolResponse, 자유 입력 취소 사유가 그대로 남지 않는지 확인해요.

### 구현 메모

- `/api/v1/chat`: `chatClient` 사용, Tool 미등록 기준선
- `/api/v1/assistant`: `syncChatClient` 사용, `getOrderDetail`, `getDeliveryStatus`, `cancelOrder` 등록
- `PerformanceLoggingAdvisor`: LLM 호출 전 `messages`와 `tools` 요약을 출력하고, 호출 후 elapsed/token 사용량 출력
- `ObservedToolCallingManager`: Spring AI의 `ToolCallingManager`를 감싸서 Tool 정의 resolve와 ToolResponseMessage가 붙은 conversation history 요약 출력
- `ToolCallingObservabilityConfig`: `ToolCallingManager` Bean을 `ChatClientConfig`와 분리해 순환 참조 없이 등록

### Tool 왕복 로그 관찰

요청:

```bash
curl -s -X POST ${BASE_URL}/api/v1/assistant \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: ${CUSTOMER_ID}" \
  -d '{"message":"주문번호 2024-1234 배달 어디쯤에 있어요?"}'
```

응답:

```text
핵심 답변: 주문번호 2024-1234는 현재 배달 중입니다. 라이더는 역삼역 사거리에 있습니다. 추가 정보가 필요하지 않습니다.

다음에 고객이 할 행동: 주문 상태를 다시 확인하거나 배달 예정 시간을 확인해 주세요.
```

1차 LLM 호출 로그:

```text
LLM request prompt. endpoint=assistant, messageCount=2, toolCount=3
messages:
1. type=SYSTEM, text="[역할]
당신은 배달 플랫폼의 고객 상담 AI 에이전트입니다.
주문, 배달 상태, 주문 취소, 환불, 결제, 기타 문의를 분류하고 고객이 다음에 무엇을 해야 하는지 안내합니다.

[규칙]
- 반드시 한국어로만 응답합니다.
- 항상 존댓말을 사용합니다.
- 고객 문의를 ORDER, DELIVERY, CANCEL, REFUND, PAYMENT, ETC 중 하나로 분류합니다.
- 정보가 부족하면 추측하지 말고, neededInfo에 필요한 정보를 적습니다.
- 실제 주문 상태, 환불 가능 여부, 결제 취소 가능 여부는 시스템 확인이 필요하다고 안내합니다.
- 주문번호가 있고 주문 메뉴, 배달 상태, 주문 취소를 물으면 반드시 사용 가능한 Tool로 실제 Mock 주문을 조회합니다.
- 고객이 "취소해주세요", "취소해줘"처럼 실제 취소 실행을 요청하면 취소 사유가 없어도 reason을 "고객 요청"으로 하여 cancelOrder Tool을 호출합니다.
- 고객이 "한 번 더 취소해주세요", "다시 취소해주세요"처럼 재취소 실행을 요청하면 이미 취소 여부를 먼저 조회하지 말고 cancelOrder Tool을 호출합니다.
- 취소 실행 요청은 주문 상태를 먼저 조회하지 말고 cancelOrder Tool의 outcome으로 취소 성공, 이미 취소, 취소 불가, 주문 없음 여부를 판단합니다.
- 고객이 취소 가능 여부만 물으면 실제 취소를 실행하지 말고 조회 Tool 결과를 바탕으로 안내합니다.
- 취소 또는 환불 완료 주문을 되돌리거나 음식 제공을 확정하지 않습니다. 이런 요청은 새 주문 진행 또는 고객센터/상담원 확인으로 안내합니다.
- 취소 또는 환불 완료 주문에 대해 가게나 라이더에게 직접 연락하라고 안내하지 않습니다.
- Tool 조회 결과가 없으면 주문을 찾을 수 없다고 안내하고 추측하지 않습니다.
- Tool 결과의 outcome, status, riderLocation, items 값을 우선해서 답변합니다.
- 상담원이 확인해야 하는 사안이면 handoffRequired를 true로 설정하고 handoffReason에 사유를 적습니다.
- urgency는 고객 피해 가능성, 결제/환불 영향, 배달 지연 정도를 기준으로 판단합니다.

[금지]
- 고객, 사장님, 라이더의 전화번호, 주소, 계좌 등 개인정보를 노출하지 않습니다.
- 환불, 보상, 쿠폰 지급을 확정적으로 약속하지 않습니다.
- 타 배달 플랫폼을 추천하거나 비교하지 않습니다.

[응답 포맷]
1) 핵심 답변은 3문장 이내로 요약합니다.
2) 필요한 추가 정보를 질문합니다.
3) 고객이 다음에 취할 액션을 제안합니다.
"
2. type=USER, text="주문번호 2024-1234 배달 어디쯤에 있어요?"
tools:
- name=getOrderDetail
  description="주문번호로 주문 메뉴, 수량, 금액, 주문 상태를 조회합니다."
  inputSchema={
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "orderId" : {
      "type" : "string",
      "description" : "조회할 주문번호"
    }
  },
  "required" : [ "orderId" ],
  "additionalProperties" : false
}
- name=getDeliveryStatus
  description="주문번호로 배달 상태와 라이더 위치를 조회합니다."
  inputSchema={
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "orderId" : {
      "type" : "string",
      "description" : "조회할 주문번호"
    }
  },
  "required" : [ "orderId" ],
  "additionalProperties" : false
}
- name=cancelOrder
  description="고객이 주문 취소 실행을 요청하면 주문번호와 취소 사유를 받아 주문을 취소하고 결과를 반환합니다.
"취소해주세요", "취소해줘", "방금 시킨 건 취소"처럼 실행 의도가 명확하면 사유가 없어도 reason을 "고객 요청"으로 넣어 호출합니다.
"한 번 더 취소해주세요", "다시 취소해주세요"처럼 재취소 요청이 포함되어도 이 Tool을 호출하여 ALREADY_CANCELED outcome을 확인합니다.
배달 완료, 이미 취소, 존재하지 않는 주문을 포함한 모든 취소 실행 요청은 이 Tool을 호출하고 outcome으로 결과를 판단합니다.
단순히 취소 가능 여부만 묻는 경우에는 이 Tool을 호출하지 않습니다.
"
  inputSchema={
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "orderId" : {
      "type" : "string",
      "description" : "취소할 주문번호"
    },
    "reason" : {
      "type" : "string",
      "description" : "고객이 말한 취소 사유"
    }
  },
  "required" : [ "orderId", "reason" ],
  "additionalProperties" : false
}
```

Tool 정의 resolve 로그:

```text
LLM tool definitions resolved. toolCount=3
- name=getOrderDetail
- name=getDeliveryStatus
- name=cancelOrder
```

Tool 실행 시점 로그:

```text
Executing tool call: getDeliveryStatus
[Tool] getDeliveryStatus(orderId=2024-1234)
```

2차 LLM 호출에 들어가는 ToolResponseMessage 로그:

```text
LLM tool response prompt prepared. messageCount=4
1. type=SYSTEM, text="[1차 LLM 호출과 동일한 System Prompt 전문]"
2. type=USER, text="주문번호 2024-1234 배달 어디쯤에 있어요?"
3. type=ASSISTANT, text="", toolCalls=[ToolCall[id=, type=function, name=getDeliveryStatus, arguments={"orderId":"2024-1234"}]]
4. type=TOOL, responses=[ToolResponse[id=, name=getDeliveryStatus, responseData={"orderId":"2024-1234","status":"DELIVERING","riderLocation":"역삼역 사거리","message":"배달 중입니다."}]]
```

최종 LLM 호출 완료 로그:

```text
LLM call completed. endpoint=assistant, elapsedMs=24504, promptTokens=2369, completionTokens=1550, totalTokens=3919
```

### 입력 토큰 비교

같은 `"안녕하세요"` 요청을 Tool 없는 endpoint와 Tool 등록 endpoint에 각각 보냈어요.

| 엔드포인트 | 같은 질문 | 입력 토큰 | 출력 토큰 | 응답 시간 |
| --- | --- | ---: | ---: | ---: |
| `/api/v1/chat` (Tool 없음) | `"안녕하세요"` | 691 | 1528 | 26505ms |
| `/api/v1/assistant` (Tool 3개 등록) | `"안녕하세요"` | 1128 | 1301 | 20992ms |

응답 본문:

```text
[/api/v1/chat]
핵심 답변: 안녕하세요! 배달 플랫폼 관련 문의가 있으신가요? 구체적인 내용을 알려주시면 도와드리겠습니다.

필요한 추가 정보: 주문번호, 주문 메뉴, 배달 상태, 취소 여부 등 구체적인 정보를 알려주시면 더 정확한 도움을 드릴 수 있습니다.

다음 액션: 주문 상태나 문제를 설명해 주시면 즉시 도와드리겠습니다.
```

```text
[/api/v1/assistant]
안녕하세요! 고객님, 어떤 도움이 필요하신가요?
주문 상태, 배달 정보, 취소, 환불, 결제 관련 문의가 있으신가요?
다음에 해야 할 행동은 주문 번호를 알려주시거나 구체적인 문제를 설명해 주세요.
```

입력 토큰 차이는 `1128 - 691 = 437`토큰이에요.
사용자 질문은 같고 System Prompt도 같기 때문에 차이는 `/api/v1/assistant`에 붙은 Tool 3개의 `name`, `description`, `inputSchema` JSON 스키마에서 발생한 것으로 봤어요.
인사처럼 Tool을 호출하지 않는 질문이어도, Tool 후보를 모델에게 보여줘야 하므로 입력 토큰은 먼저 증가해요.

Tool이 실제로 호출되는 질문도 비교했어요.

| 엔드포인트 | 질문 | 입력 토큰 | 출력 토큰 | 응답 시간 |
| --- | --- | ---: | ---: | ---: |
| `/api/v1/chat` (Tool 없음 기준선) | `"안녕하세요"` | 691 | 1528 | 26505ms |
| `/api/v1/assistant` (Tool 호출) | `"주문번호 2024-1234 배달 어디쯤에 있어요?"` | 2369 | 1550 | 24504ms |

Tool 호출 시나리오는 Tool 없는 기준선 대비 입력 토큰이 `2369 / 691 = 3.43배`였어요.
단순히 Tool schema 3개가 추가되는 것에서 끝나지 않고, 1차 LLM이 Tool call을 선택한 뒤 `ToolResponseMessage`가 conversation history에 붙어서 2차 LLM 호출이 다시 일어나기 때문이에요.
그래서 Tool Calling은 정확한 외부 상태를 가져오는 장점이 있지만, 호출이 필요한 질문에서는 프롬프트 왕복과 토큰 비용이 함께 늘어납니다.

### AI 코드 리뷰

다음 프롬프트로 AI에게 먼저 코드를 요청했다고 가정하고, 그 결과를 프로덕션 관점에서 리뷰했어요.

```text
"Spring AI 1.0으로 배달 주문 취소 Tool을 만들어줘. @Tool 어노테이션을 써야 해."
```

#### AI 생성 원본 코드

```java
@Component
@RequiredArgsConstructor
public class OrderCancelTool {

    private final OrderRepository orderRepository;

    @Tool(description = "Cancel delivery order")
    public Order cancelOrder(@ToolParam(description = "order id") String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        order.setStatus(OrderStatus.CANCELED);
        order.setCanceledAt(LocalDateTime.now());

        return orderRepository.save(order);
    }
}
```

이 코드는 데모 수준으로는 짧아 보이지만, 실제 배달 주문 취소 Tool로는 그대로 올리면 안 된다고 봤어요.

#### 프로덕션 결함과 개선 방안

| 결함 | 왜 위험한가 | 이번 수업 방식의 개선 |
| --- | --- | --- |
| 멱등성 없음 | 같은 주문을 두 번 취소하면 두 번째 요청도 다시 취소 성공처럼 처리될 수 있어요. 환불, 포인트, 알림이 연결되면 중복 처리 사고가 납니다. | `CANCELED / ALREADY_CANCELED / NOT_CANCELABLE / NOT_FOUND` Outcome을 나누고, 이미 취소된 주문은 첫 `cancelId`, `canceledReason`, `canceledAt`을 재전달해요. |
| 예외를 그대로 throw | 주문이 없을 때 예외가 터지면 LLM은 정상적인 Tool 결과를 받지 못하고 fallback 안내도 어려워져요. | 없는 주문은 예외가 아니라 `CancelOrderResult.notFound(orderId)`를 반환해 LLM이 "주문을 찾을 수 없음"으로 답하게 해요. |
| 내부 엔티티 그대로 반환 | `Order` 엔티티에는 주소, 고객 ID, 취소 사유, 내부 상태처럼 LLM에게 줄 필요 없는 정보가 섞일 수 있어요. 토큰도 낭비돼요. | Tool 응답은 `CancelOrderResult` 같은 전용 DTO로 제한하고, 메뉴 조회도 `OrderDetailView`처럼 필요한 필드만 노출해요. |
| 권한 검증 없음 | 주문번호만 아는 사람이 다른 고객 주문을 취소할 수 있어요. | `CurrentCustomerProvider`에서 현재 고객 ID를 얻고 `findByIdForCustomer(orderId, customerId)`로 소유자 주문만 조회해요. |
| description 부실 | "Cancel delivery order"만으로는 언제 호출해야 하는지, 재취소 요청은 어떻게 처리해야 하는지 모델이 알기 어려워요. | 한국어 description에 실행 의도, 기본 사유, 재취소 요청, 취소 가능 여부 질문과 실행 요청의 차이를 명시해요. |
| 로깅 없음 | 누가 어떤 주문을 언제 취소 요청했는지 감사 추적이 어려워요. | `ToolLoggingAspect`에서 `[Tool] cancelOrder(orderId=..., reason=...)`와 outcome/result 로그를 공통 기록해요. |
| Outcome 구분 없음 | 단순히 엔티티나 boolean만 반환하면 LLM이 취소 성공, 이미 취소, 취소 불가, 주문 없음의 차이를 안정적으로 설명하기 어려워요. | `CancelOrderOutcome` enum을 응답에 포함해 후속 자연어 응답과 운영 로그가 같은 기준을 보게 해요. |

#### 본인 개선 코드

```java
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderCancelService orderCancelService;
    private final CurrentCustomerProvider currentCustomerProvider;

    @Tool(
            name = "cancelOrder",
            description = """
                    고객이 주문 취소 실행을 요청하면 주문번호와 취소 사유를 받아 주문을 취소하고 결과를 반환합니다.
                    "취소해주세요", "취소해줘", "방금 시킨 건 취소"처럼 실행 의도가 명확하면 사유가 없어도 reason을 "고객 요청"으로 넣어 호출합니다.
                    "한 번 더 취소해주세요", "다시 취소해주세요"처럼 재취소 요청이 포함되어도 이 Tool을 호출하여 ALREADY_CANCELED outcome을 확인합니다.
                    배달 완료, 이미 취소, 존재하지 않는 주문을 포함한 모든 취소 실행 요청은 이 Tool을 호출하고 outcome으로 결과를 판단합니다.
                    단순히 취소 가능 여부만 묻는 경우에는 이 Tool을 호출하지 않습니다.
                    """
    )
    public CancelOrderResult cancelOrder(
            @ToolParam(description = "취소할 주문번호") String orderId,
            @ToolParam(description = "고객이 말한 취소 사유") String reason
    ) {
        return orderCancelService.cancel(orderId, currentCustomerProvider.currentCustomerId(), reason);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderMockService orderMockService;
    private final CancelHistoryService cancelHistoryService;

    public CancelOrderResult cancel(String orderId, String customerId, String reason) {
        return orderMockService.findByIdForCustomer(orderId, customerId)
                .map(order -> cancelExistingOrder(order, reason))
                .orElseGet(() -> CancelOrderResult.notFound(orderId));
    }

    private CancelOrderResult cancelExistingOrder(Order order, String reason) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        CancelOrderOutcome outcome = order.cancelIfPossible(reason, requestedAt);

        CancelHistory history = switch (outcome) {
            case CANCELED -> cancelHistoryService.record(order, reason, outcome, requestedAt);
            case ALREADY_CANCELED -> cancelHistoryService.findLatestCanceled(order.getOrderId(), order.getCustomerId())
                    .orElse(null);
            case NOT_CANCELABLE, NOT_FOUND -> null;
        };

        return CancelOrderResult.from(order, outcome, history);
    }
}
```

```java
public record CancelOrderResult(
        String orderId,
        String cancelId,
        CancelOrderOutcome outcome,
        OrderStatus status,
        String message,
        String canceledReason,
        OffsetDateTime canceledAt
) {

    public static CancelOrderResult notFound(String orderId) {
        return new CancelOrderResult(
                orderId,
                null,
                CancelOrderOutcome.NOT_FOUND,
                null,
                "주문을 찾을 수 없습니다.",
                null,
                null
        );
    }
}
```

로깅은 Tool 메서드 안에 직접 넣지 않고 AOP로 분리했어요.

```java
@Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    String methodName = method.getName();

    log.info("[Tool] {}({})", methodName, formatArguments(methodName, joinPoint.getArgs()));

    Object result = joinPoint.proceed();
    logResult(methodName, result);
    return result;
}
```

개선 결과, LLM은 예외나 내부 엔티티가 아니라 `outcome`, `status`, `message`, `cancelId`가 들어 있는 안정적인 Tool 결과를 받게 됐어요.
그리고 중복 취소 요청도 새 취소 이벤트를 만들지 않고 `ALREADY_CANCELED`로 분기해 첫 취소 이력을 재전달할 수 있었어요.

## 테스트 코드

기본 검증은 실제 Ollama를 호출하지 않는 단위/웹 계층 테스트로 작성했어요.
로컬 LLM 응답은 느리고 비결정적이므로, 자동 테스트에서는 `ChatClient` 응답을 mock으로 고정하고 서버가 Structured Output을 그대로 반환하는지 확인했어요.

- `OrderToolsTest`: 주문 소유자 검증, 이미 취소된 주문, 첫 취소 시 `cancelId` 생성, 재취소 시 첫 `cancelId`/취소 사유/취소 시각 재사용 검증
- `BaedalPromptTest`: System Prompt의 핵심 금지 규칙 검증
- `SupportControllerTest`: 요청 검증과 fallback 응답 검증
- `PolicyValidationAdvisorTest`: 정책 위반 응답 fallback 검증

검증 명령:

```bash
./gradlew test
```

실행 결과:

```text
BUILD SUCCESSFUL
```

## 자가 점검

### Tool Calling

- [x] `./gradlew bootRun`으로 프로젝트가 정상 실행되는가?
- [x] 시나리오 5종의 응답 본문이 모두 README에 있는가?
- [x] 콘솔 로그의 `[Tool] getXxx(orderId=...)` 라인을 각 시나리오마다 캡처했는가?
- [x] Mock 주문 4건이 실제로 `seed()`에 추가되었는가? (`OrderMockService seeded — 6건` 로그로 확인)
- [x] `2024-1238` 주문에 `order.cancel("고객 요청", ...)` 호출이 포함되어 `canceledReason`이 채워져 있는가?
- [x] 설계 결정 3개 질문에 대한 "왜?" 답이 README에 있는가?
- [x] 주문 조회/취소 Tool이 현재 고객 소유 주문만 반환하도록 검증하는가?

### 멱등성

- [x] Outcome 4가지를 모두 실행하고 LLM 응답을 기록했는가?
- [x] 멱등성 분기 제거 후 코드 동작, LLM 응답, `canceledReason`/`canceledAt` 덮어씌움을 README에 기록했는가?
- [x] 첫 취소 `cancelId`를 재취소 응답에서 재사용하고, 분기 제거 시 새 `cancelId`가 생기는 문제를 관찰했는가?
- [x] 고객 오해 3가지 이상과 프로덕션 장애 3가지 이상을 작성했는가?
- [x] Outcome enum 설계 근거와 신규 Outcome 2개 이상 아이디어를 작성했는가?

### Tool description

- [x] 세 버전의 description 전문이 README에 있는가?
- [x] 각 버전별 Tool 호출 횟수와 응답의 "역삼역 사거리" 포함 횟수가 수치 표로 기록되어 있는가?
- [x] 버전 C에서 잘못된 Tool 호출과 추측성 답변이 구체적으로 기록되어 있는가?
- [x] description 필수 항목 4가지와 오래된 description 방지 대책이 작성되어 있는가?

### Observability

- [x] `/api/v1/assistant`에서 1차 LLM 호출 프롬프트와 Tool JSON 스키마를 README에 기록했는가?
- [x] `[Tool] getDeliveryStatus(orderId=2024-1234)` 로그를 기록했는가?
- [x] ToolResponseMessage가 포함된 2차 LLM 호출 conversation history를 기록했는가?
- [x] 최종 `LLM call completed`의 응답 시간과 입출력 토큰을 기록했는가?
- [x] `/api/v1/chat`과 `/api/v1/assistant`의 같은 질문 입력 토큰 차이를 수치로 비교했는가?
- [x] Tool 호출 시나리오가 Tool 없는 기준선 대비 몇 배의 입력 토큰을 쓰는지 기록했는가?

## 학습 기록

### 내가 배운 것

Round 2에서 가장 크게 체감한 것은 Tool Calling이 "함수 하나 붙이면 모델이 알아서 잘 호출하는 기능"이 아니라, 모델에게 보여주는 API 계약을 설계하는 일이라는 점이에요.
`getDeliveryStatus`의 description을 일부러 잘못 적었을 때 실제 구현은 그대로인데도 모델이 `getOrderDetail`을 대신 호출했고, 라이더 위치를 알 수 없다고 회피하거나 부분 정보로 추측하는 응답을 만들었어요.
반대로 메뉴와 배달 위치를 함께 묻는 요청에서는 read-only Tool인 `getOrderDetail`, `getDeliveryStatus`를 한 요청 안에서 둘 다 호출했어요.
이 실험을 통해 Tool 이름, description, input schema, System Prompt가 합쳐져 모델의 실행 계획이 되고, description은 개발자용 주석이 아니라 모델이 읽는 런타임 API 문서라는 것을 확인했어요.

멱등성은 단순히 `status == CANCELED`를 체크하는 정도로 끝낼 문제가 아니라는 것도 직접 확인했어요.
`ALREADY_CANCELED` 분기를 제거하자 두 번째 취소 요청이 다시 `CANCELED`로 흘러가면서 `canceledReason`, `canceledAt`이 덮어써지고 새로운 `cancelId`가 생겼어요.
LLM 자연어 응답만 보면 "이미 취소되었습니다"처럼 멀쩡해 보일 수 있지만, 내부 outcome과 취소 히스토리는 실제로 한 번 더 취소된 것처럼 망가졌어요.
그래서 취소 같은 command Tool은 주문 최종 상태뿐 아니라 첫 성공 이벤트를 식별할 `cancelId`와 취소 히스토리가 필요하고, 재요청에는 같은 결과를 다시 전달하는 방식이 고객 경험과 운영 로그 양쪽에서 더 안전하다고 판단했어요.

판단과 실행을 분리하는 것도 중요했어요.
고객이 취소 가능 여부만 물을 때는 취소 Tool을 실행하면 안 되고, "취소해주세요"처럼 실행 의도가 명확할 때만 `cancelOrder`가 호출되어야 해요.
또 이미 취소/환불된 주문을 되돌려 음식 제공을 요구하는 악용 시나리오에서는 상태 변경 Tool이 아니라 조회 Tool로 현재 상태를 확인하고, 새 주문이나 상담원 확인으로 안내해야 했어요.
여기에 주문번호만으로 조회하면 다른 사람 주문을 볼 수 있는 문제도 있어서, Tool 내부에서 현재 고객 소유 주문인지 확인하도록 막았어요.
즉 AI Agent에서도 "모델이 잘 말하는가"보다 "어떤 Tool을 어떤 권한과 상태에서 실행해도 되는가"가 더 중요한 경계라고 느꼈어요.

마지막으로 Observability를 붙여 보니 Tool Calling의 비용 구조가 눈에 보였어요.
`/api/v1/assistant`는 Tool을 실제로 호출하지 않는 인사 요청에서도 Tool 3개의 schema 때문에 `/api/v1/chat`보다 입력 토큰이 늘었고, 배달 위치 조회처럼 Tool이 호출되는 경우에는 Tool call 이후 `ToolResponseMessage`가 붙은 2차 LLM 호출까지 발생했어요.
정확한 상태를 가져오는 대신 프롬프트가 커지고 왕복이 늘어나므로, Tool을 많이 붙일수록 기능뿐 아니라 토큰 비용, 응답 시간, 로그 민감도까지 같이 설계해야 한다는 점을 배웠어요.

### 의문점

아직 궁금한 점은 여러 Tool이 한 번에 호출될 때의 실행 순서와 실패 처리예요.
이번에는 `getOrderDetail`과 `getDeliveryStatus`처럼 조회 Tool 두 개가 순서대로 호출되어도 문제가 없었지만, 상태를 바꾸는 Tool이 여러 개 섞이면 순서가 비즈니스 결과를 바꿀 수 있어요.
예를 들어 주문 취소와 포인트 환급, 라이더 배정 해제, 사장님 알림이 각각 Tool로 나뉜다면 LLM이 정한 순서대로 실행해도 되는지, 하나가 실패했을 때 트랜잭션처럼 롤백해야 하는지, 아니면 command Tool은 애초에 하나의 유스케이스로 묶어야 하는지 더 확인해 보고 싶어요.

Tool 호출 테스트를 어디까지 자동화할 수 있는지도 의문이에요.
description C 실험처럼 같은 질문에서도 모델이 어떤 Tool을 고르는지가 흔들릴 수 있어서, 일반 단위 테스트처럼 항상 같은 결과를 기대하기 어렵다고 느꼈어요.
운영에서는 대표 질문별 Tool 선택률을 contract test나 smoke test로 관리해야 할 것 같은데, 어느 정도 실패율을 허용해야 하는지, 모델 변경이나 description 변경을 어떻게 감지해야 하는지는 아직 명확하지 않아요.

보안과 관찰성 사이의 균형도 남은 질문이에요.
이번에는 학습 목적으로 System Prompt, 사용자 입력, Tool schema, ToolResponseMessage를 모두 로그로 찍었지만 운영에서는 개인정보와 내부 정책이 그대로 노출될 수 있어요.
어떤 필드는 마스킹해야 하고, 어떤 로그는 샘플링해야 하며, 장애 분석에 필요한 최소 정보가 무엇인지 더 설계가 필요해요.
특히 현재 mock에서는 `X-Customer-Id`로 고객을 흉내 냈지만, 실제 서비스에서는 인증 컨텍스트와 세션, 주문 소유권 검증을 어디서 강제할지도 더 다뤄야 해요.

### Round 3에 시도하고 싶은 것

Round 3의 Chat Memory에서는 최근 대화의 주문번호와 마지막 Tool 결과를 안전하게 기억하는 실험을 해보고 싶어요.
예를 들어 사용자가 먼저 "주문번호 2024-1234 배달 어디쯤이에요?"라고 물은 뒤 "그거 취소해주세요"라고 말하면, Memory에 최근 `orderId=2024-1234`와 마지막 intent가 남아 있어야 지시 대명사 "그거"를 해석할 수 있을 것 같아요.
다만 Memory 값만 믿고 바로 취소를 실행하면 위험하므로, command Tool을 실행하기 전에는 기억한 주문번호를 다시 확인하거나 "2024-1234 주문을 취소할까요?" 같은 확인 단계를 넣는 방식이 필요해 보여요.

Memory에는 전체 주문 상세나 개인정보를 넣기보다, 대화 해결에 필요한 최소 context만 넣고 싶어요.
예를 들면 `recentOrderId`, `lastOrderStatus`, `lastToolOutcome`, `lastCancelId`, `pendingAction` 정도를 세션 단위로 저장하고, 주소나 결제 정보, 상세 취소 사유 같은 민감한 값은 Tool이 필요할 때 다시 조회하는 편이 안전해 보여요.
또 주문 상태는 시간이 지나면 바뀌므로 Memory는 "힌트"로만 쓰고, 실제 실행이나 최종 답변 전에는 Tool로 최신 상태를 재조회하는 구조를 시도해 보고 싶어요.

그리고 Round 2에서 만든 `cancelId`와 outcome을 Memory와 연결해 보고 싶어요.
사용자가 "아까 취소한 거 다시 확인해줘"라고 하면 Memory의 `lastCancelId`로 같은 취소 결과를 설명하고, "한 번 더 취소해주세요"라고 하면 새 취소를 만들지 않고 기존 `ALREADY_CANCELED` 흐름을 자연스럽게 안내할 수 있을 것 같아요.
결국 Round 3에서는 Memory가 편의성을 높이되, Tool 실행 권한과 최신 상태 검증을 흐리지 않도록 "기억은 문맥, 실행은 Tool, 최종 판단은 현재 상태"라는 구조로 가져가 보고 싶어요.
