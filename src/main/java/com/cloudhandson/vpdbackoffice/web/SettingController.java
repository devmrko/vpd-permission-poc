package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.SettingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingController {

  private final SettingService settingService;

  public SettingController(SettingService settingService) {
    this.settingService = settingService;
  }

  @GetMapping("/settings")
  public String settings(Model model) {
    model.addAttribute("ordsBaseUrl", settingService.ordsBaseUrl());
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
}
