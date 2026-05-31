package com.baedal.support.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ConversationOrderStateRepository {

    private final ConcurrentMap<String, ConversationOrderState> states = new ConcurrentHashMap<>();

    public ConversationOrderState rememberExplicitOrderIds(String conversationId, List<String> orderIds) {
        return states.compute(conversationId, (key, current) -> {
            ConversationOrderState state = current == null ? ConversationOrderState.empty(key) : current;
            return state.withExplicitOrderIds(orderIds);
        });
    }

    public ConversationOrderState get(String conversationId) {
        return states.getOrDefault(conversationId, ConversationOrderState.empty(conversationId));
    }

    public void markPendingCancel(String conversationId, String orderId) {
        if (conversationId == null || orderId == null || orderId.isBlank()) {
            return;
        }
        states.compute(conversationId, (key, current) -> {
            ConversationOrderState state = current == null ? ConversationOrderState.empty(key) : current;
            return state.withPendingCancel(orderId);
        });
    }

    public void clearPendingCancel(String conversationId) {
        if (conversationId == null) {
            return;
        }
        states.computeIfPresent(conversationId, (key, current) -> current.clearPendingCancel());
    }
}
