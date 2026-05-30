package com.baedal.support.order;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderMockService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        orders.clear();
        OffsetDateTime baseTime = OffsetDateTime.parse("2024-05-17T12:00:00+09:00");

        put(new Order(
                "2024-1234",
                "customer-1",
                OrderStatus.DELIVERING,
                List.of(
                        new OrderItem("허니콤보", 1, 23000),
                        new OrderItem("콜라", 1, 2000)
                ),
                "서울시 강남구 테헤란로 123",
                "역삼역 사거리",
                baseTime.minusMinutes(45)
        ));
        put(new Order(
                "2024-1235",
                "customer-1",
                OrderStatus.CREATED,
                List.of(
                        new OrderItem("떡볶이", 1, 9000),
                        new OrderItem("튀김", 1, 5000)
                ),
                "서울시 강남구 논현로 10",
                null,
                baseTime.minusMinutes(5)
        ));
        put(new Order(
                "2024-1236",
                "customer-1",
                OrderStatus.DELIVERED,
                List.of(
                        new OrderItem("김치찌개", 1, 11000),
                        new OrderItem("공기밥", 1, 1000)
                ),
                "서울시 서초구 서초대로 42",
                null,
                baseTime.minusHours(3)
        ));
        put(new Order(
                "2024-1237",
                "customer-1",
                OrderStatus.COOKING,
                List.of(
                        new OrderItem("불고기버거", 2, 7000),
                        new OrderItem("감자튀김", 1, 3500)
                ),
                "서울시 강남구 선릉로 77",
                null,
                baseTime.minusMinutes(20)
        ));

        Order canceledOrder = new Order(
                "2024-1238",
                "customer-1",
                OrderStatus.ACCEPTED,
                List.of(
                        new OrderItem("마라탕", 1, 15000),
                        new OrderItem("꿔바로우", 1, 12000)
                ),
                "서울시 송파구 올림픽로 1",
                null,
                baseTime.minusMinutes(30)
        );
        canceledOrder.cancelIfPossible("고객 요청", baseTime.minusMinutes(25));
        put(canceledOrder);

        put(new Order(
                "2024-1239",
                "customer-1",
                OrderStatus.ACCEPTED,
                List.of(
                        new OrderItem("초밥 세트", 1, 18000),
                        new OrderItem("미소장국", 1, 1000)
                ),
                "서울시 마포구 양화로 55",
                null,
                baseTime.minusMinutes(10)
        ));

        log.info("OrderMockService seeded — {}건", orders.size());
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public Optional<Order> findByIdForCustomer(String orderId, String customerId) {
        return findById(orderId)
                .filter(order -> order.getCustomerId().equals(customerId));
    }

    private void put(Order order) {
        orders.put(order.getOrderId(), order);
    }
}
