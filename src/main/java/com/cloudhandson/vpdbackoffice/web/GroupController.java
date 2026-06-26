package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.group.GroupCreateCommand;
import com.cloudhandson.vpdbackoffice.service.GroupService;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GroupController {

  private final GroupService groupService;
  private final UserService userService;
  private final PermissionService permissionService;

  public GroupController(GroupService groupService, UserService userService, PermissionService permissionService) {
    this.groupService = groupService;
    this.userService = userService;
    this.permissionService = permissionService;
  }

  @GetMapping("/groups")
  public String groups(Model model) {
    model.addAttribute("groups", groupService.findAll());
    model.addAttribute("users", userService.findAll());
    model.addAttribute("roles", permissionService.findRoles());
    model.addAttribute("groupUsers", groupService.findGroupUsers());
    model.addAttribute("groupRoles", groupService.findGroupRoles());
    return "groups";
  }

  @PostMapping("/groups")
  public String create(
      @RequestParam String groupCode,
      @RequestParam String groupName,
      @RequestParam(required = false) String description,
      RedirectAttributes redirectAttributes
  ) {
    groupService.createGroup(new GroupCreateCommand(groupCode, groupName, description));
    redirectAttributes.addFlashAttribute("message", "그룹을 추가했습니다.");
    return "redirect:/groups";
  }

  @PostMapping("/groups/active")
  public String active(
      @RequestParam long groupId,
      @RequestParam boolean active,
      RedirectAttributes redirectAttributes
  ) {
    groupService.setActive(groupId, active);
    redirectAttributes.addFlashAttribute("message", "그룹 상태를 변경했습니다.");
    return "redirect:/groups";
  }

  @PostMapping("/groups/users")
  public String addUser(
      @RequestParam long groupId,
      @RequestParam long userId,
      RedirectAttributes redirectAttributes
  ) {
    groupService.addUser(groupId, userId);
    redirectAttributes.addFlashAttribute("message", "그룹에 사용자를 추가했습니다.");
    return "redirect:/groups";
  }

  @PostMapping("/groups/users/delete")
  public String removeUser(
      @RequestParam long groupId,
      @RequestParam long userId,
      RedirectAttributes redirectAttributes
  ) {
    groupService.removeUser(groupId, userId);
    redirectAttributes.addFlashAttribute("message", "그룹 사용자를 해제했습니다.");
    return "redirect:/groups";
  }

  @PostMapping("/groups/roles")
  public String addRole(
      @RequestParam long groupId,
      @RequestParam long roleId,
      RedirectAttributes redirectAttributes
  ) {
    groupService.addRole(groupId, roleId);
    redirectAttributes.addFlashAttribute("message", "그룹에 역할을 부여했습니다.");
    return "redirect:/groups";
  }

  @PostMapping("/groups/roles/delete")
  public String removeRole(
      @RequestParam long groupId,
      @RequestParam long roleId,
      RedirectAttributes redirectAttributes
  ) {
    groupService.removeRole(groupId, roleId);
    redirectAttributes.addFlashAttribute("message", "그룹 역할을 해제했습니다.");
    return "redirect:/groups";
  }
}
