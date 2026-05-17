package com.baedal.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PerformanceLoggingAdvisor implements CallAdvisor {

    @Override
    public String getName() {
        return "PerformanceLoggingAdvisor";
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

        Usage usage = null;
        if (response != null
                && response.chatResponse() != null
                && response.chatResponse().getMetadata() != null) {
            usage = response.chatResponse().getMetadata().getUsage();
        }

        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
        Integer totalTokens = usage == null ? null : usage.getTotalTokens();

        log.info(
                "LLM call completed. elapsedMs={}, promptTokens={}, completionTokens={}, totalTokens={}",
                elapsedMs,
                promptTokens,
                completionTokens,
                totalTokens
        );

        return response;
    }
}
