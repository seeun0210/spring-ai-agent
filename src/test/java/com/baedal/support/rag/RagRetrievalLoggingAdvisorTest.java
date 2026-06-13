package com.baedal.support.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalLoggingAdvisorTest {

    @Test
    void summarizesRetrievedDocumentsWithMetadataAndScoreOnly() {
        Document document = Document.builder()
                .id("doc-1")
                .text("배달 완료 후 환불 정책 본문 전체는 로그에 남기지 않는다.")
                .metadata(Map.of(
                        "faqId", "refund-after-delivered",
                        "category", "refund",
                        "title", "배달 완료 후 환불 가능 조건",
                        "distance", 0.18
                ))
                .score(0.82)
                .build();

        List<String> summaries = RagRetrievalLoggingAdvisor.summariesOf(List.of(document));

        assertThat(summaries)
                .containsExactly("faqId=refund-after-delivered, category=refund, title=배달 완료 후 환불 가능 조건, score=0.8200, distance=0.18");
        assertThat(summaries.get(0)).doesNotContain("정책 본문 전체");
    }

    @Test
    void usesUnknownDefaultsWhenMetadataIsMissing() {
        Document document = Document.builder()
                .id("doc-1")
                .text("본문")
                .build();

        List<String> summaries = RagRetrievalLoggingAdvisor.summariesOf(List.of(document));

        assertThat(summaries)
                .containsExactly("faqId=unknown, category=unknown, title=unknown, score=null, distance=null");
    }

    @Test
    void canIncludeRetrievedContentForExperiments() {
        Document document = Document.builder()
                .id("doc-1")
                .text("배달 완료 후 24시간 이내 접수할 수 있습니다.")
                .metadata(Map.of(
                        "faqId", "refund-after-delivered",
                        "category", "refund",
                        "title", "배달 완료 후 환불 가능 조건"
                ))
                .score(0.82)
                .build();

        List<String> summaries = RagRetrievalLoggingAdvisor.summariesOf(List.of(document), true);

        assertThat(summaries.get(0))
                .contains("faqId=refund-after-delivered")
                .contains("content=\"배달 완료 후 24시간 이내 접수할 수 있습니다.\"");
    }
}
