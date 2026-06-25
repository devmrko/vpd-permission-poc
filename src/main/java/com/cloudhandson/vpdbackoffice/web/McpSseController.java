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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
public class McpSseController {

  private static final long SSE_TIMEOUT_MILLIS = 30L * 60L * 1000L;

  private final McpSseService mcpSseService;
  private final Map<String, SseEmitter> sessions = new ConcurrentHashMap<>();

  public McpSseController(McpSseService mcpSseService) {
    this.mcpSseService = mcpSseService;
  }

  @GetMapping(path = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sse() throws IOException {
    String sessionId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    sessions.put(sessionId, emitter);
    emitter.onCompletion(() -> sessions.remove(sessionId));
    emitter.onTimeout(() -> sessions.remove(sessionId));
    emitter.onError(error -> sessions.remove(sessionId));
    emitter.send(SseEmitter.event()
        .name("endpoint")
        .data("/mcp/messages?sessionId=" + sessionId));
    return emitter;
  }

  @PostMapping(path = "/mcp/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> message(
      @RequestParam(required = false) String sessionId,
      @RequestBody JsonNode request
  ) throws IOException {
    ObjectNode response = mcpSseService.handle(request);
    if (sessionId == null || sessionId.isBlank()) {
      return ResponseEntity.ok(response);
    }

    SseEmitter emitter = sessions.get(sessionId);
    if (emitter == null) {
      return ResponseEntity.notFound().build();
    }
    try {
      emitter.send(SseEmitter.event().name("message").data(response));
      return ResponseEntity.accepted().build();
    } catch (IOException e) {
      sessions.remove(sessionId);
      throw e;
    }
  }
}
