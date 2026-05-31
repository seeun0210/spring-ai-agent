package com.baedal.support.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolExecutionPolicy {

    public static final String CONVERSATION_ID = "conversationId";
    public static final String EXPLICIT_ORDER_IDS = "explicitOrderIds";
    public static final String RECENT_ORDER_IDS = "recentOrderIds";
    public static final String ACTIVE_ORDER_ID = "activeOrderId";
    public static final String CONFIRMED_ORDER_ID = "confirmedOrderId";

    private static final String CANCEL_ORDER = "cancelOrder";

    private final ConversationOrderStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    public ToolExecutionPolicy(ConversationOrderStateRepository stateRepository, ObjectMapper objectMapper) {
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
    }

    public ToolPolicyDecision check(String toolName, String toolInput, ToolContext toolContext) {
        if (!CANCEL_ORDER.equals(toolName)) {
            return ToolPolicyDecision.allow();
        }

        Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
        String orderId = readOrderId(toolInput);
        String conversationId = valueAsString(context.get(CONVERSATION_ID));
        List<String> explicitOrderIds = valueAsStringList(context.get(EXPLICIT_ORDER_IDS));
        String confirmedOrderId = valueAsString(context.get(CONFIRMED_ORDER_ID));

        if (orderId != null && explicitOrderIds.contains(orderId)) {
            stateRepository.clearPendingCancel(conversationId);
            return ToolPolicyDecision.allow();
        }

        if (orderId != null && orderId.equals(confirmedOrderId)) {
            stateRepository.clearPendingCancel(conversationId);
            return ToolPolicyDecision.allow();
        }

        log.warn(
                "[ToolPolicy] blocked cancelOrder. conversationId={}, proposedOrderId={}, explicitOrderIds={}, recentOrderIds={}",
                conversationId,
                orderId,
                explicitOrderIds,
                valueAsStringList(context.get(RECENT_ORDER_IDS))
        );
        stateRepository.markPendingCancel(conversationId, orderId);
        return ToolPolicyDecision.block(blockedResult(orderId, valueAsStringList(context.get(RECENT_ORDER_IDS))));
    }

    private String readOrderId(String toolInput) {
        try {
            JsonNode root = objectMapper.readTree(toolInput);
            JsonNode orderId = root.get("orderId");
            if (orderId == null || orderId.asText().isBlank()) {
                return null;
            }
            return orderId.asText().trim();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String blockedResult(String orderId, List<String> candidates) {
        try {
            return objectMapper.writeValueAsString(new BlockedCancelResult(
                    "CONFIRMATION_REQUIRED",
                    orderId,
                    candidates,
                    "취소할 주문번호를 다시 확인해 주세요. 상태 변경 작업은 현재 메시지에 명시된 주문번호가 있거나 확인된 주문에 대해서만 실행할 수 있습니다."
            ));
        } catch (JsonProcessingException ex) {
            return """
                    {"outcome":"CONFIRMATION_REQUIRED","message":"취소할 주문번호를 다시 확인해 주세요."}
                    """;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> valueAsStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private String valueAsString(Object value) {
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        return null;
    }

    private record BlockedCancelResult(
            String outcome,
            String orderId,
            List<String> candidates,
            String message
    ) {
    }
}
