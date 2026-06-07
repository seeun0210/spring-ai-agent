package com.baedal.support.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConversationOrderState(
        String conversationId,
        String activeOrderId,
        List<String> recentOrderIds,
        String pendingCancelOrderId,
        Map<String, String> observedOrderStatuses
) {

    public ConversationOrderState {
        recentOrderIds = recentOrderIds == null ? List.of() : List.copyOf(recentOrderIds);
        observedOrderStatuses = observedOrderStatuses == null ? Map.of() : Map.copyOf(observedOrderStatuses);
    }

    public ConversationOrderState(
            String conversationId,
            String activeOrderId,
            List<String> recentOrderIds,
            String pendingCancelOrderId
    ) {
        this(conversationId, activeOrderId, recentOrderIds, pendingCancelOrderId, Map.of());
    }

    public static ConversationOrderState empty(String conversationId) {
        return new ConversationOrderState(conversationId, null, List.of(), null, Map.of());
    }

    ConversationOrderState withExplicitOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return this;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>(orderIds);
        merged.addAll(recentOrderIds);
        List<String> recent = merged.stream()
                .limit(5)
                .toList();

        return new ConversationOrderState(
                conversationId,
                orderIds.get(orderIds.size() - 1),
                recent,
                pendingCancelOrderId,
                observedOrderStatuses
        );
    }

    ConversationOrderState withObservedOrderStatus(String orderId, String status) {
        if (orderId == null || orderId.isBlank() || status == null || status.isBlank()) {
            return this;
        }

        LinkedHashMap<String, String> statuses = new LinkedHashMap<>(observedOrderStatuses);
        statuses.put(orderId, status);

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.add(orderId);
        merged.addAll(recentOrderIds);
        List<String> recent = merged.stream()
                .limit(5)
                .toList();

        return new ConversationOrderState(conversationId, orderId, recent, pendingCancelOrderId, statuses);
    }

    ConversationOrderState withPendingCancel(String orderId) {
        return new ConversationOrderState(conversationId, activeOrderId, recentOrderIds, orderId, observedOrderStatuses);
    }

    ConversationOrderState clearPendingCancel() {
        return new ConversationOrderState(conversationId, activeOrderId, recentOrderIds, null, observedOrderStatuses);
    }
}
