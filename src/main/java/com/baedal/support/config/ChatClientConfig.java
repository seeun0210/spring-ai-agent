package com.baedal.support.config;

import com.baedal.support.advisor.PerformanceLoggingAdvisor;
import com.baedal.support.prompt.BaedalPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
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
    public PerformanceLoggingAdvisor streamPerformanceLoggingAdvisor() {
        return new PerformanceLoggingAdvisor("stream");
    }

    @Bean
    public ChatClient supportChatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(supportPerformanceLoggingAdvisor())
                .build();
    }

    @Bean
    public ChatClient syncChatClient() {
        return builder.clone()
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(chatPerformanceLoggingAdvisor())
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
