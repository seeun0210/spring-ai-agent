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
        neededInfo = neededInfo == null ? List.of() : List.copyOf(neededInfo);

        if (handoffRequired && (handoffReason == null || handoffReason.isBlank())) {
            throw new IllegalArgumentException("handoffRequired=true 일 때 handoffReason은 필수입니다.");
        }

        handoffReason = handoffRequired ? handoffReason.trim() : null;
    }

    public enum Category { ORDER, DELIVERY, CANCEL, REFUND, PAYMENT, ETC }
    public enum Urgency  { LOW, NORMAL, HIGH, CRITICAL }
}
