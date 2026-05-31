package com.baedal.support.memory;

import com.baedal.support.order.CurrentCustomerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationIdResolverTest {

    private final ConversationIdResolver resolver = new ConversationIdResolver(new CurrentCustomerProvider());

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesConversationIdWithCurrentCustomerId() {
        setCurrentCustomer("customer-1");

        String conversationId = resolver.resolve("cust-A");

        assertThat(conversationId).isEqualTo("customer-1:cust-A");
    }

    @Test
    void fallsBackToDefaultSessionIdWhenBlank() {
        setCurrentCustomer("customer-1");

        String conversationId = resolver.resolve(" ");

        assertThat(conversationId).isEqualTo("customer-1:default");
    }

    @Test
    void returnsOnlyCurrentCustomerSessionIds() {
        setCurrentCustomer("customer-1");

        List<String> sessionIds = resolver.sessionIdsForCurrentCustomer(List.of(
                "customer-1:cust-A",
                "customer-2:cust-A",
                "customer-1:cust-B"
        ));

        assertThat(sessionIds).containsExactly("cust-A", "cust-B");
    }

    private void setCurrentCustomer(String customerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Customer-Id", customerId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
