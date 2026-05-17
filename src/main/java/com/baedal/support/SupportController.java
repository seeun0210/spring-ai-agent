package com.baedal.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient supportChatClient;

    public SupportController(
            @Qualifier("supportChatClient") ChatClient supportChatClient
    ) {
        this.supportChatClient = supportChatClient;
    }

    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return supportChatClient
                .prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}
