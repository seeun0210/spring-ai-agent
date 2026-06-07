package com.baedal.support.service;

import com.baedal.support.order.CurrentCustomerProvider;
import com.baedal.support.order.OrderMockService;
import com.baedal.support.order.OrderViewConverter;
import com.baedal.support.tool.ConversationOrderStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderReadContextResolverTest {

    private final OrderMockService orderMockService = new OrderMockService();
    private final OrderViewConverter orderViewConverter = new OrderViewConverter();
    private final CurrentCustomerProvider currentCustomerProvider = mock(CurrentCustomerProvider.class);
    private final ConversationOrderStateRepository stateRepository = new ConversationOrderStateRepository();

    private OrderReadContextResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        Method seed = OrderMockService.class.getDeclaredMethod("seed");
        seed.setAccessible(true);
        seed.invoke(orderMockService);
        when(currentCustomerProvider.currentCustomerId()).thenReturn("customer-1");
        resolver = new OrderReadContextResolver(
                orderMockService,
                orderViewConverter,
                currentCustomerProvider,
                stateRepository
        );
    }

    @Test
    void resolvesOrderDetailReadQuestion() {
        OrderReadResolution resolution = resolver.resolve("customer-1:s1", "2024-1235 주문은 뭐 시킨 거예요?");

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.context()).contains("2024-1235", "떡볶이 1개", "튀김 1개", "14,000원");
        assertThat(resolution.directAnswer()).contains("2024-1235", "떡볶이 1개", "튀김 1개");
        assertThat(stateRepository.findSingleOrderIdByStatus("customer-1:s1", "CREATED")).contains("2024-1235");
    }

    @Test
    void resolvesDeliveryStatusReadQuestion() {
        OrderReadResolution resolution = resolver.resolve("customer-1:s1", "2024-1234 배달 어디쯤이에요?");

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.context()).contains("2024-1234", "DELIVERING", "역삼역 사거리");
        assertThat(resolution.directAnswer()).contains("2024-1234", "배달 중", "역삼역 사거리");
        assertThat(resolution.directAnswer()).doesNotContain("입니다.입니다.");
        assertThat(stateRepository.findSingleOrderIdByStatus("customer-1:s1", "DELIVERING")).contains("2024-1234");
    }

    @Test
    void ignoresWriteOrPolicyQuestions() {
        assertThat(resolver.resolve("customer-1:s1", "2024-1234 취소해줘").resolved()).isFalse();
        assertThat(resolver.resolve("customer-1:s1", "2024-1234 환불 돼요?").resolved()).isFalse();
    }
}
