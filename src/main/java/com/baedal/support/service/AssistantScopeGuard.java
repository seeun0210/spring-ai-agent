package com.baedal.support.service;

import java.util.List;
import java.util.regex.Pattern;

final class AssistantScopeGuard {

    private static final String FALLBACK = "저는 주문/배달/환불/쿠폰 관련 상담을 도와드리고 있어요. 관련 문의를 남겨주시면 도와드릴게요.";

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("\\b\\d{4}-\\d{4}\\b");

    private static final List<String> IN_SCOPE_KEYWORDS = List.of(
            "주문", "배달", "환불", "취소", "쿠폰", "결제", "계정", "개인정보",
            "보상", "지연", "오배송", "누락", "라이더", "주소", "전화번호", "영수증"
    );

    private static final List<String> CLEARLY_OUT_OF_SCOPE_KEYWORDS = List.of(
            "점심", "저녁", "아침", "야식", "뭐 먹", "메뉴 추천", "음식 추천",
            "심심", "농담", "날씨 알려", "뉴스", "주식", "영화"
    );

    private AssistantScopeGuard() {
    }

    static String fallbackIfOutOfScope(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        if (ORDER_ID_PATTERN.matcher(message).find() || containsAny(message, IN_SCOPE_KEYWORDS)) {
            return null;
        }

        if (containsAny(message, CLEARLY_OUT_OF_SCOPE_KEYWORDS)) {
            return FALLBACK;
        }

        return null;
    }

    private static boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }
}
