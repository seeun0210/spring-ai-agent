package com.baedal.support.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportResponseTest {

    @Test
    void requiresHandoffReasonWhenHandoffRequiredIsTrue() {
        assertThatThrownBy(() -> new SupportResponse(
                "상담원 확인이 필요합니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "상담원에게 문의 내용을 전달해 주세요.",
                List.of("문의 내용"),
                true,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("handoffRequired=true 일 때 handoffReason은 필수입니다.");
    }

    @Test
    void rejectsBlankHandoffReasonWhenHandoffRequiredIsTrue() {
        assertThatThrownBy(() -> new SupportResponse(
                "상담원 확인이 필요합니다.",
                SupportResponse.Category.ETC,
                SupportResponse.Urgency.HIGH,
                "상담원에게 문의 내용을 전달해 주세요.",
                List.of("문의 내용"),
                true,
                "   "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("handoffRequired=true 일 때 handoffReason은 필수입니다.");
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
}
