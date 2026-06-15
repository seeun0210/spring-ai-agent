package com.baedal.support.guardrail;

public record GuardrailResult(boolean allowed, String reason, String fallbackMessage) {

    public static GuardrailResult allow(String reason) {
        return new GuardrailResult(true, reason, null);
    }

    public static GuardrailResult block(String reason, String fallbackMessage) {
        return new GuardrailResult(false, reason, fallbackMessage);
    }
}
