package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.token.TokenIssueCommand;
import com.cloudhandson.vpdbackoffice.mapper.UserMapper;
import com.cloudhandson.vpdbackoffice.service.BearerTokenService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
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
  private final Clock clock;

  public TokenController(BearerTokenService tokenService, UserMapper userMapper, Clock clock) {
    this.tokenService = tokenService;
    this.userMapper = userMapper;
    this.clock = clock;
  }

  @GetMapping("/tokens")
  public String tokens(Model model) {
    model.addAttribute("tokens", tokenService.findAll());
    model.addAttribute("users", userMapper.findAll());
    model.addAttribute("defaultExpiresAt", defaultExpiresAt());
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
        parseBrowserDateTime(expiresAt),
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

  private String defaultExpiresAt() {
    return LocalDateTime.now(clock.withZone(ZoneId.systemDefault()))
        .plusMonths(1)
        .truncatedTo(ChronoUnit.MINUTES)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  private OffsetDateTime parseBrowserDateTime(String expiresAt) {
    return LocalDateTime.parse(expiresAt)
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime();
  }
}
