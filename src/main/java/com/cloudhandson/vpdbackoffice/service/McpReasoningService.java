package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.mcp.McpReasoningCommand;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpReasoningResult;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpToolView;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeCommand;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeResult;
import com.cloudhandson.vpdbackoffice.domain.probe.ProbeStatus;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class McpReasoningService {

  private static final int MAX_EVIDENCE_ROWS = 20;

  private final ProtectedObjectService protectedObjectService;
  private final OrdsProbeService ordsProbeService;
  private final McpToolRegistry toolRegistry;
  private final OpenAiCompatibleClient aiClient;
  private final ObjectMapper objectMapper;

  public McpReasoningService(
      ProtectedObjectService protectedObjectService,
      OrdsProbeService ordsProbeService,
      McpToolRegistry toolRegistry,
      OpenAiCompatibleClient aiClient,
      ObjectMapper objectMapper
  ) {
    this.protectedObjectService = protectedObjectService;
    this.ordsProbeService = ordsProbeService;
    this.toolRegistry = toolRegistry;
    this.aiClient = aiClient;
    this.objectMapper = objectMapper;
  }

  public McpReasoningResult reason(McpReasoningCommand command) {
    ProtectedObject object = protectedObjectService.assertEnabled(command.objectId());
    McpToolView tool = toolRegistry.toolFor(object);
    ProbeResult probeResult = ordsProbeService.runProbe(new ProbeCommand(
        command.objectId(),
        command.bearerToken(),
        normalizeLimit(command.limit())
    ));
    String evidenceJson = evidenceJson(tool, probeResult);
    String prompt = buildPrompt(command.question(), tool, evidenceJson);

    if (!aiClient.configured()) {
      return new McpReasoningResult(
          "AI_NOT_CONFIGURED",
          tool.name(),
          fallbackAnswer(command.question(), probeResult),
          prompt,
          evidenceJson,
          probeResult
      );
    }

    try {
      String answer = aiClient.chat(systemPrompt(), prompt);
      return new McpReasoningResult("SUCCESS", tool.name(), answer, prompt, evidenceJson, probeResult);
    } catch (Exception e) {
      return new McpReasoningResult(
          "AI_CALL_FAILED",
          tool.name(),
          "AI 호출은 실패했지만 ORDS 도구 실행 증거는 수집했습니다. 상세: " + e.getMessage(),
          prompt,
          evidenceJson,
          probeResult
      );
    }
  }

  private int normalizeLimit(int limit) {
    if (limit < 1) {
      return 50;
    }
    return Math.min(limit, 500);
  }

  private String evidenceJson(McpToolView tool, ProbeResult result) {
    try {
      ObjectNode evidence = objectMapper.createObjectNode();
      evidence.put("toolName", tool.name());
      evidence.put("objectId", tool.objectId());
      evidence.put("displayName", tool.displayName());
      evidence.put("ordsPath", tool.ordsPath());
      evidence.put("status", result.status().name());
      evidence.put("rowCount", result.rowCount());
      evidence.set("columns", objectMapper.valueToTree(result.columns()));
      evidence.set("maskedColumns", objectMapper.valueToTree(result.maskedColumns()));
      evidence.set("rows", objectMapper.valueToTree(limitedRows(result)));
      evidence.put("errorCode", result.errorCode());
      evidence.put("errorMessage", result.errorMessage());
      evidence.put("requestHeaders", result.requestHeaders());
      evidence.put("requestPayload", result.requestPayload());
      evidence.put("responseHeaders", result.responseHeaders());
      evidence.put("responseBody", result.responseBody());
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
    } catch (Exception e) {
      throw new AppException("MCP evidence JSON 생성 실패: " + e.getMessage());
    }
  }

  private List<?> limitedRows(ProbeResult result) {
    if (result.rows().size() <= MAX_EVIDENCE_ROWS) {
      return result.rows();
    }
    return result.rows().subList(0, MAX_EVIDENCE_ROWS);
  }

  private String buildPrompt(String question, McpToolView tool, String evidenceJson) {
    String normalizedQuestion = question == null || question.isBlank()
        ? "요약부터 작성해줘. 이 ORDS/VPD 검증 결과에서 조회 행 수, 주요 식별자, NULL 처리 여부, 권한 범위, 다음 확인 조치를 정리해줘."
        : question.trim();
    return """
        질문:
        %s

        사용한 도구:
        - name: %s
        - object: %s
        - ordsPath: %s

        도구 실행 증거(JSON):
        %s

        답변 요구사항:
        - 한국어로 답변한다.
        - 증거 JSON에 없는 데이터는 추측하지 않는다.
        - 첫 섹션은 "## 요약"으로 시작하고 3줄 이내로 쓴다.
        - 그 다음 "## 판단 근거" 섹션에 표를 사용해 rowCount, maskedColumns, status, errorCode를 정리한다.
        - 그 다음 "## 상세" 섹션에서 반환 행과 권한 범위를 설명한다.
        - 마지막 "## 다음 조치" 섹션은 운영자가 확인할 항목만 짧게 쓴다.
        - VPD 행 필터, 컬럼 NULL 처리, ORDS 오류 여부를 구분한다.
        """.formatted(normalizedQuestion, tool.name(), tool.displayName(), tool.ordsPath(), evidenceJson);
  }

  private String systemPrompt() {
    return """
        당신은 Oracle ADB VPD/Redaction/ORDS 권한 검증 보조자입니다.
        백오피스가 제공한 도구 실행 증거만 근거로 판단하고, 토큰 원문이나 비밀 값을 재출력하지 마세요.
        """;
  }

  private String fallbackAnswer(String question, ProbeResult result) {
    if (result.status() == ProbeStatus.SUCCESS) {
      return """
          ## 요약
          AI base URL/API Key 설정이 없어 모델 호출은 건너뛰었습니다.
          ORDS 호출은 성공했고 %d건이 반환되었습니다.

          ## 판단 근거
          | 항목 | 값 |
          | --- | --- |
          | status | %s |
          | rowCount | %d |
          | maskedColumns | %s |

          ## 상세
          질문: %s

          ## 다음 조치
          - 모델 설명이 필요하면 AI 설정을 확인하세요.
          - 행/컬럼 결과는 아래 Tool Evidence JSON과 Response Body를 기준으로 확인하세요.
          """.formatted(
          result.rowCount(),
          result.status().name(),
          result.rowCount(),
          result.maskedColumns().isEmpty() ? "-" : String.join(", ", result.maskedColumns()),
          safeQuestion(question)
      );
    }
    return """
        ## 요약
        AI base URL/API Key 설정이 없어 모델 호출은 건너뛰었습니다.
        ORDS 도구 실행은 성공 상태가 아닙니다.

        ## 판단 근거
        | 항목 | 값 |
        | --- | --- |
        | status | %s |
        | errorMessage | %s |

        ## 상세
        request/response 증거를 확인해 ORDS path, token, handler 오류를 구분하세요.

        ## 다음 조치
        - ORDS path와 token 만료/폐기 상태를 확인하세요.
        - handler 오류면 Response Body의 ORDS error를 확인하세요.
        """.formatted(
        result.status().name(),
        result.errorMessage() == null ? "없음" : result.errorMessage()
    );
  }

  private String safeQuestion(String question) {
    if (question == null || question.isBlank()) {
      return "기본 요약";
    }
    return question.trim();
  }
}
