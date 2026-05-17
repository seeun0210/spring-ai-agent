package com.baedal.support.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PerformanceLoggingAdvisor implements CallAdvisor, StreamAdvisor {

    private final String endpoint;

    public PerformanceLoggingAdvisor(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String getName() {
        return "PerformanceLoggingAdvisor:" + endpoint;
    }

    @Override
    public int getOrder() {
        // 체인 바깥쪽에서 LLM 왕복 시간을 측정하기 위해 큰 값을 준다.
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startedAt = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        Usage usage = usageOf(response);

        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        Integer totalTokens = usage == null ? null : usage.getTotalTokens();

        log.info(
                "LLM call completed. endpoint={}, elapsedMs={}, promptTokens={}, completionTokens={}, totalTokens={}",
                endpoint,
                elapsedMs,
                promptTokens,
                completionTokens,
                totalTokens
        );

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        long startedAt = System.currentTimeMillis();
        AtomicInteger chunks = new AtomicInteger();
        AtomicReference<Usage> lastUsage = new AtomicReference<>();

        return chain.nextStream(request)
                .doOnNext(response -> {
                    chunks.incrementAndGet();
                    Usage usage = usageOf(response);
                    if (usage != null) {
                        lastUsage.set(usage);
                    }
                })
                .doOnComplete(() -> {
                    long elapsedMs = System.currentTimeMillis() - startedAt;
                    Usage usage = lastUsage.get();
                    Integer promptTokens = usage == null ? null : usage.getPromptTokens();
                    Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
                    Integer totalTokens = usage == null ? null : usage.getTotalTokens();

                    log.info(
                            "LLM stream completed. endpoint={}, elapsedMs={}, chunks={}, promptTokens={}, completionTokens={}, totalTokens={}",
                            endpoint,
                            elapsedMs,
                            chunks.get(),
                            promptTokens,
                            completionTokens,
                            totalTokens
                    );
                })
                .doOnError(error -> {
                    long elapsedMs = System.currentTimeMillis() - startedAt;
                    log.warn(
                            "LLM stream failed. endpoint={}, elapsedMs={}, chunks={}",
                            endpoint,
                            elapsedMs,
                            chunks.get(),
                            error
                    );
                });
    }

    private Usage usageOf(ChatClientResponse response) {
        if (response == null
                || response.chatResponse() == null
                || response.chatResponse().getMetadata() == null) {
            return null;
        }
        return response.chatResponse().getMetadata().getUsage();
    }
}
