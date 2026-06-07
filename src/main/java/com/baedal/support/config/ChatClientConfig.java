package com.baedal.support.config;

import com.baedal.support.advisor.PerformanceLoggingAdvisor;
import com.baedal.support.advisor.PolicyValidationAdvisor;
import com.baedal.support.order.OrderTools;
import com.baedal.support.prompt.BaedalPrompt;
import com.baedal.support.tool.GuardedToolCallbacks;
import com.baedal.support.tool.ToolExecutionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {
    private final ChatClient.Builder builder;

    @Bean
    public PerformanceLoggingAdvisor supportPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("support");
    }

    @Bean
    public PerformanceLoggingAdvisor promptLabPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("promptLab");
    }

    @Bean
    public PerformanceLoggingAdvisor chatPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("chat");
    }

    @Bean
    public PerformanceLoggingAdvisor assistantPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("assistant");
    }

    @Bean
    public PerformanceLoggingAdvisor streamPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("stream");
    }

    @Bean
    public ChatClient policyValidationChatClient() {
        return builder.clone()
                .build();
    }

    @Bean
    public ToolCallback[] guardedOrderToolCallbacks(
            OrderTools orderTools,
            ToolExecutionPolicy toolExecutionPolicy
    ) {
        return GuardedToolCallbacks.wrap(toolExecutionPolicy, ToolCallbacks.from(orderTools));
    }

    @Bean
    public PolicyValidationAdvisor policyValidationAdvisor(
            @Qualifier("policyValidationChatClient") ChatClient policyValidationChatClient,
            ObjectMapper objectMapper
    ) {
        return new PolicyValidationAdvisor(policyValidationChatClient, objectMapper);
    }

    @Bean
    public ChatClient supportChatClient(
            PolicyValidationAdvisor policyValidationAdvisor,
            @Qualifier("guardedOrderToolCallbacks") ToolCallback[] orderToolCallbacks,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor
    ) {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultToolCallbacks(orderToolCallbacks)
                .defaultAdvisors(messageChatMemoryAdvisor, policyValidationAdvisor, supportPerformanceLoggingAdvisor())
                .build();
    }

    @Bean
    public ChatClient chatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(chatPerformanceLoggingAdvisor())
                .build();
    }

    @Bean
    public ChatClient syncChatClient(
            @Qualifier("guardedOrderToolCallbacks") ToolCallback[] orderToolCallbacks,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor
    ) {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultToolCallbacks(orderToolCallbacks)
                .defaultAdvisors(messageChatMemoryAdvisor, assistantPerformanceLoggingAdvisor())
                .build();
    }

    @Bean
    public ChatClient streamingChatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(streamPerformanceLoggingAdvisor())
                .build();
    }

    @Bean
    public ChatClient promptLabChatClient() {
        return builder.clone()
                .defaultAdvisors(promptLabPerformanceLoggingAdvisor())
                .build();
    }
}
