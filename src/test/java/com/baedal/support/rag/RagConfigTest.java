package com.baedal.support.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagConfigTest {

    private final RagConfig ragConfig = new RagConfig(new RagProperties());

    @Test
    void tokenTextSplitterKeepsShortPolicyDocumentAsSearchableChunk() {
        TokenTextSplitter splitter = ragConfig.tokenTextSplitter();
        Document document = new Document(
                "refund-basic",
                "# 환불 정책\n배달 완료 후 24시간 이내 접수할 수 있습니다.",
                Map.of()
        );

        List<Document> chunks = splitter.apply(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("24시간 이내");
    }

    @Test
    void questionAnswerAdvisorRunsAfterMemoryAdvisor() {
        VectorStore vectorStore = mock(VectorStore.class);

        QuestionAnswerAdvisor advisor = ragConfig.questionAnswerAdvisor(vectorStore);

        assertThat(advisor.getOrder()).isEqualTo(20);
    }

    @Test
    void questionAnswerAdvisorUsesConfiguredSearchRequest() {
        RagProperties properties = new RagProperties();
        properties.setTopK(8);
        properties.setSimilarityThreshold(0.7);
        properties.setAdvisorOrder(5);
        RagConfig config = new RagConfig(properties);
        VectorStore vectorStore = mock(VectorStore.class);

        QuestionAnswerAdvisor advisor = config.questionAnswerAdvisor(vectorStore);

        SearchRequest searchRequest = (SearchRequest) ReflectionTestUtils.getField(advisor, "searchRequest");
        assertThat(searchRequest).isNotNull();
        assertThat(searchRequest.getTopK()).isEqualTo(8);
        assertThat(searchRequest.getSimilarityThreshold()).isEqualTo(0.7);
        assertThat(advisor.getOrder()).isEqualTo(5);
    }

    @Test
    void tokenTextSplitterUsesConfiguredChunkSize() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(300);
        RagConfig config = new RagConfig(properties);
        Document document = new Document(
                "refund-basic",
                "# 환불 정책\n배달 완료 후 24시간 이내 접수할 수 있습니다.",
                Map.of()
        );

        List<Document> chunks = config.tokenTextSplitter().apply(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).contains("24시간 이내");
    }

    @Test
    void ragRetrievalLoggingAdvisorRunsAfterQuestionAnswerAdvisor() {
        RagRetrievalLoggingAdvisor advisor = ragConfig.ragRetrievalLoggingAdvisor();

        assertThat(advisor.getOrder()).isEqualTo(30);
    }
}
