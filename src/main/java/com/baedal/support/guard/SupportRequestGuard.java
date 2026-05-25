package com.baedal.support.guard;

import com.baedal.support.dto.SupportResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class SupportRequestGuard {

    private static final List<String> COMPETITOR_KEYWORDS = List.of(
            "쿠팡이츠",
            "요기요",
            "배달의민족",
            "배민",
            "coupang eats",
            "yogiyo"
    );

    public Optional<SupportResponse> guard(String message) {
        if (message == null || !containsCompetitorKeyword(message)) {
            return Optional.empty();
        }

        return Optional.of(new SupportResponse(
                "다른 배달앱의 주문, 매장 검색, 가격 비교는 도와드릴 수 없습니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.LOW,
                "현재 서비스의 주문, 배달, 취소, 환불, 결제 문의가 있다면 주문번호와 함께 알려주세요.",
                List.of(),
                false,
                null
        ));
    }

    private boolean containsCompetitorKeyword(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return COMPETITOR_KEYWORDS.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }
}
