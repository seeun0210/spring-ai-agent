package com.baedal.support.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class SessionControllerTest {

    private final ChatMemoryConfig config = new ChatMemoryConfig();
    private final ChatMemoryRepository repository = config.chatMemoryRepository();
    private final ChatMemory chatMemory = config.chatMemory(repository);
    private final ConversationIdResolver conversationIdResolver = org.mockito.Mockito.mock(ConversationIdResolver.class);
    private final SessionController controller = new SessionController(chatMemory, repository, conversationIdResolver);

    @Test
    void returnsMessagesForSessionInOrder() {
        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        chatMemory.add("customer-1:session-a", List.of(
                new UserMessage("주문번호 2024-1234 배달 어디쯤이에요?"),
                new AssistantMessage("배달 중입니다.")
        ));

        List<SessionController.MessageView> messages = controller.messages("session-a");

        assertThat(messages)
                .extracting(SessionController.MessageView::type)
                .containsExactly("USER", "ASSISTANT");
        assertThat(messages)
                .extracting(SessionController.MessageView::content)
                .containsExactly("주문번호 2024-1234 배달 어디쯤이에요?", "배달 중입니다.");
    }

    @Test
    void returnsConversationIdsAndClearsSession() {
        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(conversationIdResolver.sessionIdsForCurrentCustomer(anyList()))
                .thenReturn(List.of("session-a", "session-b"), List.of("session-b"));
        chatMemory.add("customer-1:session-a", List.of(new UserMessage("첫 번째 세션")));
        chatMemory.add("customer-1:session-b", List.of(new UserMessage("두 번째 세션")));

        assertThat(controller.sessions()).containsExactly("session-a", "session-b");

        controller.clear("session-a");

        assertThat(controller.messages("session-a")).isEmpty();
        assertThat(controller.sessions()).containsExactly("session-b");
    }
}
