package com.baedal.support.dto;

import java.util.List;

public record SupportResponse(
        String summary,
        Category category,
        Urgency urgency,
        String nextAction,
        List<String> neededInfo,
        boolean handoffRequired,
        String handoffReason
) {
    public SupportResponse {
        summary = trimToNull(summary);
        nextAction = trimToNull(nextAction);
        neededInfo = neededInfo == null ? List.of() : List.copyOf(neededInfo);
        handoffReason = handoffRequired ? trimToNull(handoffReason) : null;
    }

    public enum Category { ORDER, DELIVERY, CANCEL, REFUND, PAYMENT, ETC }
    public enum Urgency  { LOW, NORMAL, HIGH, CRITICAL }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
