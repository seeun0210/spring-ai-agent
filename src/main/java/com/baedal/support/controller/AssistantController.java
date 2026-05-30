package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final ChatClient syncChatClient;

    public AssistantController(@Qualifier("syncChatClient") ChatClient syncChatClient) {
        this.syncChatClient = syncChatClient;
    }

    @PostMapping
    public String assist(@Valid @RequestBody ChatRequest request) {
        return syncChatClient
                .prompt()
                .user(request.message())
                .call()
                .content();
    }
}
