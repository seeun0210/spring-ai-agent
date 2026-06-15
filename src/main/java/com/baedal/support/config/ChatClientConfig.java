package com.baedal.support.config;

import com.baedal.support.advisor.PerformanceLoggingAdvisor;
import com.baedal.support.advisor.PolicyValidationAdvisor;
import com.baedal.support.guardrail.InputGuardrailAdvisor;
import com.baedal.support.guardrail.LlmInputGuardrailAdvisor;
import com.baedal.support.guardrail.OutputGuardrailAdvisor;
import com.baedal.support.order.OrderTools;
import com.baedal.support.prompt.BaedalPrompt;
import com.baedal.support.rag.RagRetrievalLoggingAdvisor;
import com.baedal.support.tool.GuardedToolCallbacks;
import com.baedal.support.tool.ToolExecutionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {
    private final ChatClient.Builder builder;

    @Value("${baedal.assistant.advisors:memory-rag}")
    private String assistantAdvisorMode;

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
            InputGuardrailAdvisor inputGuardrailAdvisor,
            LlmInputGuardrailAdvisor llmInputGuardrailAdvisor,
            OutputGuardrailAdvisor outputGuardrailAdvisor,
            PolicyValidationAdvisor policyValidationAdvisor,
            @Qualifier("guardedOrderToolCallbacks") ToolCallback[] orderToolCallbacks,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            RagRetrievalLoggingAdvisor ragRetrievalLoggingAdvisor,
            @Qualifier("supportPerformanceLoggingAdvisor") PerformanceLoggingAdvisor performanceLoggingAdvisor
    ) {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultToolCallbacks(orderToolCallbacks)
                .defaultAdvisors(
                        inputGuardrailAdvisor,
                        llmInputGuardrailAdvisor,
                        messageChatMemoryAdvisor,
                        questionAnswerAdvisor,
                        ragRetrievalLoggingAdvisor,
                        policyValidationAdvisor,
                        outputGuardrailAdvisor,
                        performanceLoggingAdvisor
                )
                .build();
    }

    @Bean
    public ChatClient chatClient(
            InputGuardrailAdvisor inputGuardrailAdvisor,
            LlmInputGuardrailAdvisor llmInputGuardrailAdvisor,
            OutputGuardrailAdvisor outputGuardrailAdvisor,
            @Qualifier("chatPerformanceLoggingAdvisor") PerformanceLoggingAdvisor performanceLoggingAdvisor
    ) {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(inputGuardrailAdvisor, llmInputGuardrailAdvisor, outputGuardrailAdvisor, performanceLoggingAdvisor)
                .build();
    }

    @Bean
    public ChatClient syncChatClient(
            InputGuardrailAdvisor inputGuardrailAdvisor,
            LlmInputGuardrailAdvisor llmInputGuardrailAdvisor,
            OutputGuardrailAdvisor outputGuardrailAdvisor,
            @Qualifier("guardedOrderToolCallbacks") ToolCallback[] orderToolCallbacks,
            MessageChatMemoryAdvisor messageChatMemoryAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            RagRetrievalLoggingAdvisor ragRetrievalLoggingAdvisor,
            @Qualifier("assistantPerformanceLoggingAdvisor") PerformanceLoggingAdvisor performanceLoggingAdvisor
    ) {
        ChatClient.Builder assistantBuilder = builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultToolCallbacks(orderToolCallbacks);

        if ("performance-only".equalsIgnoreCase(assistantAdvisorMode)) {
            return assistantBuilder
                    .defaultAdvisors(inputGuardrailAdvisor, llmInputGuardrailAdvisor, outputGuardrailAdvisor, performanceLoggingAdvisor)
                    .build();
        }

        if ("memory-only".equalsIgnoreCase(assistantAdvisorMode)) {
            return assistantBuilder
                    .defaultAdvisors(inputGuardrailAdvisor, llmInputGuardrailAdvisor, messageChatMemoryAdvisor, outputGuardrailAdvisor, performanceLoggingAdvisor)
                    .build();
        }

        return assistantBuilder
                .defaultAdvisors(
                        inputGuardrailAdvisor,
                        llmInputGuardrailAdvisor,
                        messageChatMemoryAdvisor,
                        questionAnswerAdvisor,
                        ragRetrievalLoggingAdvisor,
                        outputGuardrailAdvisor,
                        performanceLoggingAdvisor
                )
                .build();
    }

    @Bean
    public ChatClient streamingChatClient(
            @Qualifier("streamPerformanceLoggingAdvisor") PerformanceLoggingAdvisor performanceLoggingAdvisor
    ) {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceLoggingAdvisor)
                .build();
    }

    @Bean
    public ChatClient promptLabChatClient(
            @Qualifier("promptLabPerformanceLoggingAdvisor") PerformanceLoggingAdvisor performanceLoggingAdvisor
    ) {
        return builder.clone()
                .defaultAdvisors(performanceLoggingAdvisor)
                .build();
    }
}
