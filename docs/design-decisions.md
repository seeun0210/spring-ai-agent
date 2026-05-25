# 설계 결정 문서

## System Prompt 구성

배달 상담 에이전트의 System Prompt는 역할, 규칙, 금지, 응답 포맷으로 나누었다.
역할은 주문, 배달 상태, 취소, 환불, 결제 문의를 분류하고 다음 행동을 안내하는 것으로 정의했다.
규칙에는 존댓말, 정보 부족 시 추측 금지, 시스템 확인이 필요한 영역 명시, 상담원 연결 여부 판단을 포함했다.

## System Prompt를 별도 클래스로 분리한 이유

`BaedalPrompt.SYSTEM_PROMPT`를 별도 클래스로 분리한 이유는 프롬프트를 애플리케이션의 핵심 정책으로 보기 때문이다.
System Prompt에는 역할, 금지 규칙, 응답 포맷이 포함되므로 단순한 요청 문자열이 아니라 상담 에이전트의 동작 계약에 가깝다.
이를 컨트롤러마다 직접 작성하면 `/api/v1/support`, `/api/v1/chat/stream`, `/api/v1/prompt-lab` 기본값이 서로 다른 프롬프트 버전을 사용할 위험이 있다.
따라서 한 곳에서 수정하고 여러 엔드포인트가 같은 정책을 재사용하도록 분리했다.

## ChatClient를 Config에서 build한 이유

`ChatClient.Builder`는 Spring이 주입하는 공통 builder이지만, `defaultSystem`이나 `defaultAdvisors` 같은 설정을 붙이는 객체다.
컨트롤러마다 원본 builder에 직접 설정을 붙이면 설정이 섞일 수 있으므로 `builder.clone()`으로 용도별 `ChatClient`를 만들었다.

`supportChatClient`는 `BaedalPrompt.SYSTEM_PROMPT`와 `endpoint=support`용 `PerformanceLoggingAdvisor`를 함께 적용한다.
`/api/v1/support`는 Structured Output을 반환하는 일반 호출이기 때문에 응답 시간과 토큰 수를 관찰하기 적합하다.

`syncChatClient`와 `streamingChatClient`는 같은 System Prompt를 사용하지만 각각 `endpoint=chat`, `endpoint=stream`으로 로그를 분리한다.
`PerformanceLoggingAdvisor`는 `CallAdvisor`와 `StreamAdvisor`를 함께 구현해 일반 호출은 `LLM call completed`, streaming 호출은 `LLM stream completed`로 기록한다.

`promptLabChatClient`는 기본 System Prompt를 고정하지 않은 실험용 `ChatClient`다.
프롬프트 실험 API는 요청마다 다른 System Prompt를 넣어 비교해야 하므로, 컨트롤러에서 새 `ChatClient`를 만들지 않고 요청 단위의 `.system(systemPrompt)`로 실험 프롬프트를 주입한다.
반복 실험은 서버 코드의 loop가 아니라 실제 curl 요청을 여러 번 보내는 방식으로 수행한다.
따라서 `PromptLabController`는 1회 실험만 처리하고, 요청별 응답 시간과 토큰 수를 남기기 위해 `promptLabChatClient`에는 `endpoint=promptLab`용 `PerformanceLoggingAdvisor`를 붙였다.
이렇게 하면 `ChatClient` 생성 책임은 `ChatClientConfig`에 남기고, `PromptLabController`는 실험 입력을 한 번의 요청에 적용하는 역할만 담당한다.

## 금지 규칙 3가지 선택 이유

1. 개인정보 노출 금지

배달 상담은 고객, 사장님, 라이더의 전화번호, 주소, 계좌 같은 민감 정보를 다룰 수 있다.
AI가 이런 정보를 노출하면 개인정보 침해와 안전 문제가 발생할 수 있으므로 금지 규칙으로 분리했다.

2. 환불, 보상, 쿠폰 지급 확정 약속 금지

AI는 실제 결제 상태, 주문 상태, 운영 정책을 직접 확정할 수 없다.
금전적 보상이나 환불을 확정적으로 말하면 고객 기대와 실제 처리 결과가 달라질 수 있으므로 금지했다.

3. 타 배달 플랫폼 추천 또는 비교 금지

