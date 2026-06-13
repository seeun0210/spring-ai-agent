package com.baedal.support.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

public class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolExecutionPolicy policy;

    public GuardedToolCallback(ToolCallback delegate, ToolExecutionPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        ToolPolicyDecision decision = policy.check(getToolDefinition().name(), toolInput, toolContext);
        if (!decision.allowed()) {
            return decision.toolResultJson();
        }
        String result = delegate.call(toolInput, toolContext);
        policy.recordResult(getToolDefinition().name(), result, toolContext);
        return result;
    }
}
