package com.baedal.support.order;

import java.time.OffsetDateTime;
import java.util.List;

public class Order {

    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private final String deliveryAddress;
    private final OffsetDateTime orderedAt;
    private OrderStatus status;
    private String riderLocation;
    private String canceledReason;
    private OffsetDateTime canceledAt;

    public Order(
            String orderId,
            String customerId,
            OrderStatus status,
            List<OrderItem> items,
            String deliveryAddress,
            String riderLocation,
            OffsetDateTime orderedAt
    ) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.items = List.copyOf(items);
        this.deliveryAddress = deliveryAddress;
        this.riderLocation = riderLocation;
        this.orderedAt = orderedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getRiderLocation() {
        return riderLocation;
    }

    public OffsetDateTime getOrderedAt() {
        return orderedAt;
    }

    public String getCanceledReason() {
        return canceledReason;
    }

    public OffsetDateTime getCanceledAt() {
        return canceledAt;
    }

    public int getTotalPrice() {
        return items.stream()
                .mapToInt(item -> item.price() * item.quantity())
                .sum();
    }

    public boolean isCancelable() {
        return status == OrderStatus.CREATED
                || status == OrderStatus.ACCEPTED
                || status == OrderStatus.COOKING;
    }

    public CancelOrderOutcome cancelIfPossible(String reason, OffsetDateTime canceledAt) {
        if (status == OrderStatus.CANCELED) {
            return CancelOrderOutcome.ALREADY_CANCELED;
        }

        if (!isCancelable()) {
            return CancelOrderOutcome.NOT_CANCELABLE;
        }

        cancel(normalizeCancelReason(reason), canceledAt);
        return CancelOrderOutcome.CANCELED;
    }

    private String normalizeCancelReason(String reason) {
        return reason == null || reason.isBlank() ? "고객 요청" : reason;
    }

    public void updateRiderLocation(String riderLocation) {
        this.riderLocation = riderLocation;
    }

    public void cancel(String reason, OffsetDateTime canceledAt) {
        this.status = OrderStatus.CANCELED;
        this.canceledReason = reason;
        this.canceledAt = canceledAt;
    }
}
