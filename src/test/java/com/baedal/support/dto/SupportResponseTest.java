package com.baedal.support.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportResponseTest {

    @Test
    void trimsCoreTextFields() {
        SupportResponse response = new SupportResponse(
                " 확인했습니다. ",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                " 주문 내역을 확인해 주세요. ",
                List.of(),
                false,
                null
        );

        assertThat(response.summary()).isEqualTo("확인했습니다.");
        assertThat(response.nextAction()).isEqualTo("주문 내역을 확인해 주세요.");
    }

    @Test
    void normalizesBlankTextFieldsToNullForValidator() {
        SupportResponse response = new SupportResponse(
                "   ",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "   ",
                List.of("문의 내용"),
                true,
                "   "
        );

        assertThat(response.summary()).isNull();
        assertThat(response.nextAction()).isNull();
        assertThat(response.handoffReason()).isNull();
    }

    @Test
    void trimsHandoffReasonWhenHandoffRequiredIsTrue() {
        SupportResponse response = new SupportResponse(
                "상담원 확인이 필요합니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "상담원에게 문의 내용을 전달해 주세요.",
                List.of("문의 내용"),
                true,
                " 시스템 확인 필요 "
        );

        assertThat(response.handoffReason()).isEqualTo("시스템 확인 필요");
    }

    @Test
    void clearsHandoffReasonWhenHandoffRequiredIsFalse() {
        SupportResponse response = new SupportResponse(
                "확인했습니다.",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                "주문 내역을 확인해 주세요.",
                List.of(),
                false,
                "불필요한 사유"
        );

        assertThat(response.handoffReason()).isNull();
    }

    @Test
    void normalizesNullNeededInfoToEmptyList() {
        SupportResponse response = new SupportResponse(
                "확인했습니다.",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                "주문 내역을 확인해 주세요.",
                null,
                false,
                null
        );

        assertThat(response.neededInfo()).isEmpty();
    }

    @Test
    void defensivelyCopiesNeededInfo() {
        List<String> original = new ArrayList<>();
        original.add("주문번호");

        SupportResponse response = new SupportResponse(
                "확인했습니다.",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                "주문 내역을 확인해 주세요.",
                original,
                false,
                null
        );

        original.add("전화번호");

        assertThat(response.neededInfo()).containsExactly("주문번호");
    }

    @Test
    void exposesImmutableNeededInfo() {
        SupportResponse response = new SupportResponse(
                "확인했습니다.",
                SupportResponse.Category.ORDER,
                SupportResponse.Urgency.NORMAL,
                "주문 내역을 확인해 주세요.",
                List.of("주문번호"),
                false,
                null
        );

        assertThatThrownBy(() -> response.neededInfo().add("전화번호"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
