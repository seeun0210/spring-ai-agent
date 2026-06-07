# Round 4 RAG Integration Design

## Goal

Integrate the Round 4 RAG starter into the existing Week 1-3 Baedal support agent without regressing the current package boundaries, memory behavior, tool execution safety, or structured support response validation.

Round 4 should demonstrate that policy and FAQ knowledge is not solved by Tool Calling or Chat Memory. The agent must retrieve official policy snippets from a vector store and use those snippets together with the existing conversation memory and order tools.

## Existing Context

The current codebase already separates responsibilities into focused packages:

- `controller`: HTTP endpoints only.
- `service`: request orchestration and ChatClient calls.
- `config`: ChatClient and advisor wiring.
- `memory`: conversation id resolution, message window memory, and session inspection.
- `order`: mock order domain, order views, cancellation service, and order tools.
- `tool`: tool execution guardrails and conversation order state.
- `advisor`: logging and policy validation advisors.
- `prompt`: the shared system prompt.

The Round 4 starter adds PgVector-backed RAG, knowledge markdown files, a loader, and a `QuestionAnswerAdvisor`, but it places some classes in older root-package/controller-centric patterns. We will import the feature, not the old structure.

## Recommended Approach

Use PgVector as the live vector store for this round, because the assignment explicitly asks students to observe Docker PgVector, Spring AI schema initialization, `VectorStore`, `TokenTextSplitter`, and `QuestionAnswerAdvisor`.

Keep the existing service-oriented structure and add a new `rag` package:

- `com.baedal.support.rag.FaqDocument`: immutable representation of a source policy document before conversion to Spring AI `Document`.
- `com.baedal.support.rag.KnowledgeLoader`: `ApplicationRunner` that loads `classpath:/knowledge/*.md`, parses filename metadata, chunks documents, skips already-loaded FAQ ids, and writes chunks to `VectorStore`.
- `com.baedal.support.rag.RagConfig`: owns RAG tuning values and exposes `TokenTextSplitter` plus `QuestionAnswerAdvisor`.

Wire `QuestionAnswerAdvisor` into existing ChatClients instead of rebuilding controllers around the starter:

- `supportChatClient`: `MessageChatMemoryAdvisor` -> `QuestionAnswerAdvisor` -> `PolicyValidationAdvisor` -> `PerformanceLoggingAdvisor`.
- `syncChatClient`: `MessageChatMemoryAdvisor` -> `QuestionAnswerAdvisor` -> `PerformanceLoggingAdvisor`.

The exact order values should stay explicit:

- Memory advisor: `order(10)`.
- RAG advisor: `order(20)`.
- Performance logging advisor: after the context-producing advisors.

This makes the Round 4 behavior observable: memory restores conversation context first, then RAG retrieves policy knowledge for the current user question, then the final LLM call is logged.

## Scope

### In Scope

- Add PgVector, vector advisor, and PostgreSQL dependencies.
- Keep existing JDBC chat memory/H2 dependencies and profile behavior intact.
- Add `docker-compose.yml` for local PgVector.
- Add starter knowledge markdown files under `src/main/resources/knowledge`.
- Add completed RAG configuration and knowledge loading code under `src/main/java/com/baedal/support/rag`.
- Update `ChatClientConfig` to include the RAG advisor in the existing ChatClient beans.
- Update `BaedalPrompt.SYSTEM_PROMPT` with policy citation and fallback rules.
- Add focused tests for RAG configuration and knowledge loading behavior.
- Update README with Round 4 concepts, run steps, demo commands, tuning decisions, and S3 Vectors discussion.

### Out Of Scope

- Implementing S3 Vectors.
- Replacing PgVector with OpenSearch, S3 Vectors, or another vector store.
- Rewriting controllers to match the starter package layout.
- Removing existing Week 2/3 tool policy, chat memory, or session APIs.
- Adding advanced `RetrievalAugmentationAdvisor`, query rewriting, or multi-store orchestration.

## Configuration Decisions

Use `qwen3-embedding:0.6b` as the embedding model and configure PgVector dimensions as `1024`, matching the assignment text. The chat model remains the current project default unless a test run needs an override.

