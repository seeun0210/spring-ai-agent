package com.baedal.support.order;

import org.springframework.stereotype.Component;

@Component
public class OrderViewConverter {

    public OrderDetailView toDetailView(Order order) {
        return new OrderDetailView(
                order.getOrderId(),
                order.getStatus(),
                order.getItems().stream()
                        .map(item -> new OrderMenuView(item.name(), item.quantity(), item.price()))
                        .toList(),
                order.getTotalPrice(),
                order.getOrderedAt()
        );
    }

    public DeliveryStatusView toDeliveryStatusView(Order order) {
        String message = switch (order.getStatus()) {
            case DELIVERING -> "배달 중입니다.";
            case DELIVERED -> "배달이 완료되었습니다.";
            case CANCELED -> "취소된 주문입니다.";
            case COOKING -> "매장에서 조리 중입니다.";
            case ACCEPTED -> "매장에서 주문을 접수했습니다.";
            case CREATED -> "주문이 생성되어 접수 대기 중입니다.";
        };

        return new DeliveryStatusView(
                order.getOrderId(),
                order.getStatus(),
                order.getRiderLocation(),
                message
        );
    }
}
