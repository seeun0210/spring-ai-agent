package com.baedal.support.service;

import com.baedal.support.handoff.HandoffDecision;
import com.baedal.support.handoff.HandoffDetector;
import com.baedal.support.memory.ConversationIdResolver;
import com.baedal.support.tool.ConversationOrderState;
import com.baedal.support.tool.ConversationOrderStateRepository;
import com.baedal.support.tool.OrderIdExtractor;
import com.baedal.support.tool.ToolExecutionPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AssistantService {

    private final ChatClient syncChatClient;
    private final ConversationIdResolver conversationIdResolver;
    private final ConversationOrderStateRepository orderStateRepository;
    private final AssistantProperties assistantProperties;
    private final AssistantOrderReadProperties orderReadProperties;
    private final OrderReadContextResolver orderReadContextResolver;
    private final HandoffDetector handoffDetector;

    public AssistantService(
            @Qualifier("syncChatClient") ChatClient syncChatClient,
            ConversationIdResolver conversationIdResolver,
            ConversationOrderStateRepository orderStateRepository,
            AssistantProperties assistantProperties,
            AssistantOrderReadProperties orderReadProperties,
            OrderReadContextResolver orderReadContextResolver,
            HandoffDetector handoffDetector
    ) {
        this.syncChatClient = syncChatClient;
        this.conversationIdResolver = conversationIdResolver;
        this.orderStateRepository = orderStateRepository;
        this.assistantProperties = assistantProperties;
        this.orderReadProperties = orderReadProperties;
        this.orderReadContextResolver = orderReadContextResolver;
        this.handoffDetector = handoffDetector;
    }

    public String assist(String sessionId, String message) {
        return handoffDetector.detect(message)
                .map(HandoffDecision::textMessage)
                .orElseGet(() -> assistWithFallback(sessionId, message));
    }

    private String assistWithFallback(String sessionId, String message) {
        try {
            return guardedAssist(sessionId, message);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("[Assistant] assistant failed. sessionId={}, reason={}", sessionId, ex.getMessage());
            return HandoffDecision.systemFallback().textMessage();
        }
    }

    private String guardedAssist(String sessionId, String message) {
        if (assistantProperties.isScopeGuardEnabled()) {
            String scopeFallback = AssistantScopeGuard.fallbackIfOutOfScope(message);
            if (scopeFallback != null) {
                log.info("[Assistant] out-of-scope message blocked. sessionId={}", sessionId);
                return scopeFallback;
            }
        }

        String conversationId = conversationIdResolver.resolve(sessionId);
        log.info("[Assistant] sessionId={}, conversationId={}, message={}", sessionId, conversationId, message);

        List<String> explicitOrderIds = OrderIdExtractor.extract(message);
        ConversationOrderState orderState = orderStateRepository.rememberExplicitOrderIds(conversationId, explicitOrderIds);
        OrderReadResolution orderReadResolution = resolveOrderRead(conversationId, message);
        if (orderReadProperties.getStrategy() == OrderReadStrategy.ROUTER && orderReadResolution.resolved()) {
            log.info("[Assistant] order read routed without LLM. conversationId={}", conversationId);
            return orderReadResolution.directAnswer();
        }

        String userMessage = augmentOrderRead(message, orderReadResolution);
        userMessage = augmentReference(userMessage, conversationId);

        return syncChatClient
                .prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(toolContext(conversationId, explicitOrderIds, orderState))
                .user(userMessage)
                .call()
                .content();
    }

    private OrderReadResolution resolveOrderRead(String conversationId, String message) {
        if (orderReadProperties.getStrategy() == OrderReadStrategy.PROMPT_ONLY) {
            return OrderReadResolution.unresolved();
        }
        return orderReadContextResolver.resolve(conversationId, message);
    }

    private String augmentOrderRead(String message, OrderReadResolution resolution) {
        if (orderReadProperties.getStrategy() != OrderReadStrategy.PREFETCH || !resolution.resolved()) {
            return message;
        }
        return """
                %s

                %s
                """.formatted(message, resolution.context());
    }

    private String augmentReference(String message, String conversationId) {
        if (message == null || !message.contains("배달 중")) {
            return message;
        }

        return orderStateRepository.findSingleOrderIdByStatus(conversationId, "DELIVERING")
                .map(orderId -> """
                        %s

                        [서버 확인]
                        사용자가 말한 "배달 중이던 주문"은 주문번호 %s입니다.
                        """.formatted(message, orderId))
                .orElse(message);
    }

    private Map<String, Object> toolContext(
            String conversationId,
            List<String> explicitOrderIds,
            ConversationOrderState orderState
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(ToolExecutionPolicy.CONVERSATION_ID, conversationId);
        context.put(ToolExecutionPolicy.EXPLICIT_ORDER_IDS, explicitOrderIds);
        context.put(ToolExecutionPolicy.RECENT_ORDER_IDS, orderState.recentOrderIds());
        if (orderState.activeOrderId() != null) {
            context.put(ToolExecutionPolicy.ACTIVE_ORDER_ID, orderState.activeOrderId());
        }
        return context;
    }
}
