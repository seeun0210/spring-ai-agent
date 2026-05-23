package com.baedal.support.order;

import java.time.OffsetDateTime;

public record CancelOrderResult(
        String orderId,
        String cancelId,
        CancelOrderOutcome outcome,
        OrderStatus status,
        String message,
        String canceledReason,
        OffsetDateTime canceledAt
) {

    public static CancelOrderResult notFound(String orderId) {
        return new CancelOrderResult(
                orderId,
                null,
                CancelOrderOutcome.NOT_FOUND,
                null,
                "주문을 찾을 수 없습니다.",
                null,
                null
        );
    }

    public static CancelOrderResult from(Order order, CancelOrderOutcome outcome, CancelHistory history) {
        return switch (outcome) {
            case CANCELED -> new CancelOrderResult(
                    order.getOrderId(),
                    history.cancelId().toString(),
                    outcome,
                    order.getStatus(),
                    "주문이 취소되었습니다.",
                    order.getCanceledReason(),
                    order.getCanceledAt()
            );
            case ALREADY_CANCELED -> new CancelOrderResult(
                    order.getOrderId(),
                    history == null ? null : history.cancelId().toString(),
                    outcome,
                    order.getStatus(),
                    "이미 취소된 주문입니다.",
                    order.getCanceledReason(),
                    order.getCanceledAt()
            );
            case NOT_CANCELABLE -> new CancelOrderResult(
                    order.getOrderId(),
                    null,
                    outcome,
                    order.getStatus(),
                    "현재 주문 상태에서는 취소할 수 없습니다.",
                    null,
                    null
            );
            case NOT_FOUND -> throw new IllegalArgumentException("Use notFound(orderId) for missing orders.");
        };
    }
}
