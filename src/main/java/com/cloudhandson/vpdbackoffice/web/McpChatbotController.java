package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.McpChatbotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class McpChatbotController {

  private final McpChatbotService chatbotService;

  public McpChatbotController(McpChatbotService chatbotService) {
    this.chatbotService = chatbotService;
  }

  @GetMapping("/mcp-chatbot")
  public String page() {
    return "mcp-chatbot";
  }

  @PostMapping("/mcp-chatbot")
  public String chat(
      @RequestParam(defaultValue = "vpd-live") String contextPath,
      @RequestParam(defaultValue = "") String question,
      @RequestParam(defaultValue = "") String bearerToken,
      @RequestParam(defaultValue = "50") int limit,
      HttpServletRequest request,
      Model model
  ) {
    try {
      String serverOrigin = ServletUriComponentsBuilder.fromRequestUri(request)
          .replacePath(null)
          .replaceQuery(null)
          .build()
          .toUriString();
      model.addAttribute("result", chatbotService.chat(serverOrigin, contextPath, question, bearerToken, limit));
    } catch (Exception e) {
      model.addAttribute("errorMessage", e.getMessage());
    }
    return "fragments/mcp-chatbot-result :: result";
  }
}
