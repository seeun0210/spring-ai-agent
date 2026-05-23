package com.baedal.support.order;

public record OrderItem(
        String name,
        int quantity,
        int price
) {
    public OrderItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be greater than or equal to 0");
        }
        name = name.trim();
    }
}
