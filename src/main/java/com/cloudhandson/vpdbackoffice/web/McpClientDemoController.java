package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.McpClientDemoService;
import com.cloudhandson.vpdbackoffice.service.McpToolRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class McpClientDemoController {

  private final McpToolRegistry toolRegistry;
  private final McpClientDemoService demoService;

  public McpClientDemoController(McpToolRegistry toolRegistry, McpClientDemoService demoService) {
    this.toolRegistry = toolRegistry;
    this.demoService = demoService;
  }

  @GetMapping("/mcp-client-demo")
  public String page(Model model) {
    addTools(model);
    return "mcp-client-demo";
  }

  @PostMapping("/mcp-client-demo")
  public String run(
      @RequestParam(defaultValue = "vpd-live") String contextPath,
      @RequestParam(defaultValue = "") String toolName,
      @RequestParam(defaultValue = "") String bearerToken,
      @RequestParam(defaultValue = "50") int limit,
      HttpServletRequest request,
      Model model
  ) {
    addTools(model);
    try {
      String serverOrigin = ServletUriComponentsBuilder.fromRequestUri(request)
          .replacePath(null)
          .replaceQuery(null)
          .build()
          .toUriString();
      model.addAttribute("result", demoService.run(serverOrigin, contextPath, toolName, bearerToken, limit));
    } catch (Exception e) {
      model.addAttribute("errorMessage", e.getMessage());
    }
    return "fragments/mcp-client-demo-result :: result";
  }

  private void addTools(Model model) {
    try {
      model.addAttribute("tools", toolRegistry.listTools());
    } catch (DataAccessException e) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(e);
      model.addAttribute("tools", List.of());
      model.addAttribute("runtimeError", message);
    }
  }
}
