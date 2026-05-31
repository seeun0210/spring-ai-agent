package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import com.baedal.support.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping
    public String assist(
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            @Valid @RequestBody ChatRequest request
    ) {
        return assistantService.assist(sessionId, request.message());
    }
}
