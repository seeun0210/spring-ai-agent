# Round 4 RAG 통합 설계

## 목표

Round 4 RAG starter를 기존 Week 1-3 배달 상담 에이전트에 통합한다. 기존 패키지 경계, Chat Memory 동작, Tool 실행 안전장치, structured support 응답 검증을 깨지 않는 것이 전제다.

Round 4의 핵심은 정책/FAQ 지식이 Tool Calling이나 Chat Memory만으로 해결되지 않는다는 점을 코드와 실험으로 보여주는 것이다. 에이전트는 공식 정책 문서를 VectorStore에서 검색하고, 검색된 정책 조각을 기존 대화 맥락과 주문 Tool 결과와 함께 사용해야 한다.

## 현재 코드 맥락

현재 코드는 이미 책임별 패키지 경계가 잡혀 있다.

- `controller`: HTTP endpoint만 담당한다.
- `service`: 요청 흐름 조립과 ChatClient 호출을 담당한다.
- `config`: ChatClient와 Advisor wiring을 담당한다.
- `memory`: conversation id 생성, message window memory, session 조회를 담당한다.
- `order`: mock 주문 도메인, view 변환, 취소 서비스, 주문 Tool을 담당한다.
- `tool`: Tool 실행 guardrail과 conversation order state를 담당한다.
- `advisor`: 성능 로깅과 정책 검증 Advisor를 담당한다.
- `prompt`: 공통 system prompt를 담당한다.

Round 4 starter는 PgVector 기반 RAG, knowledge markdown, loader, `QuestionAnswerAdvisor`를 추가한다. 다만 일부 코드는 예전 root package/controller 중심 구조에 맞춰져 있다. 따라서 starter의 구조를 그대로 가져오지 않고, 기능만 현재 구조에 맞게 이식한다.

## 권장 접근

이번 라운드의 실제 VectorStore는 PgVector를 사용한다. 과제에서 Docker PgVector, Spring AI schema 자동 초기화, `VectorStore`, `TokenTextSplitter`, `QuestionAnswerAdvisor` 관찰을 명시하기 때문이다.

기존 service 중심 구조는 유지하고 새 `rag` 패키지를 추가한다.

- `com.baedal.support.rag.FaqDocument`: Spring AI `Document`로 바꾸기 전의 원본 정책 문서를 표현하는 record.
- `com.baedal.support.rag.KnowledgeLoader`: `ApplicationRunner`로 `classpath:/knowledge/*.md`를 읽고, 파일명 metadata를 파싱하고, 문서를 chunking하고, 이미 적재된 FAQ id는 건너뛰고, chunk를 `VectorStore`에 저장한다.
- `com.baedal.support.rag.RagConfig`: RAG 튜닝값을 관리하고 `TokenTextSplitter`, `QuestionAnswerAdvisor` bean을 제공한다.

Controller를 starter 방식으로 되돌리지 않는다. 대신 기존 ChatClient bean에 `QuestionAnswerAdvisor`를 추가한다.

- `supportChatClient`: `MessageChatMemoryAdvisor` -> `QuestionAnswerAdvisor` -> `PolicyValidationAdvisor` -> `PerformanceLoggingAdvisor`.
- `syncChatClient`: `MessageChatMemoryAdvisor` -> `QuestionAnswerAdvisor` -> `PerformanceLoggingAdvisor`.

Advisor 순서는 명시적으로 유지한다.

- Memory Advisor: `order(10)`.
- RAG Advisor: `order(20)`.
- Performance Logging Advisor: context를 만드는 Advisor 뒤에서 실행한다.

이 순서 덕분에 Round 4 동작을 로그로 설명할 수 있다. 먼저 Memory가 이전 대화 맥락을 복원하고, 그다음 RAG가 현재 질문에 맞는 정책 지식을 검색하며, 마지막 LLM 호출 전체가 성능 로그에 남는다.

## 범위

### 포함

- PgVector, vector advisor, PostgreSQL 의존성을 추가한다.
- 기존 JDBC Chat Memory/H2 의존성과 `jdbc` profile 동작은 유지한다.
- 로컬 PgVector 실행용 `docker-compose.yml`을 추가한다.
- starter의 knowledge markdown 파일을 `src/main/resources/knowledge`에 추가한다.
- 완성된 RAG 설정과 knowledge loader를 `src/main/java/com/baedal/support/rag`에 추가한다.
- `ChatClientConfig`에 RAG Advisor를 연결한다.
- `BaedalPrompt.SYSTEM_PROMPT`에 정책 인용 규칙과 fallback 규칙을 추가한다.
- RAG 설정과 knowledge loading 경계에 대한 focused test를 추가한다.
- README에 Round 4 개념, 실행 절차, demo command, 튜닝 판단, S3 Vectors 논의를 정리한다.

### 제외

- S3 Vectors를 직접 구현하지 않는다.
- PgVector를 OpenSearch, S3 Vectors, 다른 VectorStore로 교체하지 않는다.
- Controller를 starter package layout으로 되돌리지 않는다.
- 기존 Week 2/3 Tool policy, Chat Memory, Session API를 제거하지 않는다.
- Advanced 영역인 `RetrievalAugmentationAdvisor`, query rewriting, multi-store orchestration은 구현하지 않는다.

## 설정 결정

Embedding model은 `qwen3-embedding:0.6b`를 사용하고, PgVector dimensions는 과제 설명에 맞춰 `1024`로 설정한다. Chat model은 현재 프로젝트 기본값을 유지하되, 실험 실행 시 필요하면 command line override로 바꿀 수 있게 둔다.

