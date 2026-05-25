package com.baedal.support.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
