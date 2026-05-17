package com.baedal.support;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {
    private final ChatClient.Builder builder;
    private final PerformanceLoggingAdvisor performanceLoggingAdvisor;

    @Bean
    public ChatClient supportChatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceLoggingAdvisor)
                .build();
    }

    @Bean
    public ChatClient streamingChatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .build();
    }
}
