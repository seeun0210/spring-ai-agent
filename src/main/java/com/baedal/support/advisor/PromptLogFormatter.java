package com.baedal.support.advisor;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.stream.Collectors;

final class PromptLogFormatter {

    private PromptLogFormatter() {
    }

    static String formatMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(none)";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            builder.append(i + 1)
                    .append(". ")
                    .append(formatMessage(messages.get(i)));
            if (i < messages.size() - 1) {
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    static String formatToolDefinitions(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return "(none)";
        }

        return toolDefinitions.stream()
                .map(PromptLogFormatter::formatToolDefinition)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatMessage(Message message) {
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            return "type=" + message.getMessageType()
                    + ", text=" + quote(message.getText())
                    + ", toolCalls=" + assistantMessage.getToolCalls();
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return "type=" + message.getMessageType()
                    + ", responses=" + toolResponseMessage.getResponses();
        }

        return "type=" + message.getMessageType()
                + ", text=" + quote(message.getText());
    }

    private static String formatToolDefinition(ToolDefinition toolDefinition) {
        return "- name=" + toolDefinition.name()
                + System.lineSeparator()
                + "  description=" + quote(toolDefinition.description())
                + System.lineSeparator()
                + "  inputSchema=" + toolDefinition.inputSchema();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }
}
