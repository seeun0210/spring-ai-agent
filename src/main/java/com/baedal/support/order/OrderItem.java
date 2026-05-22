package com.baedal.support.order;

public record OrderItem(
        String name,
        int quantity,
        int price
) {
}
