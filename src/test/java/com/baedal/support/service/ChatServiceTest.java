package com.baedal.support.service;

import com.baedal.support.handoff.HandoffDetector;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @Test
    void chatReturnsHandoffBeforeCallingLlm() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatService service = new ChatService(chatClient, new HandoffDetector());

        String response = service.chat("상담원 연결해 주세요");

        assertThat(response).contains("1600-0987");
        verify(chatClient, never()).prompt();
    }

    @Test
    void chatReturnsFallbackWhenLlmFails() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("LLM timeout"));
        ChatService service = new ChatService(chatClient, new HandoffDetector());

        String response = service.chat("배달 상태 확인");

        assertThat(response).contains("1600-0987");
        assertThat(response).contains("상담원");
    }
}
