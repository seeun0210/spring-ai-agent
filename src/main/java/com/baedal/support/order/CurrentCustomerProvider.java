package com.baedal.support.order;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentCustomerProvider {

    private static final String DEFAULT_CUSTOMER_ID = "customer-1";
    private static final String MOCK_CUSTOMER_HEADER = "X-Customer-Id";

    public String currentCustomerId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String customerId = attributes.getRequest().getHeader(MOCK_CUSTOMER_HEADER);
            if (customerId != null && !customerId.isBlank()) {
                return customerId;
            }
        }

        return DEFAULT_CUSTOMER_ID;
    }
}
