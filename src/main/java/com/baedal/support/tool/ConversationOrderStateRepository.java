package com.baedal.support.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public ConversationOrderState rememberObservedOrderStatus(String conversationId, String orderId, String status) {
        if (conversationId == null || orderId == null || orderId.isBlank() || status == null || status.isBlank()) {
            return get(conversationId);
        }
        return states.compute(conversationId, (key, current) -> {
            ConversationOrderState state = current == null ? ConversationOrderState.empty(key) : current;
            return state.withObservedOrderStatus(orderId, status);
        });
    }

    public Optional<String> findSingleOrderIdByStatus(String conversationId, String status) {
        if (conversationId == null || status == null || status.isBlank()) {
            return Optional.empty();
        }

        List<String> matches = get(conversationId).observedOrderStatuses()
                .entrySet()
                .stream()
                .filter(entry -> status.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
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

    public void clear(String conversationId) {
        if (conversationId == null) {
            return;
        }
        states.remove(conversationId);
    }
}
