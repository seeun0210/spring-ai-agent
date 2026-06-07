package com.baedal.support.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "baedal.assistant.order-read")
public class AssistantOrderReadProperties {

    private OrderReadStrategy strategy = OrderReadStrategy.PREFETCH;

    public OrderReadStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(OrderReadStrategy strategy) {
        this.strategy = strategy == null ? OrderReadStrategy.PREFETCH : strategy;
    }
}
