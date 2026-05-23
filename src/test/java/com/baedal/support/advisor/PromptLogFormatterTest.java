package com.baedal.support.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptLogFormatterTest {

    @Test
    void summarizeMessagesDoesNotIncludeRawText() {
        String text = "서울시 강남구 테헤란로 1로 10에서 010-1234-5678로 연락주세요.";

        String summary = PromptLogFormatter.summarizeMessages(List.of(new UserMessage(text)));

        assertThat(summary).contains("chars=");
        assertThat(summary).doesNotContain("서울시 강남구");
        assertThat(summary).doesNotContain("010-1234-5678");
    }

    @Test
    void formatMaskedMessagesMasksSensitivePatternsAndTruncatesLongText() {
        String text = "주소는 서울시 강남구 테헤란로 1로 10, 연락처는 010-1234-5678, 계좌는 12345-67890-12345678 입니다. "
                + "x".repeat(400);

        String formatted = PromptLogFormatter.formatMaskedMessages(List.of(new UserMessage(text)));

        assertThat(formatted).contains("[ADDRESS]");
        assertThat(formatted).contains("[PHONE]");
        assertThat(formatted).contains("[ACCOUNT]");
        assertThat(formatted).contains("[TRUNCATED]");
        assertThat(formatted).doesNotContain("서울시 강남구 테헤란로");
        assertThat(formatted).doesNotContain("010-1234-5678");
        assertThat(formatted).doesNotContain("12345-67890-12345678");
    }
}
