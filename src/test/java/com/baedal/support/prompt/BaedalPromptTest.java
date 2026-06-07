package com.baedal.support.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaedalPromptTest {

    @Test
    void systemPromptContainsCorePolicySections() {
        assertThat(BaedalPrompt.SYSTEM_PROMPT)
                .contains("[역할]")
                .contains("[규칙]")
                .contains("[금지]")
                .contains("[응답 포맷]")
                .contains("반드시 한국어로만 응답");
    }

    @Test
    void systemPromptContainsPrivacyCompensationAndCompetitorGuards() {
        assertThat(BaedalPrompt.SYSTEM_PROMPT)
                .contains("개인정보")
                .contains("전화번호")
                .contains("쿠폰")
                .contains("타 배달 플랫폼");
    }

    @Test
    void systemPromptContainsRagPolicyCitationAndFallbackRules() {
        assertThat(BaedalPrompt.SYSTEM_PROMPT)
                .contains("[정책 인용 규칙]")
                .contains("Context")
                .contains("해당 내용은 확인이 필요합니다. 상담원 연결로 도와드리겠습니다.")
                .contains("정책 수치와 조건은 원문 표현을 유지")
                .contains("Tool 결과를 주문별 사실로");
    }
}
