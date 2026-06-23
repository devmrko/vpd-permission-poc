package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSetCommand;
import com.cloudhandson.vpdbackoffice.domain.permission.RuleCommand;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PermissionController {

  private final PermissionService permissionService;
  private final ProtectedObjectService protectedObjectService;

  public PermissionController(
      PermissionService permissionService,
      ProtectedObjectService protectedObjectService
  ) {
    this.permissionService = permissionService;
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/permissions")
  public String permissions(Model model) {
    model.addAttribute("roles", permissionService.findRoles());
    model.addAttribute("objects", protectedObjectService.findEnabled());
    return "permissions";
  }

  @PostMapping("/permissions")
  public String save(
      @RequestParam long roleId,
      @RequestParam long objectId,
      @RequestParam String ruleType,
      @RequestParam(required = false) String ruleValue,
      @RequestParam(required = false) List<String> visibleColumns,
      RedirectAttributes redirectAttributes
  ) {
    permissionService.savePermissionSet(new PermissionSetCommand(
        roleId,
        objectId,
        "SELECT",
        List.of(new RuleCommand(ruleType, ruleValue)),
        visibleColumns == null ? List.of() : visibleColumns
    ));
    redirectAttributes.addFlashAttribute("message", "권한을 저장했습니다.");
    return "redirect:/permissions";
  }
}
