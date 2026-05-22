package com.baedal.support.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderMockService orderMockService;
    private final OrderViewConverter orderViewConverter;

    @Tool(
            name = "getOrderDetail",
            description = "주문번호로 주문 메뉴, 수량, 금액, 주문 상태를 조회합니다."
    )
    public OrderDetailView getOrderDetail(
            @ToolParam(description = "조회할 주문번호") String orderId
    ) {
        log.info("[Tool] getOrderDetail(orderId={})", orderId);

        return orderMockService.findById(orderId)
                .map(orderViewConverter::toDetailView)
                .orElse(null);
    }

    @Tool(
            name = "getDeliveryStatus",
            description = "주문번호로 배달 상태와 라이더 위치를 조회합니다."
    )
    public DeliveryStatusView getDeliveryStatus(
            @ToolParam(description = "조회할 주문번호") String orderId
    ) {
        log.info("[Tool] getDeliveryStatus(orderId={})", orderId);

        return orderMockService.findById(orderId)
                .map(orderViewConverter::toDeliveryStatusView)
                .orElse(null);
    }

    @Tool(
            name = "cancelOrder",
            description = "주문번호와 취소 사유를 받아 주문을 취소하고 결과를 반환합니다."
    )
    public CancelOrderResult cancelOrder(
            @ToolParam(description = "취소할 주문번호") String orderId,
            @ToolParam(description = "고객이 말한 취소 사유") String reason
    ) {
        log.info("[Tool] cancelOrder(orderId={}, reason={})", orderId, reason);

        return orderMockService.findById(orderId)
                .map(order -> cancelExistingOrder(order, reason))
                .orElseGet(() -> new CancelOrderResult(
                        orderId,
                        CancelOrderOutcome.NOT_FOUND,
                        null,
                        "주문을 찾을 수 없습니다.",
                        null,
                        null
                ));
    }

    private CancelOrderResult cancelExistingOrder(Order order, String reason) {
        if (order.getStatus() == OrderStatus.CANCELED) {
            return new CancelOrderResult(
                    order.getOrderId(),
                    CancelOrderOutcome.ALREADY_CANCELED,
                    order.getStatus(),
                    "이미 취소된 주문입니다.",
                    order.getCanceledReason(),
                    order.getCanceledAt()
            );
        }

        if (!order.isCancelable()) {
            return new CancelOrderResult(
                    order.getOrderId(),
                    CancelOrderOutcome.NOT_CANCELABLE,
                    order.getStatus(),
                    "현재 주문 상태에서는 취소할 수 없습니다.",
                    null,
                    null
            );
        }

        String cancelReason = reason == null || reason.isBlank() ? "고객 요청" : reason;
        order.cancel(cancelReason, OffsetDateTime.now());

        return new CancelOrderResult(
                order.getOrderId(),
                CancelOrderOutcome.CANCELED,
                order.getStatus(),
                "주문이 취소되었습니다.",
                order.getCanceledReason(),
                order.getCanceledAt()
        );
    }
}
