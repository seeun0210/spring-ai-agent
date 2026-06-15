package com.baedal.support.handoff;

import com.baedal.support.dto.SupportResponse;

public record HandoffDecision(Type type, SupportResponse supportResponse) {

    public static final String CONTACT_NUMBER = "1600-0987";

    public enum Type {
        EXPLICIT,
        LEGAL,
        HIGH_EMOTION,
        SYSTEM_FALLBACK
    }

    public static HandoffDecision of(Type type, String summary, SupportResponse.Urgency urgency, String reason) {
        return new HandoffDecision(
                type,
                new SupportResponse(
                        summary,
                        SupportResponse.Category.ETC,
                        urgency,
                        "상담원 연결을 원하시면 고객센터 " + CONTACT_NUMBER + "로 연락해 주세요. 주문번호와 문의 내용을 함께 남겨 주시면 더 빠르게 확인할 수 있습니다.",
                        java.util.List.of("주문번호", "문의 내용"),
                        true,
                        reason
                )
        );
    }

    public static HandoffDecision systemFallback() {
        return of(
                Type.SYSTEM_FALLBACK,
                "죄송해요, 답변을 준비하는 중 문제가 발생해 상담원 확인으로 전환합니다.",
                SupportResponse.Urgency.HIGH,
                "시스템 응답 실패로 상담원 확인이 필요합니다."
        );
    }

    public String textMessage() {
        return "%s %s".formatted(supportResponse.summary(), supportResponse.nextAction());
    }
}
