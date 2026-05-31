package com.baedal.support.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionPolicyTest {

    private final ConversationOrderStateRepository stateRepository = new ConversationOrderStateRepository();
    private final ToolExecutionPolicy policy = new ToolExecutionPolicy(stateRepository, new ObjectMapper());

    @Test
    void allowsCancelOrderWhenOrderIdIsExplicitInCurrentRequest() {
        ToolPolicyDecision decision = policy.check(
                "cancelOrder",
                "{\"orderId\":\"2024-1235\",\"reason\":\"고객 요청\"}",
                context(Map.of(
                        ToolExecutionPolicy.CONVERSATION_ID, "customer-1:s1",
                        ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of("2024-1235")
                ))
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void blocksCancelOrderWhenLlmInfersOrderIdWithoutExplicitOrConfirmedOrder() {
        ToolPolicyDecision decision = policy.check(
                "cancelOrder",
                "{\"orderId\":\"2024-1237\",\"reason\":\"고객 요청\"}",
                context(Map.of(
                        ToolExecutionPolicy.CONVERSATION_ID, "customer-1:s1",
                        ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of(),
                        ToolExecutionPolicy.RECENT_ORDER_IDS, List.of("2024-1234", "2024-1237")
                ))
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.toolResultJson())
                .contains("\"outcome\":\"CONFIRMATION_REQUIRED\"")
                .contains("2024-1237");
        assertThat(stateRepository.get("customer-1:s1").pendingCancelOrderId()).isEqualTo("2024-1237");
    }

    @Test
    void allowsCancelOrderWhenOrderWasConfirmedByServerState() {
        stateRepository.markPendingCancel("customer-1:s1", "2024-1237");

        ToolPolicyDecision decision = policy.check(
                "cancelOrder",
                "{\"orderId\":\"2024-1237\",\"reason\":\"고객 요청\"}",
                context(Map.of(
                        ToolExecutionPolicy.CONVERSATION_ID, "customer-1:s1",
                        ToolExecutionPolicy.EXPLICIT_ORDER_IDS, List.of(),
                        ToolExecutionPolicy.CONFIRMED_ORDER_ID, "2024-1237"
                ))
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(stateRepository.get("customer-1:s1").pendingCancelOrderId()).isNull();
    }

    @Test
    void allowsReadOnlyToolsWithoutPolicyCheck() {
        ToolPolicyDecision decision = policy.check(
                "getDeliveryStatus",
                "{\"orderId\":\"2024-1237\"}",
                context(Map.of())
        );

        assertThat(decision.allowed()).isTrue();
    }

    private ToolContext context(Map<String, Object> values) {
        return new ToolContext(values);
    }
}
