package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient syncChatClient;

    public ChatController(
            @Qualifier("syncChatClient") ChatClient syncChatClient
    ) {
        this.syncChatClient = syncChatClient;
    }

    @PostMapping
    public String chat(@Valid @RequestBody ChatRequest request) {
        return syncChatClient
                .prompt()
                .user(request.message())
                .call()
                .content();
    }
}
