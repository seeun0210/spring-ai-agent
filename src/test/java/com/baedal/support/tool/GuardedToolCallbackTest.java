package com.baedal.support.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardedToolCallbackTest {

    @Test
    void returnsPolicyResultWithoutCallingDelegateWhenBlocked() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolExecutionPolicy policy = mock(ToolExecutionPolicy.class);
        ToolContext toolContext = new ToolContext(java.util.Map.of());

        when(delegate.getToolDefinition()).thenReturn(toolDefinition("cancelOrder"));
        when(policy.check("cancelOrder", "{\"orderId\":\"2024-1237\"}", toolContext))
                .thenReturn(ToolPolicyDecision.block("{\"outcome\":\"CONFIRMATION_REQUIRED\"}"));

        GuardedToolCallback callback = new GuardedToolCallback(delegate, policy);

        String result = callback.call("{\"orderId\":\"2024-1237\"}", toolContext);

        assertThat(result).isEqualTo("{\"outcome\":\"CONFIRMATION_REQUIRED\"}");
        verify(delegate, never()).call("{\"orderId\":\"2024-1237\"}", toolContext);
    }

    @Test
    void callsDelegateWhenAllowed() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolExecutionPolicy policy = mock(ToolExecutionPolicy.class);
        ToolContext toolContext = new ToolContext(java.util.Map.of());

        when(delegate.getToolDefinition()).thenReturn(toolDefinition("cancelOrder"));
        when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        when(policy.check("cancelOrder", "{\"orderId\":\"2024-1235\"}", toolContext))
                .thenReturn(ToolPolicyDecision.allow());
        when(delegate.call("{\"orderId\":\"2024-1235\"}", toolContext)).thenReturn("{\"outcome\":\"CANCELED\"}");

        GuardedToolCallback callback = new GuardedToolCallback(delegate, policy);

        assertThat(callback.call("{\"orderId\":\"2024-1235\"}", toolContext)).isEqualTo("{\"outcome\":\"CANCELED\"}");
    }

    private ToolDefinition toolDefinition(String name) {
        return DefaultToolDefinition.builder()
                .name(name)
                .description("test")
                .inputSchema("{}")
                .build();
    }
}
