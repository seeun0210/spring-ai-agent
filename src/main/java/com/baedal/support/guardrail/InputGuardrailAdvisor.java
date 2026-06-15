package com.baedal.support.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class InputGuardrailAdvisor implements CallAdvisor {

    private static final String SCOPE_FALLBACK =
            "고객님, 저는 주문/배달/환불 관련 상담을 도와드리고 있어요. 관련 문의 내용을 입력해 주세요.";
    private static final String TOO_LONG_FALLBACK =
            "고객님, 문의 내용이 너무 길어 한 번에 처리하기 어렵습니다. 핵심 내용만 짧게 나눠서 다시 입력해 주세요.";

    private final GuardrailProperties properties;
    private final List<Pattern> injectionPatterns;

    public InputGuardrailAdvisor() {
        this(new GuardrailProperties());
    }

    public InputGuardrailAdvisor(GuardrailProperties properties) {
        this.properties = properties;
        this.injectionPatterns = properties.allInjectionPatterns()
                .stream()
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public String getName() {
        return "InputGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userInput = extractUserText(request);
        GuardrailResult result = check(userInput);
        if (!result.allowed()) {
            log.warn("[InputGuardrail] blocked. reason={}, inputLength={}",
                    result.reason(),
                    userInput == null ? 0 : userInput.length());
            return shortCircuit(request, result.fallbackMessage());
        }
        return chain.nextCall(request);
    }

    public GuardrailResult check(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.block("EMPTY_INPUT", "고객님, 문의 내용을 입력해 주시면 주문/배달/환불 상담을 도와드릴게요.");
        }
        if (input.length() > properties.getMaxInputChars()) {
            return GuardrailResult.block("INPUT_TOO_LONG", TOO_LONG_FALLBACK);
        }
        if (injectionPatterns.stream().anyMatch(pattern -> pattern.matcher(input).find())) {
            return GuardrailResult.block("PROMPT_INJECTION", SCOPE_FALLBACK);
        }
        return GuardrailResult.allow("OK");
    }

    private String extractUserText(ChatClientRequest request) {
        try {
            List<org.springframework.ai.chat.messages.Message> messages = request.prompt().getInstructions();
            for (int i = messages.size() - 1; i >= 0; i--) {
                org.springframework.ai.chat.messages.Message message = messages.get(i);
                if (message.getMessageType() == MessageType.USER) {
                    return message.getText();
                }
            }
            return request.prompt().getUserMessage() == null ? "" : request.prompt().getUserMessage().getText();
        } catch (RuntimeException e) {
            log.debug("[InputGuardrail] failed to extract user input. reason={}", e.getMessage());
            return "";
        }
    }

    private ChatClientResponse shortCircuit(ChatClientRequest request, String fallbackMessage) {
        AssistantMessage message = new AssistantMessage(fallbackMessage);
        Generation generation = new Generation(message);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(request.context())
                .build();
    }
}
