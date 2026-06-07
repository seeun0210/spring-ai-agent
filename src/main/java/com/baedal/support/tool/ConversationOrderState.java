package com.baedal.support.tool;

import java.util.LinkedHashSet;
import java.util.List;

public record ConversationOrderState(
        String conversationId,
        String activeOrderId,
        List<String> recentOrderIds,
        String pendingCancelOrderId
) {

    public ConversationOrderState {
        recentOrderIds = recentOrderIds == null ? List.of() : List.copyOf(recentOrderIds);
    }

    public static ConversationOrderState empty(String conversationId) {
        return new ConversationOrderState(conversationId, null, List.of(), null);
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

        return new ConversationOrderState(conversationId, orderIds.get(orderIds.size() - 1), recent, pendingCancelOrderId);
    }

    ConversationOrderState withPendingCancel(String orderId) {
        return new ConversationOrderState(conversationId, activeOrderId, recentOrderIds, orderId);
    }

    ConversationOrderState clearPendingCancel() {
        return new ConversationOrderState(conversationId, activeOrderId, recentOrderIds, null);
    }
}
