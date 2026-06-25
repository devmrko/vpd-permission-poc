package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.mcp.McpReasoningCommand;
import com.cloudhandson.vpdbackoffice.domain.mcp.McpToolView;
import com.cloudhandson.vpdbackoffice.service.McpReasoningService;
import com.cloudhandson.vpdbackoffice.service.McpToolRegistry;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class McpReasoningController {

  private final McpToolRegistry toolRegistry;
  private final McpReasoningService reasoningService;
  private final ProtectedObjectService protectedObjectService;

  public McpReasoningController(
      McpToolRegistry toolRegistry,
      McpReasoningService reasoningService,
      ProtectedObjectService protectedObjectService
  ) {
    this.toolRegistry = toolRegistry;
    this.reasoningService = reasoningService;
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/mcp-reasoning")
  public String page(Model model) {
    try {
      model.addAttribute("objects", protectedObjectService.findEnabled());
      model.addAttribute("tools", toolRegistry.listTools());
    } catch (DataAccessException e) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(e);
      model.addAttribute("objects", List.of());
      model.addAttribute("tools", List.of());
      model.addAttribute("runtimeError", message);
    }
    return "mcp-reasoning";
  }

  @GetMapping("/mcp/tools")
  @ResponseBody
  public Object tools() {
    try {
      return toolRegistry.listTools();
    } catch (DataAccessException e) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(e);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("status", "DB_NOT_AVAILABLE");
      response.put("title", message.title());
      response.put("message", message.message());
      response.put("tools", List.of());
      return response;
    }
  }

  @GetMapping("/mcp-sse")
  public String ssePage(Model model) {
    try {
      model.addAttribute("tools", toolRegistry.listTools());
    } catch (DataAccessException e) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(e);
      model.addAttribute("tools", List.of());
      model.addAttribute("runtimeError", message);
    }
    return "mcp-sse";
  }

  @PostMapping("/mcp-reasoning")
  public String reason(
      @RequestParam long objectId,
      @RequestParam String bearerToken,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "") String question,
      Model model
  ) {
    model.addAttribute("result", reasoningService.reason(
        new McpReasoningCommand(objectId, bearerToken, limit, question)));
    return "fragments/mcp-reasoning-result :: result";
  }
}
