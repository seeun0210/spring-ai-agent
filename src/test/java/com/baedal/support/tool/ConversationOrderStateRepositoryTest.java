package com.baedal.support.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationOrderStateRepositoryTest {

    private final ConversationOrderStateRepository repository = new ConversationOrderStateRepository();

    @Test
    void findsSingleOrderByObservedStatus() {
        repository.rememberObservedOrderStatus("customer-1:s1", "2024-1234", "DELIVERING");
        repository.rememberObservedOrderStatus("customer-1:s1", "2024-1235", "CREATED");

        assertThat(repository.findSingleOrderIdByStatus("customer-1:s1", "DELIVERING"))
                .contains("2024-1234");
    }

    @Test
    void doesNotResolveWhenMultipleOrdersShareStatus() {
        repository.rememberObservedOrderStatus("customer-1:s1", "2024-1234", "DELIVERING");
        repository.rememberObservedOrderStatus("customer-1:s1", "2024-1236", "DELIVERING");

        assertThat(repository.findSingleOrderIdByStatus("customer-1:s1", "DELIVERING"))
                .isEmpty();
    }
}
