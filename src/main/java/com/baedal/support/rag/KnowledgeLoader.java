package com.baedal.support.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnowledgeLoader implements ApplicationRunner {

    static final String DEFAULT_KNOWLEDGE_LOCATION = "classpath:/knowledge/*.md";

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final ResourcePatternResolver resourcePatternResolver;
    private final String knowledgeLocation;

    @Autowired
    public KnowledgeLoader(VectorStore vectorStore, TokenTextSplitter tokenTextSplitter) {
        this(
                vectorStore,
                tokenTextSplitter,
                new PathMatchingResourcePatternResolver(),
                DEFAULT_KNOWLEDGE_LOCATION
        );
    }

    KnowledgeLoader(
            VectorStore vectorStore,
            TokenTextSplitter tokenTextSplitter,
            ResourcePatternResolver resourcePatternResolver,
            String knowledgeLocation
    ) {
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = tokenTextSplitter;
        this.resourcePatternResolver = resourcePatternResolver;
        this.knowledgeLocation = knowledgeLocation;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources = resourcePatternResolver.getResources(knowledgeLocation);
        if (resources.length == 0) {
            log.warn("[KnowledgeLoader] knowledge resources not found. location={}", knowledgeLocation);
            return;
        }

        int loaded = 0;
        int skipped = 0;

        for (Resource resource : resources) {
            FaqDocument faq = parse(resource);
            if (alreadyLoaded(faq.id())) {
                skipped++;
                log.debug("[KnowledgeLoader] already loaded. faqId={}, title={}", faq.id(), faq.title());
                continue;
            }

            List<Document> chunks = chunk(faq);
            vectorStore.add(chunks);
            loaded++;
            log.info(
                    "[KnowledgeLoader] loaded faq. faqId={}, chunks={}, category={}",
                    faq.id(),
                    chunks.size(),
                    faq.category()
            );
        }

        log.info(
                "[KnowledgeLoader] RAG seed complete. loaded={}, skipped={}, total={}",
                loaded,
                skipped,
                resources.length
        );
    }

    FaqDocument parse(Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("Knowledge resource filename is missing: " + resource);
        }

        String base = filename.replaceFirst("\\.md$", "");
        int separator = base.indexOf("__");
        String category = separator > 0 ? base.substring(0, separator) : "general";
        String id = separator > 0 ? base.substring(separator + 2) : base;

        String body;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        String title = body.lines()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .orElse(id);

        return new FaqDocument(id, title, category, body);
    }

    boolean alreadyLoaded(String faqId) {
        SearchRequest request = SearchRequest.builder()
                .query("정책")
                .topK(1)
                .similarityThresholdAll()
                .filterExpression("faqId == '" + faqId + "'")
                .build();

        return !vectorStore.similaritySearch(request).isEmpty();
    }

    private List<Document> chunk(FaqDocument faq) {
        Document document = new Document(
                faq.id(),
                faq.content(),
                Map.of(
                        "faqId", faq.id(),
                        "title", faq.title(),
                        "category", faq.category()
                )
        );
        return tokenTextSplitter.apply(List.of(document));
    }
}
