package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.mcp.McpToolView;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeCommand;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class McpSseService {

  private final McpToolRegistry toolRegistry;
  private final OrdsProbeService ordsProbeService;
  private final ObjectMapper objectMapper;

  public McpSseService(
      McpToolRegistry toolRegistry,
      OrdsProbeService ordsProbeService,
      ObjectMapper objectMapper
  ) {
    this.toolRegistry = toolRegistry;
    this.ordsProbeService = ordsProbeService;
    this.objectMapper = objectMapper;
  }

  public ObjectNode handle(String contextPath, JsonNode request) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put("jsonrpc", "2.0");
    if (request != null && request.has("id")) {
      response.set("id", request.get("id"));
    }

    String method = request == null || !request.hasNonNull("method") ? "" : request.get("method").asText();
    try {
      response.set("result", switch (method) {
        case "initialize" -> initializeResult(contextPath);
        case "notifications/initialized" -> objectMapper.createObjectNode();
        case "tools/list" -> toolsListResult();
        case "tools/call" -> toolsCallResult(request.path("params"));
        default -> throw new AppException("지원하지 않는 MCP method입니다: " + method);
      });
    } catch (Exception e) {
      response.remove("result");
      ObjectNode error = objectMapper.createObjectNode();
      error.put("code", -32000);
      error.put("message", e.getMessage());
      response.set("error", error);
    }
    return response;
  }

  private ObjectNode initializeResult(String contextPath) {
    ObjectNode result = objectMapper.createObjectNode();
    result.put("protocolVersion", "2024-11-05");
    ObjectNode serverInfo = objectMapper.createObjectNode();
    serverInfo.put("name", "vpd-ords-backoffice-" + contextPath);
    serverInfo.put("version", "0.1.0");
    result.set("serverInfo", serverInfo);
    ObjectNode capabilities = objectMapper.createObjectNode();
    capabilities.set("tools", objectMapper.createObjectNode());
    result.set("capabilities", capabilities);
    return result;
  }

  private ObjectNode toolsListResult() {
    ObjectNode result = objectMapper.createObjectNode();
    ArrayNode tools = objectMapper.createArrayNode();
    for (McpToolView tool : toolRegistry.listTools()) {
      ObjectNode item = objectMapper.createObjectNode();
      item.put("name", tool.name());
      item.put("description", tool.description());
      item.set("inputSchema", inputSchema());
      tools.add(item);
    }
    result.set("tools", tools);
    return result;
  }

  private ObjectNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode properties = objectMapper.createObjectNode();

    ObjectNode bearerToken = objectMapper.createObjectNode();
    bearerToken.put("type", "string");
    bearerToken.put("description", "ORDS 호출에 사용할 Bearer Token 원문");
    properties.set("bearerToken", bearerToken);

    ObjectNode limit = objectMapper.createObjectNode();
    limit.put("type", "integer");
    limit.put("description", "조회 row 제한. 1부터 500까지 허용");
    limit.put("minimum", 1);
    limit.put("maximum", 500);
    properties.set("limit", limit);

    schema.set("properties", properties);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("bearerToken");
    schema.set("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  private ObjectNode toolsCallResult(JsonNode params) {
    String toolName = params.path("name").asText("");
    JsonNode arguments = params.path("arguments");
    McpToolView tool = findTool(toolName);
    String bearerToken = arguments.path("bearerToken").asText("");
    int limit = normalizeLimit(arguments.path("limit").asInt(50));
    ProbeResult probeResult = ordsProbeService.runProbe(new ProbeCommand(tool.objectId(), bearerToken, limit));

    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("toolName", tool.name());
    payload.put("objectId", tool.objectId());
    payload.put("object", tool.displayName());
    payload.put("ordsPath", tool.ordsPath());
    payload.put("status", probeResult.status().name());
    payload.put("rowCount", probeResult.rowCount());
    payload.set("columns", objectMapper.valueToTree(probeResult.columns()));
    payload.set("maskedColumns", objectMapper.valueToTree(probeResult.maskedColumns()));
    payload.set("rows", objectMapper.valueToTree(probeResult.rows()));
    payload.put("errorCode", probeResult.errorCode());
    payload.put("errorMessage", probeResult.errorMessage());
    payload.put("requestHeaders", probeResult.requestHeaders());
    payload.put("requestPayload", probeResult.requestPayload());
    payload.put("responseHeaders", probeResult.responseHeaders());
    payload.put("responseBody", probeResult.responseBody());

    ObjectNode result = objectMapper.createObjectNode();
    ArrayNode content = objectMapper.createArrayNode();
    ObjectNode text = objectMapper.createObjectNode();
    text.put("type", "text");
    text.put("text", pretty(payload));
    content.add(text);
    result.set("content", content);
    result.put("isError", probeResult.errorCode() != null);
    return result;
  }

  private McpToolView findTool(String toolName) {
    List<McpToolView> tools = toolRegistry.listTools();
    return tools.stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(() -> new AppException("MCP tool을 찾을 수 없습니다: " + toolName));
  }

  private int normalizeLimit(int limit) {
    if (limit < 1) {
      return 50;
    }
    return Math.min(limit, 500);
  }

  private String pretty(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }
}
