package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObjectCreateCommand;
import com.cloudhandson.vpdbackoffice.service.AppException;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProtectedObjectController {

  private final ProtectedObjectService protectedObjectService;

  public ProtectedObjectController(ProtectedObjectService protectedObjectService) {
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/objects")
  public String objects(Model model) {
    model.addAttribute("objects", protectedObjectService.findEnabled());
    model.addAttribute("dbObjects", protectedObjectService.findDatabaseObjects());
    return "objects";
  }

  @PostMapping("/objects")
  public String create(
      @RequestParam String owner,
      @RequestParam String objectName,
      @RequestParam String ordsPath,
      @RequestParam(required = false) String columns,
      @RequestParam(required = false) String sensitiveColumns,
      RedirectAttributes redirectAttributes
  ) {
    try {
      protectedObjectService.createObject(
          new ProtectedObjectCreateCommand(owner, objectName, ordsPath, columns, sensitiveColumns));
      redirectAttributes.addFlashAttribute("message", "보호 객체를 추가했습니다.");
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    }
    return "redirect:/objects";
  }

  @PostMapping("/objects/ords-path")
  public String updateOrdsPath(
      @RequestParam long objectId,
      @RequestParam String ordsPath,
      RedirectAttributes redirectAttributes
  ) {
    try {
      protectedObjectService.updateOrdsPath(objectId, ordsPath);
      redirectAttributes.addFlashAttribute("message", "ORDS Path를 수정했습니다.");
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    }
    return "redirect:/objects";
  }

  @PostMapping("/objects/disable")
  public String disable(@RequestParam long objectId, RedirectAttributes redirectAttributes) {
    protectedObjectService.disableObject(objectId);
    redirectAttributes.addFlashAttribute("message", "보호 객체를 비활성화했습니다.");
    return "redirect:/objects";
  }
}
