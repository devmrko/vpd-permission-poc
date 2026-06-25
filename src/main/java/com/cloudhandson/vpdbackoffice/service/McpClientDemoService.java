package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpClientDemoResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class McpClientDemoService {

  private final BackofficeProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  public McpClientDemoService(
      BackofficeProperties properties,
      ObjectMapper objectMapper,
      RestTemplateBuilder restTemplateBuilder
  ) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(15))
        .build();
  }

  public McpClientDemoResult run(
      String serverOrigin,
      String contextPath,
      String toolName,
      String bearerToken,
      int limit
  ) {
    String normalizedContextPath = normalizeContextPath(contextPath);
    URI messageUri = messageUri(serverOrigin, normalizedContextPath);

    JsonNode initializeResponse = post(messageUri, initializeRequest(1));
    JsonNode toolsListResponse = post(messageUri, toolsListRequest(2));
    JsonNode toolsCallResponse = null;
    if (hasText(toolName) && hasText(bearerToken)) {
      toolsCallResponse = post(messageUri, toolsCallRequest(3, toolName.trim(), bearerToken.trim(), normalizeLimit(limit)));
    }

    return new McpClientDemoResult(
        normalizedContextPath,
        messageUri.toString(),
        hasText(toolName) ? toolName.trim() : "",
        pretty(initializeResponse),
        pretty(toolsListResponse),
        toolsCallResponse == null ? "Bearer Token이 없어 tools/call은 실행하지 않았습니다." : pretty(toolsCallResponse),
        toolsCallResponse == null ? "TOOLS_LIST_ONLY" : "SUCCESS"
    );
  }

  private JsonNode post(URI uri, ObjectNode request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBasicAuth(properties.security().adminUser(), properties.security().adminPassword());
    String response = restTemplate.postForObject(uri, new HttpEntity<>(request, headers), String.class);
    try {
      return objectMapper.readTree(response);
    } catch (Exception e) {
      throw new AppException("MCP client 응답 JSON 파싱 실패: " + e.getMessage());
    }
  }

  private URI messageUri(String serverOrigin, String contextPath) {
    String path = "default".equals(contextPath) ? "/mcp/messages" : "/mcp/" + contextPath + "/messages";
    return UriComponentsBuilder.fromUriString(serverOrigin)
        .path(path)
        .build()
        .toUri();
  }

  private ObjectNode initializeRequest(int id) {
    ObjectNode request = request(id, "initialize");
    request.set("params", objectMapper.createObjectNode());
    return request;
  }

  private ObjectNode toolsListRequest(int id) {
    ObjectNode request = request(id, "tools/list");
    request.set("params", objectMapper.createObjectNode());
    return request;
  }

  private ObjectNode toolsCallRequest(int id, String toolName, String bearerToken, int limit) {
    ObjectNode request = request(id, "tools/call");
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", toolName);
    ObjectNode arguments = objectMapper.createObjectNode();
    arguments.put("bearerToken", bearerToken);
    arguments.put("limit", limit);
    params.set("arguments", arguments);
    request.set("params", params);
    return request;
  }

  private ObjectNode request(int id, String method) {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("id", id);
    request.put("method", method);
    return request;
  }

  private String normalizeContextPath(String contextPath) {
    String normalized = contextPath == null || contextPath.isBlank() ? "default" : contextPath.trim();
    if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
      throw new AppException("MCP context path는 영문/숫자로 시작하고 영문/숫자/_/-만 사용할 수 있습니다: " + contextPath);
    }
    return normalized.toLowerCase();
  }

  private int normalizeLimit(int limit) {
    if (limit < 1) {
      return 50;
    }
    return Math.min(limit, 500);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String pretty(JsonNode node) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (Exception e) {
      return String.valueOf(node);
    }
  }
}
