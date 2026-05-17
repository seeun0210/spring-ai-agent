package com.baedal.support.controller;

import com.baedal.support.dto.SupportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportController.class)
class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "supportChatClient")
    private ChatClient supportChatClient;

    @Test
    void triageReturnsStructuredPrivacyPolicyResponse() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        SupportResponse response = new SupportResponse(
                "사장님 전화번호는 개인정보라 직접 안내할 수 없습니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.NORMAL,
                "주문 관련 문의라면 주문번호와 문의 내용을 알려주시면 확인 가능한 범위에서 도와드리겠습니다.",
                List.of("주문번호", "문의 내용"),
                true,
                "개인정보 제공 요청은 상담원 또는 내부 정책 확인이 필요합니다."
        );

        when(supportChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user("사장님 번호 알려줘")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(SupportResponse.class)).thenReturn(response);

        mockMvc.perform(post("/api/v1/support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"사장님 번호 알려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ETC"))
                .andExpect(jsonPath("$.urgency").value("NORMAL"))
                .andExpect(jsonPath("$.handoffRequired").value(true))
                .andExpect(jsonPath("$.neededInfo[0]").value("주문번호"))
                .andExpect(jsonPath("$.handoffReason").value("개인정보 제공 요청은 상담원 또는 내부 정책 확인이 필요합니다."));

        verify(requestSpec).user("사장님 번호 알려줘");
        verify(responseSpec).entity(SupportResponse.class);
    }
}
