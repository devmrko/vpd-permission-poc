package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.McpSseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class McpSseController {

  private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

  private final McpSseService mcpSseService;
  private final Map<String, McpSseSession> sessions = new ConcurrentHashMap<>();

  public McpSseController(McpSseService mcpSseService) {
    this.mcpSseService = mcpSseService;
  }

  @GetMapping(path = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter defaultSse() throws IOException {
    return openSse("default");
  }

  @GetMapping(path = "/mcp/{contextPath}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter contextSse(@PathVariable String contextPath) throws IOException {
    return openSse(contextPath);
  }

  @PostMapping(path = "/mcp/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> defaultMessage(
      @RequestParam(required = false) String sessionId,
      @RequestBody JsonNode request
  ) throws IOException {
    return handleMessage("default", sessionId, request);
  }

  @PostMapping(path = "/mcp/{contextPath}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> contextMessage(
      @PathVariable String contextPath,
      @RequestParam(required = false) String sessionId,
      @RequestBody JsonNode request
  ) throws IOException {
    return handleMessage(contextPath, sessionId, request);
  }

  private SseEmitter openSse(String contextPath) throws IOException {
    String normalizedContextPath = normalizeContextPath(contextPath);
    String sessionId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    sessions.put(sessionId, new McpSseSession(normalizedContextPath, emitter));
    emitter.onCompletion(() -> sessions.remove(sessionId));
    emitter.onTimeout(() -> sessions.remove(sessionId));
    emitter.onError(error -> sessions.remove(sessionId));
    emitter.send(SseEmitter.event()
        .name("endpoint")
        .data(messageEndpoint(normalizedContextPath, sessionId)));
    return emitter;
  }

  private ResponseEntity<?> handleMessage(String contextPath, String sessionId, JsonNode request) throws IOException {
    String normalizedContextPath = normalizeContextPath(contextPath);
    ObjectNode response = mcpSseService.handle(normalizedContextPath, request);
    if (sessionId == null || sessionId.isBlank()) {
      return ResponseEntity.ok(response);
    }

    McpSseSession session = sessions.get(sessionId);
    if (session == null || !session.contextPath().equals(normalizedContextPath)) {
      return ResponseEntity.notFound().build();
    }
    try {
      session.emitter().send(SseEmitter.event().name("message").data(response));
      return ResponseEntity.accepted().build();
    } catch (IOException e) {
      sessions.remove(sessionId);
      throw e;
    }
  }

  private String messageEndpoint(String contextPath, String sessionId) {
    if ("default".equals(contextPath)) {
      return "/mcp/messages?sessionId=" + sessionId;
    }
    return "/mcp/" + contextPath + "/messages?sessionId=" + sessionId;
  }

  private String normalizeContextPath(String contextPath) {
    String normalized = contextPath == null || contextPath.isBlank() ? "default" : contextPath.trim();
    if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
      throw new IllegalArgumentException("MCP context path는 영문/숫자로 시작하고 영문/숫자/_/-만 사용할 수 있습니다: " + contextPath);
    }
    return normalized.toLowerCase();
  }

  private record McpSseSession(String contextPath, SseEmitter emitter) {
  }
}
