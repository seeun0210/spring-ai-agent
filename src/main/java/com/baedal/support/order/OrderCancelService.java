package com.baedal.support.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderMockService orderMockService;
    private final CancelHistoryService cancelHistoryService;

    public CancelOrderResult cancel(String orderId, String customerId, String reason) {
        return orderMockService.findByIdForCustomer(orderId, customerId)
                .map(order -> cancelExistingOrder(order, reason))
                .orElseGet(() -> CancelOrderResult.notFound(orderId));
    }

    private CancelOrderResult cancelExistingOrder(Order order, String reason) {
        OffsetDateTime requestedAt = OffsetDateTime.now();
        CancelOrderOutcome outcome = order.cancelIfPossible(reason, requestedAt);

        CancelHistory history = switch (outcome) {
            case CANCELED -> cancelHistoryService.record(order, reason, outcome, requestedAt);
            case ALREADY_CANCELED -> cancelHistoryService.findLatestCanceled(order.getOrderId(), order.getCustomerId())
                    .orElse(null);
            case NOT_CANCELABLE, NOT_FOUND -> null;
        };

        return CancelOrderResult.from(order, outcome, history);
    }
}
