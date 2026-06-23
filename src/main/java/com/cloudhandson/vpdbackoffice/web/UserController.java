package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.user.UserCreateCommand;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UserController {

  private final UserService userService;
  private final PermissionService permissionService;

  public UserController(UserService userService, PermissionService permissionService) {
    this.userService = userService;
    this.permissionService = permissionService;
  }

  @GetMapping("/users")
  public String users(Model model) {
    model.addAttribute("users", userService.findAll());
    model.addAttribute("roles", permissionService.findRoles());
    model.addAttribute("userRoles", userService.findUserRoles());
    return "users";
  }

  @PostMapping("/users")
  public String create(
      @RequestParam String username,
      @RequestParam String empNo,
      @RequestParam String deptCode,
      RedirectAttributes redirectAttributes
  ) {
    userService.createUser(new UserCreateCommand(username, empNo, deptCode));
    redirectAttributes.addFlashAttribute("message", "사용자를 추가했습니다.");
    return "redirect:/users";
  }

  @PostMapping("/users/active")
  public String active(
      @RequestParam long userId,
      @RequestParam boolean active,
      RedirectAttributes redirectAttributes
  ) {
    userService.setActive(userId, active);
    redirectAttributes.addFlashAttribute("message", "사용자 상태를 변경했습니다.");
    return "redirect:/users";
  }

  @PostMapping("/users/roles")
  public String grantRole(
      @RequestParam long userId,
      @RequestParam long roleId,
      RedirectAttributes redirectAttributes
  ) {
    userService.grantRole(userId, roleId);
    redirectAttributes.addFlashAttribute("message", "역할을 부여했습니다.");
    return "redirect:/users";
  }

  @PostMapping("/users/roles/delete")
  public String revokeRole(
      @RequestParam long userId,
      @RequestParam long roleId,
      RedirectAttributes redirectAttributes
  ) {
    userService.revokeRole(userId, roleId);
    redirectAttributes.addFlashAttribute("message", "역할을 해제했습니다.");
    return "redirect:/users";
  }
}
