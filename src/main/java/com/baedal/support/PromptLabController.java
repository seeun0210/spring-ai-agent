package com.baedal.support;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/prompt-lab")
public class PromptLabController {

    private final ChatClient.Builder builder;

    @PostMapping
    public PromptLabResult experiment(@RequestBody PromptLabRequest req) {
        String systemPrompt = req.systemPrompt() == null || req.systemPrompt().isBlank()
                ? BaedalPrompt.SYSTEM_PROMPT
                : req.systemPrompt();
        int repeat = Math.max(1, Math.min(req.repeat(), 10));

        ChatClient chatClient = builder.clone()
                .defaultSystem(systemPrompt)
                .build();

        List<SupportResponse> results = new ArrayList<>();
        for (int i = 0; i < repeat; i++) {
            SupportResponse response = chatClient
                    .prompt()
                    .user(req.message())
                    .call()
                    .entity(SupportResponse.class);
            results.add(response);
        }

        return PromptLabResult.from(results);
    }

    public record PromptLabRequest(
            String systemPrompt,
            String message,
            int repeat
    ) {}

    public record PromptLabResult(
            int totalRuns,
            Map<String, Long> categoryCounts,
            Map<String, Long> urgencyCounts,
            double categoryConsistency
    ) {
        public static PromptLabResult from(List<SupportResponse> results) {
            var catCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.category().name(), Collectors.counting()));
            var urgCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.urgency().name(), Collectors.counting()));
            long maxCat = catCounts.values().stream()
                    .mapToLong(Long::longValue).max().orElse(0);

            return new PromptLabResult(
                    results.size(), catCounts, urgCounts,
                    results.isEmpty() ? 0 : (double) maxCat / results.size()
            );
        }
    }
}
