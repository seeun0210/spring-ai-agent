# loop-play-spring-ai-agent

Spring AI 기반 배달 상담 에이전트 학습용 스타터 코드입니다.

## 개요

루퍼스 부트캠프 "Spring AI 배달 상담 에이전트" 6주 과정의 Week 1 미션 스타터 코드입니다.
`ChatClient`, System Prompt, Structured Output, Streaming, Observability 개념을 실습합니다.

## 빠른 시작

```bash
./gradlew bootRun
```

## Round 1 구현 요약

- `/api/v1/support`: `BaedalPrompt.SYSTEM_PROMPT`를 적용하고 `SupportResponse` Structured Output을 반환합니다.
- `/api/v1/prompt-lab`: 요청으로 받은 System Prompt를 반복 실행하고 category consistency를 계산합니다.
- `/api/v1/chat/stream`: SSE 기반 streaming 응답을 반환합니다.
- `/`: 상담, 스트리밍, 프롬프트 실험을 직접 실행할 수 있는 데모 UI를 제공합니다.

## 주요 설계 결정

`System Prompt`는 `BaedalPrompt` 별도 클래스로 분리했습니다.
컨트롤러 내부 문자열로 두면 `/support`, `/stream`, `/prompt-lab` 기본값에서 같은 프롬프트를 중복 관리하게 되고, 금지 규칙이나 응답 포맷을 수정할 때 엔드포인트별로 다른 버전이 섞일 위험이 있기 때문입니다.

배달 상담용 `ChatClient`는 `ChatClientConfig`에서 `supportChatClient`, `streamingChatClient`로 나누어 미리 build했습니다.
공통 System Prompt는 재사용하되, advisor 적용 범위는 엔드포인트별로 달라질 수 있기 때문입니다.
예를 들어 `PerformanceLoggingAdvisor`는 일반 호출인 `/support`에 붙이고, streaming API는 별도의 `streamingChatClient`로 분리했습니다.

`PromptLabController`는 `ChatClient.Builder`를 유지합니다.
이 API는 요청마다 다른 System Prompt를 실험해야 하므로 고정된 `ChatClient` Bean보다 `builder.clone().defaultSystem(systemPrompt).build()` 방식이 목적에 맞습니다.

자세한 설계 근거는 [`docs/design-decisions.md`](docs/design-decisions.md)에 기록했습니다.

## 테스트

```bash
./gradlew build
```
