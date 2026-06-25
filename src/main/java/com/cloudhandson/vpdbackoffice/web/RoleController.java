package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.PermissionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoleController {

  private final PermissionService permissionService;

  public RoleController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping("/roles")
  public String roles(Model model) {
    model.addAttribute("roles", permissionService.findRoles());
    return "roles";
  }

  @PostMapping("/roles")
  public String create(
      @RequestParam String roleName,
      @RequestParam(required = false) String description,
      @RequestParam(defaultValue = "PUBLIC") String maxSensitivityLevel,
      RedirectAttributes redirectAttributes
  ) {
    permissionService.createRole(roleName, description, maxSensitivityLevel);
    redirectAttributes.addFlashAttribute("message", "역할을 추가했습니다.");
    return "redirect:/roles";
  }

  @PostMapping("/roles/max-sensitivity")
  public String updateMaxSensitivity(
      @RequestParam long roleId,
      @RequestParam String maxSensitivityLevel,
      RedirectAttributes redirectAttributes
  ) {
    permissionService.updateRoleMaxSensitivity(roleId, maxSensitivityLevel);
    redirectAttributes.addFlashAttribute("message", "역할 민감도 허용 상한을 수정했습니다.");
    return "redirect:/roles";
  }

  @PostMapping("/roles/delete")
  public String delete(@RequestParam long roleId, RedirectAttributes redirectAttributes) {
    permissionService.deleteRole(roleId);
    redirectAttributes.addFlashAttribute("message", "역할을 삭제했습니다.");
    return "redirect:/roles";
  }
}
