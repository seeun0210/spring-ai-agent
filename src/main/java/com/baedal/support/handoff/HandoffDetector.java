package com.baedal.support.handoff;

import com.baedal.support.dto.SupportResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class HandoffDetector {

    private static final List<String> LEGAL_KEYWORDS = List.of(
            "법적", "소송", "고소", "신고", "소비자원", "공정거래", "변호사", "내용증명", "법원"
    );

    private static final List<String> EXPLICIT_KEYWORDS = List.of(
            "상담원", "상담사", "사람이랑", "직원 연결", "고객센터", "전화 상담", "전화 연결", "상담 연결"
    );

    private static final List<String> HIGH_EMOTION_KEYWORDS = List.of(
            "화나", "화가 나", "짜증", "열받", "분노", "최악", "욕 나오", "시발", "씨발", "빡치"
    );

    public Optional<HandoffDecision> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, LEGAL_KEYWORDS)) {
            return Optional.of(HandoffDecision.of(
                    HandoffDecision.Type.LEGAL,
                    "법적 대응 또는 공식 신고 의사가 포함되어 상담원 확인으로 전환합니다.",
                    SupportResponse.Urgency.CRITICAL,
                    "법적 분쟁 가능성이 있는 문의입니다."
            ));
        }
        if (containsAny(normalized, EXPLICIT_KEYWORDS)) {
            return Optional.of(HandoffDecision.of(
                    HandoffDecision.Type.EXPLICIT,
                    "상담원 연결 요청으로 확인되어 상담원 확인으로 전환합니다.",
                    SupportResponse.Urgency.HIGH,
                    "사용자가 상담원 연결 요청을 명시했습니다."
            ));
        }
        if (containsAny(normalized, HIGH_EMOTION_KEYWORDS)) {
            return Optional.of(HandoffDecision.of(
                    HandoffDecision.Type.HIGH_EMOTION,
                    "불편이 큰 문의로 보여 상담원 확인으로 전환합니다.",
                    SupportResponse.Urgency.HIGH,
                    "높은 감정 강도의 문의입니다."
            ));
        }
        return Optional.empty();
    }

    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(message::contains);
    }
}
