package com.cloudhandson.vpdbackoffice.domain.mcp;

public record McpReasoningCommand(
    long objectId,
    String bearerToken,
    int limit,
    String question
) {
}
