package com.cloudhandson.vpdbackoffice.domain.mcp;

public record McpClientDemoResult(
    String contextPath,
    String messageUrl,
    String selectedTool,
    String initializeResponse,
    String toolsListResponse,
    String toolsCallResponse,
    String status
) {
}
