package com.baedal.support.order;

import java.time.OffsetDateTime;

public record CancelOrderResult(
        String orderId,
        CancelOrderOutcome outcome,
        OrderStatus status,
        String message,
        String canceledReason,
        OffsetDateTime canceledAt
) {
}
