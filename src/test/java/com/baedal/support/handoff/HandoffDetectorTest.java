package com.baedal.support.handoff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffDetectorTest {

    private final HandoffDetector detector = new HandoffDetector();

    @Test
    void detectsExplicitAgentTransferRequest() {
        HandoffDecision decision = detector.detect("상담원 연결해 주세요").orElseThrow();

        assertThat(decision.type()).isEqualTo(HandoffDecision.Type.EXPLICIT);
        assertThat(decision.supportResponse().handoffRequired()).isTrue();
        assertThat(decision.supportResponse().nextAction()).contains("1600-0987");
    }

    @Test
    void prioritizesLegalEscalationBeforeHighEmotion() {
        HandoffDecision decision = detector.detect("너무 화나서 법적으로 신고하겠습니다").orElseThrow();

        assertThat(decision.type()).isEqualTo(HandoffDecision.Type.LEGAL);
        assertThat(decision.supportResponse().handoffReason()).contains("법적");
    }

    @Test
    void detectsHighEmotionEscalation() {
        HandoffDecision decision = detector.detect("배달이 계속 늦어서 너무 화나요").orElseThrow();

        assertThat(decision.type()).isEqualTo(HandoffDecision.Type.HIGH_EMOTION);
        assertThat(decision.supportResponse().handoffReason()).contains("감정");
    }

    @Test
    void allowsNormalOrderQuestion() {
        assertThat(detector.detect("2024-1234 배달 어디쯤이에요?")).isEmpty();
    }
}
