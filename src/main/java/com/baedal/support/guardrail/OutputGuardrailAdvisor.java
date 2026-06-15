package com.baedal.support.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

@Slf4j
public class OutputGuardrailAdvisor implements CallAdvisor {

    private static final String LEAK_FALLBACK =
            "고객님, 저는 주문/배달/환불 관련 상담을 도와드리고 있어요. 궁금하신 내용을 알려주세요.";
    private static final String EMPTY_FALLBACK =
            "죄송해요, 답변을 준비하는 데 어려움이 있었습니다. 다시 한 번 말씀해 주시거나 상담원 연결을 원하시면 '상담원'이라고 입력해 주세요.";

    private final SensitiveDataMasker masker;
    private final GuardrailProperties properties;

    public OutputGuardrailAdvisor(SensitiveDataMasker masker) {
        this(masker, new GuardrailProperties());
    }

    public OutputGuardrailAdvisor(SensitiveDataMasker masker, GuardrailProperties properties) {
        this.masker = masker;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "OutputGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        String text = contentOf(response);

        if (text == null || text.isBlank()) {
            return replace(response, EMPTY_FALLBACK, "EMPTY_RESPONSE");
        }
        if (containsLeakMarker(text)) {
            return replace(response, LEAK_FALLBACK, "PROMPT_LEAK");
        }
        if (masker.containsSensitive(text)) {
            return replace(response, masker.mask(text), "SENSITIVE_MASKED");
        }
        return response;
    }

    boolean containsLeakMarker(String text) {
        if (text == null) {
            return false;
        }
        return properties.allLeakMarkers().stream().anyMatch(text::contains);
    }

    private String contentOf(ChatClientResponse response) {
        if (response == null
                || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private ChatClientResponse replace(ChatClientResponse response, String newText, String reason) {
        log.info("[OutputGuardrail] response replaced. reason={}", reason);
        ChatResponse originalChatResponse = response == null ? null : response.chatResponse();
        ChatResponse chatResponse = originalChatResponse == null
                ? new ChatResponse(List.of(new Generation(new AssistantMessage(newText))))
                : ChatResponse.builder()
                .from(originalChatResponse)
                .generations(List.of(new Generation(new AssistantMessage(newText))))
                .build();

        if (response == null) {
            return ChatClientResponse.builder()
                    .chatResponse(chatResponse)
                    .build();
        }
        return response.mutate()
                .chatResponse(chatResponse)
                .build();
    }
}
