package com.baedal.support.service;

import com.baedal.support.order.CurrentCustomerProvider;
import com.baedal.support.order.DeliveryStatusView;
import com.baedal.support.order.Order;
import com.baedal.support.order.OrderDetailView;
import com.baedal.support.order.OrderMenuView;
import com.baedal.support.order.OrderMockService;
import com.baedal.support.order.OrderViewConverter;
import com.baedal.support.tool.ConversationOrderStateRepository;
import com.baedal.support.tool.OrderIdExtractor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class OrderReadContextResolver {

    private final OrderMockService orderMockService;
    private final OrderViewConverter orderViewConverter;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final ConversationOrderStateRepository stateRepository;

    public OrderReadContextResolver(
            OrderMockService orderMockService,
            OrderViewConverter orderViewConverter,
            CurrentCustomerProvider currentCustomerProvider,
            ConversationOrderStateRepository stateRepository
    ) {
        this.orderMockService = orderMockService;
        this.orderViewConverter = orderViewConverter;
        this.currentCustomerProvider = currentCustomerProvider;
        this.stateRepository = stateRepository;
    }

    public OrderReadResolution resolve(String conversationId, String message) {
        List<String> orderIds = OrderIdExtractor.extract(message);
        if (orderIds.size() != 1 || !isReadOnlyIntent(message)) {
            return OrderReadResolution.unresolved();
        }

        String orderId = orderIds.get(0);
        return orderMockService.findByIdForCustomer(orderId, currentCustomerProvider.currentCustomerId())
                .map(order -> resolveOrder(conversationId, order, message))
                .orElse(OrderReadResolution.unresolved());
    }

    private OrderReadResolution resolveOrder(String conversationId, Order order, String message) {
        if (isDeliveryIntent(message)) {
            DeliveryStatusView view = orderViewConverter.toDeliveryStatusView(order);
            stateRepository.rememberObservedOrderStatus(conversationId, view.orderId(), view.status().name());
            return new OrderReadResolution(true, deliveryContext(view), deliveryAnswer(view));
        }

        OrderDetailView view = orderViewConverter.toDetailView(order);
        stateRepository.rememberObservedOrderStatus(conversationId, view.orderId(), view.status().name());
        return new OrderReadResolution(true, detailContext(view), detailAnswer(view));
    }

    private boolean isReadOnlyIntent(String message) {
        return !containsAny(message, List.of("취소", "환불", "보상", "쿠폰"))
                && (isDeliveryIntent(message) || isDetailIntent(message));
    }

    private boolean isDeliveryIntent(String message) {
        return containsAny(message, List.of("배달", "어디", "도착", "라이더", "상태"));
    }

    private boolean isDetailIntent(String message) {
        return containsAny(message, List.of("뭐 시킨", "무엇을", "메뉴", "주문 내역", "상세", "금액", "얼마"));
    }

    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

    private String deliveryContext(DeliveryStatusView view) {
        return """
                [서버 확인 - 주문 배달 상태]
                주문번호: %s
                주문 상태: %s
                라이더 위치: %s
                상태 메시지: %s
                """.formatted(
                view.orderId(),
                view.status(),
                valueOrNone(view.riderLocation()),
                view.message()
        );
    }

    private String deliveryAnswer(DeliveryStatusView view) {
        return "주문번호 %s의 현재 상태는 %s %s%s".formatted(
                view.orderId(),
                sentence(view.message()),
                view.riderLocation() == null ? "" : "라이더는 현재 " + view.riderLocation() + "에 있습니다. ",
                "추가 확인이 필요하면 주문번호와 문의 내용을 남겨 주세요."
        ).trim();
    }

    private String detailContext(OrderDetailView view) {
        return """
                [서버 확인 - 주문 상세]
                주문번호: %s
                주문 상태: %s
                주문 메뉴: %s
                총 금액: %s원
                """.formatted(
                view.orderId(),
                view.status(),
                menuSummary(view.items()),
                won(view.totalPrice())
        );
    }

    private String detailAnswer(OrderDetailView view) {
        return "주문번호 %s는 %s를 주문하셨습니다. 총 금액은 %s원입니다.".formatted(
                view.orderId(),
                menuSummary(view.items()),
                won(view.totalPrice())
        );
    }

    private String menuSummary(List<OrderMenuView> items) {
        return items.stream()
                .map(item -> item.name() + " " + item.quantity() + "개")
                .toList()
                .stream()
                .reduce((left, right) -> left + ", " + right)
                .orElse("없음");
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "없음" : value;
    }

    private String won(int value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private String sentence(String value) {
        if (value == null || value.isBlank()) {
            return "확인이 필요합니다.";
        }
        String trimmed = value.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) {
            return trimmed;
        }
        return trimmed + ".";
    }
}
