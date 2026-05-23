package com.baedal.support.advisor;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

final class PromptLogFormatter {

    private static final int MAX_TEXT_LENGTH = 300;
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\b\\d{2,6}-\\d{2,6}-\\d{2,8}\\b");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(서울시|서울|경기도|부산시|대구시|인천시|광주시|대전시|울산시|세종시)[^\\n\",]{0,40}(로|길)\\s*\\d+");

    private PromptLogFormatter() {
    }

    static String summarizeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(none)";
        }

        return messages.stream()
                .map(PromptLogFormatter::summarizeMessage)
                .collect(Collectors.joining(", "));
    }

    static String summarizeToolDefinitions(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return "(none)";
        }

        return toolDefinitions.stream()
                .map(ToolDefinition::name)
                .collect(Collectors.joining(", "));
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

    static String formatMaskedMessages(List<Message> messages) {
        return sanitize(formatMessages(messages));
    }

    static String formatToolDefinitions(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return "(none)";
        }

        return toolDefinitions.stream()
                .map(PromptLogFormatter::formatToolDefinition)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String summarizeMessage(Message message) {
        if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
            return message.getMessageType() + "(toolCalls=" + assistantMessage.getToolCalls().size() + ")";
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return message.getMessageType() + "(responses=" + toolResponseMessage.getResponses().size() + ")";
        }

        String text = message.getText();
        return message.getMessageType() + "(chars=" + (text == null ? 0 : text.length()) + ")";
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
        return "\"" + abbreviate(sanitize(value)) + "\"";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = PHONE_PATTERN.matcher(value).replaceAll("[PHONE]");
        sanitized = ACCOUNT_PATTERN.matcher(sanitized).replaceAll("[ACCOUNT]");
        return ADDRESS_PATTERN.matcher(sanitized).replaceAll("[ADDRESS]");
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "...[TRUNCATED]";
    }
}
