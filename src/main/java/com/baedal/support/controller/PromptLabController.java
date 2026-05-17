package com.baedal.support.controller;

import com.baedal.support.dto.SupportResponse;
import com.baedal.support.dto.PromptLabRequest;
import com.baedal.support.prompt.BaedalPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prompt-lab")
public class PromptLabController {

    private final ChatClient promptLabChatClient;

    public PromptLabController(
            @Qualifier("promptLabChatClient") ChatClient promptLabChatClient
    ) {
        this.promptLabChatClient = promptLabChatClient;
    }

    @PostMapping
    public SupportResponse experiment(@RequestBody PromptLabRequest req) {
        String systemPrompt = req.systemPrompt() == null || req.systemPrompt().isBlank()
                ? BaedalPrompt.SYSTEM_PROMPT
                : req.systemPrompt();

        return promptLabChatClient
                .prompt()
                .system(systemPrompt)
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}
