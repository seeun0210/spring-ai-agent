package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat/stream")
public class StreamingChatController {

    private final ChatClient streamingChatClient;

    public StreamingChatController(
            @Qualifier("streamingChatClient") ChatClient streamingChatClient
    ) {
        this.streamingChatClient = streamingChatClient;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        return streamingChatClient
                .prompt()
                .user(req.message())
                .stream()
                .content();
    }
}
