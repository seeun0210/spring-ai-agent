package com.baedal.support.service;

import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.tool.ConversationOrderStateRepository;
import com.baedal.support.tool.ToolExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    @Test
    void assistPassesSessionIdAsChatMemoryConversationIdAndToolContext() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorsCaptor = ArgumentCaptor.forClass(Consumer.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> toolContextCaptor = ArgumentCaptor.forClass(Map.class);

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(advisorsCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.toolContext(toolContextCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.user("2024-1234 어디쯤이에요?")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("배달 중입니다.");

        AssistantService service = new AssistantService(chatClient, conversationIdResolver, orderStateRepository);

        String response = service.assist("session-a", "2024-1234 어디쯤이에요?");

        advisorsCaptor.getValue().accept(advisorSpec);
        assertThat(response).isEqualTo("배달 중입니다.");
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "customer-1:session-a");
        assertThat(toolContextCaptor.getValue())
                .containsEntry(ToolExecutionPolicy.CONVERSATION_ID, "customer-1:session-a")
                .containsEntry(ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of("2024-1234"))
                .containsEntry(ToolExecutionPolicy.RECENT_ORDER_IDS, List.of("2024-1234"))
                .containsEntry(ToolExecutionPolicy.ACTIVE_ORDER_ID, "2024-1234");
    }

    @Test
    void assistCarriesRecentOrderIdsWithoutTreatingPronounAsExplicitId() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> toolContextCaptor = ArgumentCaptor.forClass(Map.class);

        orderStateRepository.rememberExplicitOrderIds("customer-1:session-a", List.of("2024-1234", "2024-1237"));

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(toolContextCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.user("그 주문 취소해주세요")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("주문번호를 다시 확인해 주세요.");

        AssistantService service = new AssistantService(chatClient, conversationIdResolver, orderStateRepository);

        service.assist("session-a", "그 주문 취소해주세요");

        assertThat(toolContextCaptor.getValue())
                .containsEntry(ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of())
                .containsEntry(ToolExecutionPolicy.RECENT_ORDER_IDS, List.of("2024-1234", "2024-1237"))
                .containsEntry(ToolExecutionPolicy.ACTIVE_ORDER_ID, "2024-1237");
    }
}
