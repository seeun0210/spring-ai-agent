package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import com.baedal.support.dto.SupportResponse;
import com.baedal.support.guard.SupportRequestGuard;
import com.baedal.support.validator.SupportResponseValidator;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient supportChatClient;
    private final SupportRequestGuard supportRequestGuard;
    private final SupportResponseValidator supportResponseValidator;

    public SupportController(
            @Qualifier("supportChatClient") ChatClient supportChatClient,
            SupportRequestGuard supportRequestGuard,
            SupportResponseValidator supportResponseValidator
    ) {
        this.supportChatClient = supportChatClient;
        this.supportRequestGuard = supportRequestGuard;
        this.supportResponseValidator = supportResponseValidator;
    }

    @PostMapping
    public SupportResponse triage(@Valid @RequestBody ChatRequest req) {
        return supportRequestGuard.guard(req.message())
                .orElseGet(() -> callLlm(req.message()));
    }

    private SupportResponse callLlm(String message) {
        SupportResponse response = supportChatClient
                .prompt()
                .user(message)
                .call()
                .entity(SupportResponse.class);

        return supportResponseValidator.validate(response);
    }
}
