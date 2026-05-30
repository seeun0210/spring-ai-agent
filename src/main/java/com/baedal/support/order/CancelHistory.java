package com.baedal.support.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancelHistory(
        UUID cancelId,
        String orderId,
        String customerId,
        String reason,
        CancelOrderOutcome outcome,
        OffsetDateTime requestedAt
) {
}
