package com.baedal.support.memory;

import com.baedal.support.order.CurrentCustomerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationIdResolver {

    private final CurrentCustomerProvider currentCustomerProvider;

    public String resolve(String sessionId) {
        return currentCustomerProvider.currentCustomerId() + ":" + normalize(sessionId);
    }

    public List<String> sessionIdsForCurrentCustomer(List<String> conversationIds) {
        String prefix = currentCustomerProvider.currentCustomerId() + ":";
        return conversationIds.stream()
                .filter(conversationId -> conversationId.startsWith(prefix))
                .map(conversationId -> conversationId.substring(prefix.length()))
                .toList();
    }

    private String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank()
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : sessionId.trim();
    }
}
