package com.baedal.support.service;

public record OrderReadResolution(
        boolean resolved,
        String context,
        String directAnswer
) {

    static OrderReadResolution unresolved() {
        return new OrderReadResolution(false, null, null);
    }
}
