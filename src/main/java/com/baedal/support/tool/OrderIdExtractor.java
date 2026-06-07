package com.baedal.support.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrderIdExtractor {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("\\b\\d{4}-\\d{4}\\b");

    private OrderIdExtractor() {
    }

    public static List<String> extract(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }

        Matcher matcher = ORDER_ID_PATTERN.matcher(message);
        LinkedHashSet<String> orderIds = new LinkedHashSet<>();
        while (matcher.find()) {
            orderIds.add(matcher.group());
        }
        return List.copyOf(orderIds);
    }
}
