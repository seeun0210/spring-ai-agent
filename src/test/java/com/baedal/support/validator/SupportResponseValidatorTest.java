package com.baedal.support.validator;

import com.baedal.support.dto.SupportResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SupportResponseValidatorTest {

    private final SupportResponseValidator validator = new SupportResponseValidator();

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
}
