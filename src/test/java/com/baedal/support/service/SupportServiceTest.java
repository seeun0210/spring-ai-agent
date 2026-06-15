package com.baedal.support.service;

import com.baedal.support.dto.SupportResponse;
import com.baedal.support.guard.SupportRequestGuard;
import com.baedal.support.handoff.HandoffDetector;
import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.validator.SupportResponseValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportServiceTest {

    @Test
    void triagePassesSessionIdAsChatMemoryConversationId() {
        ChatClient chatClient = mock(ChatClient.class);
        SupportRequestGuard requestGuard = mock(SupportRequestGuard.class);
        SupportResponseValidator responseValidator = mock(SupportResponseValidator.class);
        ConversationIdResolver conversationIdResolver = mock(ConversationIdResolver.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorsCaptor = ArgumentCaptor.forClass(Consumer.class);
        SupportResponse llmResponse = new SupportResponse(
                "주문번호를 알려주세요.",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                "주문번호가 있으면 확인할 수 있습니다.",
                List.of("주문번호"),
                false,
                null
        );

        when(conversationIdResolver.resolve("support-session")).thenReturn("customer-1:support-session");
        when(requestGuard.guard("배달 상태 확인")).thenReturn(Optional.empty());
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.advisors(advisorsCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.user("배달 상태 확인")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(SupportResponse.class)).thenReturn(llmResponse);
        when(responseValidator.validate(llmResponse)).thenReturn(llmResponse);

        SupportService service = new SupportService(
                chatClient,
                requestGuard,
                responseValidator,
                conversationIdResolver,
                new HandoffDetector()
        );

        SupportResponse response = service.triage("support-session", "배달 상태 확인");

        advisorsCaptor.getValue().accept(advisorSpec);
        assertThat(response).isSameAs(llmResponse);
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "customer-1:support-session");
    }
}
