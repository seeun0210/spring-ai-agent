package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import com.baedal.support.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public String chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.message());
    }
}
