package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.BackofficeSchemaService;
import com.cloudhandson.vpdbackoffice.service.SettingService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingController {

  private final SettingService settingService;
  private final BackofficeSchemaService backofficeSchemaService;

  public SettingController(SettingService settingService, BackofficeSchemaService backofficeSchemaService) {
    this.settingService = settingService;
    this.backofficeSchemaService = backofficeSchemaService;
  }

  @GetMapping("/settings")
  public String settings(Model model) {
    try {
      model.addAttribute("ordsBaseUrl", settingService.ordsBaseUrl());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage error = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("ordsBaseUrl", "");
      model.addAttribute("runtimeErrorTitle", error.title());
      model.addAttribute("runtimeErrorMessage", error.message());
    }
    return "settings";
  }

  @PostMapping("/settings/ords")
  public String updateOrds(
      @RequestParam String ordsBaseUrl,
      RedirectAttributes redirectAttributes
  ) {
    settingService.updateOrdsBaseUrl(ordsBaseUrl);
    redirectAttributes.addFlashAttribute("message", "ORDS 설정을 저장했습니다.");
    return "redirect:/settings";
  }

  @PostMapping("/settings/schema/initialize")
  public String initializeSchema(RedirectAttributes redirectAttributes) {
    redirectAttributes.addFlashAttribute("schemaResults", backofficeSchemaService.initializeSchema());
    redirectAttributes.addFlashAttribute("message", "백오피스 지원 테이블 DDL을 실행했습니다.");
    return "redirect:/settings";
  }
}
