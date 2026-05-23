package com.baedal.support.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record OrderDetailView(
        String orderId,
        OrderStatus status,
        List<OrderMenuView> items,
        int totalPrice,
        OffsetDateTime orderedAt
) {
    public OrderDetailView {
        orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        orderedAt = Objects.requireNonNull(orderedAt, "orderedAt must not be null");
    }
}
