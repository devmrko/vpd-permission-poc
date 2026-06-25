package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.mcp.McpChatbotResult;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpClientDemoResult;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpToolView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class McpChatbotService {

  private final McpToolRegistry toolRegistry;
  private final McpClientDemoService mcpClient;
  private final OpenAiCompatibleClient aiClient;
  private final ObjectMapper objectMapper;

  public McpChatbotService(
      McpToolRegistry toolRegistry,
      McpClientDemoService mcpClient,
      OpenAiCompatibleClient aiClient,
      ObjectMapper objectMapper
  ) {
    this.toolRegistry = toolRegistry;
    this.mcpClient = mcpClient;
    this.aiClient = aiClient;
    this.objectMapper = objectMapper;
  }

  public McpChatbotResult chat(
      String serverOrigin,
      String contextPath,
      String question,
      String bearerToken,
      int limit
  ) {
    String normalizedQuestion = normalizeQuestion(question);
    List<McpToolView> tools = toolRegistry.listTools();
    if (tools.isEmpty()) {
      throw new AppException("라우팅할 MCP tool이 없습니다. ORDS 조회 Handler 대상을 먼저 등록하세요.");
    }
    McpToolView selectedTool = selectTool(tools, normalizedQuestion);
    McpClientDemoResult clientResult = mcpClient.run(
        serverOrigin,
        contextPath,
        selectedTool.name(),
        bearerToken,
        limit
    );
    String routingReason = routingReason(selectedTool, normalizedQuestion);
    String answer = answer(normalizedQuestion, selectedTool, clientResult, routingReason);
    return new McpChatbotResult(
        clientResult.contextPath(),
        normalizedQuestion,
        selectedTool.name(),
        clientResult.status(),
        answer,
        routingReason,
        clientResult.toolsCallResponse(),
        clientResult.toolsListResponse()
    );
  }

  private McpToolView selectTool(List<McpToolView> tools, String question) {
    String normalized = normalizeForMatch(question);
    return tools.stream()
        .max(Comparator.comparingInt(tool -> score(tool, normalized)))
        .orElseThrow();
  }

  private int score(McpToolView tool, String question) {
    int score = 0;
    for (String token : tokens(tool.name())) {
      if (question.contains(token)) {
        score += 3;
      }
    }
    for (String token : tokens(tool.displayName())) {
      if (question.contains(token)) {
        score += 4;
      }
    }
    for (String token : tokens(tool.ordsPath())) {
      if (question.contains(token)) {
        score += 2;
      }
    }
    return score;
  }

  private String routingReason(McpToolView tool, String question) {
    int score = score(tool, normalizeForMatch(question));
    if (score <= 0) {
      return "질문에서 특정 보호 객체명을 찾지 못해 첫 번째 MCP tool을 선택했습니다: " + tool.displayName();
    }
    return "질문과 보호 객체/tool 이름이 매칭되어 선택했습니다: " + tool.displayName()
        + " (" + tool.name() + ")";
  }

  private String answer(
      String question,
      McpToolView tool,
      McpClientDemoResult clientResult,
      String routingReason
  ) {
    String fallback = fallbackAnswer(question, tool, clientResult, routingReason);
    if (!aiClient.configured() || !"SUCCESS".equals(clientResult.status())) {
      return fallback;
    }
    try {
      return aiClient.chat(systemPrompt(), userPrompt(question, tool, clientResult, routingReason));
    } catch (Exception e) {
      return fallback + "\n\nAI 답변 생성은 실패했습니다: " + e.getMessage();
    }
  }

  private String fallbackAnswer(
      String question,
      McpToolView tool,
      McpClientDemoResult clientResult,
      String routingReason
  ) {
    if (!"SUCCESS".equals(clientResult.status())) {
      return """
          질문을 라우팅했습니다.

          선택한 tool: %s
          라우팅 근거: %s

          Bearer Token이 없어 ORDS tools/call은 실행하지 않았습니다. 토큰을 입력하면 실제 VPD/ORDS 결과까지 조회합니다.
          """.formatted(tool.name(), routingReason);
    }

    JsonNode result = parse(clientResult.toolsCallResponse());
    String toolText = result.path("result").path("content").path(0).path("text").asText("{}");
    JsonNode payload = parse(toolText);
    String status = payload.path("status").asText("UNKNOWN");
    int rowCount = payload.path("rowCount").asInt(0);
    JsonNode maskedColumns = payload.path("maskedColumns");
    return """
        질문을 MCP tool로 라우팅해 ORDS/VPD 결과를 조회했습니다.

        선택한 tool: %s
        라우팅 근거: %s
        ORDS 상태: %s
        반환 행 수: %d
        NULL 처리 컬럼: %s

        질문: %s
        """.formatted(tool.name(), routingReason, status, rowCount, maskedColumns.toString(), question);
  }

  private String systemPrompt() {
    return """
        당신은 Oracle ORDS/VPD MCP 라우팅 결과를 설명하는 운영 보조자입니다.
        제공된 MCP tool 결과 JSON만 근거로 한국어로 간결하게 답변하세요.
        Bearer Token 원문은 절대 출력하지 마세요.
        """;
  }

  private String userPrompt(String question, McpToolView tool, McpClientDemoResult clientResult, String routingReason) {
    return """
        질문:
        %s

        선택한 tool:
        - name: %s
        - object: %s
        - ordsPath: %s

        라우팅 근거:
        %s

        MCP tools/call 응답:
        %s

        답변 형식:
        1. 한 문장 요약
        2. 선택한 tool과 근거
        3. 행 필터/컬럼 NULL 처리/오류 여부
        4. 운영자가 다음에 확인할 것
        """.formatted(question, tool.name(), tool.displayName(), tool.ordsPath(), routingReason, clientResult.toolsCallResponse());
  }

  private JsonNode parse(String value) {
    try {
      return objectMapper.readTree(value);
    } catch (Exception e) {
      return objectMapper.createObjectNode().put("raw", value);
    }
  }

  private String normalizeQuestion(String question) {
    if (question == null || question.isBlank()) {
      return "현재 토큰으로 조회 가능한 데이터를 요약해줘.";
    }
    return question.trim();
  }

  private List<String> tokens(String value) {
    return List.of(normalizeForMatch(value).split("[^a-z0-9가-힣]+")).stream()
        .filter(token -> token.length() >= 2)
        .toList();
  }

  private String normalizeForMatch(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
