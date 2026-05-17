package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

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
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest req) {
        return streamingChatClient
                .prompt()
                .user(req.message())
                .stream()
                .content()
                .timeout(Duration.ofSeconds(60))
                .onErrorMap(
                        TimeoutException.class,
                        ex -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "stream timeout", ex)
                );
    }
}
