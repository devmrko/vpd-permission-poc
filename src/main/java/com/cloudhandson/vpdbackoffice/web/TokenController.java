package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.token.TokenIssueCommand;
import com.cloudhandson.vpdbackoffice.mapper.UserMapper;
import com.cloudhandson.vpdbackoffice.service.BearerTokenService;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TokenController {

  private final BearerTokenService tokenService;
  private final UserMapper userMapper;

  public TokenController(BearerTokenService tokenService, UserMapper userMapper) {
    this.tokenService = tokenService;
    this.userMapper = userMapper;
  }

  @GetMapping("/tokens")
  public String tokens(Model model) {
    model.addAttribute("tokens", tokenService.findAll());
    model.addAttribute("users", userMapper.findAll());
    return "tokens";
  }

  @PostMapping("/tokens")
  public String issue(
      @RequestParam long userId,
      @RequestParam String expiresAt,
      @RequestParam(required = false) String description,
      RedirectAttributes redirectAttributes
  ) {
    var issued = tokenService.issueToken(new TokenIssueCommand(
        userId,
        OffsetDateTime.parse(expiresAt),
        description
    ));
    redirectAttributes.addFlashAttribute("issued", issued);
    return "redirect:/tokens";
  }

  @PostMapping("/tokens/revoke")
  public String revoke(
      @RequestParam long keyId,
      @RequestParam(required = false) String reason,
      RedirectAttributes redirectAttributes
  ) {
    tokenService.revokeToken(keyId, reason);
    redirectAttributes.addFlashAttribute("message", "토큰을 회수했습니다.");
    return "redirect:/tokens";
  }
}
