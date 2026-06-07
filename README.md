# loop-play-spring-ai-agent

## Round 4 - RAG로 배달 정책/FAQ 지식 연동

이 README는 Round 4 진행을 위해 새로 시작한 문서입니다.
Round 3까지의 Chat Memory 실험과 검증 내용은 [docs/round3-backup.md](docs/round3-backup.md)에 보관했습니다.

Round 3까지 만든 에이전트는 "그거 취소해줘"처럼 이전 대화의 주문번호를 이어받는 질문을 처리할 수 있게 됐습니다.
하지만 환불 정책, 지연 보상 기준, 쿠폰 중복 규칙처럼 배달 서비스의 실제 정책 문서가 필요한 질문은 Tool Calling이나 Chat Memory만으로 해결할 수 없습니다.
Round 4는 정책/FAQ 문서를 검색해서 LLM 프롬프트에 넣어 주는 RAG를 붙이는 라운드입니다.

이번 라운드의 한 줄 메시지는 단순합니다.
RAG를 "연결하는 것"은 Advisor 한 줄에 가깝지만, 정작 어려운 건 얼마나 쪼갤지, 몇 건을 가져올지, 얼마 이상의 유사도를 신뢰할지, 모를 땐 어떻게 답할지 판단하는 일입니다.

## 실행 환경

- Java 17
- Spring Boot 3.4.1
- Spring AI 1.0.0
- Chat model: Ollama `qwen3:4b`
- Embedding model: Ollama `qwen3-embedding:0.6b`
- VectorStore: PgVector
- Local database: Docker Compose로 실행하는 PostgreSQL + pgvector

Round 4 구현이 진행되면 이 README에 PgVector 실행, knowledge indexing, retrieval demo, threshold/chunking 실험 결과를 이어서 기록합니다.

## 이번 라운드에 배우는 것

- 왜 RAG가 필요한가를 LLM의 학습 데이터 한계, 최신성, 도메인 특화 지식의 세 관점에서 설명합니다.
- RAG의 두 파이프라인인 indexing과 retrieval을 구분합니다.
- Spring AI의 `VectorStore`, `EmbeddingModel`, `TokenTextSplitter`, `QuestionAnswerAdvisor` 역할을 구분합니다.
- PgVector를 Docker로 띄우고 `initialize-schema: true`로 schema가 자동 생성되는 과정을 관찰합니다.
- 문서 chunking의 크기와 overlap trade-off를 설명하고, 값을 바꿔 검색 품질 변화를 관찰합니다.
- Round 3의 `MessageChatMemoryAdvisor`와 Round 4의 `QuestionAnswerAdvisor`가 같은 chain에서 어떻게 협업하는지 로그로 확인합니다.
- 검색 결과가 없을 때의 fallback 전략을 similarity threshold와 system prompt로 설계합니다.

## 학습 목표

- [ ] 왜 RAG가 필요한가를 LLM의 학습 데이터 한계, 최신성, 도메인 특화 지식의 세 관점에서 설명할 수 있다.
- [ ] RAG의 두 파이프라인인 indexing과 retrieval을 말로 설명할 수 있다.
- [ ] 임베딩 벡터의 직관을 "의미가 가까우면 거리가 가깝다"로 설명하고, 키워드 검색과의 차이를 말할 수 있다.
- [ ] `VectorStore`, `EmbeddingModel`, `TokenTextSplitter`, `QuestionAnswerAdvisor`의 역할을 구분할 수 있다.
- [ ] Chunk size와 overlap trade-off를 설명하고, 값을 바꿔 검색 품질 변화를 관찰할 수 있다.
- [ ] Memory Advisor와 QA Advisor가 같은 chain에서 어떻게 협업하는지 로그로 설명할 수 있다.
- [ ] 검색 결과가 없을 때의 fallback 전략을 similarity threshold와 system prompt로 설계할 수 있다.

## 1부. 왜 RAG가 필요한가 - LLM이 모르는 것을 답하게 하는 법

### 1.1 Round 3까지의 한계

지금까지 우리 에이전트는 "그거 취소해줘"까지는 잘 해결하게 됐습니다.
하지만 아래 질문들은 성격이 다릅니다.

```text
고객: 배달 완료 후에도 환불 받을 수 있나요?
봇:   배달의 환불 정책이 뭐였더라...

고객: 비 오는 날 배달이 늦으면 보상 받을 수 있나요?
봇:   "비 오는 날"에 관한 공식 정책이 있었나...

고객: 쿠폰 적용이 안 돼요. 중복 사용 가능한가요?
봇:   쿠폰 중복 규칙이 뭐였지...
```

이 질문들은 주문번호를 기억한다고 풀리지 않고, 주문 Tool을 호출한다고 풀리지도 않습니다.
필요한 것은 배달 서비스의 실제 정책 문서입니다.

핵심은 LLM이 만능 지식 저장소가 아니라는 점입니다.
배달의 환불 규정, 지연 보상 기준, 쿠폰 정책 같은 도메인 특화 지식은 모델 학습 데이터에 들어있다고 기대하면 안 됩니다.
RAG는 LLM이 모르는 지식을 검색해서 프롬프트에 넣어 준다는 단순한 아이디어이고, 그래서 강력합니다.

### 1.2 RAG가 해결하는 세 가지 문제

| 문제 | 예시 | RAG 없이는 |
| --- | --- | --- |
| LLM 학습 데이터에 없는 지식 | 배달 환불 정책, 지연 보상 기준 | LLM이 그럴듯하게 꾸며낸 환각 응답 |
| 최신성 | 이번 달 이벤트, 어제 개정된 쿠폰 정책 | 파인튜닝으로 따라잡기 어려운 비용과 주기 |
| 출처 추적 가능성 | "이 답의 근거 문서가 뭔가?"라는 감사 요구 | LLM 응답만으로는 근거 확인이 어려움 |

