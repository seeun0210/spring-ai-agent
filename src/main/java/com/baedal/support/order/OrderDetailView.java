package com.baedal.support.order;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetailView(
        String orderId,
        OrderStatus status,
        List<OrderMenuView> items,
        int totalPrice,
        OffsetDateTime orderedAt
) {
}
