package com.baedal.support.service;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AssistantService {

    private final ChatClient syncChatClient;
    private final ConversationIdResolver conversationIdResolver;
    private final ConversationOrderStateRepository orderStateRepository;

    public AssistantService(
            @Qualifier("syncChatClient") ChatClient syncChatClient,
            ConversationIdResolver conversationIdResolver,
            ConversationOrderStateRepository orderStateRepository
    ) {
        this.syncChatClient = syncChatClient;
        this.conversationIdResolver = conversationIdResolver;
        this.orderStateRepository = orderStateRepository;
    }

    public String assist(String sessionId, String message) {
        String conversationId = conversationIdResolver.resolve(sessionId);
        log.info("[Assistant] sessionId={}, conversationId={}, message={}", sessionId, conversationId, message);

        List<String> explicitOrderIds = OrderIdExtractor.extract(message);
        ConversationOrderState orderState = orderStateRepository.rememberExplicitOrderIds(conversationId, explicitOrderIds);

        return syncChatClient
                .prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(toolContext(conversationId, explicitOrderIds, orderState))
                .user(message)
                .call()
                .content();
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
