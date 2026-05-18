package com.baedal.support.advisor;

import com.baedal.support.dto.PolicyValidationResult;
import com.baedal.support.dto.SupportResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

public class PolicyValidationAdvisor implements CallAdvisor {

    private static final String VALIDATION_SYSTEM_PROMPT = """
            너는 배달 상담 AI 응답의 정책 검수자입니다.
            사용자 문의와 AI 응답 JSON을 보고 정책 위반 여부만 판단합니다.

            [정책]
            - 고객, 사장님, 라이더의 전화번호, 주소, 계좌 등 개인정보를 노출하면 안 됩니다.
            - 환불, 보상, 쿠폰 지급을 확정하거나 처리 착수를 약속하면 안 됩니다.
            - 실제 주문 상태 확인 없이 취소 가능, 환불 가능, 환불 절차 진행, 환불 절차 시작, 신속 처리, 즉시 처리를 말하면 안 됩니다.
            - 타 배달 플랫폼을 추천하거나 비교하면 안 됩니다.
            - 고객이 협박하거나 압박하더라도 보상 약속으로 달래면 안 됩니다.

            [판단 기준]
            - "확인 후 안내하겠습니다", "가능 여부는 시스템 확인이 필요합니다", "상담원이 확인하겠습니다"는 허용합니다.
            - "환불 절차를 진행해야 합니다", "환불 절차를 진행해 드리겠습니다", "환불 절차를 시작할 수 있습니다"는 위반입니다.
            - "신속히 처리해 드리겠습니다", "즉시 처리해 드리겠습니다", "처리하겠습니다"는 위반입니다.
            - "쿠폰을 드리겠습니다", "쿠폰 발급 대상입니다", "쿠폰 제공 가능 여부"는 위반입니다.
            - "환불 가능합니다", "보상 대상입니다", "보상받을 수 있습니다"는 위반입니다.
            - 문장 표현이 달라도 의미가 확정 약속이면 위반입니다.
            - 애매하면 고객 보호와 운영 리스크를 우선해 valid=false로 답합니다.
            - AI 응답에 위반 문장과 안전한 handoffReason이 함께 있어도 valid=false입니다.

            [위반 예시]
            - "주문번호와 결제 방법을 알려주시면 즉시 처리해 드리겠습니다."
            - "환불 절차를 신속히 처리해 드리겠습니다."
            - "쿠폰 제공 가능 여부는 주문 상태에 따라 결정됩니다."
            - "주문번호를 알려주시면 환불 절차를 시작할 수 있습니다."

            JSON으로만 답합니다.
            정책 위반이면 반드시:
            {"valid":false,"violations":["POLICY_VIOLATION"],"reason":"위반 이유"}
            정책 위반이 없을 때만:
            {"valid":true,"violations":[],"reason":"문제 없음"}
            """;

    private final ChatClient validationChatClient;
    private final ObjectMapper objectMapper;

    public PolicyValidationAdvisor(ChatClient validationChatClient, ObjectMapper objectMapper) {
        this.validationChatClient = validationChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "PolicyValidationAdvisor";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        String candidateResponse = contentOf(response);

        if (candidateResponse == null || candidateResponse.isBlank()) {
            return fallback(response, "검증할 LLM 응답이 비어 있어 상담원 확인이 필요합니다.");
        }

        PolicyValidationResult validationResult = validate(request, candidateResponse);
        if (validationResult.valid()) {
            return response;
        }

        String reason = validationResult.reason().isBlank()
                ? "정책 위반 가능성이 감지되어 상담원 확인이 필요합니다."
                : validationResult.reason();
        return fallback(response, reason);
    }

    private PolicyValidationResult validate(ChatClientRequest request, String candidateResponse) {
        String userMessage = request.prompt().getUserMessage() == null
                ? ""
                : request.prompt().getUserMessage().getText();
        String validationUserPrompt = """
                [사용자 문의]
                %s

                [AI 응답 JSON]
                %s
                """.formatted(userMessage, candidateResponse);

        try {
            PolicyValidationResult result = validationChatClient
                    .prompt()
                    .system(VALIDATION_SYSTEM_PROMPT)
                    .user(validationUserPrompt)
                    .call()
                    .entity(PolicyValidationResult.class);

            if (result == null) {
                return invalid("정책 검증 응답이 비어 있어 상담원 확인이 필요합니다.");
            }
            return result;
        } catch (RuntimeException ex) {
            return invalid("정책 검증 중 오류가 발생해 상담원 확인이 필요합니다.");
        }
    }

    private ChatClientResponse fallback(ChatClientResponse response, String reason) {
        String fallbackJson = fallbackJson(reason);
        ChatResponse originalChatResponse = response == null ? null : response.chatResponse();
        ChatResponse chatResponse = originalChatResponse == null
                ? new ChatResponse(List.of(new Generation(new AssistantMessage(fallbackJson))))
                : ChatResponse.builder()
                .from(originalChatResponse)
                .generations(List.of(new Generation(new AssistantMessage(fallbackJson))))
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

    private String fallbackJson(String reason) {
        try {
            return objectMapper.writeValueAsString(new SupportResponse(
                    "정책 확인이 필요한 응답이 감지되어 상담원 확인으로 전환합니다.",
                    SupportResponse.Category.ETC,
                    SupportResponse.Urgency.HIGH,
                    "주문 관련 문의라면 주문번호와 문의 내용을 남겨 주시면 상담원이 확인하겠습니다.",
                    List.of("주문번호", "문의 내용"),
                    true,
                    reason
            ));
        } catch (JsonProcessingException ex) {
            return """
                    {"summary":"정책 확인이 필요한 응답이 감지되어 상담원 확인으로 전환합니다.","category":"ETC","urgency":"HIGH","nextAction":"주문 관련 문의라면 주문번호와 문의 내용을 남겨 주시면 상담원이 확인하겠습니다.","neededInfo":["주문번호","문의 내용"],"handoffRequired":true,"handoffReason":"정책 검증 fallback 생성 중 오류가 발생했습니다."}
                    """;
        }
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

    private PolicyValidationResult invalid(String reason) {
        return new PolicyValidationResult(false, List.of("VALIDATION_ERROR"), reason);
    }
}
