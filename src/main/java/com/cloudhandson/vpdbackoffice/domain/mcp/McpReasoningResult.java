package com.cloudhandson.vpdbackoffice.domain.mcp;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeResult;

public record McpReasoningResult(
    String modelStatus,
    String toolName,
    String answer,
    String prompt,
    String evidenceJson,
    ProbeResult probeResult
) {
}
