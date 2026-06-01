package com.baedal.support.memory;

import com.baedal.support.tool.ConversationOrderStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/session")
public class SessionController {

    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ConversationIdResolver conversationIdResolver;
    private final ConversationOrderStateRepository orderStateRepository;

    @GetMapping("/{sessionId}/messages")
    public List<MessageView> messages(@PathVariable String sessionId) {
        return chatMemory.get(conversationIdResolver.resolve(sessionId))
                .stream()
                .map(MessageView::from)
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public void clear(@PathVariable String sessionId) {
        String conversationId = conversationIdResolver.resolve(sessionId);
        chatMemory.clear(conversationId);
        orderStateRepository.clear(conversationId);
        log.info("[Session] clear sessionId={}", sessionId);
    }

    @GetMapping("/ids")
    public List<String> sessions() {
        return conversationIdResolver.sessionIdsForCurrentCustomer(chatMemoryRepository.findConversationIds());
    }

    public record MessageView(String type, String content) {
        static MessageView from(Message message) {
            return new MessageView(message.getMessageType().name(), message.getText());
        }
    }
}
