package com.baedal.support.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksKoreanPhoneNumberWithoutMaskingOrderId() {
        String text = "주문번호 2024-1234 관련해서 010-1234-5678로 연락 주세요.";

        String masked = masker.mask(text);

        assertThat(masked).contains("2024-1234");
        assertThat(masked).contains("010-****-5678");
        assertThat(masked).doesNotContain("010-1234-5678");
    }

    @Test
    void masksCompactKoreanPhoneNumber() {
        String text = "연락처는 01012345678입니다.";

        String masked = masker.mask(text);

        assertThat(masked).contains("010-****-5678");
        assertThat(masked).doesNotContain("01012345678");
    }

    @Test
    void masksEmailLocalPart() {
        String text = "영수증은 customer@example.com으로 보내 주세요.";

        String masked = masker.mask(text);

        assertThat(masked).contains("c***@example.com");
        assertThat(masked).doesNotContain("customer@example.com");
    }

    @Test
    void masksKoreanAddress() {
        String text = "배달지는 서울시 강남구 역삼동 123-45입니다.";

        String masked = masker.mask(text);

        assertThat(masked).contains("[주소 비공개]");
        assertThat(masked).doesNotContain("서울시 강남구 역삼동 123-45");
    }

    @Test
    void detectsSensitiveData() {
        assertThat(masker.containsSensitive("010-1234-5678")).isTrue();
        assertThat(masker.containsSensitive("customer@example.com")).isTrue();
        assertThat(masker.containsSensitive("서울시 강남구 역삼동 123-45")).isTrue();
        assertThat(masker.containsSensitive("2024-1234 배달 상태")).isFalse();
    }
}
