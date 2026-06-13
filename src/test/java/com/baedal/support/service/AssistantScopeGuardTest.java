package com.baedal.support.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantScopeGuardTest {

    @Test
    void blocksClearlyOutOfScopeLunchRecommendation() {
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("오늘 점심 뭐 먹을까요?"))
                .contains("주문/배달/환불/쿠폰 관련 상담");
    }

    @Test
    void allowsDeliveryRefundCouponOrderAndAccountQuestions() {
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("배달 완료 후 환불 받을 수 있나요?")).isNull();
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("쿠폰 중복 사용 가능한가요?")).isNull();
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("2024-1234 주문 어디쯤이에요?")).isNull();
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("계정 정보는 어떻게 바꿔요?")).isNull();
    }

    @Test
    void allowsAmbiguousQuestionsToAvoidOverBlocking() {
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("그거 취소해줘")).isNull();
        assertThat(AssistantScopeGuard.fallbackIfOutOfScope("문제가 생겼어요")).isNull();
    }
}
