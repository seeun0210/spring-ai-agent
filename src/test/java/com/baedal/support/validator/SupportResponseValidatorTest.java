package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

    @ParameterizedTest
    @MethodSource("forbiddenPromiseVariants")
    void detectsForbiddenPromiseVariants(String summary) {
        SupportResponse response = new SupportResponse(
                summary,
                SupportResponse.Category.REFUND,
                SupportResponse.Urgency.HIGH,
                "주문번호를 알려주세요.",
                List.of("주문번호"),
                false,
                null
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason())
                .isEqualTo("쿠폰, 환불, 보상 확정 표현이 감지되어 상담원 확인이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("competitorResponseVariants")
    void detectsCompetitorMentionsInLlmResponse(String summary) {
        SupportResponse response = new SupportResponse(
                summary,
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.NORMAL,
                "현재 서비스의 주문 문의를 남겨주세요.",
                List.of(),
                false,
                null
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason())
                .isEqualTo("타 배달앱 비교 또는 추천 표현이 감지되어 상담원 확인이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("personalInformationResponseVariants")
    void detectsPersonalInformationInResponseFields(
            String summary,
            String nextAction,
            boolean handoffRequired,
            String handoffReason
    ) {
        SupportResponse response = new SupportResponse(
                summary,
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.NORMAL,
                nextAction,
                List.of(),
                handoffRequired,
                handoffReason
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason())
                .isEqualTo("개인정보로 보이는 값이 감지되어 상담원 확인이 필요합니다.");
    }

    @ParameterizedTest
    @MethodSource("neededInfoPolicyViolationVariants")
    void detectsPolicyViolationsInNeededInfo(List<String> neededInfo, String expectedReason) {
        SupportResponse response = new SupportResponse(
                "확인했습니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.NORMAL,
                "현재 서비스의 주문 문의를 남겨주세요.",
                neededInfo,
                false,
                null
        );

        SupportResponse validated = validator.validate(response);

        assertThat(validated.category()).isEqualTo(SupportResponse.Category.ETC);
        assertThat(validated.urgency()).isEqualTo(SupportResponse.Urgency.HIGH);
        assertThat(validated.handoffRequired()).isTrue();
        assertThat(validated.handoffReason()).isEqualTo(expectedReason);
    }

    private static Stream<String> forbiddenPromiseVariants() {
        return Stream.of(
                "쿠폰 을 제공해 드리겠습니다.",
                "환불\n처리해 드리겠습니다.",
                "보상해드리겠습니다."
        );
    }

    private static Stream<String> competitorResponseVariants() {
        return Stream.of(
                "쿠팡이츠가 더 빠를 수 있습니다.",
                "배민과 비교하면 이 서비스가 저렴합니다.",
                "coupang eats에서 찾아보세요."
        );
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

    private static Stream<Arguments> personalInformationResponseVariants() {
        return Stream.of(
                arguments("010-1234-5678로 연락드리겠습니다.", "현재 서비스의 주문 문의를 남겨주세요.", false, null),
                arguments("확인했습니다.", "owner@example.com으로 문의해 주세요.", false, null),
                arguments("확인했습니다.", "현재 서비스의 주문 문의를 남겨주세요.", true, "123-456-789012 계좌 확인 필요")
        );
    }

    private static Stream<Arguments> neededInfoPolicyViolationVariants() {
        return Stream.of(
                arguments(List.of("쿠폰 을 제공"), "쿠폰, 환불, 보상 확정 표현이 감지되어 상담원 확인이 필요합니다."),
                arguments(List.of("쿠팡이츠 주문번호"), "타 배달앱 비교 또는 추천 표현이 감지되어 상담원 확인이 필요합니다."),
                arguments(List.of("010-1234-5678"), "개인정보로 보이는 값이 감지되어 상담원 확인이 필요합니다.")
        );
    }
}
