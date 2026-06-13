package com.baedal.support.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeLoaderTest {

    @Test
    void springCanInstantiateKnowledgeLoaderWithProductionConstructor() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(VectorStore.class, () -> mock(VectorStore.class));
        context.registerBean(TokenTextSplitter.class, () -> new TokenTextSplitter(800, 350, 5, 10_000, true));
        context.register(KnowledgeLoader.class);

        assertThatCode(context::refresh).doesNotThrowAnyException();

        context.close();
    }

    @Test
    void parseExtractsCategoryIdTitleAndContentFromResourceFilename() throws Exception {
        KnowledgeLoader loader = testLoader(mock(VectorStore.class), resource("refund__refund-basic.md", """
                # 기본 환불 정책

                배달 완료 후 24시간 이내 접수할 수 있습니다.
                """));

        FaqDocument faq = loader.parse(resource("refund__refund-basic.md", """
                # 기본 환불 정책

                배달 완료 후 24시간 이내 접수할 수 있습니다.
                """));

        assertThat(faq.id()).isEqualTo("refund-basic");
        assertThat(faq.title()).isEqualTo("기본 환불 정책");
        assertThat(faq.category()).isEqualTo("refund");
        assertThat(faq.content()).contains("24시간 이내");
    }

    @Test
    void runAddsChunkedDocumentWithMetadataWhenFaqWasNotLoaded() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        Resource refundPolicy = resource("refund__refund-basic.md", """
                # 기본 환불 정책

                배달 완료 후 24시간 이내 접수할 수 있습니다.
                """);
        KnowledgeLoader loader = testLoader(vectorStore, refundPolicy);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        loader.run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        assertThat(documentsCaptor.getValue()).hasSize(1);
        Document added = documentsCaptor.getValue().get(0);
        assertThat(added.getText()).contains("24시간 이내");
        assertThat(added.getMetadata())
                .containsEntry("faqId", "refund-basic")
                .containsEntry("title", "기본 환불 정책")
                .containsEntry("category", "refund");
    }

    @Test
    void runSkipsDocumentWhenFaqIdAlreadyExists() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        Resource refundPolicy = resource("refund__refund-basic.md", """
                # 기본 환불 정책

                배달 완료 후 24시간 이내 접수할 수 있습니다.
                """);
        KnowledgeLoader loader = testLoader(vectorStore, refundPolicy);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("existing")));

        loader.run(new DefaultApplicationArguments());

        verify(vectorStore, never()).add(any());
    }

    @Test
    void alreadyLoadedSearchesByFaqIdMetadataWithoutSimilarityThreshold() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeLoader loader = testLoader(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("existing")));

        boolean loaded = loader.alreadyLoaded("refund-basic");

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(loaded).isTrue();
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(1);
        assertThat(requestCaptor.getValue().getSimilarityThreshold())
                .isEqualTo(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL);
        assertThat(requestCaptor.getValue().getFilterExpression()).isNotNull();
    }

    private KnowledgeLoader testLoader(VectorStore vectorStore, Resource... resources) {
        return new KnowledgeLoader(
                vectorStore,
                new TokenTextSplitter(800, 350, 5, 10_000, true),
                resolver(resources),
                "classpath:/knowledge/*.md"
        );
    }

    private ResourcePatternResolver resolver(Resource... resources) {
        return new ResourcePatternResolver() {
            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                return resources;
            }

            @Override
            public Resource getResource(String location) {
                throw new UnsupportedOperationException("getResource is not used by KnowledgeLoader tests");
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
    }

    private Resource resource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
