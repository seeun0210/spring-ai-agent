package com.baedal.support.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;

import java.util.List;

@Slf4j
public class RagRetrievalLoggingAdvisor implements CallAdvisor {

    static final int ORDER = 30;

    private final boolean logRetrievedContent;

    public RagRetrievalLoggingAdvisor() {
        this(false);
    }

    public RagRetrievalLoggingAdvisor(boolean logRetrievedContent) {
        this.logRetrievedContent = logRetrievedContent;
    }

    @Override
    public String getName() {
        return "RagRetrievalLoggingAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        List<Document> documents = retrievedDocumentsOf(request);
        log.info(
                "[RAG] retrieved documents. count={}, documents={}",
                documents.size(),
                summariesOf(documents, logRetrievedContent)
        );
        return chain.nextCall(request);
    }

    @SuppressWarnings("unchecked")
    private List<Document> retrievedDocumentsOf(ChatClientRequest request) {
        Object value = request.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (value instanceof List<?> documents && documents.stream().allMatch(Document.class::isInstance)) {
            return (List<Document>) documents;
        }
        return List.of();
    }

    static List<String> summariesOf(List<Document> documents) {
        return summariesOf(documents, false);
    }

    static List<String> summariesOf(List<Document> documents, boolean includeContent) {
        return documents.stream()
                .map(document -> summaryOf(document, includeContent))
                .toList();
    }

    private static String summaryOf(Document document, boolean includeContent) {
        String summary = "faqId=%s, category=%s, title=%s, score=%s, distance=%s".formatted(
                metadata(document, "faqId"),
                metadata(document, "category"),
                metadata(document, "title"),
                score(document),
                nullableMetadata(document, "distance")
        );
        if (!includeContent) {
            return summary;
        }
        return summary + ", content=\"%s\"".formatted(document.getText());
    }

    private static Object metadata(Document document, String key) {
        return document.getMetadata().getOrDefault(key, "unknown");
    }

    private static Object nullableMetadata(Document document, String key) {
        return document.getMetadata().get(key);
    }

    private static String score(Document document) {
        return document.getScore() == null ? "null" : "%.4f".formatted(document.getScore());
    }
}