### 1.3 그냥 Tool로 검색 API를 만들면 안 되는가?

Tool로 `searchPolicy(keyword)`를 만드는 것도 가능한 설계입니다.
실제 서비스에서도 정책 검색을 명시적으로 통제해야 하거나, 검색 실행 자체를 audit log로 남겨야 한다면 Tool 방식이 더 적합할 수 있습니다.

다만 상담 도메인에서는 고객이 "환불 정책 검색해줘"라고 말하기보다 "배달 완료됐는데 환불 돼요?"라고 묻는 경우가 대부분입니다.

| 방식 | 설계 | 언제 쓰나 |
| --- | --- | --- |
| Tool 방식 | `searchPolicy(keyword)` Tool을 만들고 LLM이 필요할 때 호출 | 고객이 검색 의도를 명시하거나, 검색 실행 자체를 통제해야 할 때 |
| RAG 방식 | 사용자 질문을 자동으로 임베딩하고, 검색 결과를 프롬프트에 주입 | 고객 질문 자체가 이미 정책 검색을 필요로 하는 상담일 때 |

이번 Round 4에서는 RAG 방식이 더 자연스럽습니다.
검색 의도를 별도로 감지하지 않아도 모든 질문에 대해 관련 정책을 찾아 프롬프트에 넣을 수 있기 때문입니다.

### 1.4 RAG의 두 파이프라인

RAG 시스템은 indexing과 retrieval이라는 두 개의 다른 파이프라인으로 나뉩니다.
이 둘을 분리해서 이해해야 디버깅이 쉬워집니다.

```text
[인덱싱 파이프라인] - 앱 기동 시 또는 문서 갱신 시 실행

FAQ/정책 Markdown 파일
  -> 파일 읽기
  -> Spring AI Document
  -> TokenTextSplitter로 chunk 생성
  -> EmbeddingModel(qwen3-embedding:0.6b)로 chunk 벡터화
  -> 벡터 + 원문 텍스트 + metadata를 VectorStore(PgVector)에 저장

[검색 파이프라인] - 매 요청마다 실행

고객 질문
  -> 같은 EmbeddingModel(qwen3-embedding:0.6b)로 질문 벡터화
  -> VectorStore.similaritySearch
  -> 유사도 상위 K개 문서 조회
  -> QuestionAnswerAdvisor가 Context를 프롬프트에 주입
  -> Context + 원래 질문으로 LLM 응답 생성
```

인덱싱 때 쓴 임베딩 모델과 검색 때 쓴 임베딩 모델은 같아야 합니다.
서로 다른 임베딩 모델이 만든 벡터는 같은 좌표계에 있지 않기 때문에 거리 비교가 의미 없어집니다.

### 1.5 임베딩의 직관

임베딩 모델은 문장을 고정된 차원의 벡터로 바꿉니다.
`qwen3-embedding:0.6b`는 1024차원 벡터를 사용하고, 문장의 의미를 1024개의 숫자로 압축한다고 볼 수 있습니다.

핵심 직관은 의미가 가까운 문장은 벡터 공간에서도 가깝다는 것입니다.

```text
"환불 받을 수 있나요?"   -> [0.21, -0.15, 0.42, ...]
"돈 돌려받을 수 있어요?" -> [0.19, -0.14, 0.41, ...]  의미가 가까움
"치킨 2마리 주문할게요"  -> [0.75, -0.02, -0.33, ...] 의미가 멂
```

그래서 RAG는 키워드 검색과 달리 "환불"이라는 단어가 없어도 "돈 돌려받다"와 가까운 정책을 찾을 수 있습니다.
이것이 벡터 검색의 힘입니다.

### 1.6 왜 청킹이 필요한가

정책 문서를 통째로 하나의 벡터로 만들면 질문과의 유사도가 뭉툭해집니다.
A4 10장짜리 문서 전체를 벡터 하나에 압축하면, 실제 답은 한 문단에만 있어도 전체 문서의 평균적인 의미로 검색됩니다.
또 답에 필요한 조각은 일부인데 전체 문서를 프롬프트에 넣으면 입력 토큰이 늘고 노이즈도 커집니다.

그래서 문서를 적당한 크기의 chunk로 쪼개고, 각 chunk를 독립적으로 임베딩합니다.

| 청크 크기 | 장점 | 단점 |
| --- | --- | --- |
| 작게, 200~400 토큰 | 검색 정확도가 높아질 수 있음 | 앞뒤 맥락이 잘릴 수 있음 |
| 중간, 600~1000 토큰 | 정확도와 맥락 보존의 균형 | 실험으로 맞춰야 할 튜닝 포인트가 많음 |
| 크게, 1500~3000 토큰 | 맥락을 많이 보존 | 유사도가 뭉툭해지고 토큰 낭비가 커질 수 있음 |

Overlap은 chunk 경계에서 맥락이 끊기는 문제를 줄이는 장치입니다.
예를 들어 chunk size 800, overlap 200이면 인접 chunk가 200토큰 정도의 문맥을 공유합니다.

Chunk size와 overlap은 RAG에서 가장 많이 튜닝하는 값입니다.
처음부터 정답을 맞히려 하기보다, 실제 질문과 검색 결과를 보면서 조정하는 실험으로 봐야 합니다.

## 다음 작성 흐름

다음 절에서는 Spring AI의 `EmbeddingModel`, `VectorStore`, `TokenTextSplitter`, `QuestionAnswerAdvisor`를 코드와 연결해서 정리합니다.
그다음 PgVector 인덱싱 파이프라인, Memory + RAG advisor chain, fallback 전략, S3 Vectors 운영 판단을 이어서 기록합니다.
