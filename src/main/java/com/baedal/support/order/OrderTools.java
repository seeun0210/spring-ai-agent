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
    private final CurrentCustomerProvider currentCustomerProvider;

    @Tool(
            name = "getOrderDetail",
            description = "주문번호로 주문 메뉴, 수량, 금액, 주문 상태를 조회합니다."
    )
    public OrderDetailView getOrderDetail(
            @ToolParam(description = "조회할 주문번호") String orderId
    ) {
        log.info("[Tool] getOrderDetail(orderId={})", orderId);

        return orderMockService.findByIdForCustomer(orderId, currentCustomerProvider.currentCustomerId())
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

        return orderMockService.findByIdForCustomer(orderId, currentCustomerProvider.currentCustomerId())
                .map(orderViewConverter::toDeliveryStatusView)
                .orElse(null);
    }

    @Tool(
            name = "cancelOrder",
            description = """
                    고객이 주문 취소 실행을 요청하면 주문번호와 취소 사유를 받아 주문을 취소하고 결과를 반환합니다.
                    "취소해주세요", "취소해줘", "방금 시킨 건 취소"처럼 실행 의도가 명확하면 사유가 없어도 reason을 "고객 요청"으로 넣어 호출합니다.
                    단순히 취소 가능 여부만 묻는 경우에는 이 Tool을 호출하지 않습니다.
                    """
    )
    public CancelOrderResult cancelOrder(
            @ToolParam(description = "취소할 주문번호") String orderId,
            @ToolParam(description = "고객이 말한 취소 사유") String reason
    ) {
        log.info("[Tool] cancelOrder(orderId={}, reason={})", orderId, reason);

        return orderMockService.findByIdForCustomer(orderId, currentCustomerProvider.currentCustomerId())
                .map(order -> {
                    CancelOrderOutcome outcome = order.cancelIfPossible(reason, OffsetDateTime.now());
                    return CancelOrderResult.from(order, outcome);
                })
                .orElseGet(() -> CancelOrderResult.notFound(orderId));
    }
}