Start with:

- `topK = 4`: enough to cover adjacent refund, delay, coupon, or cancellation snippets without injecting the whole knowledge base.
- `similarityThreshold = 0.5`: a starting threshold for local observation, not a universal truth.
- `TokenTextSplitter(800, 350, 5, 10000, true)`: follows the assignment baseline and is reasonable for short policy documents that are already split by topic.

These values should be explained in README as experiment baselines, not production defaults.

## Fallback And Prompt Rules

The system prompt should add policy citation rules:

- Answer refund, cancellation, delivery-delay compensation, coupon, and account-policy questions using provided RAG context.
- If the context does not contain the answer, do not invent policy. Use a fixed fallback message: "해당 내용은 확인이 필요합니다. 상담원 연결로 도와드리겠습니다."
- Preserve policy numbers and conditions exactly, such as "24시간 이내" or required evidence.
- For out-of-domain questions, explain that the assistant handles order, delivery, cancellation, refund, payment, and coupon support.
- When tool results and policy context both apply, tool results provide order-specific facts and RAG context provides policy rules.

This gives two layers of hallucination defense: similarity threshold filters weak matches, and the prompt constrains behavior when context is absent or insufficient.

## S3 Vectors Discussion

S3 Vectors should be discussed in README as an architecture note, not implemented.

Based on the AWS announcement and the hybrid vector storage discussion, S3 Vectors is useful for massive, durable, lower-cost vector storage with metadata filtering and integrations with Bedrock Knowledge Bases and OpenSearch. It is not the first choice for this local Round 4 hot-path FAQ chatbot, where the assignment requires PgVector and local observability. A senior production direction would be:

- Keep hot, latency-sensitive support FAQ vectors in PgVector or OpenSearch.
- Consider S3 Vectors for large, long-tail, low-frequency, archival, compliance, or background enrichment vectors.
- Introduce hybrid retrieval only when query volume, corpus size, latency SLOs, and cost justify the orchestration complexity.

## Testing Strategy

Add tests at the boundary of our code, not by requiring a live LLM for unit tests:

- `RagConfigTest`: verifies the splitter bean exists and the advisor bean can be created with a mocked `VectorStore`.
- `KnowledgeLoaderTest`: verifies filename parsing through a package-visible helper or focused loader method, metadata creation, chunk add behavior, and duplicate skip behavior with mocked `VectorStore`.
- Existing service tests should continue to prove memory conversation ids and tool context are passed correctly.

Runtime verification remains necessary for the full RAG path:

- Start PgVector with Docker Compose.
- Ensure `ollama pull qwen3-embedding:0.6b` has been done.
- Start the app and observe `KnowledgeLoader` logs.
- Call `/api/v1/assistant` with refund, weather delay, coupon, memory-plus-RAG, and out-of-domain prompts.
- Confirm logs show RAG context/search behavior and responses preserve source policy details.

## Documentation Requirements

README should include:

- Why RAG is needed beyond Tool Calling and Memory.
- Indexing vs retrieval pipelines in prose.
- Spring AI component roles: `EmbeddingModel`, `VectorStore`, `TokenTextSplitter`, `QuestionAnswerAdvisor`.
- PgVector local run instructions.
- Tuning decisions for chunk size, topK, and similarity threshold.
- Advisor chain ordering and why Memory should run before RAG.
- Fallback strategy and hallucination risk.
- Demo curl commands and expected observations.
- S3 Vectors as a non-implemented production architecture discussion.

## Risks

- PgVector auto-configuration may conflict with existing H2/JDBC chat memory assumptions if datasource settings are not profile-scoped carefully.
- `QuestionAnswerAdvisor` can change structured `/support` output behavior by injecting extra context.
- Local verification depends on Ollama models and Docker availability.
- Duplicate detection through `similaritySearch` plus metadata filter is sufficient for the assignment, but it will not detect changed document content.

## Approval

Proceed with this design by preserving the existing Week 1-3 structure and integrating Round 4 RAG as an additive layer.
