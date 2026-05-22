package com.baedal.support.order;

public record DeliveryStatusView(
        String orderId,
        OrderStatus status,
        String riderLocation,
        String message
) {
}
