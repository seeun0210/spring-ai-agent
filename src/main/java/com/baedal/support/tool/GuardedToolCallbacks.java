package com.baedal.support.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;

public final class GuardedToolCallbacks {

    private GuardedToolCallbacks() {
    }

    public static ToolCallback[] wrap(ToolExecutionPolicy policy, ToolCallback... callbacks) {
        return Arrays.stream(callbacks)
                .map(callback -> new GuardedToolCallback(callback, policy))
                .toArray(ToolCallback[]::new);
    }
}
