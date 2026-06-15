package com.baedal.support.guardrail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GuardrailProperties.class)
public class GuardrailConfig {

    @Bean
    public InputGuardrailAdvisor inputGuardrailAdvisor(GuardrailProperties properties) {
        return new InputGuardrailAdvisor(properties);
    }

    @Bean
    public ChatClient guardrailClassifierChatClient(ChatClient.Builder builder) {
        return builder.clone().build();
    }

    @Bean
    public LlmInputGuardrailAdvisor llmInputGuardrailAdvisor(
            ChatClient guardrailClassifierChatClient,
            GuardrailProperties properties
    ) {
        return new LlmInputGuardrailAdvisor(guardrailClassifierChatClient, properties);
    }

    @Bean
    public SensitiveDataMasker sensitiveDataMasker() {
        return new SensitiveDataMasker();
    }

    @Bean
    public OutputGuardrailAdvisor outputGuardrailAdvisor(
            SensitiveDataMasker sensitiveDataMasker,
            GuardrailProperties properties
    ) {
        return new OutputGuardrailAdvisor(sensitiveDataMasker, properties);
    }
}
