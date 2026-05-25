package com.baedal.support.dto;

import java.util.List;

public record PolicyValidationResult(
        boolean valid,
        List<String> violations,
        String reason
) {
    public PolicyValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        reason = reason == null ? "" : reason.trim();
    }
}
