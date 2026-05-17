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
}
