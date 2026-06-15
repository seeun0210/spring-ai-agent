package com.baedal.support.guardrail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensitiveDataMasker {

    private static final Pattern PHONE_KR = Pattern.compile("01[016789][\\s-]?\\d{3,4}[\\s-]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ROAD_ADDRESS = Pattern.compile(
            "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|전라|경상|제주)" +
                    "(?:특별시|광역시|특별자치시|도|특별자치도|시)?\\s*" +
                    "[가-힣]+(?:시|군|구)\\s+" +
                    "[가-힣0-9\\-\\s]{1,30}(?:동|읍|면|로|길)\\s*\\d+(?:-\\d+)?"
    );

    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = maskPhone(text);
        masked = maskEmail(masked);
        return maskAddress(masked);
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PHONE_KR.matcher(text).find()
                || EMAIL.matcher(text).find()
                || ROAD_ADDRESS.matcher(text).find();
    }

    private String maskPhone(String text) {
        Matcher matcher = PHONE_KR.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group();
            String digits = raw.replaceAll("\\D", "");
            String replacement = digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String maskEmail(String text) {
        Matcher matcher = EMAIL.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group();
            int at = raw.indexOf('@');
            String local = raw.substring(0, at);
            String domain = raw.substring(at);
            String replacement = (local.length() <= 1 ? "*" : local.charAt(0) + "***") + domain;
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String maskAddress(String text) {
        return ROAD_ADDRESS.matcher(text).replaceAll("[주소 비공개]");
    }
}
