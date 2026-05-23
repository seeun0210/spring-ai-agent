package com.baedal.support.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentCustomerProviderTest {

    private final CurrentCustomerProvider currentCustomerProvider = new CurrentCustomerProvider();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsCustomerIdFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Customer-Id", " customer-1 ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String customerId = currentCustomerProvider.currentCustomerId();

        assertThat(customerId).isEqualTo("customer-1");
    }

    @Test
    void throwsUnauthorizedWhenHeaderIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(currentCustomerProvider::currentCustomerId)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void throwsUnauthorizedWhenHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Customer-Id", " ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(currentCustomerProvider::currentCustomerId)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(401));
    }
}
