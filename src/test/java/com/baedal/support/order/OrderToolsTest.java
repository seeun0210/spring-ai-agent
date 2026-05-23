package com.baedal.support.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderToolsTest {

    private final CurrentCustomerProvider currentCustomerProvider = mock(CurrentCustomerProvider.class);
    private CancelHistoryService cancelHistoryService;
    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        OrderMockService orderMockService = new OrderMockService();
        orderMockService.seed();
        cancelHistoryService = new CancelHistoryService();
        OrderCancelService orderCancelService = new OrderCancelService(orderMockService, cancelHistoryService);
        orderTools = new OrderTools(orderMockService, new OrderViewConverter(), currentCustomerProvider, orderCancelService);
    }

    @Test
    void getOrderDetailReturnsOrderWhenCustomerOwnsOrder() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        OrderDetailView detail = orderTools.getOrderDetail("2024-1234");

        assertThat(detail).isNotNull();
        assertThat(detail.orderId()).isEqualTo("2024-1234");
        assertThat(detail.items())
                .extracting(OrderMenuView::name)
                .contains("허니콤보", "콜라");
    }

    @Test
    void getOrderDetailReturnsNullWhenCustomerDoesNotOwnOrder() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-2");

        OrderDetailView detail = orderTools.getOrderDetail("2024-1234");

        assertThat(detail).isNull();
    }

    @Test
    void cancelOrderDoesNotRevealOrderOwnedByAnotherCustomer() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-2");

        CancelOrderResult result = orderTools.cancelOrder("2024-1235", "고객 요청");

        assertThat(result.outcome()).isEqualTo(CancelOrderOutcome.NOT_FOUND);
        assertThat(result.status()).isNull();
    }

    @Test
    void cancelOrderReturnsAlreadyCanceledForOwnedCanceledOrder() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        CancelOrderResult result = orderTools.cancelOrder("2024-1238", "다시 취소");

        assertThat(result.outcome()).isEqualTo(CancelOrderOutcome.ALREADY_CANCELED);
        assertThat(result.cancelId()).isNull();
        assertThat(result.canceledReason()).isEqualTo("고객 요청");
        assertThat(result.canceledAt()).isNotNull();
    }

    @Test
    void cancelOrderRecordsCancelHistoryOnFirstCancel() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        CancelOrderResult result = orderTools.cancelOrder("2024-1239", "고객 요청");

        assertThat(result.outcome()).isEqualTo(CancelOrderOutcome.CANCELED);
        assertThat(result.cancelId()).isNotBlank();
        assertThat(cancelHistoryService.findByOrderId("2024-1239"))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.cancelId().toString()).isEqualTo(result.cancelId());
                    assertThat(history.reason()).isEqualTo("고객 요청");
                    assertThat(history.outcome()).isEqualTo(CancelOrderOutcome.CANCELED);
                });
    }

    @Test
    void cancelOrderReusesFirstCancelHistoryForRepeatedCancel() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        CancelOrderResult first = orderTools.cancelOrder("2024-1239", "고객 요청");
        CancelOrderResult second = orderTools.cancelOrder("2024-1239", "테스트 재요청");

        assertThat(second.outcome()).isEqualTo(CancelOrderOutcome.ALREADY_CANCELED);
        assertThat(second.cancelId()).isEqualTo(first.cancelId());
        assertThat(second.canceledReason()).isEqualTo(first.canceledReason());
        assertThat(second.canceledAt()).isEqualTo(first.canceledAt());
        assertThat(cancelHistoryService.findByOrderId("2024-1239")).hasSize(1);
    }
}
