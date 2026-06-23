package com.cloudhandson.vpdbackoffice.domain.mcp;

public record McpToolView(
    String name,
    String description,
    long objectId,
    String displayName,
    String ordsPath
) {
}
