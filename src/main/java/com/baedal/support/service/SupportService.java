package com.baedal.support.service;

import com.baedal.support.dto.SupportResponse;
import com.baedal.support.guard.SupportRequestGuard;
import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.validator.SupportResponseValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SupportService {

    private final ChatClient supportChatClient;
    private final SupportRequestGuard supportRequestGuard;
    private final SupportResponseValidator supportResponseValidator;
    private final ConversationIdResolver conversationIdResolver;

    public SupportService(
            @Qualifier("supportChatClient") ChatClient supportChatClient,
            SupportRequestGuard supportRequestGuard,
            SupportResponseValidator supportResponseValidator,
            ConversationIdResolver conversationIdResolver
    ) {
        this.supportChatClient = supportChatClient;
        this.supportRequestGuard = supportRequestGuard;
        this.supportResponseValidator = supportResponseValidator;
        this.conversationIdResolver = conversationIdResolver;
    }

    public SupportResponse triage(String sessionId, String message) {
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
