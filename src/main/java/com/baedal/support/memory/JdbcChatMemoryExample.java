package com.baedal.support.memory;

/**
 * JDBC Chat Memory 전환 시 참고용 문서 클래스다.
 *
 * <p>현재 과제 구현은 짧은 로컬 실험과 세션 분리 관찰이 목적이라
 * {@link org.springframework.ai.chat.memory.InMemoryChatMemoryRepository}를 기본으로 쓴다.
 * 서버 재시작 후 대화 보존, 멀티 인스턴스 공유, 상담 이력 감사가 필요해지는 순간에는
 * JDBC 저장소가 더 적합하다.
 *
 * <p>전환 방향:
 * <ol>
 *     <li>{@code spring-ai-starter-model-chat-memory-repository-jdbc}와 DB 드라이버를 추가한다.</li>
 *     <li>{@link ChatMemoryConfig#chatMemoryRepository()}의 {@code !jdbc} 프로필 제한을 유지한다.</li>
 *     <li>{@code jdbc} 프로필에서 Spring AI가 자동 구성한 JDBC repository를 주입받는다.</li>
 *     <li>대화 이력 보존 기간, 삭제 배치, 개인정보 저장 전 마스킹 정책을 함께 정한다.</li>
 * </ol>
 */
public final class JdbcChatMemoryExample {

    private JdbcChatMemoryExample() {
    }
}
