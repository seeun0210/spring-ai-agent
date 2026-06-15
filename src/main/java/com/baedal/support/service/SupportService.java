package com.baedal.support.service;

import com.baedal.support.dto.SupportResponse;
import com.baedal.support.guard.SupportRequestGuard;
import com.baedal.support.handoff.HandoffDecision;
import com.baedal.support.handoff.HandoffDetector;
import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.validator.SupportResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
public class SupportService {

    private final ChatClient supportChatClient;
    private final SupportRequestGuard supportRequestGuard;
    private final SupportResponseValidator supportResponseValidator;
    private final ConversationIdResolver conversationIdResolver;
    private final HandoffDetector handoffDetector;

    public SupportService(
            @Qualifier("supportChatClient") ChatClient supportChatClient,
            SupportRequestGuard supportRequestGuard,
            SupportResponseValidator supportResponseValidator,
            ConversationIdResolver conversationIdResolver,
            HandoffDetector handoffDetector
    ) {
        this.supportChatClient = supportChatClient;
        this.supportRequestGuard = supportRequestGuard;
        this.supportResponseValidator = supportResponseValidator;
        this.conversationIdResolver = conversationIdResolver;
        this.handoffDetector = handoffDetector;
    }

    public SupportResponse triage(String sessionId, String message) {
        return handoffDetector.detect(message)
                .map(HandoffDecision::supportResponse)
                .orElseGet(() -> triageWithFallback(sessionId, message));
    }

    private SupportResponse triageWithFallback(String sessionId, String message) {
        try {
            return guardedTriage(sessionId, message);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("[SupportService] support triage failed. sessionId={}, reason={}", sessionId, ex.getMessage());
            return HandoffDecision.systemFallback().supportResponse();
        }
    }

    private SupportResponse guardedTriage(String sessionId, String message) {
        return supportRequestGuard.guard(message)
                .orElseGet(() -> callLlm(sessionId, message));
    }

    private SupportResponse callLlm(String sessionId, String message) {
        String conversationId = conversationIdResolver.resolve(sessionId);

        SupportResponse response = supportChatClient
                .prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .call()
                .entity(SupportResponse.class);

        return supportResponseValidator.validate(response);
    }
}
