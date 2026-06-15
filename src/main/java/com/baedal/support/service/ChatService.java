package com.baedal.support.service;

import com.baedal.support.handoff.HandoffDecision;
import com.baedal.support.handoff.HandoffDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final HandoffDetector handoffDetector;

    public ChatService(
            @Qualifier("chatClient") ChatClient chatClient,
            HandoffDetector handoffDetector
    ) {
        this.chatClient = chatClient;
        this.handoffDetector = handoffDetector;
    }

    public String chat(String message) {
        return handoffDetector.detect(message)
                .map(HandoffDecision::textMessage)
                .orElseGet(() -> chatWithFallback(message));
    }

    private String chatWithFallback(String message) {
        try {
            return chatClient
                    .prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            log.warn("[ChatService] chat failed. reason={}", ex.getMessage());
            return HandoffDecision.systemFallback().textMessage();
        }
    }
}
