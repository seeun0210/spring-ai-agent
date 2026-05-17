package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final List<Pattern> FORBIDDEN_PROMISE_PATTERNS = FORBIDDEN_PROMISES.stream()
            .map(SupportResponseValidator::normalize)
            .map(Pattern::quote)
            .map(Pattern::compile)
            .toList();
    private static final List<String> COMPETITOR_KEYWORDS = List.of(
            "쿠팡이츠",
            "요기요",
            "배달의민족",
            "배민",
            "coupang eats",
            "yogiyo"
    );

    private static final Pattern PHONE_NUMBER = Pattern.compile("\\b01[016789][- .]?\\d{3,4}[- .]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\b\\d{2,6}[- ]\\d{2,6}[- ]\\d{2,8}\\b");

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

        String content = String.join(" ",
                text(response.summary()),
                text(response.nextAction()),
                text(response.neededInfo()),
                text(response.handoffReason())
        );

        if (containsForbiddenPromise(content)) {
            return fallback("쿠폰, 환불, 보상 확정 표현이 감지되어 상담원 확인이 필요합니다.");
        }

        if (containsCompetitorKeyword(content)) {
            return fallback("타 배달앱 비교 또는 추천 표현이 감지되어 상담원 확인이 필요합니다.");
        }

        if (containsPersonalInformation(content)) {
            return fallback("개인정보로 보이는 값이 감지되어 상담원 확인이 필요합니다.");
        }

        return response;
    }

    private boolean containsForbiddenPromise(String content) {
        String normalizedContent = normalize(content);
        return FORBIDDEN_PROMISE_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalizedContent).find());
    }

    private boolean containsCompetitorKeyword(String content) {
        String normalizedContent = normalize(content);
        return COMPETITOR_KEYWORDS.stream()
                .map(SupportResponseValidator::normalize)
                .anyMatch(normalizedContent::contains);
    }

    private boolean containsPersonalInformation(String content) {
        return PHONE_NUMBER.matcher(content).find()
                || EMAIL.matcher(content).find()
                || ACCOUNT_NUMBER.matcher(content).find();
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

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String text(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream()
                .map(this::text)
                .collect(Collectors.joining(" "));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
