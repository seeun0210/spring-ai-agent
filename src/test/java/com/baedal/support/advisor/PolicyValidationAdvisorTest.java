package com.baedal.support.advisor;

import com.baedal.support.dto.PolicyValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyValidationAdvisorTest {

    private final ChatClient validationChatClient = mock(ChatClient.class);
    private final PolicyValidationAdvisor advisor = new PolicyValidationAdvisor(validationChatClient, new ObjectMapper());

    @Test
    void replacesResponseWithFallbackWhenPolicyJudgeRejectsOutput() {
        ChatClientRequest request = request("환불 안 해주면 인터넷에 올릴 거야. 쿠폰이라도 줘");
        ChatClientResponse candidateResponse = response("""
                {"summary":"환불 절차를 시작해 드리겠습니다.","category":"REFUND","urgency":"HIGH","nextAction":"주문번호를 알려주세요.","neededInfo":["주문번호"],"handoffRequired":true,"handoffReason":"확인이 필요합니다."}
                """);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(candidateResponse);
        mockValidationResult(new PolicyValidationResult(
                false,
                List.of("REFUND_PROMISE"),
                "실제 주문 상태 확인 없이 환불 절차 시작을 확정했습니다."
        ));

        ChatClientResponse advisedResponse = advisor.adviseCall(request, chain);

        String content = contentOf(advisedResponse);
        assertThat(content).contains("\"category\":\"ETC\"");
        assertThat(content).contains("\"handoffRequired\":true");
        assertThat(content).contains("실제 주문 상태 확인 없이 환불 절차 시작을 확정했습니다.");
    }

    @Test
    void replacesRecentCouponPressureLiveFailureWithFallbackWhenPolicyJudgeRejectsOutput() {
        ChatClientRequest request = request("환불 안 해주면 인터넷에 올릴 거야. 쿠폰이라도 줘");
        ChatClientResponse candidateResponse = response("""
                {"summary":"환불 요청이 있으신데, 주문번호와 결제 방법을 알려주시면 즉시 처리해 드리겠습니다. 인터넷에 올릴 것이라며 위협하는 내용이 있으므로 신속히 확인해 주세요. 쿠폰 제공 가능 여부는 주문 상태에 따라 결정됩니다.","category":"REFUND","urgency":"HIGH","nextAction":"주문번호와 결제 방법을 알려주시면 신속히 처리해 드리겠습니다.","neededInfo":["주문번호","결제방법"],"handoffRequired":true,"handoffReason":"주문 상태 및 결제 정보 확인이 필요합니다."}
                """);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(candidateResponse);
        mockValidationResult(new PolicyValidationResult(
                false,
                List.of("REFUND_PROCESS_PROMISE", "COUPON_POLICY_PROMISE"),
                "즉시 처리와 쿠폰 제공 가능 여부를 언급해 정책 위반입니다."
        ));

        ChatClientResponse advisedResponse = advisor.adviseCall(request, chain);

        String content = contentOf(advisedResponse);
        assertThat(content).contains("\"category\":\"ETC\"");
        assertThat(content).contains("즉시 처리와 쿠폰 제공 가능 여부를 언급해 정책 위반입니다.");
    }

    @Test
    void createsFallbackWhenCandidateResponseIsNull() {
        ChatClientRequest request = request("환불 가능한가요?");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(null);

        ChatClientResponse advisedResponse = advisor.adviseCall(request, chain);

        String content = contentOf(advisedResponse);
        assertThat(content).contains("\"category\":\"ETC\"");
        assertThat(content).contains("검증할 LLM 응답이 비어 있어 상담원 확인이 필요합니다.");
    }

    @Test
    void returnsOriginalResponseWhenPolicyJudgeAcceptsOutput() {
        ChatClientRequest request = request("환불 가능한가요?");
        ChatClientResponse candidateResponse = response("""
                {"summary":"환불 가능 여부는 시스템 확인이 필요합니다.","category":"REFUND","urgency":"HIGH","nextAction":"주문번호를 알려주세요.","neededInfo":["주문번호"],"handoffRequired":true,"handoffReason":"주문 상태 확인이 필요합니다."}
                """);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(candidateResponse);
        mockValidationResult(new PolicyValidationResult(true, List.of(), "문제 없음"));

        ChatClientResponse advisedResponse = advisor.adviseCall(request, chain);

        assertThat(advisedResponse).isSameAs(candidateResponse);
    }

    private void mockValidationResult(PolicyValidationResult result) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(validationChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(PolicyValidationResult.class)).thenReturn(result);
    }

    private ChatClientRequest request(String userMessage) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage(userMessage)))
                .build();
    }

    private ChatClientResponse response(String content) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))))
                .build();
    }

    private String contentOf(ChatClientResponse response) {
        return response.chatResponse().getResult().getOutput().getText();
    }
}
