package com.baedal.support.service;

import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.handoff.HandoffDetector;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    @Test
    void assistReturnsScopeFallbackWithoutCallingLlmForClearlyOutOfDomainMessage() {
        ChatClient chatClient = mock(ChatClient.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                null
        );

        String response = service.assist("session-a", "오늘 점심 뭐 먹을까요?");

        assertThat(response).contains("주문/배달/환불/쿠폰 관련 상담");
        verify(chatClient, never()).prompt();
    }

    @Test
    void assistCanDisableScopeGuardForFallbackExperiments() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(org.mockito.ArgumentMatchers.anyMap())).thenReturn(requestSpec);
        when(requestSpec.user("오늘 점심 뭐 먹을까요?")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("LLM 응답");

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                null,
                false
        );

        assertThat(service.assist("session-a", "오늘 점심 뭐 먹을까요?")).isEqualTo("LLM 응답");
    }

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

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                null
        );

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

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                null
        );

        service.assist("session-a", "그 주문 취소해주세요");

        assertThat(toolContextCaptor.getValue())
                .containsEntry(ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of())
                .containsEntry(ToolExecutionPolicy.RECENT_ORDER_IDS, List.of("2024-1234", "2024-1237"))
                .containsEntry(ToolExecutionPolicy.ACTIVE_ORDER_ID, "2024-1237");
    }

    @Test
    void assistAugmentsDeliveringOrderReferenceFromObservedToolState() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> toolContextCaptor = ArgumentCaptor.forClass(Map.class);

        orderStateRepository.rememberExplicitOrderIds("customer-1:session-a", List.of("2024-1234", "2024-1235"));
        orderStateRepository.rememberObservedOrderStatus("customer-1:session-a", "2024-1234", "DELIVERING");
        orderStateRepository.rememberObservedOrderStatus("customer-1:session-a", "2024-1235", "CREATED");

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(toolContextCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.user(org.mockito.ArgumentMatchers.contains("2024-1234"))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("2024-1234 환불 안내");

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                null
        );

        String response = service.assist("session-a", "아까 배달 중이던 주문 환불 돼요?");

        assertThat(response).isEqualTo("2024-1234 환불 안내");
        assertThat(toolContextCaptor.getValue().get(ToolExecutionPolicy.RECENT_ORDER_IDS))
                .asList()
                .contains("2024-1234", "2024-1235");
    }

    @Test
    void promptOnlyStrategyDoesNotPrefetchExplicitReadQuestion() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(org.mockito.ArgumentMatchers.anyMap())).thenReturn(requestSpec);
        when(requestSpec.user("2024-1235 주문은 뭐 시킨 거예요?")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("LLM 응답");

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PROMPT_ONLY,
                mock(OrderReadContextResolver.class)
        );

        assertThat(service.assist("session-a", "2024-1235 주문은 뭐 시킨 거예요?")).isEqualTo("LLM 응답");
    }

    @Test
    void prefetchStrategyAugmentsExplicitReadQuestion() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();
        OrderReadContextResolver orderReadContextResolver = mock(OrderReadContextResolver.class);

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(orderReadContextResolver.resolve("customer-1:session-a", "2024-1235 주문은 뭐 시킨 거예요?"))
                .thenReturn(new OrderReadResolution(true, "[서버 확인]\n주문번호 2024-1235 메뉴: 떡볶이 1개", "직접 응답"));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(org.mockito.ArgumentMatchers.anyMap())).thenReturn(requestSpec);
        when(requestSpec.user(org.mockito.ArgumentMatchers.contains("주문번호 2024-1235 메뉴"))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("LLM 응답");

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.PREFETCH,
                orderReadContextResolver
        );

        assertThat(service.assist("session-a", "2024-1235 주문은 뭐 시킨 거예요?")).isEqualTo("LLM 응답");
    }

    @Test
    void routerStrategyReturnsDirectAnswerForExplicitReadQuestion() {
        ChatClient chatClient = mock(ChatClient.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ConversationOrderStateRepository orderStateRepository = new ConversationOrderStateRepository();
        OrderReadContextResolver orderReadContextResolver = mock(OrderReadContextResolver.class);

        when(conversationIdResolver.resolve("session-a")).thenReturn("customer-1:session-a");
        when(orderReadContextResolver.resolve("customer-1:session-a", "2024-1235 주문은 뭐 시킨 거예요?"))
                .thenReturn(new OrderReadResolution(true, "[서버 확인]", "주문번호 2024-1235는 떡볶이 1개입니다."));

        AssistantService service = service(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                OrderReadStrategy.ROUTER,
                orderReadContextResolver
        );

        assertThat(service.assist("session-a", "2024-1235 주문은 뭐 시킨 거예요?"))
                .contains("2024-1235", "떡볶이 1개");
        verify(chatClient, never()).prompt();
    }

    private AssistantService service(
            ChatClient chatClient,
            ConversationIdResolver conversationIdResolver,
            ConversationOrderStateRepository orderStateRepository,
            OrderReadStrategy strategy,
            OrderReadContextResolver orderReadContextResolver
    ) {
        AssistantOrderReadProperties properties = new AssistantOrderReadProperties();
        properties.setStrategy(strategy);
        if (orderReadContextResolver == null) {
            orderReadContextResolver = mock(OrderReadContextResolver.class);
            when(orderReadContextResolver.resolve(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString()
            )).thenReturn(OrderReadResolution.unresolved());
        }
        return new AssistantService(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                new AssistantProperties(),
                properties,
                orderReadContextResolver,
                new HandoffDetector()
        );
    }

    private AssistantService service(
            ChatClient chatClient,
            ConversationIdResolver conversationIdResolver,
            ConversationOrderStateRepository orderStateRepository,
            OrderReadStrategy strategy,
            OrderReadContextResolver orderReadContextResolver,
            boolean scopeGuardEnabled
    ) {
        AssistantOrderReadProperties orderReadProperties = new AssistantOrderReadProperties();
        orderReadProperties.setStrategy(strategy);
        AssistantProperties assistantProperties = new AssistantProperties();
        assistantProperties.setScopeGuardEnabled(scopeGuardEnabled);
        if (orderReadContextResolver == null) {
            orderReadContextResolver = mock(OrderReadContextResolver.class);
            when(orderReadContextResolver.resolve(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString()
            )).thenReturn(OrderReadResolution.unresolved());
        }
        return new AssistantService(
                chatClient,
                conversationIdResolver,
                orderStateRepository,
                assistantProperties,
                orderReadProperties,
                orderReadContextResolver,
                new HandoffDetector()
        );
    }
}
