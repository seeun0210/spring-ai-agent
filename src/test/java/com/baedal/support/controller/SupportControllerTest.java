package com.baedal.support.controller;

import com.baedal.support.dto.SupportResponse;
import com.baedal.support.validator.SupportResponseValidator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportController.class)
@Import({SupportResponseValidator.class, GlobalExceptionHandler.class})
class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "supportChatClient")
    private ChatClient supportChatClient;

    @Test
    void triageReturnsStructuredPrivacyPolicyResponse() throws Exception {
        SupportResponse response = new SupportResponse(
                "사장님 전화번호는 개인정보라 직접 안내할 수 없습니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.NORMAL,
                "주문 관련 문의라면 주문번호와 문의 내용을 알려주시면 확인 가능한 범위에서 도와드리겠습니다.",
                List.of("주문번호", "문의 내용"),
                true,
                "개인정보 제공 요청은 상담원 또는 내부 정책 확인이 필요합니다."
        );

        ChatClient.CallResponseSpec responseSpec = mockChatClientResponse("사장님 번호 알려줘", response);

        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"사장님 번호 알려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ETC"))
                .andExpect(jsonPath("$.urgency").value("NORMAL"))
                .andExpect(jsonPath("$.handoffRequired").value(true))
                .andExpect(jsonPath("$.neededInfo[0]").value("주문번호"))
                .andExpect(jsonPath("$.handoffReason").value("개인정보 제공 요청은 상담원 또는 내부 정책 확인이 필요합니다."));

        verify(responseSpec).entity(SupportResponse.class);
    }

    @Test
    void triageRejectsBlankMessageBeforeCallingLlm() throws Exception {
        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(supportChatClient);
    }

    @Test
    void triageRejectsNullMessageBeforeCallingLlm() throws Exception {
        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(supportChatClient);
    }

    @Test
    void triageForcesHandoffWhenCouponPromiseIsReturned() throws Exception {
        SupportResponse response = new SupportResponse(
                "쿠폰을 제공해 드리겠습니다.",
                SupportResponse.Category.REFUND,
                SupportResponse.Urgency.HIGH,
                "주문번호를 알려주세요.",
                List.of("주문번호"),
                false,
                null
        );

        mockChatClientResponse("쿠폰이라도 줘", response);

        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"쿠폰이라도 줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ETC"))
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.handoffRequired").value(true))
                .andExpect(jsonPath("$.handoffReason").value("쿠폰, 환불, 보상 확정 표현이 감지되어 상담원 확인이 필요합니다."));
    }

    @Test
    void triageReturnsInternalErrorWhenChatClientFails() throws Exception {
        when(supportChatClient.prompt()).thenThrow(new RuntimeException("LLM connection failed"));

        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"배달 상태 확인\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    private ChatClient.CallResponseSpec mockChatClientResponse(String userMessage, SupportResponse response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(supportChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(userMessage)).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(SupportResponse.class)).thenReturn(response);

        return responseSpec;
    }
}
