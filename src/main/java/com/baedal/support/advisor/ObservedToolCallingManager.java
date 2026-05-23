package com.baedal.support.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ObservedToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        List<ToolDefinition> toolDefinitions = delegate.resolveToolDefinitions(chatOptions);

        log.info(
                "LLM tool definitions resolved. toolCount={}\n{}",
                toolDefinitions.size(),
                PromptLogFormatter.formatToolDefinitions(toolDefinitions)
        );

        return toolDefinitions;
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);

        log.info(
                "LLM tool response prompt prepared. messageCount={}\n{}",
                result.conversationHistory().size(),
                PromptLogFormatter.formatMessages(result.conversationHistory())
        );

        return result;
    }
}
