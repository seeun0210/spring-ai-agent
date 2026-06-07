package com.baedal.support.memory;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatMemoryConfig {

    public static final int MAX_MESSAGES = 20;
    private static final String MAX_MESSAGES_PROPERTY = "${baedal.chat-memory.max-messages:20}";

    @Bean
    @Profile("!jdbc")
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(
            ChatMemoryRepository chatMemoryRepository,
            @Value(MAX_MESSAGES_PROPERTY) int maxMessages
    ) {
        return buildChatMemory(chatMemoryRepository, maxMessages);
    }

    ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return buildChatMemory(chatMemoryRepository, MAX_MESSAGES);
    }

    ChatMemory buildChatMemory(ChatMemoryRepository chatMemoryRepository, int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(10)
                .build();
    }
}
