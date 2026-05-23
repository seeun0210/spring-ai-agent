package com.baedal.support.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void cancelOrderNormalizesBlankReason() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        CancelOrderResult result = orderTools.cancelOrder("2024-1239", "   ");

        assertThat(result.outcome()).isEqualTo(CancelOrderOutcome.CANCELED);
        assertThat(result.canceledReason()).isEqualTo("고객 요청");
        assertThat(cancelHistoryService.findByOrderId("2024-1239"))
                .singleElement()
                .satisfies(history -> assertThat(history.reason()).isEqualTo("고객 요청"));
    }

    @Test
    void cancelOrderTrimsReason() {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");

        CancelOrderResult result = orderTools.cancelOrder("2024-1239", "  고객 변심  ");

        assertThat(result.outcome()).isEqualTo(CancelOrderOutcome.CANCELED);
        assertThat(result.canceledReason()).isEqualTo("고객 변심");
        assertThat(cancelHistoryService.findByOrderId("2024-1239"))
                .singleElement()
                .satisfies(history -> assertThat(history.reason()).isEqualTo("고객 변심"));
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

    @Test
    void cancelOrderCreatesSingleCancelHistoryForConcurrentRepeatedCancel() throws Exception {
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");
        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<CancelOrderResult>> tasks = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            tasks.add(() -> {
                start.await();
                return orderTools.cancelOrder("2024-1239", "동시 취소");
            });
        }

        List<java.util.concurrent.Future<CancelOrderResult>> futures = tasks.stream()
                .map(executor::submit)
                .toList();
        start.countDown();

        List<CancelOrderResult> results = new ArrayList<>();
        for (java.util.concurrent.Future<CancelOrderResult> future : futures) {
            results.add(future.get());
        }
        executor.shutdownNow();

        List<CancelOrderResult> canceled = results.stream()
                .filter(result -> result.outcome() == CancelOrderOutcome.CANCELED)
                .toList();
        List<CancelOrderResult> alreadyCanceled = results.stream()
                .filter(result -> result.outcome() == CancelOrderOutcome.ALREADY_CANCELED)
                .toList();

        assertThat(canceled).hasSize(1);
        assertThat(alreadyCanceled).hasSize(requestCount - 1);
        assertThat(alreadyCanceled)
                .allSatisfy(result -> assertThat(result.cancelId()).isEqualTo(canceled.get(0).cancelId()));
        assertThat(cancelHistoryService.findByOrderId("2024-1239")).hasSize(1);
    }

    @Test
    void cancelHistoryRejectsNullRequestedAt() {
        Order order = new Order(
                "2024-9999",
                "customer-1",
                OrderStatus.ACCEPTED,
                List.of(new OrderItem("김밥", 1, 4000)),
                "서울시 강남구 테헤란로 1",
                null,
                OffsetDateTime.now()
        );

        assertThatNullPointerException()
                .isThrownBy(() -> cancelHistoryService.record(order, "고객 요청", CancelOrderOutcome.CANCELED, null))
                .withMessage("requestedAt must not be null");
    }

    @Test
    void orderDetailViewDefensivelyCopiesItems() {
        List<OrderMenuView> items = new ArrayList<>();
        items.add(new OrderMenuView("김밥", 1, 4000));

        OrderDetailView view = new OrderDetailView(
                "2024-9999",
                OrderStatus.CREATED,
                items,
                4000,
                OffsetDateTime.now()
        );
        items.add(new OrderMenuView("라면", 1, 5000));

        assertThat(view.items()).extracting(OrderMenuView::name).containsExactly("김밥");
        assertThatThrownBy(() -> view.items().add(new OrderMenuView("추가", 1, 1000)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void orderItemRejectsInvalidValues() {
        assertThatThrownBy(() -> new OrderItem(null, 1, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        assertThatThrownBy(() -> new OrderItem(" ", 1, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        assertThatThrownBy(() -> new OrderItem("김밥", 0, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be greater than 0");
        assertThatThrownBy(() -> new OrderItem("김밥", 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("price must be greater than or equal to 0");
    }

    @Test
    void cancelIfPossibleDoesNotBypassDeliveredStatus() {
        Order order = new Order(
                "2024-9999",
                "customer-1",
                OrderStatus.DELIVERED,
                List.of(new OrderItem("김밥", 1, 4000)),
                "서울시 강남구 테헤란로 1",
                null,
                OffsetDateTime.now()
        );

        CancelOrderOutcome outcome = order.cancelIfPossible("고객 요청", OffsetDateTime.now());

        assertThat(outcome).isEqualTo(CancelOrderOutcome.NOT_CANCELABLE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getCanceledAt()).isNull();
    }
}
