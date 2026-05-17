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

    private static Stream<String> forbiddenPromiseVariants() {
        return Stream.of(
                "쿠폰 을 제공해 드리겠습니다.",
                "환불\n처리해 드리겠습니다.",
                "보상해드리겠습니다."
        );
    }
}
