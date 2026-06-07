package com.baedal.support.tool;

public record ToolPolicyDecision(boolean allowed, String toolResultJson) {

    public static ToolPolicyDecision allow() {
        return new ToolPolicyDecision(true, null);
    }

    public static ToolPolicyDecision block(String toolResultJson) {
        return new ToolPolicyDecision(false, toolResultJson);
    }
}
