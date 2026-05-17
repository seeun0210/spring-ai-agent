package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SupportResponseValidator {

    private static final List<String> FORBIDDEN_PROMISES = List.of(
            "쿠폰을 제공",
            "쿠폰을 지급",
            "쿠폰을 드리",
            "쿠폰 제공",
            "쿠폰 지급",
            "환불해 드리",
            "환불을 처리",
            "환불 처리",
            "보상해 드리",
            "보상을 제공"
    );

    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b01[016789][- .]?\\d{3,4}[- .]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{2,6}[- ]\\d{2,6}[- ]\\d{2,8}\\b");

    public SupportResponse validate(SupportResponse response) {
        if (response == null) {
            return fallback("LLM 응답이 비어 있어 상담원 확인이 필요합니다.");
        }

        String content = String.join(" ",
                text(response.summary()),
                text(response.nextAction()),
                text(response.handoffReason())
        );

        if (containsForbiddenPromise(content)) {
            return fallback("쿠폰, 환불, 보상 확정 표현이 감지되어 상담원 확인이 필요합니다.");
        }

        if (containsPersonalInformation(content)) {
            return fallback("개인정보로 보이는 값이 감지되어 상담원 확인이 필요합니다.");
        }

        return response;
    }

    private boolean containsForbiddenPromise(String content) {
        String normalizedContent = normalize(content);
        return FORBIDDEN_PROMISES.stream()
                .map(this::normalize)
                .anyMatch(normalizedContent::contains);
    }

    private boolean containsPersonalInformation(String content) {
        return PHONE_NUMBER.matcher(content).find()
                || EMAIL.matcher(content).find()
                || ACCOUNT_NUMBER.matcher(content).find();
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

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return text(value).replaceAll("\\s+", "");
    }
}
