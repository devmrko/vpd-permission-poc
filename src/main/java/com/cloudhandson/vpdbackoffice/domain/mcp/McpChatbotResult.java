package com.cloudhandson.vpdbackoffice.domain.mcp;

public record McpChatbotResult(
    String contextPath,
    String question,
    String selectedTool,
    String status,
    String answer,
    String routingReason,
    String toolResultJson,
    String toolsListJson
) {
}