상담 에이전트의 목적은 현재 플랫폼 안에서 고객 문제를 해결하는 것이다.
타사 비교나 추천은 상담 목적과 맞지 않고 브랜드 정책에도 영향을 줄 수 있으므로 금지했다.

## 응답 필드 추가 이유

`handoffRequired`와 `handoffReason`을 추가했다.
배달 상담에서는 AI가 안내할 수 있는 영역과 실제 상담원 또는 내부 시스템 확인이 필요한 영역이 분리되어야 한다.
이 필드는 환불 가능 여부, 결제 취소, 라이더/가게 확인처럼 AI가 단독으로 확정하면 안 되는 상황을 명확히 표현하기 위해 필요하다.

## 금지 규칙의 한계와 보완

실험 중 System Prompt에 금지 규칙이 있어도 "쿠폰을 제공해 드리겠습니다" 같은 확정 약속이 나오는 사례가 있었다.
따라서 금지 규칙은 모델 응답을 유도하는 1차 방어선으로 보고, 서버에서는 `PolicyValidationAdvisor`로 한 번 더 검사한다.

처음에는 `SupportResponseValidator`로 쿠폰/환불/보상 확정 표현과 전화번호, 이메일, 계좌번호로 보이는 패턴을 탐지했다.
다만 문자열 패턴 방식은 "환불 절차를 시작할 수 있습니다", "쿠폰 제공 가능 여부"처럼 표현이 바뀐 확정성 응답을 놓칠 수 있어, 추가 실험으로 `PolicyValidationAdvisor`를 붙였다.
`PolicyValidationAdvisor`는 상담 LLM 응답을 받은 뒤 사용자 문의와 응답 JSON을 별도 검증 프롬프트에 넣어 자연어 맥락 기반으로 정책 위반 여부를 판단한다.
검증 결과가 invalid이면 원본 LLM 응답을 그대로 반환하지 않고, `handoffRequired=true`인 안전한 상담원 확인 응답으로 교체한다.
이 방식도 모델 판단에 의존하므로 완벽한 방어선은 아니지만, 과제에서 관찰한 고위험 실패를 서버 경계에서 한 번 더 줄이는 보완책이다.

맥락 검증의 차이를 보기 위해 직접적인 금지어가 없는 응답도 별도로 검증했다.
예를 들어 "리뷰 쓰기 전에 뭐라도 성의 표시 해줘"라는 압박성 요청에 대해 "고객님께 유리한 방향으로 마무리", "고객님께 손해 없도록 정리"라고 답하는 경우가 있다.
이 표현에는 쿠폰, 환불, 보상이라는 단어가 없지만 실제 주문 상태 확인 없이 고객에게 유리한 처리를 약속하는 의미가 되므로 `PolicyValidationAdvisor`는 invalid로 판단했다.
반대로 "조치 가능 여부는 주문 상태와 운영 정책 확인이 필요합니다", "상담원이 확인하겠습니다"처럼 처리 가능성을 확정하지 않는 응답은 valid로 판단했다.

타 배달앱 요청은 출력 검증이 아니라 `SupportRequestGuard`에서 입력 단계에 먼저 차단한다.
"쿠팡이츠에서 찾아줘", "요기요랑 비교해줘" 같은 요청은 현재 서비스의 주문/배달/취소/환불/결제 상담 범위를 벗어난다.
따라서 LLM을 호출하지 않고 `ETC`, `LOW`, `handoffRequired=false` 응답으로 현재 서비스 문의만 안내한다.
이는 토큰 비용을 줄이고, 모델이 타사 비교 상담을 이어가는 실패를 줄이기 위한 입력 가드다.

## Category 선택 이유

카테고리는 `ORDER`, `DELIVERY`, `CANCEL`, `REFUND`, `PAYMENT`, `ETC`로 구성했다.
배달 상담의 주요 문의는 주문, 배달, 취소, 환불, 결제 문제로 나뉘며, 이 범주에 들어가지 않는 문의는 `ETC`로 처리한다.
기존 카테고리에 `CANCEL`을 추가한 이유는 취소 문의가 환불과 연결될 수는 있지만 고객 의도와 처리 흐름이 다르기 때문이다.
