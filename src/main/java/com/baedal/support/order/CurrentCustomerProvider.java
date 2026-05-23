package com.baedal.support.order;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class CurrentCustomerProvider {

    private static final String MOCK_CUSTOMER_HEADER = "X-Customer-Id";

    public String currentCustomerId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new ResponseStatusException(UNAUTHORIZED, "인증 정보가 필요합니다.");
        }

        String customerId = attributes.getRequest().getHeader(MOCK_CUSTOMER_HEADER);
        if (customerId == null || customerId.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "X-Customer-Id 헤더가 필요합니다.");
        }

        return customerId.trim();
    }
}
