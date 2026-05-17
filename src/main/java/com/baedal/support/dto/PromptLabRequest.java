package com.baedal.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptLabRequest(
        String systemPrompt,
        @NotBlank(message = "message는 필수입니다.")
        @Size(max = 1000, message = "message는 1000자 이하여야 합니다.")
        String message
) {
}
