package com.baedal.support.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.content.Content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMemoryConfigTest {

    private final ChatMemoryConfig config = new ChatMemoryConfig();

    @Test
    void messageWindowKeepsRecentMessagesOnly() {
        ChatMemoryRepository repository = config.chatMemoryRepository();
        ChatMemory chatMemory = config.chatMemory(repository);

        for (int i = 0; i < ChatMemoryConfig.MAX_MESSAGES + 5; i++) {
            chatMemory.add("session-a", List.of(new UserMessage("message-" + i)));
        }

        assertThat(chatMemory.get("session-a"))
                .hasSize(ChatMemoryConfig.MAX_MESSAGES)
                .first()
                .extracting(Content::getText)
                .isEqualTo("message-5");
    }

    @Test
    void memoryAdvisorRunsBeforePerformanceAdvisor() {
        ChatMemoryRepository repository = config.chatMemoryRepository();
        ChatMemory chatMemory = config.chatMemory(repository);

        assertThat(config.messageChatMemoryAdvisor(chatMemory).getOrder()).isEqualTo(10);
    }

    @Test
    void newSystemMessageReplacesPreviousSystemMessage() {
        ChatMemoryRepository repository = config.chatMemoryRepository();
        ChatMemory chatMemory = config.chatMemory(repository);

        chatMemory.add("session-a", List.of(
                new SystemMessage("old system"),
                new UserMessage("first user")
        ));
        chatMemory.add("session-a", List.of(new SystemMessage("new system")));

        assertThat(chatMemory.get("session-a"))
                .extracting(Content::getText)
                .containsExactly("first user", "new system");
    }

    @Test
    void systemMessageIsPreservedWhenWindowTrimsOldMessages() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(3)
                .build();

        chatMemory.add("session-a", List.of(
                new SystemMessage("system prompt"),
                new UserMessage("old user"),
                new AssistantMessage("old assistant")
        ));
        chatMemory.add("session-a", List.of(new UserMessage("new user")));

        assertThat(chatMemory.get("session-a"))
                .extracting(Content::getText)
                .containsExactly("system prompt", "old assistant", "new user");
    }

    @Test
    void concurrentAddsToSameConversationCanLoseOneMessage() throws Exception {
        BlockingReadChatMemoryRepository repository = new BlockingReadChatMemoryRepository(2);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(ChatMemoryConfig.MAX_MESSAGES)
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> chatMemory.add("session-a", List.of(new UserMessage("message-a"))));
        Future<?> second = executor.submit(() -> chatMemory.add("session-a", List.of(new UserMessage("message-b"))));

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(chatMemory.get("session-a"))
                .extracting(Content::getText)
                .hasSize(1)
                .containsAnyOf("message-a", "message-b");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RACE_PROBE", matches = "true")
    void realInMemoryRepositoryCanLoseMessagesUnderConcurrentAdds() throws Exception {
        int requestCount = 200;
        int maxAttempts = 20;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            ChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(requestCount + 1)
                    .build();
            ExecutorService executor = Executors.newFixedThreadPool(requestCount);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<Object>> futures = IntStream.range(0, requestCount)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        chatMemory.add("race-session", List.of(new UserMessage("message-" + i)));
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            int storedCount = chatMemory.get("race-session").size();
            if (storedCount < requestCount) {
                System.out.printf(
                        "Observed ChatMemory lost update: attempt=%d, requested=%d, stored=%d%n",
                        attempt,
                        requestCount,
                        storedCount
                );
                return;
            }
        }

        throw new AssertionError(
                "No lost update observed after " + maxAttempts + " attempts. "
                        + "This probe is timing-dependent; rerun it or increase requestCount."
        );
    }

    private static class BlockingReadChatMemoryRepository implements ChatMemoryRepository {

        private final CountDownLatch readLatch;
        private final Map<String, List<org.springframework.ai.chat.messages.Message>> store = new ConcurrentHashMap<>();

        BlockingReadChatMemoryRepository(int readCount) {
            this.readLatch = new CountDownLatch(readCount);
        }

        @Override
        public List<String> findConversationIds() {
            return new ArrayList<>(store.keySet());
        }

        @Override
        public List<org.springframework.ai.chat.messages.Message> findByConversationId(String conversationId) {
            List<org.springframework.ai.chat.messages.Message> snapshot =
                    new ArrayList<>(store.getOrDefault(conversationId, List.of()));
            readLatch.countDown();
            try {
                if (!readLatch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("concurrent reads did not arrive in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for concurrent reads", e);
            }
            return snapshot;
        }

        @Override
        public void saveAll(String conversationId, List<org.springframework.ai.chat.messages.Message> messages) {
            store.put(conversationId, new ArrayList<>(messages));
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            store.remove(conversationId);
        }
    }
}