초기 기준값은 다음으로 잡는다.

- `topK = 4`: 환불, 지연, 쿠폰, 취소처럼 인접한 정책 조각을 함께 볼 수 있으면서 전체 knowledge base를 프롬프트에 밀어 넣지는 않는 기준값이다.
- `similarityThreshold = 0.5`: 로컬 관찰을 위한 시작점이다. universal default가 아니라 실험으로 조정해야 할 값이다.
- `TokenTextSplitter(800, 350, 5, 10000, true)`: 과제 baseline을 따르며, 이미 topic 단위로 나뉜 짧은 정책 문서에는 합리적인 출발점이다.

이 값들은 README에서 production default가 아니라 실험 baseline으로 설명한다.

## Fallback 및 Prompt 규칙

System prompt에는 정책 인용 규칙을 추가한다.

- 환불, 취소, 배달 지연 보상, 쿠폰, 계정 정책 질문은 제공된 RAG context를 근거로 답한다.
- Context에 답이 없으면 정책을 지어내지 않고 고정 문구를 사용한다: "해당 내용은 확인이 필요합니다. 상담원 연결로 도와드리겠습니다."
- "24시간 이내", "사진 증빙" 같은 정책 수치와 조건은 원문 표현을 유지한다.
- 상담 범위 밖 질문에는 주문, 배달, 취소, 환불, 결제, 쿠폰 관련 상담을 돕는 역할이라고 안내한다.
- Tool 결과와 정책 context가 모두 관련될 때는 Tool 결과를 주문별 사실로, RAG context를 정책 규칙으로 사용한다.

이렇게 두 겹으로 환각을 줄인다. 유사도 임계값은 약한 검색 결과를 거르고, prompt 규칙은 context가 없거나 부족할 때 LLM이 임의로 정책을 만들어내지 못하게 한다.

## S3 Vectors 논의

S3 Vectors는 README의 아키텍처 노트로 다루고 구현하지 않는다.

AWS 발표와 hybrid vector storage 논의를 기준으로 보면, S3 Vectors는 metadata filter와 Bedrock Knowledge Bases/OpenSearch 연동을 제공하는 대규모 durable vector storage 선택지다. 하지만 이번 Round 4의 로컬 hot-path FAQ 상담 챗봇에는 우선순위가 낮다. 과제의 목적은 PgVector와 Spring AI RAG chain을 직접 관찰하는 것이기 때문이다.

운영 환경에서의 판단은 다음처럼 정리한다.

- 실시간 상담 FAQ처럼 latency가 중요한 hot vector는 PgVector 또는 OpenSearch에 둔다.
- 대규모, long-tail, 저빈도, archival, compliance, background enrichment vector는 S3 Vectors 후보로 본다.
- Hybrid retrieval은 query volume, corpus size, latency SLO, cost가 복잡도를 정당화할 때만 도입한다.

## 테스트 전략

Unit test는 live LLM이나 실제 PgVector에 의존하지 않고 우리 코드의 경계에서 작성한다.

- `RagConfigTest`: splitter bean이 생성되는지, mocked `VectorStore`로 advisor bean을 만들 수 있는지 확인한다.
- `KnowledgeLoaderTest`: 파일명 파싱, metadata 생성, chunk add 동작, duplicate skip 동작을 mocked `VectorStore`로 확인한다.
- 기존 service test는 conversation id와 tool context 전달을 계속 검증한다.

전체 RAG path는 runtime 검증이 필요하다.

- Docker Compose로 PgVector를 띄운다.
- `ollama pull qwen3-embedding:0.6b`가 끝나 있어야 한다.
- 앱을 실행하고 `KnowledgeLoader` 로그를 확인한다.
- `/api/v1/assistant`에 환불, 우천 지연, 쿠폰, Memory+RAG, 상담 범위 밖 질문을 보낸다.
- 로그에서 RAG 검색/context 동작을 확인하고, 응답이 정책 원문 수치와 조건을 보존하는지 확인한다.

## 문서화 요구사항

README에는 다음을 포함한다.

- Tool Calling과 Memory만으로 RAG 질문을 해결할 수 없는 이유.
- Indexing pipeline과 Retrieval pipeline 설명.
- Spring AI 구성요소 역할: `EmbeddingModel`, `VectorStore`, `TokenTextSplitter`, `QuestionAnswerAdvisor`.
- PgVector 로컬 실행 방법.
- chunk size, topK, similarity threshold 선택 근거.
- Advisor chain 순서와 Memory가 RAG보다 먼저 실행되어야 하는 이유.
- Fallback 전략과 환각 위험.
- Demo curl command와 기대 관찰 포인트.
- 구현하지 않는 production architecture 논의로서의 S3 Vectors.

## 위험 요소

- PgVector auto-configuration이 기존 H2/JDBC Chat Memory 설정과 충돌할 수 있으므로 datasource/profile 경계를 조심해야 한다.
- `QuestionAnswerAdvisor`가 structured `/support` 응답에 추가 context를 넣으면서 category/urgency 판단을 바꿀 수 있다.
- 전체 검증은 Ollama model과 Docker 실행 가능 여부에 의존한다.
- `similaritySearch`와 metadata filter로 중복을 판단하는 방식은 과제에는 충분하지만, 문서 내용 변경은 감지하지 못한다.

## 승인 기준

기존 Week 1-3 구조를 유지하고, Round 4 RAG를 additive layer로 통합한다. 이 설계에 따라 구현 계획을 작성하고 구현을 진행한다.
