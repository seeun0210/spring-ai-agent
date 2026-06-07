package com.baedal.support.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIdExtractorTest {

    @Test
    void extractsDistinctOrderIdsInMentionOrder() {
        assertThat(OrderIdExtractor.extract("2024-1234 말고 2024-1237, 다시 2024-1234"))
                .containsExactly("2024-1234", "2024-1237");
    }

    @Test
    void returnsEmptyListWhenOrderIdIsNotExplicitlyMentioned() {
        assertThat(OrderIdExtractor.extract("그 주문 취소해주세요")).isEmpty();
    }
}
