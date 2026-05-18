package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SupportResponseValidatorTest {

    private final SupportResponseValidator validator = new SupportResponseValidator();

    @Test
    void returnsFallbackWhenResponseIsNull() {
        SupportResponse validated = validator.validate(null);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason()).isEqualTo("LLM 응답이 비어 있어 상담원 확인이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("invalidCoreFieldResponses")
    void returnsFallbackWhenCoreFieldsAreInvalid(SupportResponse response) {
        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason()).isEqualTo("LLM 응답의 필수 필드가 비어 있어 상담원 확인이 필요합니다.");
    }

    @Test
    void returnsFallbackWhenHandoffReasonIsMissing() {
        SupportResponse response = new SupportResponse(
                "상담원 확인이 필요합니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "상담원에게 문의 내용을 전달해 주세요.",
                List.of("문의 내용"),
                true,
                null
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason()).isEqualTo("상담원 전환 사유가 비어 있어 상담원 확인이 필요합니다.");
    }

    @Test
    void doesNotApplyStringPatternPolicyValidation() {
        SupportResponse response = new SupportResponse(
                "환불이 가능합니다. 쿠폰을 제공해 드리겠습니다. 010-1234-5678로 연락드리겠습니다.",
                SupportResponse.Category.REFUND,
                SupportResponse.Urgency.HIGH,
                "주문번호를 알려주시면 환불 절차를 진행해 드리겠습니다.",
                List.of("주문번호"),
                true,
                "주문 상태 확인이 필요합니다."
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated).isEqualTo(response);
    }

    private static Stream<SupportResponse> invalidCoreFieldResponses() {
        return Stream.of(
                new SupportResponse(null, SupportResponse.Category.ORDER, SupportResponse.Urgency.NORMAL, "주문 내역을 확인해 주세요.", List.of(), false, null),
                new SupportResponse("   ", SupportResponse.Category.ORDER, SupportResponse.Urgency.NORMAL, "주문 내역을 확인해 주세요.", List.of(), false, null),
                new SupportResponse("확인했습니다.", null, SupportResponse.Urgency.NORMAL, "주문 내역을 확인해 주세요.", List.of(), false, null),
                new SupportResponse("확인했습니다.", SupportResponse.Category.ORDER, null, "주문 내역을 확인해 주세요.", List.of(), false, null),
                new SupportResponse("확인했습니다.", SupportResponse.Category.ORDER, SupportResponse.Urgency.NORMAL, null, List.of(), false, null),
                new SupportResponse("확인했습니다.", SupportResponse.Category.ORDER, SupportResponse.Urgency.NORMAL, "   ", List.of(), false, null)
        );
    }
}
