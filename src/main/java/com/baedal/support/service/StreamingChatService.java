package com.baedal.support.service;

import com.baedal.support.guardrail.GuardrailResult;
import com.baedal.support.guardrail.InputGuardrailAdvisor;
import com.baedal.support.handoff.HandoffDecision;
import com.baedal.support.handoff.HandoffDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@Service
public class StreamingChatService {

    private final ChatClient streamingChatClient;
    private final HandoffDetector handoffDetector;
    private final InputGuardrailAdvisor inputGuardrailAdvisor;

    public StreamingChatService(
            @Qualifier("streamingChatClient") ChatClient streamingChatClient,
            HandoffDetector handoffDetector,
            InputGuardrailAdvisor inputGuardrailAdvisor
    ) {
        this.streamingChatClient = streamingChatClient;
        this.handoffDetector = handoffDetector;
        this.inputGuardrailAdvisor = inputGuardrailAdvisor;
    }

    public Flux<String> stream(String message) {
        return handoffDetector.detect(message)
                .map(decision -> Flux.just(decision.textMessage()))
                .orElseGet(() -> streamWithInputGuardrail(message));
    }

    private Flux<String> streamWithInputGuardrail(String message) {
        GuardrailResult guardrailResult = inputGuardrailAdvisor.check(message);
        if (!guardrailResult.allowed()) {
            return Flux.just(guardrailResult.fallbackMessage());
        }

        return streamingChatClient
                .prompt()
                .user(message)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(ex -> {
                    log.warn("[StreamingChatService] stream failed. reason={}", ex.getMessage());
                    return Flux.just(HandoffDecision.systemFallback().textMessage());
                });
    }
}
