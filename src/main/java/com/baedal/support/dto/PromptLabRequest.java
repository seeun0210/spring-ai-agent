package com.baedal.support.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptLabRequest(
        String systemPrompt,
        @NotBlank(message = "message는 필수입니다.")
        String message
) {
}
