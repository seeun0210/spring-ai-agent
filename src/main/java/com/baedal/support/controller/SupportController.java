package com.baedal.support.controller;

import com.baedal.support.dto.ChatRequest;
import com.baedal.support.dto.SupportResponse;
import com.baedal.support.validator.SupportResponseValidator;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient supportChatClient;
    private final SupportResponseValidator supportResponseValidator;

    public SupportController(
            @Qualifier("supportChatClient") ChatClient supportChatClient,
            SupportResponseValidator supportResponseValidator
    ) {
        this.supportChatClient = supportChatClient;
        this.supportResponseValidator = supportResponseValidator;
    }

    @PostMapping
    public SupportResponse triage(@Valid @RequestBody ChatRequest req) {
        SupportResponse response = supportChatClient
                .prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);

        return supportResponseValidator.validate(response);
    }
}
