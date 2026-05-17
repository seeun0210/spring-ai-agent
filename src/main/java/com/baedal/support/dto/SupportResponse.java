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
    public enum Category { ORDER, DELIVERY, CANCEL, REFUND, PAYMENT, ETC }
    public enum Urgency  { LOW, NORMAL, HIGH, CRITICAL }
}
