package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupportResponseValidator {

    public SupportResponse validate(SupportResponse response) {
        if (response == null) {
            return fallback("LLM 응답이 비어 있어 상담원 확인이 필요합니다.");
        }

        if (hasInvalidCoreFields(response)) {
            return fallback("LLM 응답의 필수 필드가 비어 있어 상담원 확인이 필요합니다.");
        }

        if (response.handoffRequired() && !hasText(response.handoffReason())) {
            return fallback("상담원 전환 사유가 비어 있어 상담원 확인이 필요합니다.");
        }

        return response;
    }

    private boolean hasInvalidCoreFields(SupportResponse response) {
        return !hasText(response.summary())
                || response.category() == null
                || response.urgency() == null
                || !hasText(response.nextAction());
    }

    private SupportResponse fallback(String reason) {
        return new SupportResponse(
                "정책 확인이 필요한 응답이 감지되어 상담원 확인으로 전환합니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "주문 관련 문의라면 주문번호와 문의 내용을 남겨 주시면 상담원이 확인하겠습니다.",
                List.of("주문번호", "문의 내용"),
                true,
                reason
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
